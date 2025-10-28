// New file: TelegramClientFacade.kt
package com.aes.grammplayer

import org.drinkless.tdlib.TdApi

object TelegramClientFacade : ITelegramClientManager {

    private var delegate: ITelegramClientManager = RealTelegramClientManager()
    private const val mockPhoneNumber: String = "+123456789"  // Your specific mock phone number

    override val isInitialized: Boolean
        get() = delegate.isInitialized

    override fun initialize() {
        delegate.initialize()
    }

    override fun clearDownloadedFiles(): Int {
        return delegate.clearDownloadedFiles()
    }

    override fun getDirectorySize(): Double {
        return delegate.getDirectorySize()
    }

    override fun sendPhoneNumber(phone: String) {
        if (phone == mockPhoneNumber) {
            if (delegate is RealTelegramClientManager) {
                // Close the real client if it was initialized
                delegate.close()
            }
            delegate = MockTelegramClientManager()
        }
        delegate.sendPhoneNumber(phone)
    }

    override fun sendAuthCode(code: String) {
        delegate.sendAuthCode(code)
    }

    override fun startFileDownload(fileId: Int?) {
        delegate.startFileDownload(fileId)
    }

    override fun loadAllGroups(limit: Int, onGroupLoaded: (TdApi.Chat) -> Unit) {
        delegate.loadAllGroups(limit, onGroupLoaded)
    }

    override suspend fun loadMessagesForChat(chatId: Long, fromMessageId: Long, limit: Int): List<TdApi.Message> {
        return delegate.loadMessagesForChat(chatId, fromMessageId, limit)
    }

    override fun close() {
        delegate.close()
        // Reset to real for next use, if desired
        delegate = RealTelegramClientManager()
    }

    override fun cancelDownloadAndDelete(fileId: Int?) {
        delegate.cancelDownloadAndDelete(fileId)
    }

    override fun parseMessageContent(content: TdApi.MessageContent, chatId: Long): MediaMessage {
        return delegate.parseMessageContent(content, chatId)
    }
}