package com.aes.grammplayer.history

import android.content.Context
import android.util.Log
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.HistoryHelper
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.provider.Page
import com.aes.grammplayer.session.UserSession
import com.aes.grammplayer.ui.features.history.HistoryItem
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * File-based history for the current login.
 *
 * - One JSON file per login under filesDir/history/
 * - Cap [MAX_ENTRIES]; newest first
 * - Written only on detail-page visit
 * - Deleted on logout / clear history
 */
object HistoryStore {

    private const val TAG = "HistoryStore"
    const val FILE_VERSION = 1
    const val MAX_ENTRIES = 100
    const val DEFAULT_PAGE_SIZE = 20

    private val gson: Gson = GsonBuilder().create()
    private val mutex = Mutex()

    @Volatile
    private var cachedLoginKey: String? = null

    @Volatile
    private var cachedEntries: List<HistoryEntry>? = null

    suspend fun recordVisit(context: Context, message: MediaMessage) = withContext(Dispatchers.IO) {
        if (message.id == 0L && message.fileId == 0) return@withContext
        val app = context.applicationContext
        val loginKey = resolveLoginKey(app)
        val entry = HistoryEntry.fromMessage(message)
        mutex.withLock {
            val current = loadEntriesLocked(app, loginKey).toMutableList()
            current.removeAll { it.messageId == entry.messageId }
            current.add(0, entry)
            val trimmed = if (current.size > MAX_ENTRIES) current.take(MAX_ENTRIES) else current
            writeEntriesLocked(app, loginKey, trimmed)
            Log.d(TAG, "recordVisit messageId=${entry.messageId} size=${trimmed.size} login=$loginKey")
        }
    }

    suspend fun clear(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val loginKey = resolveLoginKey(app)
        mutex.withLock {
            deleteFileLocked(app, loginKey)
            SettingsDataStore(app).clearPlaybackPosition()
            Log.d(TAG, "Cleared history file for login=$loginKey")
        }
    }

    /**
     * Lazy page of history items (newest first).
     * Loads the JSON once into memory, then slices for UI paging.
     */
    suspend fun loadPage(
        context: Context,
        offset: Int,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): Page<HistoryItem> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val loginKey = resolveLoginKey(app)
        mutex.withLock {
            val entries = loadEntriesLocked(app, loginKey)
            if (offset >= entries.size) {
                return@withContext Page(
                    items = emptyList(),
                    nextOffset = offset,
                    nextCursor = 0L,
                    endReached = true
                )
            }
            val page = entries.drop(offset).take(pageSize.coerceAtLeast(1))
            val nextOffset = offset + page.size
            Page(
                items = page.map { it.toHistoryItem() },
                nextOffset = nextOffset,
                nextCursor = 0L,
                endReached = nextOffset >= entries.size
            )
        }
    }

    suspend fun latestBackdropUrl(context: Context): String? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val loginKey = resolveLoginKey(app)
        mutex.withLock {
            val entries = loadEntriesLocked(app, loginKey)
            for (entry in entries) {
                entry.backgroundImageUrl
                    .takeIf { PosterFetcher.isTrustedImageUrl(it) }
                    ?.let { return@withContext it }
            }
            null
        }
    }

    suspend fun resolveLoginKey(context: Context): String {
        HistoryHelper.prepareSession(context)
        val phone = UserSession.phoneNumber.trim()
        if (phone.isNotEmpty()) return sanitizeLoginKey(phone)
        val stored = SettingsDataStore(context.applicationContext).getActivePhone()?.trim().orEmpty()
        if (stored.isNotEmpty()) return sanitizeLoginKey(stored)
        return "user_default"
    }

    private fun sanitizeLoginKey(raw: String): String =
        raw.replace(Regex("[^A-Za-z0-9_+.-]"), "_").ifBlank { "user_default" }

    private fun historyDir(context: Context): File =
        File(context.filesDir, "history").also { if (!it.exists()) it.mkdirs() }

    private fun historyFile(context: Context, loginKey: String): File =
        File(historyDir(context), "history_${sanitizeLoginKey(loginKey)}.json")

    private fun loadEntriesLocked(context: Context, loginKey: String): List<HistoryEntry> {
        if (cachedLoginKey == loginKey && cachedEntries != null) {
            return cachedEntries!!
        }
        val file = historyFile(context, loginKey)
        if (!file.isFile || file.length() == 0L) {
            cachedLoginKey = loginKey
            cachedEntries = emptyList()
            return emptyList()
        }
        return try {
            val envelope = gson.fromJson(file.readText(), HistoryFile::class.java)
            val entries = envelope?.entries.orEmpty()
            cachedLoginKey = loginKey
            cachedEntries = entries
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Corrupt history file ${file.name}; treating as empty", e)
            try {
                file.delete()
            } catch (_: Exception) {
            }
            cachedLoginKey = loginKey
            cachedEntries = emptyList()
            emptyList()
        }
    }

    private fun writeEntriesLocked(context: Context, loginKey: String, entries: List<HistoryEntry>) {
        val file = historyFile(context, loginKey)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        val envelope = HistoryFile(version = FILE_VERSION, loginKey = loginKey, entries = entries)
        tmp.writeText(gson.toJson(envelope))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        cachedLoginKey = loginKey
        cachedEntries = entries
    }

    private fun deleteFileLocked(context: Context, loginKey: String) {
        val file = historyFile(context, loginKey)
        if (file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete ${file.absolutePath}")
        }
        File(file.parentFile, "${file.name}.tmp").delete()
        if (cachedLoginKey == loginKey) {
            cachedLoginKey = null
            cachedEntries = null
        }
    }
}
