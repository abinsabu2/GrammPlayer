package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object DashboardBackdropHelper {

    private const val TAG = "DashboardBackdropHelper"

    suspend fun resolveLastItemBackdropUrl(context: Context): String? = withContext(Dispatchers.IO) {
        HistoryHelper.prepareSession(context)
        val db = AppDatabase.getDatabase(context)
        val userId = HistoryHelper.resolveActiveUserId(context)
        val historyRows = db.historyDao().getByUser(userId).first()

        for (row in historyRows) {
            val message = db.mediaMessageDao().getById(row.message).first()
            if (message == null) {
                Log.w(TAG, "History row ${row.id} references missing message ${row.message}")
                continue
            }

            val onDisk = MediaFileHelper.existsOnDisk(message.localPath)
            val isDownloaded = row.downloaded && (message.isDownloaded || onDisk)
            val isDownloading = row.downloading && !isDownloaded
            if (!row.viewed && !isDownloaded && !isDownloading) continue

            message.backgroundImageUrl
                .takeIf { PosterFetcher.isTrustedImageUrl(it) }
                ?.let { saved ->
                    Log.d(TAG, "Using saved TMDB backdrop for message=${message.id}")
                    return@withContext saved
                }

            val fetched = PosterFetcher.fetchBackdropUrl(ReleaseTitleParser.parse(message.title))
            if (!fetched.isNullOrBlank()) {
                Log.d(TAG, "Fetched TMDB backdrop for message=${message.id}")
                db.mediaMessageDao().insert(message.copy(backgroundImageUrl = fetched))
                return@withContext fetched
            }

            Log.d(TAG, "No backdrop for message=${message.id}, trying older history row")
        }

        Log.d(TAG, "No backdrop found in history for user=$userId")
        null
    }
}