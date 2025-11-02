package com.aes.grammplayer

import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

object TelegramClientManager {

    var client: Client? = null
    val isInitialized: Boolean
        get() = client != null

    // --- NEW: To hold the currently active storage path ---
    private var activeStoragePath: String = ""
    private var activeFileDirectory: String = ""

    /**
     * Initializes the TDLib client, automatically selecting the best storage location.
     */
    fun initialize() {
        if (isInitialized) return

        // --- NEW: Storage selection logic ---
        activeStoragePath = getBestAvailableStoragePath()
        Log.i("StorageManager", "Using storage path: $activeStoragePath")
        // --- End of new logic ---

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
            // --- UPDATED: Use the dynamically selected storage path ---
            databaseDirectory = activeStoragePath
            useMessageDatabase = true
            useSecretChats = false
            // Tell TDLib that the files directory is within our chosen path
            filesDirectory = activeFileDirectory
        }
        client?.send(parameters, TdLibUpdateHandler)
    }

    /**
     * NEW: Determines the best storage path (internal or external) based on availability.
     */
    private fun getBestAvailableStoragePath(): String {
        val internalPath = GPlayerApplication.AppContext.filesDir.absolutePath + "/tdlib"
        val externalPath = getExternalStoragePath()

        // Prioritize external storage if it's available and writable.
        if (externalPath != null) {
            val externalDir = File(externalPath)
            // Ensure the directory can be created and written to.
            if (externalDir.exists() || externalDir.mkdirs()) {
                if (externalDir.canWrite()) {
                    return externalPath
                }
            }
        }

        // Fallback to internal storage if external is not available or not writable.
        return internalPath
    }

    /**
     * NEW: Finds a writable external storage path (USB, SD card, etc.).
     */
    private fun getExternalStoragePath(): String? {
        val context = GPlayerApplication.AppContext
        // Get all possible external storage directories.
        val externalStorageVolumes: Array<out File> = ContextCompat.getExternalFilesDirs(context, null)

        // Find the first one that is removable and mounted.
        val externalStorage = externalStorageVolumes.firstOrNull {
            // isRemovable is the key to finding USB drives/SD cards on Android TV.
            Environment.isExternalStorageRemovable(it) && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        }

        return externalStorage?.let { it.absolutePath + "/tdlib" }
    }


    /**
     * UPDATED: Deletes files from the currently active storage path.
     * This now works for both internal and external storage.
     */
    fun clearDownloadedFiles(): Int {
        // Use the activeStoragePath which could be internal or external
        var deletedFilesCount = 0
        val subdirectoriesToClear = listOf("documents", "temp", "videos")

        subdirectoriesToClear.forEach { subdir ->
            // Construct path based on the active storage directory
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
     * NEW: Calculates the size of the activeFileDirectory and its contents.
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
    fun startFileDownload(fileId: Int?) {
        client?.send(TdApi.DownloadFile(fileId ?: 0, 1, 0, 0, false)) {
            Log.d("TDLib", "Download command sent for fileId=$fileId")
        }
    }

    fun loadAllGroups(limit: Int = 100000, onGroupLoaded: (TdApi.Chat) -> Unit) {
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), limit)) { result ->
            if (result is TdApi.Chats) {
                result.chatIds.forEach { chatId ->
                    client?.send(TdApi.GetChat(chatId)) { chatObj ->
                        if (chatObj is TdApi.Chat) {
                            onGroupLoaded(chatObj)
                        }
                    }
                }
            }
        }
    }

    suspend fun loadMessagesForChat(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 100
    ): List<TdApi.Message> = withContext(Dispatchers.IO) {
        val response = CompletableDeferred<TdApi.Object?>()
        client?.send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit, false)) {
            response.complete(it)
        }
        val result = response.await()
        if (result is TdApi.Messages) {
            return@withContext result.messages.toList()
        }
        return@withContext emptyList()
    }

    fun close() {
        client?.send(TdApi.Close(), TdLibUpdateHandler)
        client = null
    }

    fun cancelDownloadAndDelete(fileId: Int?) {
        val id = fileId ?: return
        client?.send(TdApi.CancelDownloadFile(id, false),TdLibUpdateHandler) {
            Log.d("TDLib", "Sent cancel command for fileId=$id")
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
                    // Core properties
                    id = file.id.toLong(),
                    title = video.fileName.ifEmpty { "Video" },
                    description = content.caption.text, // Use the video caption as the description
                    studio = "Telegram",
                    // Media file properties
                    chatId = chatId,
                    isMedia = true,
                    localPath = file.local.path.takeIf { it.isNotEmpty() },
                    fileId = file.id,
                    mimeType = video.mimeType,
                    // Dimensions and duration
                    width = video.width,
                    height = video.height,
                    duration = video.duration,
                    size = file.size,
                    // Thumbnail properties
                    thumbnailPath = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    isDownloaded = file.local.isDownloadingCompleted,
                    isDownloadActive = file.local.isDownloadingActive,// Use thumbnail for card image
                    uniqueId = file.remote.uniqueId.takeIf { it.isNotEmpty() }
                )
            }

            is TdApi.MessageDocument -> {
                val document = content.document
                val file = document.document
                val thumbnail = document.thumbnail

                MediaMessage(
                    // Core properties
                    id = file.id.toLong(),
                    title = document.fileName.ifEmpty { "Document" },
                    description = content.caption.text, // Use the document caption
                    studio = "Telegram",

                    // Media file properties
                    isMedia = true, // A document can be a media file (e.g., mp4, mkv)
                    localPath = file.local.path.takeIf { it.isNotEmpty() },
                    fileId = file.id,
                    mimeType = document.mimeType,
                    chatId = chatId,
                    // Dimensions and duration (not applicable for all documents)
                    size = file.size,
                    // Thumbnail properties
                    thumbnailPath = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    isDownloaded = file.local.isDownloadingCompleted,
                    isDownloadActive = file.local.isDownloadingActive,
                    uniqueId = file.remote.uniqueId.takeIf { it.isNotEmpty() }
                )
            }

            is TdApi.MessageText -> {
                // Handle plain text messages
                MediaMessage(
                    id = content.hashCode().toLong(), // Generate a stable ID for text messages
                    title = content.text.text,
                    description = content.text.text,
                    isMedia = false,
                    studio = "Telegram",
                    chatId = chatId
                )
            }

            else -> {
                // Default case for unhandled message types
                MediaMessage(
                    id = content.hashCode().toLong(),
                    title = "Unsupported Content",
                    description = "This message type is not currently supported.",
                    isMedia = false,
                    studio = "Telegram",
                    chatId = chatId
                )
            }
        }
    }

}
