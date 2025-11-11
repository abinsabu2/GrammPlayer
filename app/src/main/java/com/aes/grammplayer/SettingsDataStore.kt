package com.aes.grammplayer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val START_POINT_TYPE = stringPreferencesKey("start_point_type")
        val START_POINT_VALUE = intPreferencesKey("start_point_value")
        val BUFFER_COUNTER = intPreferencesKey("buffer_counter")
        val PROGRESS_THRESHOLD = intPreferencesKey("progress_threshold")
        val BUFFER_SIZE_THRESHOLD = intPreferencesKey("buffer_size_threshold")
    }

    val autoPlay: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[AUTO_PLAY] ?: true
    }

    suspend fun setAutoPlay(autoPlay: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[AUTO_PLAY] = autoPlay
        }
    }

    val startPointType: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[START_POINT_TYPE] ?: "progress"
    }

    suspend fun setStartPointType(type: String) {
        appContext.dataStore.edit { preferences ->
            preferences[START_POINT_TYPE] = type
        }
    }

    val startPointValue: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[START_POINT_VALUE] ?: 0
    }

    suspend fun setStartPointValue(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[START_POINT_VALUE] = value
        }
    }

    val bufferCounter: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[BUFFER_COUNTER] ?: 5
    }

    suspend fun setBufferCounter(buffer: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[BUFFER_COUNTER] = buffer
        }
    }

    val progressThreshold: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[PROGRESS_THRESHOLD] ?: 30
    }

    suspend fun setProgressThreshold(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[PROGRESS_THRESHOLD] = value
        }
    }

    val bufferSizeThreshold: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[BUFFER_SIZE_THRESHOLD] ?: 300
    }

    suspend fun setBufferSizeThreshold(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[BUFFER_SIZE_THRESHOLD] = value
        }
    }
}
