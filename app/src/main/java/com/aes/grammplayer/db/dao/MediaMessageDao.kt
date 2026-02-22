package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: MediaMessage): Long

    @Update
    suspend fun update(media: MediaMessage)

    @Delete
    suspend fun delete(media: MediaMessage)

    @Query("SELECT * FROM MediaMessage")
    fun getAll(): Flow<List<MediaMessage>>

    @Query("SELECT * FROM MediaMessage WHERE id = :id")
    suspend fun getById(id: Int): MediaMessage?

    @Query("SELECT * FROM MediaMessage WHERE chat = :chatId")
    fun getByChat(chatId: Int): Flow<List<MediaMessage>>

    @Query("SELECT * FROM MediaMessage WHERE isDownloaded = 1")
    fun getDownloaded(): Flow<List<MediaMessage>>

    @Query("UPDATE MediaMessage SET isDownloaded = :downloaded WHERE id = :id")
    suspend fun updateDownloadStatus(id: Int, downloaded: Boolean)

    @Query("SELECT COUNT(*) FROM MediaMessage")
    suspend fun count(): Int
}