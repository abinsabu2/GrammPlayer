package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.History
import com.aes.grammplayer.db.model.MediaMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History): Long

    @Delete
    suspend fun delete(history: History)

    @Query("SELECT * FROM History")
    fun getAll(): Flow<List<History>>

    @Query("SELECT * FROM History WHERE user = :userId ORDER BY id DESC")
    fun getByUser(userId: Int): Flow<List<History>>

    @Query("SELECT * FROM History WHERE chat = :chatId ORDER BY id DESC")
    fun getByChat(chatId: Int): Flow<List<History>>

    @Query("DELETE FROM History WHERE user = :userId")
    suspend fun clearHistoryForUser(userId: Int)

    @Query("SELECT COUNT(*) FROM History")
    suspend fun count(): Int

    @Query("DELETE FROM History WHERE user = :userId AND message = :messageId")
    suspend fun deleteByUserAndMessage(userId: Int, messageId: Long)

    @Query("SELECT * FROM History WHERE user = :userId AND message = :messageId LIMIT 1")
    suspend fun getByUserAndMessage(userId: Int, messageId: Long): History?

    @Query(
        """
        SELECT m.* FROM MediaMessage m
        INNER JOIN History h ON m.id = h.message
        WHERE h.user = :userId
        ORDER BY h.id DESC
        """
    )
    fun getMediaMessagesForUser(userId: Int): Flow<List<MediaMessage>>
}