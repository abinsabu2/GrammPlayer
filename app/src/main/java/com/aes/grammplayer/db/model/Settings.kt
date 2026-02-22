package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Settings")
data class Settings(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bufferSize: Int?,
    val bufferPercentage: Int?,
    val autoplay: Boolean,
    val toc: Boolean = false,
    val onBoard: Boolean = false,
    val gridSize: Int
)