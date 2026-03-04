package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Users")
data class User(
    @PrimaryKey val id: Long,
    val phone: String,
    val isTestUser: Boolean = false,
    val isConnected: Boolean = false
)