package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.Settings
import kotlinx.coroutines.flow.Flow

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: Settings)

    @Update
    suspend fun update(settings: Settings)

    @Delete
    suspend fun delete(settings: Settings)

    @Query("SELECT * FROM Settings")
    fun getAll(): Flow<List<Settings>>

    @Query("SELECT * FROM Settings WHERE id = :id")
    fun getById(id: Int): Flow<Settings?>

    @Query("SELECT * FROM Settings WHERE activeUserId = :userId")
    suspend fun getByUserId(userId: Int): Settings?

    @Query("SELECT COUNT(*) FROM Settings")
    suspend fun count(): Int
}