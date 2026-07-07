package com.aes.grammplayer.db.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.History
import com.aes.grammplayer.db.repository.HistoryRepository
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HistoryRepository(AppDatabase.getDatabase(application).historyDao())

    fun getByUser(userId: Int) = repository.getHistoryByUser(userId)
    fun insert(history: History) = viewModelScope.launch { repository.insert(history) }
    fun delete(history: History) = viewModelScope.launch { repository.delete(history) }
    fun clearHistoryForUser(userId: Int) = viewModelScope.launch { repository.clearHistoryForUser(userId) }
}