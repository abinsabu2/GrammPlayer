package com.aes.grammplayer.util.tdlib

import com.aes.grammplayer.db.model.MediaMessage
import org.drinkless.tdlib.TdApi

object MediaMessageMapper {

    fun fromTdFile(
        file: TdApi.File,
        chatId: Long,
        title: String,
        description: String,
        mimeType: String,
        thumbnailPath: String,
        width: Int = 0,
        height: Int = 0,
        duration: Long = 0L,
        isMedia: Boolean = true
    ): MediaMessage = MediaMessage(
        id = file.id.toLong(),
        chat = chatId.toInt(),
        title = title,
        description = description,
        studio = "Telegram",
        isMedia = isMedia,
        localPath = file.local.path.ifEmpty { "" },
        fileId = file.id,
        mimeType = mimeType,
        videoUrl = "",
        width = width,
        height = height,
        duration = duration,
        size = file.size,
        thumbnailPath = thumbnailPath,
        cardImageUrl = thumbnailPath,
        backgroundImageUrl = "",
        isDownloaded = file.local.isDownloadingCompleted,
        isDownloadActive = file.local.isDownloadingActive,
        uniqueId = file.remote.uniqueId.ifEmpty { "" }
    )

    fun textMessage(chatId: Long, id: Long, text: String): MediaMessage =
        unsupported(chatId, id, text, text)

    fun unsupported(
        chatId: Long,
        id: Long,
        title: String = "Unsupported Content",
        description: String = "This message type is not currently supported."
    ): MediaMessage =
        MediaMessage(
            id = id,
            chat = chatId.toInt(),
            title = title,
            description = description,
            studio = "Telegram",
            isMedia = false,
            localPath = "",
            fileId = 0,
            mimeType = "",
            videoUrl = "",
            width = 0,
            height = 0,
            duration = 0L,
            size = 0L,
            thumbnailPath = "",
            cardImageUrl = "",
            backgroundImageUrl = "",
            isDownloaded = false,
            isDownloadActive = false,
            uniqueId = ""
        )
}