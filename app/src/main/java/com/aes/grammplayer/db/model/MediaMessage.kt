package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(
    tableName = "MediaMessage",
    indices = [Index("chat")]
)
data class MediaMessage(
    @PrimaryKey val id: Long,
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
    companion object {
        internal const val serialVersionUID = 727566175075960653L
    }
}