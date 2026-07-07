package com.aes.grammplayer.provider

import android.util.Log
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.helper.HistoryHelper
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.ui.features.history.HistoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object HistoryDataProvider {

    private const val TAG = "HistoryDataProvider"

    suspend fun loadHistory(): List<HistoryItem> = withContext(Dispatchers.IO) {
        try {
            val context = GPlayerApplication.AppContext
            val db = AppDatabase.getDatabase(context)
            val userId = HistoryHelper.resolveActiveUserId(context)
            val historyRows = db.historyDao().getByUser(userId).first()
            val items = mutableListOf<HistoryItem>()

            historyRows.forEach { row ->
                val message = db.mediaMessageDao().getById(row.message).first()
                if (message == null) {
                    Log.w(TAG, "History row ${row.id} references missing message ${row.message}")
                    return@forEach
                }
                val chat = db.chatDao().getById(message.chat).first()
                if (chat == null || chat.userId != userId) {
                    Log.w(
                        TAG,
                        "Skipping history row ${row.id}: chat ${message.chat} belongs to user ${chat?.userId}, not $userId"
                    )
                    return@forEach
                }

                val onDisk = MediaFileHelper.existsOnDisk(message.localPath)
                val isDownloaded = row.downloaded && (message.isDownloaded || onDisk)
                val isDownloading = row.downloading && !isDownloaded
                if (!row.viewed && !isDownloaded && !isDownloading) return@forEach

                items.add(
                    HistoryItem(
                        message = message,
                        isViewed = row.viewed,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading
                    )
                )
            }

            items
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history", e)
            emptyList()
        }
    }
}