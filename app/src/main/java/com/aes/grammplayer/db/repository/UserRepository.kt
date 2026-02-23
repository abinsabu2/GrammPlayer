package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.dao.UserDao
import com.aes.grammplayer.db.model.User
import com.aes.grammplayer.session.UserSession
import kotlinx.coroutines.flow.Flow

class UserRepository(private val userDao: UserDao) {
    fun getUsers(): Flow<List<User>> = userDao.getAll()

    fun getUserById(id: Int): Flow<User?> = userDao.getById(id)

    suspend fun insert(user: User): Long = userDao.insert(user)

    suspend fun update(user: User) = userDao.update(user)

    suspend fun delete(user: User) = userDao.delete(user)

    suspend fun getUserByPhone(phone: String): User? = userDao.getByPhone(phone)

    suspend fun count(): Int = userDao.count()

}