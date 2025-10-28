// New file: MockTelegramClientManager.kt
package com.aes.grammplayer

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import kotlin.math.min

class MockTelegramClientManager : ITelegramClientManager {

    private var initialized = false
    private val mockPhoneNumber: String = "+123456789"  // Your specific mock phone number
    private val mockAuthCode: String = "00000"  // Mock authentication code

    private var authState: TdApi.AuthorizationState? = null  // To simulate auth states

    override val isInitialized: Boolean
        get() = initialized

    override fun initialize() {
        if (initialized) return
        initialized = true
        Log.i("MockTelegram", "Mock client initialized")
        // Simulate setting parameters, but no real client
    }

    override fun clearDownloadedFiles(): Int {
        // Simulate deleting files
        Log.d("MockTelegram", "Mock clear downloaded files")
        return 5  // Mock count
    }

    override fun getDirectorySize(): Double {
        // Mock size
        Log.d("MockTelegram", "Mock directory size")
        return 42.5  // Mock size in MB
    }

    override fun sendPhoneNumber(phone: String) {
        if (phone != mockPhoneNumber) {
            // Should not happen, but log
            Log.e("MockTelegram", "Unexpected phone in mock mode: $phone")
            return
        }
        Log.d("MockTelegram", "Mock send phone number: $phone")
        // Simulate update to wait for code
        authState = TdApi.AuthorizationStateWaitCode()
        TdLibUpdateHandler.onResult(TdApi.UpdateAuthorizationState(authState as TdApi.AuthorizationStateWaitCode))
    }

    override fun sendAuthCode(code: String) {
        Log.d("MockTelegram", "Mock send auth code: $code")
        if (code == mockAuthCode) {
            // Simulate successful auth
            authState = TdApi.AuthorizationStateReady()
            TdLibUpdateHandler.onResult(TdApi.UpdateAuthorizationState(authState as TdApi.AuthorizationStateReady))
        } else {
            // Simulate wrong code
            authState = TdApi.AuthorizationStateWaitCode()
            TdLibUpdateHandler.onResult(TdApi.UpdateAuthorizationState(authState as TdApi.AuthorizationStateWaitCode))
        }
    }

    override fun startFileDownload(fileId: Int?) {
        val id = fileId ?: return
        Log.d("MockTelegram", "Mock start file download for id=$id")
        // Simulate immediate completion
        val mockFile = TdApi.File().apply {
            this.id = id
            local = TdApi.LocalFile("/mock/path/to/file$id", true, true, false, true, 0, 0, 1000000)
        }
        TdLibUpdateHandler.onResult(TdApi.UpdateFile(mockFile))
    }

    override fun loadAllGroups(limit: Int, onGroupLoaded: (TdApi.Chat) -> Unit) {
        Log.d("MockTelegram", "Mock load all groups")
        createMockChats(limit).forEach { onGroupLoaded(it) }
    }

    override suspend fun loadMessagesForChat(
        chatId: Long,
        fromMessageId: Long,
        limit: Int
    ): List<TdApi.Message> = withContext(Dispatchers.IO) {
        Log.d("MockTelegram", "Mock load messages for chat $chatId")
        createMockMessages(chatId, limit)
    }

    override fun close() {
        initialized = false
        authState = null
        Log.d("MockTelegram", "Mock client closed")
    }

    override fun cancelDownloadAndDelete(fileId: Int?) {
        val id = fileId ?: return
        Log.d("MockTelegram", "Mock cancel download and delete for id=$id")
        // Simulate canceled file
        val mockFile = TdApi.File().apply {
            this.id = id
            local = TdApi.LocalFile("", false, false, false, false, 0, 0, 0)
        }
        TdLibUpdateHandler.onResult(TdApi.UpdateFile(mockFile))
    }

    override fun parseMessageContent(content: TdApi.MessageContent, chatId: Long): MediaMessage {
        // Same as original, since it's parsing logic
        return when (content) {
            is TdApi.MessageVideo -> {
                val video = content.video
                val file = video.video
                val thumbnail = video.thumbnail

                MediaMessage(
                    id = file.id.toLong(),
                    title = video.fileName.ifEmpty { "Video" },
                    description = content.caption.text,
                    studio = "Telegram",
                    chatId = chatId,
                    isMedia = true,
                    localPath = file.local.path.takeIf { it.isNotEmpty() },
                    fileId = file.id,
                    mimeType = video.mimeType,
                    width = video.width,
                    height = video.height,
                    duration = video.duration,
                    size = file.size,
                    thumbnailPath = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    isDownloaded = file.local.isDownloadingCompleted,
                    isDownloadActive = file.local.isDownloadingActive,
                    uniqueId = file.remote.uniqueId.takeIf { it.isNotEmpty() }
                )
            }
            is TdApi.MessageDocument -> {
                val document = content.document
                val file = document.document
                val thumbnail = document.thumbnail

                MediaMessage(
                    id = file.id.toLong(),
                    title = document.fileName.ifEmpty { "Document" },
                    description = content.caption.text,
                    studio = "Telegram",
                    isMedia = true,
                    localPath = file.local.path.takeIf { it.isNotEmpty() },
                    fileId = file.id,
                    mimeType = document.mimeType,
                    chatId = chatId,
                    size = file.size,
                    thumbnailPath = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    cardImageUrl = thumbnail?.file?.local?.path?.takeIf { it.isNotEmpty() },
                    isDownloaded = file.local.isDownloadingCompleted,
                    isDownloadActive = file.local.isDownloadingActive,
                    uniqueId = file.remote.uniqueId.takeIf { it.isNotEmpty() }
                )
            }
            is TdApi.MessageText -> {
                MediaMessage(
                    id = content.hashCode().toLong(),
                    title = content.text.text,
                    description = content.text.text,
                    isMedia = false,
                    studio = "Telegram",
                    chatId = chatId
                )
            }
            else -> {
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

    // Mock data generation

    private fun createMockChats(limit: Int): List<TdApi.Chat> {
        val mockChats = mutableListOf<TdApi.Chat>()
        return mockChats
    }

    private fun createMockMessages(chatId: Long, limit: Int): List<TdApi.Message> {
        val mockMessages = mutableListOf<TdApi.Message>()
        return mockMessages
    }
}