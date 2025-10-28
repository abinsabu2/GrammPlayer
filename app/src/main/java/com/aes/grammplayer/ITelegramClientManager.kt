// New file: ITelegramClientManager.kt
package com.aes.grammplayer

import kotlinx.coroutines.CompletableDeferred
import org.drinkless.tdlib.TdApi
import java.io.File

interface ITelegramClientManager {
    val isInitialized: Boolean
    fun initialize()
    fun clearDownloadedFiles(): Int
    fun getDirectorySize(): Double
    fun sendPhoneNumber(phone: String)
    fun sendAuthCode(code: String)
    fun startFileDownload(fileId: Int?)
    fun loadAllGroups(limit: Int = 100000, onGroupLoaded: (TdApi.Chat) -> Unit)
    suspend fun loadMessagesForChat(chatId: Long, fromMessageId: Long = 0, limit: Int = 100): List<TdApi.Message>
    fun close()
    fun cancelDownloadAndDelete(fileId: Int?)
    fun parseMessageContent(content: TdApi.MessageContent, chatId: Long): MediaMessage
}