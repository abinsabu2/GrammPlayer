package com.aes.grammplayer.provider


import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.provider.JsonSeedStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aes.grammplayer.util.tdlib.TelegramClientManager

object ChatsDataProvider {

    const val PAGE_SIZE = 50

    /**
     * Loads ONE page of chats.
     *
     * @param mode   true = local (Room), false = remote (TDLib)
     * @param offset paging offset, applies to both local and remote
     * @param filter optional post-fetch filter; filtered-out rows still advance the offset
     */
    suspend fun loadGroupsPage(
        mode: Boolean = true,
        offset: Int = 0,
        pageSize: Int = PAGE_SIZE,
        filter: ((Chat) -> Boolean)? = null,
    ): Page<Chat> {
        val context = GPlayerApplication.Companion.AppContext

        return withContext(Dispatchers.IO) {
            try {
                val chats = if (mode) {
                    JsonSeedStore.getChatsPaged(pageSize, offset)
                } else {
                    TelegramClientManager.loadGroupsPage(
                        offset = offset,
                        pageSize = pageSize,
                        userId = 1
                    )
                }

                Page(
                    items = chats.filter { chat -> filter?.invoke(chat) ?: true },
                    nextOffset = offset + pageSize,
                    nextCursor = 0L,
                    endReached = chats.size < pageSize
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("ChatsDataProvider", "Error loading chats: ${e.message}", e)
                Page(emptyList(), offset, 0L, endReached = true)
            }
        }
    }
}
