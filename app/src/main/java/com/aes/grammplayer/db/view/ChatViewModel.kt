package com.aes.grammplayer.db.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.dao.ChatDao
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.db.repository.ChatRepository
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(AppDatabase.getDatabase(application) as ChatDao)

    val allChats = repository.getChats()

    fun getByUser(userId: Int) = repository.getChatsByUserId(userId)
    fun insert(chat: Chat) = viewModelScope.launch { repository.insert(chat) }
    fun update(chat: Chat) = viewModelScope.launch { repository.update(chat) }
    fun delete(chat: Chat) = viewModelScope.launch { repository.delete(chat) }
}