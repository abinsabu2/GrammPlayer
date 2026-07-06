package com.aes.grammplayer.util.tdlib

import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import com.aes.grammplayer.BuildConfig
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

object TelegramClientManager {

    var client: Client? = null
    val isInitialized: Boolean
        get() = client != null

    var activeStoragePath: String = ""
    private var activeFileDirectory: String = ""

    // Guards against re-entrant/concurrent logOut()/close() calls, and lets other
    // parts of the app (e.g. TdLibUpdateHandler's error filter) know teardown is in progress.
    @Volatile
    var isLoggingOut: Boolean = false
        private set

    private const val LOGOUT_TIMEOUT_MS = 10_000L

    /**
     * Initializes the TDLib client, automatically selecting the best storage location.
     */
    fun initialize() {
        if (isInitialized) return

        activeStoragePath = getBestAvailableStoragePath()
        Log.i("StorageManager", "Using storage path: $activeStoragePath")

        client = Client.create(TdLibUpdateHandler, null, null)
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        activeFileDirectory = "$activeStoragePath/files"
        val parameters = TdApi.SetTdlibParameters().apply {
            apiId = BuildConfig.API_ID
            apiHash = BuildConfig.API_HASH
            systemLanguageCode = "en"
            deviceModel = "Android TV"
            systemVersion = "10"
            applicationVersion = "1.0"
            databaseDirectory = activeStoragePath
            useMessageDatabase = true
            useSecretChats = false
            filesDirectory = activeFileDirectory
        }
        client?.send(parameters, TdLibUpdateHandler)
    }

    /**
     * Determines the best storage path (internal or external) based on availability.
     */
    fun getBestAvailableStoragePath(): String {
        val internalPath = GPlayerApplication.Companion.AppContext.filesDir.absolutePath + "/tdlib"
        val externalPath = getExternalStoragePath()

        if (externalPath != null) {
            val externalDir = File(externalPath)
            if (externalDir.exists() || externalDir.mkdirs()) {
                if (externalDir.canWrite()) {
                    return externalPath
                }
            }
        }

        return internalPath
    }

    /**
     * Finds a writable external storage path (USB, SD card, etc.).
     */
    private fun getExternalStoragePath(): String? {
        val context = GPlayerApplication.Companion.AppContext
        val externalStorageVolumes: Array<out File> = ContextCompat.getExternalFilesDirs(context, null)

        val externalStorage = externalStorageVolumes.firstOrNull {
            Environment.isExternalStorageRemovable(it) && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        }

        return externalStorage?.let { it.absolutePath + "/tdlib" }
    }

    /**
     * Deletes files from the currently active storage path.
     */
    fun clearDownloadedFiles(): Int {
        var deletedFilesCount = 0
        val subdirectoriesToClear = listOf("documents", "temp", "videos")

        subdirectoriesToClear.forEach { subdir ->
            val directory = File(activeFileDirectory, subdir)
            if (directory.exists() && directory.isDirectory) {
                directory.walkTopDown().forEach { file ->
                    if (file.isFile && file.delete()) {
                        deletedFilesCount++
                    }
                }
            }
        }
        return deletedFilesCount
    }

    /**
     * Calculates the size of the activeFileDirectory and its contents.
     * @return The total size in MB, or 0.0 if the directory does not exist.
     */
    fun getDirectorySize(): Double {
        val directory = File(activeFileDirectory)
        if (!directory.exists() || !directory.isDirectory) {
            return 0.0
        }

        var totalSize = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        return totalSize / (1024.0 * 1024.0)
    }

    fun sendPhoneNumber(phone: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null), TdLibUpdateHandler)
    }

    /**
     * Sends the authentication code. The result is handled by the global handler.
     */
    fun sendAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), TdLibUpdateHandler)
    }

    /**
     * Starts a file download. Progress updates will be sent to the global handler.
     */
    fun startFileDownload(fileId: Int) {
        client?.send(TdApi.DownloadFile(fileId ?: 0, 1, 0, 0, false)) {
            Log.d("TDLib", "Download command sent for fileId=$fileId")
        }
    }

    /**
     * Loads all chats from Telegram and maps them to the local Chat model.
     * Suspends until all individual GetChat calls have completed.
     */
    suspend fun loadAllGroups(limit: Int = 100000, userId: Int): List<Chat> =
        suspendCancellableCoroutine { continuation ->
            client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit)) { result ->
                if (result is TdApi.Chats) {
                    val chatIds = result.chatIds

                    if (chatIds.isEmpty()) {
                        continuation.resume(emptyList())
                        return@send
                    }

                    val chats = Collections.synchronizedList(mutableListOf<Chat>())
                    val remaining = AtomicInteger(chatIds.size)

                    chatIds.forEach { chatId ->
                        client?.send(TdApi.GetChat(chatId)) { chatObj ->
                            if (chatObj is TdApi.Chat) {

                                // Skip "Telegram" system chat
                                if (chatObj.title == "Telegram") {
                                    remaining.decrementAndGet()
                                    return@send
                                }

                                // Skip chats whose last message is a contact registration notification
                                val lastMessage = chatObj.lastMessage
                                if (lastMessage != null && lastMessage.content is TdApi.MessageContactRegistered) {
                                    remaining.decrementAndGet()
                                    return@send
                                }

                                chats.add(chatObj.toAppChat(userId))
                            }

                            if (remaining.decrementAndGet() == 0) {
                                continuation.resume(chats)
                            }
                        }
                    }
                } else {
                    continuation.resume(emptyList())
                }
            }
        }

    /**
     * Maps a TdApi.Chat to the local Chat database model.
     */
    private fun TdApi.Chat.toAppChat(userId: Int): Chat {
        return Chat(
            id = this.id,
            type = when (this.type) {
                is TdApi.ChatTypePrivate    -> 0
                is TdApi.ChatTypeBasicGroup -> 1
                is TdApi.ChatTypeSupergroup -> 2
                is TdApi.ChatTypeSecret     -> 3
                else                        -> -1
            },
            title = this.title,
            photoId = this.photo?.small?.remote?.id ?: "",
            lastMessageId = this.lastMessage?.id?.toInt() ?: 0,
            order = this.positions.firstOrNull()?.order?.toInt() ?: 0,
            isPinned = this.positions.firstOrNull()?.isPinned ?: false,
            isMarkedAsUnread = this.isMarkedAsUnread,
            isBlocked = this.blockList != null,
            hasScheduledMessages = this.hasScheduledMessages,
            canBeDeletedOnlyForSelf = this.canBeDeletedOnlyForSelf,
            canBeDeletedForAllUsers = this.canBeDeletedForAllUsers,
            canBeReported = this.canBeReported,
            defaultDisableNotification = this.defaultDisableNotification,
            unreadCount = this.unreadCount,
            lastReadInboxMessageId = this.lastReadInboxMessageId.toInt(),
            lastReadOutboxMessageId = this.lastReadOutboxMessageId.toInt(),
            unreadMentionCount = this.unreadMentionCount,
            unreadReactionCount = this.unreadReactionCount,
            notificationSettingsMuteFor = this.notificationSettings.muteFor,
            replyMarkupMessageId = this.replyMarkupMessageId.toInt(),
            draftMessageText = (this.draftMessage?.inputMessageText as? TdApi.InputMessageText)
                ?.text?.text ?: "",
            clientData = this.clientData,
            userId = userId
        )
    }

    suspend fun loadMessagesForChat(
        chatId: Long,
        limit: Int = 100,
    ): List<MediaMessage> = withContext(Dispatchers.IO) {
        val allMessages = mutableListOf<MediaMessage>()
        var fromMessageId = 0L

        while (true) {
            val response = CompletableDeferred<TdApi.Object?>()
            client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) {
                response.complete(it)
            }

            val result = response.await()

            if (result !is TdApi.Messages || result.messages.isEmpty()) {
                break
            }

            result.messages
                .filter { message ->
                    message.content is TdApi.MessageVideo ||
                            message.content is TdApi.MessageDocument
                }
                .forEach { message ->
                    allMessages.add(parseMessageContent(message.content, chatId))
                }

            fromMessageId = result.messages.last().id
        }

        allMessages
    }

    fun cancelDownloadAndDelete(fileIds: MutableSet<Int>) {
        for (fileId in fileIds) {
            client?.send(TdApi.CancelDownloadFile(fileId, false)) {
                Log.d("TDLib", "Sent cancel command for fileId=$fileId")
            }
        }
        clearDownloadedFiles()
    }

    fun parseMessageContent(content: TdApi.MessageContent, chatId: Long): MediaMessage {
        return when (content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                val file = video.video
                val thumbnail = video.thumbnail

                MediaMessage(
                    id = file.id.toLong(),
                    chat = chatId.toInt(),
                    title = video.fileName.ifEmpty { "Video" },
                    description = content.caption.text,
                    studio = "Telegram",
                    isMedia = true,
                    localPath = file.local.path.ifEmpty { "" },
                    fileId = file.id,
                    mimeType = video.mimeType,
                    videoUrl = "",
                    width = video.width,
                    height = video.height,
                    duration = video.duration.toLong(),
                    size = file.size,
                    thumbnailPath = thumbnail?.file?.local?.path ?: "",
                    cardImageUrl = thumbnail?.file?.local?.path ?: "",
                    backgroundImageUrl = "",
                    isDownloaded = file.local.isDownloadingCompleted,
                    isDownloadActive = file.local.isDownloadingActive,
                    uniqueId = file.remote.uniqueId.ifEmpty { "" }
                )
            }

            is TdApi.MessageDocument -> {
                val document = content.document
                val file = document.document
                val thumbnail = document.thumbnail

                MediaMessage(
                    id = file.id.toLong(),
                    chat = chatId.toInt(),
                    title = document.fileName.ifEmpty { "Document" },
                    description = content.caption.text,
                    studio = "Telegram",
                    isMedia = true,
                    localPath = file.local.path.ifEmpty { "" },
                    fileId = file.id,
                    mimeType = document.mimeType,
                    videoUrl = "",
                    width = 0,
                    height = 0,
                    duration = 0L,
                    size = file.size,
                    thumbnailPath = thumbnail?.file?.local?.path ?: "",
                    cardImageUrl = thumbnail?.file?.local?.path ?: "",
                    backgroundImageUrl = "",
                    isDownloaded = file.local.isDownloadingCompleted,
                    isDownloadActive = file.local.isDownloadingActive,
                    uniqueId = file.remote.uniqueId.ifEmpty { "" }
                )
            }

            is TdApi.MessageText -> {
                MediaMessage(
                    id = content.hashCode().toLong(),
                    chat = chatId.toInt(),
                    title = content.text.text,
                    description = content.text.text,
                    studio = "Telegram",
                    isMedia = false,
                    localPath = "",
                    fileId = 0,
                    mimeType = "",
                    videoUrl = "",
                    width = 0,
                    height = 0,
                    duration = 0L,
                    size = 0L,
                    thumbnailPath = "",
                    cardImageUrl = "",
                    backgroundImageUrl = "",
                    isDownloaded = false,
                    isDownloadActive = false,
                    uniqueId = ""
                )
            }

            else -> {
                MediaMessage(
                    id = content.hashCode().toLong(),
                    chat = chatId.toInt(),
                    title = "Unsupported Content",
                    description = "This message type is not currently supported.",
                    studio = "Telegram",
                    isMedia = false,
                    localPath = "",
                    fileId = 0,
                    mimeType = "",
                    videoUrl = "",
                    width = 0,
                    height = 0,
                    duration = 0L,
                    size = 0L,
                    thumbnailPath = "",
                    cardImageUrl = "",
                    backgroundImageUrl = "",
                    isDownloaded = false,
                    isDownloadActive = false,
                    uniqueId = ""
                )
            }
        }
    }

    /**
     * Closes the TDLib client WITHOUT logging out (session persists server-side).
     * Waits for AuthorizationStateClosed before nulling the client reference, so a
     * subsequent initialize() can't race a still-finalizing native Client instance.
     * Falls back to nulling after a timeout if TDLib never reports Closed (shouldn't
     * normally happen, but avoids hanging forever if something goes wrong).
     */
    suspend fun close() {
        if (client == null) return
        isLoggingOut = true
        try {
            client?.send(TdApi.Close(), TdLibUpdateHandler)

            val closed = withTimeoutOrNull(LOGOUT_TIMEOUT_MS.milliseconds) {
                TdLibUpdateHandler.authorizationState
                    .filterIsInstance<TdApi.AuthorizationStateClosed>()
                    .first()
            }

            if (closed == null) {
                Log.w("TelegramClientManager", "Timed out waiting for AuthorizationStateClosed during close()")
            }
        } finally {
            client = null
            isLoggingOut = false
        }
    }

    /**
     * Logs the user out: terminates the server-side session and wipes TDLib's local
     * data. Waits for TDLib to confirm AuthorizationStateClosed before nulling the
     * client, then clears our own downloaded-file cache on top of that.
     *
     * IMPORTANT: this does NOT cancel any in-flight sync work (TelegramSyncWorker /
     * TelegramSyncListener collectors) — callers must stop that themselves before
     * invoking logOut(), otherwise pending TDLib requests made during teardown will
     * resolve with a "Request aborted" TdApi.Error. That error is expected TDLib
     * behavior during shutdown; filter it out in your error handler rather than
     * treating it as a real failure.
     *
     * @return number of locally cached files deleted after logout completed.
     */
    suspend fun logOut(): Int {
        if (client == null) return 0
        isLoggingOut = true
        try {
            client?.send(TdApi.LogOut(), TdLibUpdateHandler)

            val closed = withTimeoutOrNull(LOGOUT_TIMEOUT_MS.milliseconds) {
                TdLibUpdateHandler.authorizationState
                    .filterIsInstance<TdApi.AuthorizationStateClosed>()
                    .first()
            }

            if (closed == null) {
                Log.w("TelegramClientManager", "Timed out waiting for AuthorizationStateClosed during logOut()")
            }

            return clearDownloadedFiles()
        } finally {
            client = null
            isLoggingOut = false
        }
    }
}