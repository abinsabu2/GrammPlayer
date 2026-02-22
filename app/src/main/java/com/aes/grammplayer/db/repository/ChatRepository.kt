package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.Chat

class ChatRepository(private val db: AppDatabase) {
    fun getAll() = db.chatDao().getAll()
    fun getByUser(userId: Int) = db.chatDao().getByUser(userId)
    suspend fun getById(id: Int) = db.chatDao().getById(id)
    suspend fun insert(chat: Chat) = db.chatDao().insert(chat)
    suspend fun update(chat: Chat) = db.chatDao().update(chat)
    suspend fun delete(chat: Chat) = db.chatDao().delete(chat)
}