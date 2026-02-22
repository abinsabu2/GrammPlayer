package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "Chats",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("user")]
)
data class Chat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: Int,
    val user: Int
)