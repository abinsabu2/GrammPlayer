package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.history.HistoryStore
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DashboardBackdropHelper {

    private const val TAG = "DashboardBackdropHelper"

    suspend fun resolveLastItemBackdropUrl(context: Context): String? = withContext(Dispatchers.IO) {
        HistoryStore.latestBackdropUrl(context)?.let { saved ->
            Log.d(TAG, "Using saved backdrop from history file")
            return@withContext saved
        }

        // Fallback: try fetching for newest history titles (page of entries via loadPage).
        val page = HistoryStore.loadPage(context, offset = 0, pageSize = 10)
        for (item in page.items) {
            val fetched = PosterFetcher.fetchBackdropUrl(ReleaseTitleParser.parse(item.message.title))
            if (!fetched.isNullOrBlank()) {
                Log.d(TAG, "Fetched TMDB backdrop for message=${item.message.id}")
                return@withContext fetched
            }
        }

        Log.d(TAG, "No backdrop found in history")
        null
    }
}
