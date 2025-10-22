package com.aes.grammplayer

import android.R
import android.util.Log
import com.aes.grammplayer.MessageGridFragment.Companion.ARG_CHAT_ID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.io.path.exists

object TelegramClientManager {

    var client: Client? = null
    val isInitialized: Boolean
        get() = client != null

    /**
     * Initializes the TDLib client using the global TdLibUpdateHandler.
     * This function no longer accepts a callback.
     */
    fun initialize() {
        if (isInitialized) return // Prevent re-initialization

        // The TdLibUpdateHandler is now the single, central callback for the client.
        client = Client.create(TdLibUpdateHandler, null, null)

        Client.execute(TdApi.SetLogVerbosityLevel(1))

        val parameters = TdApi.SetTdlibParameters().apply {
            apiId = 21805799
            apiHash = "b74b95eace7c9327effac15b6a0c8d91"
            systemLanguageCode = "en"
            deviceModel = "Android TV"
            systemVersion = "10"
            applicationVersion = "1.0"
            databaseDirectory = GPlayerApplication.AppContext.filesDir.absolutePath + "/tdlib"
            useMessageDatabase = true
            useSecretChats = false
        }
        // The global handler will receive the result of this command.
        client?.send(parameters, TdLibUpdateHandler)
    }

    /**
     * Sends the phone number. The result is handled by the global handler.
     */
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

    /**
     * Deletes all files from the TDLib 'documents' and 'temp' directories.
     * This is a safer way to clear cache without touching the core database files.
     * Returns the number of files deleted.
     */
    fun clearDownloadedFiles(): Int {
        val baseTdlibPath = GPlayerApplication.AppContext.filesDir.absolutePath + "/tdlib"
        var deletedFilesCount = 0

        // Define the subdirectories to clear
        val subdirectoriesToClear = listOf("documents", "temp")

        subdirectoriesToClear.forEach { subdir ->
            val directory = File(baseTdlibPath, subdir)
            if (directory.exists() && directory.isDirectory) {
                // Use walkTopDown to iterate through all files and subdirectories
                directory.walkTopDown().forEach { file ->
                    // Make sure it's a file and not a directory before deleting
                    if (file.isFile && file.delete()) {
                        deletedFilesCount++
                    }
                }
            }
        }

        return deletedFilesCount
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
        client?.send(TdApi.Close(), null)
        client = null
    }

    // In TelegramClientManager.kt

    /**
     * Cancels an active download and deletes the partially downloaded file from the cache.
     */
    fun cancelDownloadAndDelete(fileId: Int?) {
        val id = fileId ?: return

        // First, tell TDLib to cancel the network operation.
        client?.send(TdApi.CancelDownloadFile(id, false)) {
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
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() } // Use thumbnail for card image
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
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() }
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
