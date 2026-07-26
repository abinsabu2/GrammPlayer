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

    @Query("SELECT * FROM Chats LIMIT :limit OFFSET :offset")
    suspend fun getAllPaged(limit: Int, offset: Int): List<Chat>

    @Query("SELECT * FROM Chats WHERE id = :id")
    fun getById(id: Int): Flow<Chat?>

    @Query("SELECT COUNT(*) FROM Chats")
    suspend fun count(): Int
}
