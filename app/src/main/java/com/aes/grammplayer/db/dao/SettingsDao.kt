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

    @Query("SELECT * FROM Settings WHERE id = :id")
    fun getSettings(id: Int): Flow<Settings?>

    @Query("SELECT * FROM Settings LIMIT 1")
    fun getFirstSettings(): Flow<Settings?>

    @Query("SELECT COUNT(*) FROM Settings")
    suspend fun count(): Int
}