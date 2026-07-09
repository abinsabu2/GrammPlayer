package com.aes.grammplayer.ui.features.details

// ==================== Reused from BottomSheet ====================
data class DownloadingFileInfo(
    val fileId: Int,
    val downloadedSize: Float,
    val totalSize: Float,
    val progress: Int,
    var localPath: String? = null
)