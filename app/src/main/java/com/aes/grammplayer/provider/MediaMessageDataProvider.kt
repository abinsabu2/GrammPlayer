package com.aes.grammplayer.provider

import android.annotation.SuppressLint
import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.provider.JsonSeedStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aes.grammplayer.util.tdlib.TelegramClientManager

object MediaMessageDataProvider {

    const val PAGE_SIZE = 50

    /**
     * Loads ONE page of media messages for a chat.
     *
     * @param mode   true = local (Room, offset paging), false = remote (TDLib, cursor paging)
     * @param offset local paging offset (Room)
     * @param cursor remote paging cursor: TDLib fromMessageId (0 = newest)
     */
    @SuppressLint("LongLogTag")
    suspend fun loadMediaMessagesPage(
        mode: Boolean = true,
        chatId: Long,
        offset: Int = 0,
        cursor: Long = 0L,
        pageSize: Int = PAGE_SIZE,
    ): Page<MediaMessage> {
        val context = GPlayerApplication.Companion.AppContext

        return withContext(Dispatchers.IO) {
            try {
                if (mode) {
                    val items = JsonSeedStore.getMessagesPaged(chatId.toInt(), pageSize, offset)
                    Page(
                        items = items,
                        nextOffset = offset + items.size,
                        nextCursor = 0L,
                        endReached = items.size < pageSize
                    )
                } else {
                    val page = TelegramClientManager.loadMessagesPage(
                        chatId = chatId,
                        fromMessageId = cursor,
                        pageSize = pageSize
                    )
                    Page(
                        items = page.items,
                        nextOffset = 0,
                        nextCursor = page.nextFromMessageId,
                        endReached = page.endReached
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(
                    "MediaMessageDataProvider",
                    "Error loading media messages for chat $chatId: ${e.message}",
                    e
                )
                Page(emptyList(), offset, cursor, endReached = true)
            }
        }
    }
}
