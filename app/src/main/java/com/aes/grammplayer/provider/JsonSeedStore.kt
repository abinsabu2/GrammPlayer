package com.aes.grammplayer.provider

import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.db.model.MediaMessage
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object JsonSeedStore {

    private val gson: Gson = Gson()
    private const val SEED_JSON = "seed/test_seed.json"

    @Volatile
    private var cachedChats: List<Chat>? = null

    @Volatile
    private var cachedMessagesByChat: Map<Int, List<MediaMessage>>? = null

    // ponytail: in-memory seed, add Room back only if >10k items proves slow
    suspend fun getChatsPaged(limit: Int, offset: Int): List<Chat> = withContext(Dispatchers.IO) {
        if (cachedChats == null) {
            cachedChats = loadSeed().chats
        }
        cachedChats!!.drop(offset).take(limit)
    }

    suspend fun getMessagesPaged(chatId: Int, limit: Int, offset: Int): List<MediaMessage> = withContext(Dispatchers.IO) {
        if (cachedMessagesByChat == null) {
            cachedMessagesByChat = buildMessagesMap(loadSeed())
        }
        val all = cachedMessagesByChat!![chatId] ?: emptyList()
        all.drop(offset).take(limit)
    }

    private fun loadSeed(): SeedData {
        val ctx = GPlayerApplication.AppContext
        ctx.assets.open(SEED_JSON).use { input ->
            val json = input.bufferedReader().readText()
            return gson.fromJson(json, SeedData::class.java)
        }
    }

    private fun buildMessagesMap(seed: SeedData): Map<Int, List<MediaMessage>> {
        val map = mutableMapOf<Int, MutableList<MediaMessage>>()
        for (seedMsg in seed.mediaMessages) {
            val msg = seedMsg.toMediaMessage()
            map.getOrPut(msg.chat) { mutableListOf() }.add(msg)
        }
        return map
    }

    private data class SeedData(
        val version: Int,
        val user: UserSeed?,
        val chats: List<Chat>,
        val mediaMessages: List<MediaMessageSeed>
    )

    private data class UserSeed(
        val id: Int,
        val phone: String,
        val isTestUser: Boolean,
        val isConnected: Boolean
    )

    private data class MediaMessageSeed(
        val id: Long,
        @SerializedName(value = "chatId", alternate = ["chat"]) val chatId: Int,
        val title: String,
        val description: String,
        val studio: String,
        val width: Int,
        val height: Int,
        val duration: Long,
        val size: Long,
        val isMedia: Boolean,
        val localPath: String,
        val fileId: Int,
        val mimeType: String,
        val videoUrl: String,
        val thumbnailPath: String,
        val cardImageUrl: String,
        val backgroundImageUrl: String,
        val isDownloaded: Boolean,
        val isDownloadActive: Boolean,
        val uniqueId: String
    ) {
        fun toMediaMessage() = MediaMessage(
            id = id,
            chat = chatId,
            title = title,
            description = description,
            studio = studio,
            width = width,
            height = height,
            duration = duration,
            size = size,
            isMedia = isMedia,
            localPath = localPath,
            fileId = fileId,
            mimeType = mimeType,
            videoUrl = videoUrl,
            thumbnailPath = thumbnailPath,
            cardImageUrl = cardImageUrl,
            backgroundImageUrl = backgroundImageUrl,
            isDownloaded = isDownloaded,
            isDownloadActive = isDownloadActive,
            uniqueId = uniqueId
        )
    }
}
