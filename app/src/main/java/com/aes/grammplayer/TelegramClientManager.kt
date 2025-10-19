package com.aes.grammplayer

import android.util.Log
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
                        if (chatObj is TdApi.Chat && (chatObj.type is TdApi.ChatTypeSupergroup || chatObj.type is TdApi.ChatTypeBasicGroup)) {
                            onGroupLoaded(chatObj)
                        }
                    }
                }
            } else {
                Log.e("TDLib", "Failed to get chats: $result")
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
            Log.d("CacheClear", "Checking directory: ${directory.absolutePath}")

            if (directory.exists() && directory.isDirectory) {
                // Use walkTopDown to iterate through all files and subdirectories
                directory.walkTopDown().forEach { file ->
                    // Make sure it's a file and not a directory before deleting
                    if (file.isFile && file.delete()) {
                        Log.d("CacheClear", "Deleted file: ${file.name}")
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
}
