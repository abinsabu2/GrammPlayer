package com.aes.grammplayer.helper

import android.content.Context
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object DownloadingDashboardHelper {

    suspend fun loadActiveDownloadMessage(context: Context): MediaMessage? = withContext(Dispatchers.IO) {
        val session = ActiveDownloadManager.currentSession() ?: return@withContext null
        val message = AppDatabase.getDatabase(context)
            .mediaMessageDao()
            .getById(session.messageId)
            .first()
            ?: return@withContext null

        message.copy(
            title = session.title.ifBlank { message.title },
            localPath = session.localPath ?: message.localPath,
            isDownloadActive = true
        )
    }
}