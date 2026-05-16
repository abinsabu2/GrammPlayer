package com.aes.grammplayer.provider


import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.flow.first

object ChatsDataProvider {

    suspend fun loadAllGroups(
        mode: Boolean = true,   // true = local, false = remote
        filter: ((Chat) -> Boolean)? = null,
        onGroupLoaded: (Chat) -> Unit
    ) {
        val context = GPlayerApplication.Companion.AppContext
        val database = AppDatabase.getDatabase(context)

        withContext(Dispatchers.IO) {
            try {
                val chats = if (mode) {
                    database.chatDao().getAll().first()
                } else {
                    TelegramClientManager.loadAllGroups(limit = 10000, userId = 1)
                }

                chats
                    .filter { chat -> filter?.invoke(chat) ?: true }
                    .forEach { chat -> onGroupLoaded(chat) }

            } catch (e: Exception) {
                Log.e("ChatsDataProvider", "Error loading chats: ${e.message}", e)
            }
        }
        }
    }