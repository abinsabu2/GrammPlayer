package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.User

class UserRepository(private val db: AppDatabase) {
    fun getAll() = db.userDao().getAll()
    fun getValidatedUser() = db.userDao().getValidatedUser()
    suspend fun getById(id: Int) = db.userDao().getById(id)
    suspend fun insert(user: User) = db.userDao().insert(user)
    suspend fun update(user: User) = db.userDao().update(user)
    suspend fun delete(user: User) = db.userDao().delete(user)
}