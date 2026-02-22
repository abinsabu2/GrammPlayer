package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.Chat
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chat: Chat): Long

    @Update
    suspend fun update(chat: Chat)

    @Delete
    suspend fun delete(chat: Chat)

    @Query("SELECT * FROM Chats")
    fun getAll(): Flow<List<Chat>>

    @Query("SELECT * FROM Chats WHERE id = :id")
    suspend fun getById(id: Int): Chat?

    @Query("SELECT * FROM Chats WHERE user = :userId")
    fun getByUser(userId: Int): Flow<List<Chat>>

    @Query("SELECT COUNT(*) FROM Chats")
    suspend fun count(): Int
}