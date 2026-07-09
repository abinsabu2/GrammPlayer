package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "History",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Chat::class,
            parentColumns = ["id"],
            childColumns = ["chat"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaMessage::class,
            parentColumns = ["id"],
            childColumns = ["message"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("user"),
        Index("chat"),
        Index("message"),
        Index(value = ["user", "message"], unique = true)
    ]
)
data class History(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user: Int,
    val chat: Int,
    val message: Long,
    val viewed: Boolean = false,
    val downloaded: Boolean = false,
    val downloading: Boolean = false
)