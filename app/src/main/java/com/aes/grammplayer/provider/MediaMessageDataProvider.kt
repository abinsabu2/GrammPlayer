package com.aes.grammplayer.provider

import android.annotation.SuppressLint
import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.flow.first

object MediaMessageDataProvider {

    @SuppressLint("LongLogTag")
    suspend fun loadAllMediaMessages(
        chatId: Long,
        limit: Int = 100000,
        onMediaLoaded: (MediaMessage) -> Unit,
    ) {
        val context = GPlayerApplication.Companion.AppContext
        val database = AppDatabase.getDatabase(context)

        withContext(Dispatchers.IO) {
            try {
                database.mediaMessageDao().getByChatId(chatId.toInt()).first().take(limit).forEach { mediaMessage ->
                    onMediaLoaded(mediaMessage)
                }
            } catch (e: Exception) {
                Log.e("MediaMessageDataProvider", "Error loading media messages for chat $chatId: ${e.message}", e)
            }
        }
    }

}