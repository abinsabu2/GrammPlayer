package com.aes.grammplayer.db.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.db.repository.MediaMessageRepository
import kotlinx.coroutines.launch

class MediaMessageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaMessageRepository(AppDatabase.getDatabase(application))

    val allMedia = repository.getAll()
    val downloadedMedia = repository.getDownloaded()

    fun getByChat(chatId: Int) = repository.getByChat(chatId)
    fun insert(media: MediaMessage) = viewModelScope.launch { repository.insert(media) }
    fun update(media: MediaMessage) = viewModelScope.launch { repository.update(media) }
    fun delete(media: MediaMessage) = viewModelScope.launch { repository.delete(media) }
    fun updateDownloadStatus(id: Int, downloaded: Boolean) = viewModelScope.launch { repository.updateDownloadStatus(id, downloaded) }
}