package com.aes.grammplayer.db.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phone: String,
    val isTest: Boolean,
    val validated: Boolean
)