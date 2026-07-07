package com.aes.grammplayer.helper

import android.os.StatFs
import java.io.File
import java.text.DecimalFormat

object FormatHelper {

    private val oneDecimal = DecimalFormat("0.0")

    fun formatBytes(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "N/A"
        val mb = sizeBytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    fun formatBytesMb(sizeBytes: Long): String {
        if (sizeBytes <= 0L) return "N/A"
        return String.format("%.2f MB", sizeBytes / 1024.0 / 1024.0)
    }

    fun formatBufferSizeMb(sizeMb: Int): String =
        if (sizeMb >= 1024) String.format("%.1f GB", sizeMb / 1024.0) else "$sizeMb MB"

    fun formatAvailableStorage(filesDir: File): String {
        val stat = StatFs(filesDir.path)
        val availableGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.1f GB", availableGb)
    }

    fun formatMimeType(mimeType: String): String =
        mimeType.substringAfterLast('/').uppercase()

    fun formatDownloadProgress(progress: Int, downloadedBytes: Long, totalBytes: Long): String {
        if (totalBytes <= 0L) return "$progress%"
        val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
        val totalMb = totalBytes / (1024.0 * 1024.0)
        return "Downloading : $progress% (${oneDecimal.format(downloadedMb)} MB / ${oneDecimal.format(totalMb)} MB)"
    }
}