package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.History
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: History)

    @Delete
    suspend fun delete(history: History)

    @Query("SELECT * FROM History WHERE user = :userId")
    fun getByUser(userId: Int): Flow<List<History>>

    @Query("DELETE FROM History WHERE user = :userId")
    suspend fun clearHistoryForUser(userId: Int)
}