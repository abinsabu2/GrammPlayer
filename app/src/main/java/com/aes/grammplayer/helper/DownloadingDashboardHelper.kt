package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object DownloadingDashboardHelper {

    private const val TAG = "DownloadingDashboardHelper"

    sealed class DashboardDownloadItem {
        data class InProgress(val message: MediaMessage) : DashboardDownloadItem()
        data class Ready(val message: MediaMessage) : DashboardDownloadItem()
    }

    suspend fun loadDashboardDownloadItem(context: Context): DashboardDownloadItem? =
        withContext(Dispatchers.IO) {
            ActiveDownloadManager.currentSession()?.let { session ->
                val message = loadFreshMessage(context, session, markActive = true) ?: return@withContext null
                Log.d(TAG, "Active download fileId=${message.fileId} path=${message.localPath}")
                return@withContext DashboardDownloadItem.InProgress(message)
            }

            ActiveDownloadManager.peekCompletedSession()?.let { session ->
                val message = loadFreshMessage(context, session, markActive = false) ?: return@withContext null
                Log.d(TAG, "Completed download fileId=${message.fileId} downloaded=${message.isDownloaded}")
                return@withContext DashboardDownloadItem.Ready(message)
            }

            null
        }

    private suspend fun loadFreshMessage(
        context: Context,
        session: ActiveDownloadManager.Session,
        markActive: Boolean
    ): MediaMessage? {
        val stored = AppDatabase.getDatabase(context)
            .mediaMessageDao()
            .getById(session.messageId)
            .first()
            ?: return null

        val merged = stored.copy(
            title = session.title.ifBlank { stored.title },
            localPath = session.localPath?.takeIf { it.isNotBlank() } ?: stored.localPath,
            isDownloadActive = markActive
        )
        val synced = merged.copy()
        MediaFileHelper.syncMessageFromFile(synced)
        val complete = isCompleteOnDisk(synced)
        if (complete) {
            synced.isDownloaded = true
        }
        return synced.copy(isDownloadActive = markActive && !complete)
    }

    private fun isCompleteOnDisk(message: MediaMessage): Boolean {
        if (message.isDownloaded) return true
        if (message.size <= 0L) {
            return MediaFileHelper.isPlayable(message.localPath)
        }
        val file = MediaFileHelper.resolveFile(message.localPath) ?: return false
        return file.length() >= message.size
    }
}