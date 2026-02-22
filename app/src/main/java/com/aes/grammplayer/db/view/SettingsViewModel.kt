package com.aes.grammplayer.db.view

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.Settings
import com.aes.grammplayer.db.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(AppDatabase.getDatabase(application))

    val firstSettings = repository.getFirstSettings()

    fun getSettings(id: Int) = repository.getSettings(id)
    fun insert(settings: Settings) = viewModelScope.launch { repository.insert(settings) }
    fun update(settings: Settings) = viewModelScope.launch { repository.update(settings) }

    fun updateOnboarding() {
        viewModelScope.launch {
            val current = getSettings(1).first() ?: return@launch
            update(current.copy(onBoard = true))
        }
    }
}