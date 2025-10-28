// New file: RealTelegramClientManager.kt
package com.aes.grammplayer

import kotlinx.coroutines.CompletableDeferred
import org.drinkless.tdlib.TdApi

class RealTelegramClientManager : ITelegramClientManager {
    override val isInitialized: Boolean
        get() = TelegramClientManager.isInitialized

    override fun initialize() {
        TelegramClientManager.initialize()
    }

    override fun clearDownloadedFiles(): Int {
        return TelegramClientManager.clearDownloadedFiles()
    }

    override fun getDirectorySize(): Double {
        return TelegramClientManager.getDirectorySize()
    }

    override fun sendPhoneNumber(phone: String) {
        TelegramClientManager.sendPhoneNumber(phone)
    }

    override fun sendAuthCode(code: String) {
        TelegramClientManager.sendAuthCode(code)
    }

    override fun startFileDownload(fileId: Int?) {
        TelegramClientManager.startFileDownload(fileId)
    }

    override fun loadAllGroups(limit: Int, onGroupLoaded: (TdApi.Chat) -> Unit) {
        TelegramClientManager.loadAllGroups(limit, onGroupLoaded)
    }

    override suspend fun loadMessagesForChat(chatId: Long, fromMessageId: Long, limit: Int): List<TdApi.Message> {
        return TelegramClientManager.loadMessagesForChat(chatId, fromMessageId, limit)
    }

    override fun close() {
        TelegramClientManager.close()
    }

    override fun cancelDownloadAndDelete(fileId: Int?) {
        TelegramClientManager.cancelDownloadAndDelete(fileId)
    }

    override fun parseMessageContent(content: TdApi.MessageContent, chatId: Long): MediaMessage {
        return TelegramClientManager.parseMessageContent(content, chatId)
    }
}