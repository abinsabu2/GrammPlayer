package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "MediaMessage",
    foreignKeys = [
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chat"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("chat")]
)
data class MediaMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val chat: Int,
    val title: String,
    val description: String,
    val studio: String,
    val isMedia: Boolean,
    val localPath: String,
    val fileId: Int,
    val mimeType: String,
    val videoUrl: String,
    val width: Int,
    val height: Int,
    val duration: Int,
    val size: Int,
    val thumbnailPath: String,
    val cardImageUrl: String,
    val backgroundImageUrl: String,
    val isDownloaded: Boolean,
    val isDownloadActive: Boolean,
    val uniqueId: String
)