package com.aes.grammplayer.db.repository

import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.Settings

class SettingsRepository(private val db: AppDatabase) {
    fun getFirstSettings() = db.settingsDao().getFirstSettings()
    fun getSettings(id: Int) = db.settingsDao().getSettings(id)
    suspend fun insert(settings: Settings) = db.settingsDao().insert(settings)
    suspend fun update(settings: Settings) = db.settingsDao().update(settings)
}