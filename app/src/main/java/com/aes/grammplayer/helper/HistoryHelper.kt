package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.config.TestUserConfig
import com.aes.grammplayer.db.model.model.UserType
import com.aes.grammplayer.history.HistoryStore
import com.aes.grammplayer.session.UserSession
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Session / active-user helpers.
 * History list persistence lives in [HistoryStore] (JSON per login), not Room.
 */
object HistoryHelper {

    private const val TAG = "HistoryHelper"

    /** Clears file-based history for the current login (+ temporary playback position). */
    suspend fun clear(context: Context) {
        HistoryStore.clear(context)
    }

    suspend fun prepareSession(context: Context) {
        restoreSession(context)
        syncActiveUser(context)
    }

    suspend fun restoreSession(context: Context) {
        if (UserSession.phoneNumber.trim().isNotEmpty()) return
        val phone = SettingsDataStore(context.applicationContext).getActivePhone() ?: return
        UserSession.initialize(phone)
        UserSession.userType =
            if (TestUserConfig.isTestUser(phone)) UserType.TEST else UserType.REAL
        Log.d(TAG, "Restored session for phone=$phone")
    }

    suspend fun persistActivePhone(context: Context, phone: String) {
        if (phone.isBlank()) return
        SettingsDataStore(context.applicationContext).setActivePhone(phone)
    }

    suspend fun syncActiveUser(context: Context) {
        restoreSession(context)
        val phone = UserSession.phoneNumber.trim()
        if (phone.isEmpty()) return
        persistActivePhone(context, phone)
    }
}
