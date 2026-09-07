package com.aes.grammplayer.db.model

import java.io.Serializable

data class MediaMessage(
    val id: Long,
    val chat: Int,
    val title: String,
    val description: String,
    val studio: String,
    val width: Int,
    val height: Int,
    val duration: Long,
    val size: Long,
    val isMedia: Boolean,
    var localPath: String,
    val fileId: Int,
    val mimeType: String,
    val videoUrl: String,
    val thumbnailPath: String,
    val cardImageUrl: String,
    val backgroundImageUrl: String,
    var isDownloaded: Boolean,
    val isDownloadActive: Boolean,
    val uniqueId: String
) : Serializable {
    companion object { internal const val serialVersionUID = 727566175075960653L }
}
