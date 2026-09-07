package com.aes.grammplayer.helper

import android.util.Log
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.ui.features.details.DownloadingFileInfo
import java.io.File

object MediaFileHelper {

    private const val TAG = "MediaFileHelper"

    /** Returns the on-disk file when [path] points to a real, non-empty file. */
    fun resolveFile(path: String?): File? {
        if (path.isNullOrBlank()) {
            Log.d(TAG, "resolveFile: null or blank path")
            return null
        }
        val file = File(path)
        return if (file.isFile && file.exists() && file.length() > 0L) file else {
            Log.w(TAG, "resolveFile: not playable path=$path exists=${file.exists()} isFile=${file.isFile} len=${if (file.exists()) file.length() else -1}")
            null
        }
    }

    /** True when a file exists at [path] (including empty/partial files). */
    fun existsOnDisk(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val file = File(path)
        return file.isFile && file.exists()
    }

    fun isPlayable(path: String?): Boolean = resolveFile(path) != null

    fun deleteFiles(paths: Collection<String?>): Int {
        var deletedCount = 0
        paths.filterNotNull().distinct().forEach { path ->
            val file = File(path)
            if (file.exists() && file.delete()) {
                deletedCount++
            }
        }
        return deletedCount
    }

    /**
     * Updates [message] from the physical file on disk and returns playback info,
     * or null when no playable file is present.
     */
    fun syncMessageFromFile(message: MediaMessage): DownloadingFileInfo? {
        val file = resolveFile(message.localPath)
        if (file == null) {
            if (message.isDownloaded) message.isDownloaded = false
            return null
        }
        message.localPath = file.absolutePath
        val fileSize = file.length()
        if (message.size <= 0L || fileSize >= message.size) {
            message.isDownloaded = true
        }
        return buildDownloadingFileInfo(
            fileId = message.fileId,
            localPath = file.absolutePath,
            expectedSize = message.size
        )
    }

    fun buildDownloadingFileInfo(
        fileId: Int,
        localPath: String,
        expectedSize: Long
    ): DownloadingFileInfo {
        val file = File(localPath)
        val downloadedMb = file.length().toFloat() / (1024 * 1024)
        val totalMb = expectedSize.toFloat() / (1024 * 1024)
        val progress = if (expectedSize > 0L) {
            ((file.length() * 100) / expectedSize).toInt().coerceIn(0, 100)
        } else {
            100
        }
        return DownloadingFileInfo(
            fileId = fileId,
            downloadedSize = downloadedMb,
            totalSize = totalMb,
            progress = progress,
            localPath = localPath
        )
    }
}