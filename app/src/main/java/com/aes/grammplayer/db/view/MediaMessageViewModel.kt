package com.aes.grammplayer.db.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.dao.MediaMessageDao
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.db.repository.MediaMessageRepository
import kotlinx.coroutines.launch

class MediaMessageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaMessageRepository(AppDatabase.getDatabase(application) as MediaMessageDao)

    val allMedia = repository.getMediaMessages()
    val downloadedMedia = repository.getDownloadedMedia()

    fun getByChat(chatId: Int) = repository.getMediaMessagesByChatId(chatId)
    fun insert(media: MediaMessage) = viewModelScope.launch { repository.insert(media) }
    fun update(media: MediaMessage) = viewModelScope.launch { repository.update(media) }
    fun delete(media: MediaMessage) = viewModelScope.launch { repository.delete(media) }
}