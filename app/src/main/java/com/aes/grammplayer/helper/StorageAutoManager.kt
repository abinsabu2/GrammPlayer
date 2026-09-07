package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object StorageAutoManager {

    private const val TAG = "StorageAutoManager"

    // ponytail: single threshold int, per-dir quotas later; use bulk clearAllDownloadState not per-message updates.
    suspend fun ensureFreeSpace(ctx: Context, thresholdMb: Int = 500): Int = withContext(Dispatchers.IO) {
        val thresholdBytes = thresholdMb.toLong() * 1024L * 1024L
        val free = ApplicationHelper.getInternalFreeBytes()
        if (free >= thresholdBytes) {
            Log.i(TAG, "ensureFreeSpace: free ${ApplicationHelper.formatFreeBytes(free)} >= ${thresholdMb}MB, no delete")
            return@withContext 0
        }
        // Resolve files directories: primary (best available) + internal fallback
        val baseDirs = mutableListOf<File>()
        try {
            val primary = File(ApplicationHelper.getFilesDirectory())
            baseDirs.add(primary)
        } catch (_: Exception) {}
        val fallback = File(ApplicationHelper.getInternalStoragePath() + "/files")
        if (baseDirs.none { it.absolutePath == fallback.absolutePath }) baseDirs.add(fallback)
        // Spec double path fallback for strict compliance
        val specDouble = File(ApplicationHelper.getInternalStoragePath() + "/tdlib/files")
        if (specDouble.exists() && baseDirs.none { it.absolutePath == specDouble.absolutePath }) baseDirs.add(specDouble)

        val subdirs = listOf("documents", "temp", "videos", "test_videos")
        val allFiles = mutableListOf<File>()
        for (base in baseDirs) {
            for (sub in subdirs) {
                val dir = File(base, sub)
                if (!dir.exists() || !dir.isDirectory) continue
                try {
                    dir.walkTopDown().forEach { f ->
                        if (f.isFile) allFiles.add(f)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "walk failed $dir: $e")
                }
            }
        }
        // Also check test_videos at filesDir root fallback if not already? Already covered

        if (allFiles.isEmpty()) {
            Log.i(TAG, "ensureFreeSpace: no files to delete, free ${ApplicationHelper.formatFreeBytes(free)}")
            return@withContext 0
        }
        allFiles.sortBy { it.lastModified() }

        var deleted = 0
        for (file in allFiles) {
            if (ApplicationHelper.getInternalFreeBytes() >= thresholdBytes) break
            try {
                if (file.delete()) {
                    deleted++
                } else {
                    Log.w(TAG, "failed to delete ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "delete exception ${file.absolutePath}: $e")
            }
        }
        if (deleted > 0) {
            // ponytail: DB removed — file delete suffices
// Try TDLib remove equivalent
            try {
                val client = com.aes.grammplayer.util.tdlib.TelegramClientManager.client
                if (client != null) {
                    suspendCancellableCoroutine<Unit> { cont ->
                        client.send(
                            org.drinkless.tdlib.TdApi.RemoveAllFilesFromDownloads(false, true, true)
                        ) { result ->
                            if (result is org.drinkless.tdlib.TdApi.Error) {
                                Log.w(TAG, "RemoveAllFilesFromDownloads failed: ${result.message}")
                            }
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "TdLib remove failed: $e")
            }
            Log.i(TAG, "ensureFreeSpace deleted $deleted files, free now ${ApplicationHelper.formatFreeBytes(ApplicationHelper.getInternalFreeBytes())}")
        } else {
            Log.i(TAG, "ensureFreeSpace deleted 0, free ${ApplicationHelper.formatFreeBytes(ApplicationHelper.getInternalFreeBytes())}")
        }
        return@withContext deleted
    }

    suspend fun moveToExternalIfPossible(ctx: Context): Int = withContext(Dispatchers.IO) {
        if (!ApplicationHelper.isExternalStorageAvailable()) {
            Log.i(TAG, "moveToExternalIfPossible: external not available")
            return@withContext 0
        }
        val externalFile = ApplicationHelper.getExternalStorageFile() ?: return@withContext 0
        val externalBase = File(externalFile.absolutePath + "/tdlib/files")

        val candidates = mutableListOf<File>()
        candidates.add(File(ApplicationHelper.getInternalStoragePath() + "/files"))
        val specDouble = File(ApplicationHelper.getInternalStoragePath() + "/tdlib/files")
        if (specDouble.absolutePath != candidates[0].absolutePath) candidates.add(specDouble)
        try {
            val primary = File(ApplicationHelper.getFilesDirectory())
            if (candidates.none { it.absolutePath == primary.absolutePath }) candidates.add(primary)
        } catch (_: Exception) {}

        val subdirs = listOf("documents", "temp", "videos", "test_videos")
        var moved = 0
        for (internalBase in candidates) {
            if (!internalBase.exists()) continue
            // Avoid moving if internalBase is actually external (same path)
            if (internalBase.absolutePath == externalBase.absolutePath) continue
            // Also avoid if internalBase is within externalFile path
            if (internalBase.absolutePath.startsWith(externalFile.absolutePath)) continue
            for (sub in subdirs) {
                val srcDir = File(internalBase, sub)
                if (!srcDir.exists() || !srcDir.isDirectory) continue
                val files = try { srcDir.listFiles()?.filter { it.isFile } ?: emptyList() } catch (_: Exception) { emptyList() }
                for (src in files) {
                    try {
                        val destDir = File(externalBase, sub)
                        if (!destDir.exists()) destDir.mkdirs()
                        val dest = File(destDir, src.name)
                        if (dest.exists()) continue
                        var ok = src.renameTo(dest)
                        if (!ok) {
                            try {
                                src.copyTo(dest, overwrite = false)
                                ok = dest.exists() && dest.length() == src.length()
                                if (ok) src.delete()
                                else {
                                    try { if (dest.exists()) dest.delete() } catch (_: Exception) {}
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "copy fallback failed for ${src.name}: $e")
                                ok = false
                            }
                        }
                        if (ok) moved++ else Log.w(TAG, "failed to move ${src.absolutePath}")
                    } catch (e: Exception) {
                        Log.w(TAG, "move failed ${src.name}: $e")
                    }
                }
            }
        }
        if (moved > 0) Log.i(TAG, "moveToExternalIfPossible moved $moved files")
        return@withContext moved
    }
}
