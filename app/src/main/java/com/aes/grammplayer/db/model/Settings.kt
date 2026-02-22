package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Settings",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["activeUserId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("activeUserId")]
)
data class Settings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bufferSize: Int?,
    val bufferPercentage: Int?,
    val autoplay: Boolean,
    val toc: Boolean = false,
    val onBoard: Boolean = false,
    val gridSize: Int,
    val activeUserId: Int? = null
)