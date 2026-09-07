package com.aes.grammplayer.ui.features.dashboard

import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.ui.features.history.HistoryItem

/**
 * Wrapper for Continue Watching card — holds resume position/duration alongside message.
 * Used in unified In Progress row (max 2 items).
 */
data class ContinueWatchingItem(
    val message: MediaMessage,
    val positionMs: Long,
    val durationMs: Long,
    val historyItem: HistoryItem? = null
)
