package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.dao.SettingsDao
import com.aes.grammplayer.db.model.Settings
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val settingsDao: SettingsDao) {
    fun getSettings(): Flow<List<Settings>> = settingsDao.getAll()

    fun getSettingsById(id: Int): Flow<Settings?> = settingsDao.getById(id)

    suspend fun insert(settings: Settings) = settingsDao.insert(settings)

    suspend fun update(settings: Settings) = settingsDao.update(settings)

    suspend fun delete(settings: Settings) = settingsDao.delete(settings)

    suspend fun getSettingsByUserId(userId: Int): Settings? = settingsDao.getByUserId(userId)

    suspend fun count(): Int = settingsDao.count()
}