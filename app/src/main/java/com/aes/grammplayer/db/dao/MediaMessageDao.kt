package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mediaMessage: MediaMessage): Long

    @Query("SELECT * FROM MediaMessage WHERE id = :id")
    fun getById(id: Long): Flow<MediaMessage?>

    @Query("SELECT * FROM MediaMessage WHERE chat = :chatId ORDER BY id DESC LIMIT :limit OFFSET :offset")
    suspend fun getByChatIdPaged(chatId: Int, limit: Int, offset: Int): List<MediaMessage>

    @Query("SELECT COUNT(*) FROM MediaMessage")
    suspend fun count(): Int

    @Query("UPDATE MediaMessage SET localPath = '', isDownloaded = 0, isDownloadActive = 0")
    suspend fun clearAllDownloadState()
}
