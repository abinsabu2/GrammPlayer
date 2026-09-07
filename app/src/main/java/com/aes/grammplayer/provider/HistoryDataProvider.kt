package com.aes.grammplayer.provider

import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.history.HistoryStore
import com.aes.grammplayer.ui.features.history.HistoryItem

object HistoryDataProvider {

    private const val TAG = "HistoryDataProvider"

    suspend fun loadHistoryPage(
        offset: Int,
        pageSize: Int = HistoryStore.DEFAULT_PAGE_SIZE
    ): Page<HistoryItem> {
        return try {
            val context = GPlayerApplication.AppContext
            HistoryStore.loadPage(context, offset, pageSize)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history page offset=$offset", e)
            Page(emptyList(), offset, 0L, endReached = true)
        }
    }
}
