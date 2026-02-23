package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.dao.MediaMessageDao
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.flow.Flow

class MediaMessageRepository(private val mediaMessageDao: MediaMessageDao) {
    fun getMediaMessages(): Flow<List<MediaMessage>> = mediaMessageDao.getAll()

    fun getMediaMessageById(id: Int): Flow<MediaMessage?> = mediaMessageDao.getById(id)

    fun getMediaMessagesByChatId(chatId: Int): Flow<List<MediaMessage>> = mediaMessageDao.getByChatId(chatId)

    suspend fun insert(mediaMessage: MediaMessage): Long = mediaMessageDao.insert(mediaMessage)

    suspend fun update(mediaMessage: MediaMessage) = mediaMessageDao.update(mediaMessage)

    suspend fun delete(mediaMessage: MediaMessage) = mediaMessageDao.delete(mediaMessage)

    fun getDownloadedMedia(): Flow<List<MediaMessage>> = mediaMessageDao.getDownloadedMedia()

    suspend fun count(): Int = mediaMessageDao.count()
}