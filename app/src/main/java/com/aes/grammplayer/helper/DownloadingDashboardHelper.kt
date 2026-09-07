package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DownloadingDashboardHelper {

    private const val TAG = "DownloadingDashboardHelper"

    sealed class DashboardDownloadItem {
        data class InProgress(val message: MediaMessage) : DashboardDownloadItem()
        data class Ready(val message: MediaMessage) : DashboardDownloadItem()
    }

    suspend fun loadDashboardDownloadItem(context: Context): DashboardDownloadItem? =
        withContext(Dispatchers.IO) {
            val session = ActiveDownloadManager.currentSession() ?: return@withContext null
            val message = loadFreshMessage(session, markActive = true) ?: return@withContext null
            Log.d(TAG, "Active download fileId=${message.fileId} path=${message.localPath}")
            return@withContext DashboardDownloadItem.InProgress(message)
        }

    private fun loadFreshMessage(
        session: ActiveDownloadManager.Session,
        markActive: Boolean
    ): MediaMessage? {
        // ponytail: DB removed — construct from active session only; file check decides complete
        val merged = MediaMessage(
            id = session.messageId,
            chat = 0,
            title = session.title,
            description = "",
            studio = "",
            width = 0,
            height = 0,
            duration = 0,
            size = 0,
            isMedia = true,
            localPath = session.localPath ?: "",
            fileId = session.fileId,
            mimeType = "video/mp4",
            videoUrl = "",
            thumbnailPath = "",
            cardImageUrl = "",
            backgroundImageUrl = "",
            isDownloaded = false,
            isDownloadActive = markActive,
            uniqueId = ""
        )
        val complete = isCompleteOnDisk(merged)
        if (complete) merged.isDownloaded = true
        return merged.copy(isDownloadActive = markActive && !complete)
    }

    private fun isCompleteOnDisk(message: MediaMessage): Boolean {
        if (message.isDownloaded) return true
        if (message.size <= 0L) return MediaFileHelper.isPlayable(message.localPath)
        val file = MediaFileHelper.resolveFile(message.localPath) ?: return false
        return file.length() >= message.size
    }
}
