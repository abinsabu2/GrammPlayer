package com.aes.grammplayer.ui.features.history

import com.aes.grammplayer.db.model.MediaMessage

data class HistoryItem(
    val message: MediaMessage,
    val isViewed: Boolean,
    val isDownloaded: Boolean,
    val isDownloading: Boolean = false
)