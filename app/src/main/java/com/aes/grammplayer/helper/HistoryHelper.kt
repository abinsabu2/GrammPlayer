package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import com.aes.grammplayer.config.TestUserConfig
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.db.model.History
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.db.model.Settings
import com.aes.grammplayer.db.model.User
import com.aes.grammplayer.db.model.model.UserType
import com.aes.grammplayer.session.UserSession
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object HistoryHelper {

    private const val TAG = "HistoryHelper"

    suspend fun recordDetailVisit(context: Context, message: MediaMessage) {
        prepareSession(context)
        record(context, message, viewed = true)
    }

    suspend fun record(
        context: Context,
        message: MediaMessage,
        viewed: Boolean = false,
        downloaded: Boolean = false,
        downloading: Boolean = false
    ) {
        if (!viewed && !downloaded && !downloading) return
        withContext(Dispatchers.IO) {
            try {
                prepareSession(context)
                val db = AppDatabase.getDatabase(context)
                val userId = resolveUserId(db)
                ensureUserExists(db, userId)
                ensureDefaultSettings(db, userId)
                ensureChatExists(db, message, userId)

                val isDownloaded = downloaded || message.isDownloaded
                val isDownloading = downloading && !isDownloaded
                val snapshot = message.copy(
                    isDownloaded = isDownloaded,
                    isDownloadActive = !isDownloaded && (isDownloading || message.isDownloadActive),
                    localPath = message.localPath
                )
                db.mediaMessageDao().insert(snapshot)

                val existing = db.historyDao().getByUserAndMessage(userId, snapshot.id)
                // Delete first so re-visits get a new auto-increment id and appear at the top.
                db.historyDao().deleteByUserAndMessage(userId, snapshot.id)
                val rowId = db.historyDao().insert(
                    History(
                        user = userId,
                        chat = snapshot.chat,
                        message = snapshot.id,
                        viewed = (existing?.viewed == true) || viewed,
                        downloaded = (existing?.downloaded == true) || downloaded,
                        downloading = when {
                            downloaded -> false
                            isDownloading -> true
                            else -> existing?.downloading == true
                        }
                    )
                )
                Log.d(
                    TAG,
                    "Recorded history id=$rowId user=$userId message=${snapshot.id} viewed=$viewed downloaded=$downloaded downloading=$isDownloading"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record history for message ${message.id}", e)
            }
        }
    }

    suspend fun clearDownloading(context: Context, message: MediaMessage) {
        withContext(Dispatchers.IO) {
            try {
                prepareSession(context)
                val db = AppDatabase.getDatabase(context)
                val userId = resolveUserId(db)
                val existing = db.historyDao().getByUserAndMessage(userId, message.id) ?: return@withContext

                db.mediaMessageDao().insert(message.copy(isDownloadActive = false))
                db.historyDao().deleteByUserAndMessage(userId, message.id)
                db.historyDao().insert(existing.copy(downloading = false))
                Log.d(TAG, "Cleared downloading flag user=$userId message=${message.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear downloading for message ${message.id}", e)
            }
        }
    }

    suspend fun clear(context: Context) {
        withContext(Dispatchers.IO) {
            prepareSession(context)
            val db = AppDatabase.getDatabase(context)
            val userId = resolveActiveUserId(db)
            db.historyDao().clearHistoryForUser(userId)
            Log.d(TAG, "Cleared history for user=$userId")
        }
    }

    suspend fun resolveActiveUserId(context: Context): Int =
        resolveActiveUserId(AppDatabase.getDatabase(context))

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

    private suspend fun resolveUserId(db: AppDatabase): Int = resolveActiveUserId(db)

    private suspend fun resolveActiveUserId(db: AppDatabase): Int {
        val phone = UserSession.phoneNumber.trim()
        if (phone.isNotEmpty()) {
            db.userDao().getByPhone(phone)?.let { return it.id.toInt() }
        }

        val settings = db.settingsDao().getAll().first().firstOrNull()
        if (settings != null) return settings.activeUserId

        val connectedUsers = db.userDao().getConnectedUsers()
        if (connectedUsers.size == 1) return connectedUsers.first().id.toInt()

        return 1
    }

    private suspend fun ensureUserExists(db: AppDatabase, userId: Int) {
        if (db.userDao().getById(userId).first() != null) return
        db.userDao().insert(
            User(
                id = userId.toLong(),
                phone = UserSession.phoneNumber,
                isTestUser = UserSession.isTestUser(),
                isConnected = true
            )
        )
        Log.d(TAG, "Created stub user id=$userId")
    }

    private suspend fun ensureDefaultSettings(db: AppDatabase, userId: Int) {
        if (db.settingsDao().count() > 0) return
        db.settingsDao().insert(
            Settings(
                id = 1,
                autoplay = true,
                activeUserId = userId
            )
        )
        Log.d(TAG, "Created default settings for user=$userId")
    }

    private suspend fun ensureChatExists(db: AppDatabase, message: MediaMessage, userId: Int) {
        val existing = db.chatDao().getById(message.chat).first()
        if (existing != null) {
            if (existing.userId != userId) {
                db.chatDao().update(existing.copy(userId = userId))
                Log.d(TAG, "Reassigned chat ${message.chat} to user=$userId")
            }
            return
        }

        db.chatDao().insert(
            Chat(
                id = message.chat.toLong(),
                type = 1,
                title = "Chat ${message.chat}",
                photoId = "",
                lastMessageId = 0,
                order = 0,
                isPinned = false,
                isMarkedAsUnread = false,
                isBlocked = false,
                hasScheduledMessages = false,
                canBeDeletedOnlyForSelf = true,
                canBeDeletedForAllUsers = false,
                canBeReported = true,
                defaultDisableNotification = false,
                unreadCount = 0,
                lastReadInboxMessageId = 0,
                lastReadOutboxMessageId = 0,
                unreadMentionCount = 0,
                unreadReactionCount = 0,
                notificationSettingsMuteFor = 0,
                replyMarkupMessageId = 0,
                draftMessageText = "",
                clientData = "",
                userId = userId
            )
        )
        Log.d(TAG, "Created stub chat id=${message.chat} for user=$userId")
    }
}