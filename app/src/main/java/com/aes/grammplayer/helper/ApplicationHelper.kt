package com.aes.grammplayer.helper

import android.os.Environment
import androidx.core.content.ContextCompat
import com.aes.grammplayer.GPlayerApplication
import java.io.File

 object ApplicationHelper {

    /**
     * NEW: Determines the best storage path (internal or external) based on availability.
     */
    fun getBestAvailableStoragePath(): String {
        val internalPath = GPlayerApplication.AppContext.filesDir.absolutePath + "/tdlib"
        val externalPath = getExternalStoragePath()

        // Prioritize external storage if it's available and writable.
        if (externalPath != null) {
            val externalDir = File(externalPath)
            // Ensure the directory can be created and written to.
            if (externalDir.exists() || externalDir.mkdirs()) {
                if (externalDir.canWrite()) {
                    return externalPath
                }
            }
        }

        // Fallback to internal storage if external is not available or not writable.
        return internalPath
    }

    /**
     * NEW: Finds a writable external storage path (USB, SD card, etc.).
     */
    private fun getExternalStoragePath(): String? {
        val context = GPlayerApplication.AppContext
        // Get all possible external storage directories.
        val externalStorageVolumes: Array<out File> = ContextCompat.getExternalFilesDirs(context, null)

        // Find the first one that is removable and mounted.
        val externalStorage = externalStorageVolumes.firstOrNull {
            // isRemovable is the key to finding USB drives/SD cards on Android TV.
            Environment.isExternalStorageRemovable(it) && Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED
        }

        return externalStorage?.let { it.absolutePath + "/tdlib" }
    }

    /**
     * UPDATED: Deletes files from the currently active storage path.
     * This now works for both internal and external storage.
     */
    fun clearDownloadedFiles(): Int {
        // Use the activeStoragePath which could be internal or external
        var deletedFilesCount = 0
        val subdirectoriesToClear = listOf("documents", "temp", "videos")
        val activeFileDirectory = getActiveFileDirectory()
        subdirectoriesToClear.forEach { subdir ->
            // Construct path based on the active storage directory
            val directory = File(activeFileDirectory, subdir)
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

    /**
     * NEW: Calculates the size of the activeFileDirectory and its contents.
     * @return The total size in MB, or 0.0 if the directory does not exist.
     */
    fun getDirectorySize(): Double {
        val activeFileDirectory = getActiveFileDirectory()
        val directory = File(activeFileDirectory)
        if (!directory.exists() || !directory.isDirectory) {
            return 0.0
        }

        var totalSize = 0L
        directory.walkTopDown().forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        return totalSize / (1024.0 * 1024.0)
    }

    fun getActiveFileDirectory(): String {
        val activeStoragePath = getBestAvailableStoragePath()
        val activeFileDirectory = "$activeStoragePath/files"

        return activeFileDirectory
    }


}