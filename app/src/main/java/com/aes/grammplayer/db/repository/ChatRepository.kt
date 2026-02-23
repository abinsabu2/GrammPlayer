package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.dao.ChatDao
import com.aes.grammplayer.db.model.Chat
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    fun getChats(): Flow<List<Chat>> = chatDao.getAll()

    fun getChatById(id: Int): Flow<Chat?> = chatDao.getById(id)

    fun getChatsByUserId(userId: Int): Flow<List<Chat>> = chatDao.getByUserId(userId)

    suspend fun insert(chat: Chat): Long = chatDao.insert(chat)

    suspend fun update(chat: Chat) = chatDao.update(chat)

    suspend fun delete(chat: Chat) = chatDao.delete(chat)

    fun getPinnedChats(userId: Int): Flow<List<Chat>> = chatDao.getPinnedChats(userId)

    suspend fun count(): Int = chatDao.count()
}