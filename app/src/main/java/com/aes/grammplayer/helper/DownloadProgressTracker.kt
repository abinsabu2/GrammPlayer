package com.aes.grammplayer.helper

import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

object DownloadProgressTracker {

    data class FileProgress(
        val fileId: Int,
        val progress: Int
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val activeDownloads = mutableMapOf<Int, FileProgress>()

    private val _updates = MutableSharedFlow<Int>(extraBufferCapacity = 64)
    val updates = _updates.asSharedFlow()

    init {
        scope.launch {
            TdLibUpdateHandler.fileUpdate.collect { update ->
                handleFileUpdate(update.file)
            }
        }
    }

    private suspend fun handleFileUpdate(file: TdApi.File) {
        if (isDownloadComplete(file)) {
            val wasTracked = activeDownloads.remove(file.id) != null
            val isManaged = ActiveDownloadManager.wasRecentlyCompleted(file.id) ||
                ActiveDownloadManager.isActive(file.id)
            if (wasTracked || isManaged) {
                _updates.emit(file.id)
            }
            return
        }

        if (!file.local.isDownloadingActive) {
            if (activeDownloads.remove(file.id) != null) {
                _updates.emit(file.id)
            }
            return
        }

        val progress = progressFromFile(file)
        activeDownloads[file.id] = FileProgress(fileId = file.id, progress = progress)
        _updates.emit(file.id)
    }

    fun progressFor(fileId: Int): Int? = activeDownloads[fileId]?.progress

    fun isDownloading(fileId: Int): Boolean = activeDownloads.containsKey(fileId)

    fun clear(fileId: Int) {
        if (activeDownloads.remove(fileId) != null) {
            scope.launch { _updates.emit(fileId) }
        }
    }

    fun progressFromMessage(message: MediaMessage): Int? {
        progressFor(message.fileId)?.let { return it }
        if (!isMessageDownloading(message)) return null

        val path = message.localPath
        if (!MediaFileHelper.existsOnDisk(path)) return 0

        return MediaFileHelper.buildDownloadingFileInfo(
            fileId = message.fileId,
            localPath = path,
            expectedSize = message.size
        ).progress.coerceIn(0, 99)
    }

    fun isMessageDownloading(message: MediaMessage): Boolean {
        ActiveDownloadManager.currentSession()?.let { active ->
            return active.fileId == message.fileId
        }
        if (isDownloading(message.fileId)) return true
        if (!message.isDownloadActive) return false
        return !isCompleteDownload(message)
    }

    private fun isCompleteDownload(message: MediaMessage): Boolean {
        if (message.size <= 0L) {
            return message.isDownloaded && MediaFileHelper.isPlayable(message.localPath)
        }
        val file = MediaFileHelper.resolveFile(message.localPath) ?: return message.isDownloaded
        return file.length() >= message.size
    }

    private fun progressFromFile(file: TdApi.File): Int {
        val totalBytes = file.expectedSize
        if (totalBytes <= 0L) return 0
        return ((file.local.downloadedSize * 100) / totalBytes)
            .toInt()
            .coerceIn(0, 99)
    }

    private fun isDownloadComplete(file: TdApi.File): Boolean {
        return file.local.isDownloadingCompleted ||
            (file.expectedSize > 0L && file.local.downloadedSize >= file.expectedSize)
    }
}