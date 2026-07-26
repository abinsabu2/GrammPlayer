package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.config.TestUserConfig
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.Settings
import com.aes.grammplayer.db.model.User
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
        withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                var user = db.userDao().getByPhone(phone)
                if (user == null) {
                    val nextId = db.userDao().getAll().first().maxOfOrNull { it.id }?.plus(1L) ?: 1L
                    user = User(
                        id = nextId,
                        phone = phone,
                        isTestUser = UserSession.isTestUser(),
                        isConnected = true
                    )
                    db.userDao().insert(user)
                } else {
                    user = user.copy(isConnected = true)
                    db.userDao().update(user)
                }

                db.userDao().getAll().first().forEach { other ->
                    if (other.id != user.id && other.isConnected) {
                        db.userDao().update(other.copy(isConnected = false))
                    }
                }

                val userId = user.id.toInt()
                val settings = db.settingsDao().getAll().first().firstOrNull()
                if (settings != null) {
                    db.settingsDao().update(settings.copy(activeUserId = userId, userConnected = true))
                } else {
                    db.settingsDao().insert(
                        Settings(
                            id = 1,
                            autoplay = true,
                            activeUserId = userId,
                            userConnected = true
                        )
                    )
                }
                Log.d(TAG, "Synced active user id=$userId phone=$phone")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync active user for phone=$phone", e)
            }
        }
    }
}
