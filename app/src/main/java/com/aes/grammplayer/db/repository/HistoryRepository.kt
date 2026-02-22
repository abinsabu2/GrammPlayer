package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.History

class HistoryRepository(private val db: AppDatabase) {
    fun getByUser(userId: Int) = db.historyDao().getByUser(userId)
    suspend fun insert(history: History) = db.historyDao().insert(history)
    suspend fun delete(history: History) = db.historyDao().delete(history)
    suspend fun clearHistoryForUser(userId: Int) = db.historyDao().clearHistoryForUser(userId)
}