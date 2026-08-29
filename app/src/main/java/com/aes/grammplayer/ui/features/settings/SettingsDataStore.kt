package com.aes.grammplayer.ui.features.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(context: Context) {

    private val appContext = context.applicationContext

    companion object {
        val AUTO_PLAY = booleanPreferencesKey("auto_play")
        val PROGRESS_THRESHOLD = intPreferencesKey("progress_threshold")
        val BUFFER_SIZE_THRESHOLD = intPreferencesKey("buffer_size_threshold")
        val MESSAGES_PAGE_SIZE = intPreferencesKey("messages_page_size")

        const val DEFAULT_MESSAGES_PAGE_SIZE = 50

        /** Temporary external-VLC playhead; cleared on cache/history clear or Play from start. */
        val LAST_PLAYBACK_MESSAGE_ID = longPreferencesKey("last_playback_message_id")
        val LAST_PLAYBACK_POSITION_MS = longPreferencesKey("last_playback_position_ms")
        val LAST_PLAYBACK_DURATION_MS = longPreferencesKey("last_playback_duration_ms")

        const val MIN_RESUME_POSITION_MS = 5_000L
        const val END_RESUME_CLEAR_MS = 5_000L

        val IS_ONBOARDING_DONE = booleanPreferencesKey("is_onboarding_done")

        val IS_TOC_ACCEPTED = booleanPreferencesKey("is_toc_accepted")

        val IS_TEST_MODE = booleanPreferencesKey("is_test_mode")
        val ACTIVE_PHONE = stringPreferencesKey("active_phone")

        val STORAGE_AUTO_DELETE = booleanPreferencesKey("storage_auto_delete")
        val STORAGE_MOVE_TO_SD = booleanPreferencesKey("storage_move_to_sd")
        val STORAGE_THRESHOLD_MB = intPreferencesKey("storage_threshold_mb")
    }


    val isTestMode: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[IS_TEST_MODE] ?: false
    }

    suspend fun setTestMode(isTestMode: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[IS_TEST_MODE] = isTestMode
        }
    }

    val isOnboardingDone: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[IS_ONBOARDING_DONE] ?: false
    }

    suspend fun setOnboardingDone(isOnboardingDone: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[IS_ONBOARDING_DONE] = isOnboardingDone
        }
    }


    val isTocAccepted: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[IS_TOC_ACCEPTED] ?: false
    }

    suspend fun setTocAccepted(isTocAccepted: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[IS_TOC_ACCEPTED] = isTocAccepted
        }
    }

    val autoPlay: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[AUTO_PLAY] ?: true
    }

    suspend fun setAutoPlay(autoPlay: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[AUTO_PLAY] = autoPlay
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

    val messagesPageSize: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[MESSAGES_PAGE_SIZE] ?: DEFAULT_MESSAGES_PAGE_SIZE
    }

    suspend fun setMessagesPageSize(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[MESSAGES_PAGE_SIZE] = value.coerceIn(10, 500)
        }
    }

    suspend fun setActivePhone(phone: String) {
        appContext.dataStore.edit { preferences ->
            if (phone.isBlank()) {
                preferences.remove(ACTIVE_PHONE)
            } else {
                preferences[ACTIVE_PHONE] = phone
            }
        }
    }

    suspend fun getActivePhone(): String? =
        appContext.dataStore.data.first()[ACTIVE_PHONE]?.takeIf { it.isNotBlank() }

    /**
     * Saves a temporary resume bookmark for [messageId].
     * Positions near the start or end are treated as finished / not worth resuming and clear storage.
     */
    suspend fun savePlaybackPosition(messageId: Long, positionMs: Long, durationMs: Long = 0L) {
        if (positionMs < MIN_RESUME_POSITION_MS) {
            clearPlaybackPosition()
            return
        }
        if (durationMs > 0L && durationMs - positionMs < END_RESUME_CLEAR_MS) {
            clearPlaybackPosition()
            return
        }
        appContext.dataStore.edit { preferences ->
            preferences[LAST_PLAYBACK_MESSAGE_ID] = messageId
            preferences[LAST_PLAYBACK_POSITION_MS] = positionMs
            preferences[LAST_PLAYBACK_DURATION_MS] = durationMs.coerceAtLeast(0L)
        }
    }

    /** Returns saved position ms for [messageId], or null if none / different title. */
    suspend fun getPlaybackPosition(messageId: Long): Long? {
        val prefs = appContext.dataStore.data.first()
        val savedId = prefs[LAST_PLAYBACK_MESSAGE_ID] ?: return null
        if (savedId != messageId) return null
        val positionMs = prefs[LAST_PLAYBACK_POSITION_MS] ?: return null
        if (positionMs < MIN_RESUME_POSITION_MS) return null
        val durationMs = prefs[LAST_PLAYBACK_DURATION_MS] ?: 0L
        if (durationMs > 0L && durationMs - positionMs < END_RESUME_CLEAR_MS) return null
        return positionMs
    }

    data class PlaybackInfo(val messageId: Long, val positionMs: Long, val durationMs: Long)

    // ponytail: single bookmark only; switch to map keys playback_${id}_pos when >1 resume needed
    suspend fun getLastPlaybackInfo(): PlaybackInfo? {
        val prefs = appContext.dataStore.data.first()
        val savedId = prefs[LAST_PLAYBACK_MESSAGE_ID] ?: return null
        val positionMs = prefs[LAST_PLAYBACK_POSITION_MS] ?: return null
        val durationMs = prefs[LAST_PLAYBACK_DURATION_MS] ?: 0L
        if (positionMs < MIN_RESUME_POSITION_MS) return null
        if (durationMs > 0L && durationMs - positionMs < END_RESUME_CLEAR_MS) return null
        return PlaybackInfo(savedId, positionMs, durationMs)
    }

    suspend fun getPlaybackProgress(messageId: Long): Pair<Long, Long>? {
        val prefs = appContext.dataStore.data.first()
        val savedId = prefs[LAST_PLAYBACK_MESSAGE_ID] ?: return null
        if (savedId != messageId) return null
        val positionMs = prefs[LAST_PLAYBACK_POSITION_MS] ?: return null
        val durationMs = prefs[LAST_PLAYBACK_DURATION_MS] ?: 0L
        if (positionMs < MIN_RESUME_POSITION_MS) return null
        if (durationMs > 0L && durationMs - positionMs < END_RESUME_CLEAR_MS) return null
        return positionMs to durationMs
    }

    suspend fun clearPlaybackPosition() {
        appContext.dataStore.edit { preferences ->
            preferences.remove(LAST_PLAYBACK_MESSAGE_ID)
            preferences.remove(LAST_PLAYBACK_POSITION_MS)
            preferences.remove(LAST_PLAYBACK_DURATION_MS)
        }
    }

    val storageAutoDelete: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[STORAGE_AUTO_DELETE] ?: true
    }

    val storageMoveToSd: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[STORAGE_MOVE_TO_SD] ?: false
    }

    val storageThresholdMb: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[STORAGE_THRESHOLD_MB] ?: 500
    }

    suspend fun setStorageAutoDelete(value: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[STORAGE_AUTO_DELETE] = value
        }
    }

    suspend fun setStorageMoveToSd(value: Boolean) {
        appContext.dataStore.edit { preferences ->
            preferences[STORAGE_MOVE_TO_SD] = value
        }
    }

    suspend fun setStorageThresholdMb(value: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[STORAGE_THRESHOLD_MB] = value.coerceIn(200, 2000)
        }
    }
}
