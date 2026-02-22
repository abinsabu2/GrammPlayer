package com.aes.grammplayer.db.dao

import androidx.room.*
import com.aes.grammplayer.db.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long

    @Update
    suspend fun update(user: User)

    @Delete
    suspend fun delete(user: User)

    @Query("SELECT * FROM Users")
    fun getAll(): Flow<List<User>>

    @Query("SELECT * FROM Users WHERE id = :id")
    suspend fun getById(id: Int): User?

    @Query("SELECT * FROM Users WHERE validated = 1 LIMIT 1")
    fun getValidatedUser(): Flow<User?>
}