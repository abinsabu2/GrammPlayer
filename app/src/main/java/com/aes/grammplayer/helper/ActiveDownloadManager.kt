package com.aes.grammplayer.helper

import android.content.Context
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ActiveDownloadManager {

    data class Session(
        val fileId: Int,
        val messageId: Long,
        val title: String,
        val localPath: String?
    ) {
        val displayTitle: String
            get() = ReleaseTitleParser.parse(title).displayTitle.ifEmpty { title.ifEmpty { "Untitled" } }
    }

    @Volatile
    private var current: Session? = null

    @Volatile
    private var completed: Session? = null

    fun currentSession(): Session? = current

    fun peekCompletedSession(): Session? = completed

    fun clearCompletedSession() {
        completed = null
    }

    fun isActive(fileId: Int): Boolean = current?.fileId == fileId

    fun wasRecentlyCompleted(fileId: Int): Boolean =
        completed?.fileId == fileId || current?.fileId == fileId

    fun otherActiveSession(fileId: Int): Session? {
        val active = current ?: return null
        return active.takeIf { it.fileId != fileId }
    }

    fun begin(message: MediaMessage) {
        completed = null
        current = Session(
            fileId = message.fileId,
            messageId = message.id,
            title = message.title,
            localPath = message.localPath.takeIf { it.isNotBlank() }
        )
    }

    fun updateLocalPath(fileId: Int, localPath: String?) {
        val active = current ?: return
        if (active.fileId != fileId) return
        current = active.copy(localPath = localPath?.takeIf { it.isNotBlank() })
    }

    fun complete(fileId: Int) {
        if (current?.fileId == fileId) {
            completed = current
            current = null
        }
    }

    fun release(fileId: Int) {
        complete(fileId)
    }

    suspend fun cancelActiveDownload(context: Context, session: Session? = current) {
        val active = session ?: return
        withContext(Dispatchers.IO) {
            TelegramClientManager.cancelDownloadAndDelete(mutableSetOf(active.fileId))
            MediaFileHelper.deleteFiles(listOfNotNull(active.localPath))
            DownloadProgressTracker.clear(active.fileId)
        }
        if (current?.fileId == active.fileId) {
            current = null
        }
        if (completed?.fileId == active.fileId) {
            completed = null
        }
    }
}