package com.aes.grammplayer.provider

import android.annotation.SuppressLint
import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.flow.first

object MediaMessageDataProvider {

    @SuppressLint("LongLogTag")
    suspend fun loadAllMediaMessages(
        mode: Boolean = true,   // true = local, false = remote
        chatId: Long,
        limit: Int = 100000,
        onMediaLoaded: (MediaMessage) -> Unit,
    ) {
        val context = GPlayerApplication.Companion.AppContext
        val database = AppDatabase.getDatabase(context)

        withContext(Dispatchers.IO) {
            try {
                val mediaMessages = if (mode) {
                    database.mediaMessageDao().getByChatId(chatId.toInt()).first()
                } else {
                    TelegramClientManager.loadMessagesForChat(
                        chatId = chatId,
                        limit = 100
                    )
                }

                mediaMessages.forEach { mediaMessage -> onMediaLoaded(mediaMessage) }

            } catch (e: Exception) {
                Log.e(
                    "MediaMessageDataProvider",
                    "Error loading media messages for chat $chatId: ${e.message}",
                    e
                )
            }
        }
    }
}