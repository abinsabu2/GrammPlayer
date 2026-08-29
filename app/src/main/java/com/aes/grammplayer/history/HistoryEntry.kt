package com.aes.grammplayer.history

import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.ui.features.history.HistoryItem

/**
 * Minimal fields required for history cards + reopening media details.
 * Written only when the user opens a detail page.
 */
data class HistoryEntry(
    val messageId: Long,
    val chatId: Int,
    val title: String,
    val fileId: Int,
    val size: Long,
    val localPath: String = "",
    val thumbnailPath: String = "",
    val cardImageUrl: String = "",
    val backgroundImageUrl: String = "",
    val mimeType: String = "",
    val uniqueId: String = "",
    val visitedAt: Long = System.currentTimeMillis(),
    val duration: Long = 0L
) {
    fun toMediaMessage(): MediaMessage {
        val onDisk = MediaFileHelper.existsOnDisk(localPath)
        return MediaMessage(
            id = messageId,
            chat = chatId,
            title = title,
            description = "",
            studio = "",
            width = 0,
            height = 0,
            duration = 0L,
            size = size,
            isMedia = true,
            localPath = localPath,
            fileId = fileId,
            mimeType = mimeType.ifBlank { "video/*" },
            videoUrl = "",
            thumbnailPath = thumbnailPath,
            cardImageUrl = cardImageUrl,
            backgroundImageUrl = backgroundImageUrl,
            isDownloaded = onDisk,
            isDownloadActive = false,
            uniqueId = uniqueId
        )
    }

    fun toHistoryItem(): HistoryItem {
        val message = toMediaMessage()
        val onDisk = MediaFileHelper.existsOnDisk(message.localPath)
        return HistoryItem(
            message = message,
            isViewed = true,
            isDownloaded = onDisk || message.isDownloaded,
            isDownloading = false
        )
    }

    companion object {
        fun fromMessage(message: MediaMessage, visitedAt: Long = System.currentTimeMillis()): HistoryEntry =
            HistoryEntry(
                messageId = message.id,
                chatId = message.chat,
                title = message.title,
                fileId = message.fileId,
                size = message.size,
                localPath = message.localPath.orEmpty(),
                thumbnailPath = message.thumbnailPath.orEmpty(),
                cardImageUrl = message.cardImageUrl.orEmpty(),
                backgroundImageUrl = message.backgroundImageUrl.orEmpty(),
                mimeType = message.mimeType.orEmpty(),
                uniqueId = message.uniqueId.orEmpty(),
                visitedAt = visitedAt
            )
    }
}

/** On-disk JSON envelope for one login. */
data class HistoryFile(
    val version: Int = HistoryStore.FILE_VERSION,
    val loginKey: String = "",
    val entries: List<HistoryEntry> = emptyList()
)
