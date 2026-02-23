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
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("activeUserId")]
)
data class Settings(
    @PrimaryKey val id: Int,
    val bufferSize: Int? = null,
    val bufferPercentage: Int? = null,
    val autoplay: Boolean,
    val isTocAccepted: Boolean = false,
    val isOnBoard: Boolean = false,
    val gridSize: Int = 4,
    val activeUserId: Int,
    val userConnected: Boolean? = null
)