package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.dao.HistoryDao
import com.aes.grammplayer.db.model.History
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    fun getHistory(): Flow<List<History>> = historyDao.getAll()

    fun getHistoryByUser(userId: Int): Flow<List<History>> = historyDao.getByUser(userId)

    fun getHistoryByChat(chatId: Int): Flow<List<History>> = historyDao.getByChat(chatId)

    suspend fun insert(history: History) = historyDao.insert(history)

    suspend fun delete(history: History) = historyDao.delete(history)

    suspend fun clearHistoryForUser(userId: Int) = historyDao.clearHistoryForUser(userId)

    suspend fun count(): Int = historyDao.count()
}