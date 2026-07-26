package com.aes.grammplayer.helper

import android.os.Environment
import androidx.core.content.ContextCompat
import com.aes.grammplayer.GPlayerApplication
import java.io.File

object ApplicationHelper {

    /**
     * Determines the best storage path (internal or external) based on availability.
     */
    fun getBestAvailableStoragePath(): String {
        val internalPath = GPlayerApplication.AppContext.filesDir.absolutePath + "/tdlib"
        val externalPath = getExternalStoragePath()

        if (externalPath != null) {
            val externalDir = File(externalPath)
            if (externalDir.exists() || externalDir.mkdirs()) {
                if (externalDir.canWrite()) {
                    return externalPath
                }
            }
        }

        return internalPath
    }

    fun getFilesDirectory(storagePath: String = getBestAvailableStoragePath()): String =
        "$storagePath/files"

    /**
     * Deletes downloaded media files under TDLib's files directory.
     * Pass [filesDirectory] when TDLib has a pinned path from client initialization.
     */
    fun clearDownloadedFiles(filesDirectory: String = getFilesDirectory()): Int {
        var deletedFilesCount = 0
        val subdirectoriesToClear = listOf("documents", "temp", "videos")

        subdirectoriesToClear.forEach { subdir ->
            val directory = File(filesDirectory, subdir)
            if (directory.exists() && directory.isDirectory) {
                directory.walkTopDown().forEach { file ->
                    if (file.isFile && file.delete()) {
                        deletedFilesCount++
                    }
                }
            }
        }
        return deletedFilesCount
    }

    private fun getExternalStoragePath(): String? {
        val context = GPlayerApplication.AppContext
        val externalStorageVolumes: Array<out File> =
            ContextCompat.getExternalFilesDirs(context, null)

        val externalStorage = externalStorageVolumes.firstOrNull {
            Environment.isExternalStorageRemovable(it) &&
                Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        }

        return externalStorage?.let { it.absolutePath + "/tdlib" }
    }
}