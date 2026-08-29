package com.aes.grammplayer.util.tdlib

import android.content.Context
import android.util.Log
import com.aes.grammplayer.BuildConfig
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.helper.ApplicationHelper
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * One page of media messages from TDLib chat history.
 * [nextFromMessageId] is the cursor for the next page (oldest message id seen).
 */
data class MessagesPage(
    val items: List<MediaMessage>,
    val nextFromMessageId: Long,
    val endReached: Boolean
)

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
    private const val HISTORY_PAGE_TIMEOUT_MS = 15_000L

    /**
     * Initializes the TDLib client, automatically selecting the best storage location.
     */
    fun initialize() {
        if (isInitialized) return

        // ponytail: keep DB on internal (reliable, not vfat), files on external when SD present
        val internalPath = ApplicationHelper.getInternalStoragePath()
        val filesPath = ApplicationHelper.getBestAvailableStoragePath() // external if mounted else internal
        activeStoragePath = internalPath
        activeFileDirectory = ApplicationHelper.getFilesDirectory(filesPath)
        Log.i("StorageManager", "DB: $internalPath, Files: $activeFileDirectory (ext=${ApplicationHelper.isExternalStorageAvailable()} freeExt=${ApplicationHelper.formatFreeBytes(ApplicationHelper.getExternalFreeBytes())})")

        client = Client.create(TdLibUpdateHandler, null, null)
        Client.execute(TdApi.SetLogVerbosityLevel(1))
        val parameters = TdApi.SetTdlibParameters().apply {
            apiId = BuildConfig.API_ID
            apiHash = BuildConfig.API_HASH
            systemLanguageCode = "en"
            deviceModel = "Android TV"
            systemVersion = "10"
            applicationVersion = "1.0"
            databaseDirectory = internalPath
            useMessageDatabase = true
            useSecretChats = false
            filesDirectory = activeFileDirectory
        }
        client?.send(parameters, TdLibUpdateHandler)
    }

    /**
     * Clears downloaded media cache safely: asks TDLib to drop completed downloads,
     * deletes local files off the main thread, and resets download flags in Room.
     */
    suspend fun clearDownloadCache(context: Context): ApplicationHelper.ClearResult = withContext(Dispatchers.IO) {
        var totalCount = 0
        var totalBytes = 0L
        val candidates = listOf(
            activeFileDirectory,
            ApplicationHelper.getFilesDirectory(),
            ApplicationHelper.getInternalStoragePath() + "/files",
            ApplicationHelper.getInternalStoragePath() + "/tdlib/files"
        ).distinct().filter { it.isNotBlank() }
        for (dir in candidates) {
            try {
                val f = java.io.File(dir)
                if (f.exists() && f.isDirectory) {
                    val r = ApplicationHelper.clearDownloadedFilesWithStats(dir)
                    totalCount += r.count
                    totalBytes += r.bytes
                }
            } catch (_: Exception) {}
        }
        val roots = listOf(
            ApplicationHelper.getBestAvailableStoragePath(),
            ApplicationHelper.getInternalStoragePath()
        ).distinct()
        for (root in roots) {
            try {
                val testDir = java.io.File(root, "test_videos")
                if (testDir.exists() && testDir.isDirectory) {
                    testDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val len = file.length()
                            if (file.delete()) {
                                totalCount++
                                totalBytes += len
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        AppDatabase.getDatabase(context).mediaMessageDao().clearAllDownloadState()
        requestTdLibRemoveCompletedDownloads()
        ApplicationHelper.ClearResult(totalCount, totalBytes)
    }

    /**
     * Deletes files from TDLib's pinned files directory.
     * Prefer [clearDownloadCache] from the UI — it also syncs TDLib and the database.
     */
    fun clearDownloadedFiles(): ApplicationHelper.ClearResult {
        var totalCount = 0
        var totalBytes = 0L
        val candidates = listOf(
            activeFileDirectory,
            ApplicationHelper.getFilesDirectory(),
            ApplicationHelper.getInternalStoragePath() + "/files",
            ApplicationHelper.getInternalStoragePath() + "/tdlib/files"
        ).distinct().filter { it.isNotBlank() }
        for (dir in candidates) {
            try {
                val f = java.io.File(dir)
                if (f.exists() && f.isDirectory) {
                    val r = ApplicationHelper.clearDownloadedFilesWithStats(dir)
                    totalCount += r.count
                    totalBytes += r.bytes
                }
            } catch (_: Exception) {}
        }
        val roots = listOf(
            ApplicationHelper.getBestAvailableStoragePath(),
            ApplicationHelper.getInternalStoragePath()
        ).distinct()
        for (root in roots) {
            try {
                val testDir = java.io.File(root, "test_videos")
                if (testDir.exists() && testDir.isDirectory) {
                    testDir.walkTopDown().forEach { file ->
                        if (file.isFile) {
                            val len = file.length()
                            if (file.delete()) {
                                totalCount++
                                totalBytes += len
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return ApplicationHelper.ClearResult(totalCount, totalBytes)
    }

    private suspend fun requestTdLibRemoveCompletedDownloads() {
        val tdClient = client ?: return
        suspendCancellableCoroutine { continuation ->
            tdClient.send(
                TdApi.RemoveAllFilesFromDownloads(
                    /* onlyActive = */ false,
                    /* onlyCompleted = */ true,
                    /* deleteFromCache = */ true
                )
            ) { result ->
                if (result is TdApi.Error) {
                    Log.w(
                        "TelegramClientManager",
                        "RemoveAllFilesFromDownloads failed: ${result.message}"
                    )
                }
                continuation.resume(Unit)
            }
        }
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
     * Loads ONE page of chats from Telegram, mapped to the local Chat model.
     * `GetChats` returns the main list ordered; we fetch `offset + pageSize` ids and
     * resolve only the `[offset, offset + pageSize)` slice via `GetChat`.
     * Suspends until every GetChat call in the slice has completed.
     */
    suspend fun loadGroupsPage(offset: Int, pageSize: Int, userId: Int): List<Chat> =
        suspendCancellableCoroutine { continuation ->
            val outerClient = client
            if (outerClient == null) {
                continuation.resume(emptyList())
                return@suspendCancellableCoroutine
            }
            outerClient.send(TdApi.GetChats(TdApi.ChatListMain(), offset + pageSize)) { result ->
                if (result is TdApi.Chats) {
                    val pageIds = result.chatIds.drop(offset).take(pageSize)

                    if (pageIds.isEmpty()) {
                        continuation.resume(emptyList())
                        return@send
                    }

                    val chats = Collections.synchronizedList(mutableListOf<Chat>())
                    val remaining = AtomicInteger(pageIds.size)

                    pageIds.forEach { chatId ->
                        val innerClient = client
                        if (innerClient == null) {
                            if (remaining.decrementAndGet() == 0) continuation.resume(chats)
                            return@forEach
                        }
                        innerClient.send(TdApi.GetChat(chatId)) { chatObj ->
                            if (chatObj is TdApi.Chat) {
                                val lastMessage = chatObj.lastMessage
                                val skip = chatObj.title == "Telegram" ||
                                        (lastMessage != null &&
                                                lastMessage.content is TdApi.MessageContactRegistered)
                                if (!skip) {
                                    chats.add(chatObj.toAppChat(userId))
                                }
                            }
                            // Always decrement exactly once per callback so the
                            // coroutine resumes even when the last id is skipped.
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

    /**
     * Loads ONE page of media messages (video/document) for a chat, paging backward
     * from [fromMessageId] (0 = newest). Keeps fetching raw history batches only until
     * [pageSize] media items are gathered, then returns the cursor for the next page.
     */
    suspend fun loadMessagesPage(
        chatId: Long,
        fromMessageId: Long,
        pageSize: Int = 50,
    ): MessagesPage = withContext(Dispatchers.IO) {
        val collected = mutableListOf<MediaMessage>()
        var cursor = fromMessageId
        var endReached = false

        while (collected.size < pageSize) {
            val activeClient = client
            if (activeClient == null) {
                endReached = true
                break
            }
            val response = CompletableDeferred<TdApi.Object?>()
            activeClient.send(TdApi.GetChatHistory(chatId, cursor, 0, 100, false)) {
                response.complete(it)
            }

            val result = withTimeoutOrNull(HISTORY_PAGE_TIMEOUT_MS.milliseconds) { response.await() }

            if (result !is TdApi.Messages || result.messages.isEmpty()) {
                endReached = true
                break
            }

            result.messages
                .filter { message ->
                    message.content is TdApi.MessageVideo ||
                            message.content is TdApi.MessageDocument
                }
                .forEach { message ->
                    collected.add(parseMessageContent(message.content, chatId))
                }

            cursor = result.messages.last().id
        }

        MessagesPage(collected, cursor, endReached)
    }

    fun cancelDownloadAndDelete(fileIds: MutableSet<Int>) {
        for (fileId in fileIds) {
            client?.send(TdApi.CancelDownloadFile(fileId, false)) {
                Log.d("TDLib", "Sent cancel command for fileId=$fileId")
            }
            client?.send(TdApi.RemoveFileFromDownloads(fileId, true)) {
                Log.d("TDLib", "Removed fileId=$fileId from downloads")
            }
        }
    }

    fun parseMessageContent(content: TdApi.MessageContent, chatId: Long): MediaMessage {
        return when (content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                val file = video.video
                MediaMessageMapper.fromTdFile(
                    file = file,
                    chatId = chatId,
                    title = video.fileName.ifEmpty { "Video" },
                    description = content.caption.text,
                    mimeType = video.mimeType,
                    thumbnailPath = getThumbnailPath(file, video.fileName.ifEmpty { "Video" }),
                    width = video.width,
                    height = video.height,
                    duration = video.duration.toLong()
                )
            }

            is TdApi.MessageDocument -> {
                val document = content.document
                val file = document.document
                MediaMessageMapper.fromTdFile(
                    file = file,
                    chatId = chatId,
                    title = document.fileName.ifEmpty { "Document" },
                    description = content.caption.text,
                    mimeType = document.mimeType,
                    thumbnailPath = getThumbnailPath(file, document.fileName.ifEmpty { "Document" })
                )
            }

            is TdApi.MessageText ->
                MediaMessageMapper.textMessage(
                    chatId = chatId,
                    id = content.hashCode().toLong(),
                    text = content.text.text
                )

            else ->
                MediaMessageMapper.unsupported(chatId, content.hashCode().toLong())
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

            return clearDownloadedFiles().count
        } finally {
            client = null
            isLoggingOut = false
        }
    }

    /**
     * Returns the path to a thumbnail for [file]. Currently this always produces a
     * generated abstract image (unique per file, no text/glyph). The image is cached
     * per file (keyed on the TDLib unique id) so it stays visually stable across
     * syncs instead of being regenerated each time.
     */
    private fun getThumbnailPath(
        file: TdApi.File,
        fallbackName: String
    ): String {
        return try {
            val cacheKey = file.remote.uniqueId.ifEmpty { file.id.toString() }

            // Reuse an already-generated image if we have one.
            ThumbnailGenerator.existingThumbnail(cacheKey)?.let { return it }

            val bitmap = ThumbnailGenerator.generatePlaceholder(seed = cacheKey)

            ThumbnailGenerator.saveBitmap(bitmap, cacheKey) ?: ""
        } catch (e: Exception) {
            Log.e("Thumbnail", "Failed to generate placeholder thumbnail", e)
            ""
        }
    }
}