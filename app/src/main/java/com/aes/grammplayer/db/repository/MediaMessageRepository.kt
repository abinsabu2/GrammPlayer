package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.MediaMessage

class MediaMessageRepository(private val db: AppDatabase) {
    fun getAll() = db.mediaMessageDao().getAll()
    fun getByChat(chatId: Int) = db.mediaMessageDao().getByChat(chatId)
    fun getDownloaded() = db.mediaMessageDao().getDownloaded()
    suspend fun getById(id: Int) = db.mediaMessageDao().getById(id)
    suspend fun insert(media: MediaMessage) = db.mediaMessageDao().insert(media)
    suspend fun update(media: MediaMessage) = db.mediaMessageDao().update(media)
    suspend fun delete(media: MediaMessage) = db.mediaMessageDao().delete(media)
    suspend fun updateDownloadStatus(id: Int, downloaded: Boolean) = db.mediaMessageDao().updateDownloadStatus(id, downloaded)
}