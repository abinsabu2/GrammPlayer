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
    fun getById(id: Int): Flow<User?>

    @Query("SELECT * FROM Users WHERE phone = :phone")
    suspend fun getByPhone(phone: String): User?

    @Query("SELECT COUNT(*) FROM Users")
    suspend fun count(): Int
}