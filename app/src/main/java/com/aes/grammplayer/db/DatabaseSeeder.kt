package com.aes.grammplayer.db

import com.aes.grammplayer.db.model.*

object DatabaseSeeder {

    suspend fun seed(db: AppDatabase) {
        seedSettings(db)
        val userIds = seedUsers(db)
        val chatIds = seedChats(db, userIds)
        val messageIds = seedMediaMessages(db, chatIds)
        seedHistory(db, userIds, chatIds, messageIds)
    }

    private suspend fun seedSettings(db: AppDatabase) {
        val settings = listOf(
            Settings(
                bufferSize = 1024,
                bufferPercentage = 25,
                autoplay = true,
                toc = false,
                onBoard = false,
                gridSize = 4
            )
        )
        settings.forEach { db.settingsDao().insert(it) }
    }

    private suspend fun seedUsers(db: AppDatabase): List<Long> {
        val users = listOf(
            User(phone = "+1234567890", isTest = false, validated = true),
            User(phone = "+0987654321", isTest = true,  validated = false),
            User(phone = "+1122334455", isTest = false, validated = true),
            User(phone = "+9988776655", isTest = true,  validated = true),
            User(phone = "+5544332211", isTest = false, validated = false)
        )
        return users.map { db.userDao().insert(it) }
    }

    private suspend fun seedChats(db: AppDatabase, userIds: List<Long>): List<Long> {
        val chats = listOf(
            Chat(name = "Movies Channel",      type = 1, user = userIds[0].toInt()),
            Chat(name = "Series Hub",          type = 1, user = userIds[0].toInt()),
            Chat(name = "Documentaries",       type = 2, user = userIds[1].toInt()),
            Chat(name = "Music Videos",        type = 2, user = userIds[2].toInt()),
            Chat(name = "Short Films",         type = 3, user = userIds[2].toInt()),
            Chat(name = "Anime Collection",    type = 1, user = userIds[3].toInt()),
            Chat(name = "Sports Highlights",   type = 2, user = userIds[4].toInt())
        )
        return chats.map { db.chatDao().insert(it) }
    }

    private suspend fun seedMediaMessages(db: AppDatabase, chatIds: List<Long>): List<Long> {
        val messages = listOf(
            MediaMessage(
                chat = chatIds[0].toInt(),
                title = "Inception",
                description = "A thief who steals corporate secrets through dream-sharing technology.",
                studio = "Warner Bros",
                isMedia = true,
                localPath = "/storage/movies/inception.mp4",
                fileId = 1001,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/inception.mp4",
                width = 1920,
                height = 1080,
                duration = 8880,
                size = 2048000,
                thumbnailPath = "/storage/thumbnails/inception.jpg",
                cardImageUrl = "https://example.com/cards/inception.jpg",
                backgroundImageUrl = "https://example.com/bg/inception.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_inception_001"
            ),
            MediaMessage(
                chat = chatIds[0].toInt(),
                title = "Interstellar",
                description = "A team of explorers travel through a wormhole in space.",
                studio = "Paramount Pictures",
                isMedia = true,
                localPath = "/storage/movies/interstellar.mp4",
                fileId = 1002,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/interstellar.mp4",
                width = 1920,
                height = 1080,
                duration = 10140,
                size = 3072000,
                thumbnailPath = "/storage/thumbnails/interstellar.jpg",
                cardImageUrl = "https://example.com/cards/interstellar.jpg",
                backgroundImageUrl = "https://example.com/bg/interstellar.jpg",
                isDownloaded = false,
                isDownloadActive = true,
                uniqueId = "uid_interstellar_002"
            ),
            MediaMessage(
                chat = chatIds[1].toInt(),
                title = "Breaking Bad S01E01",
                description = "A chemistry teacher diagnosed with cancer turns to making meth.",
                studio = "AMC Studios",
                isMedia = true,
                localPath = "/storage/series/bb_s01e01.mp4",
                fileId = 1003,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/bb_s01e01.mp4",
                width = 1280,
                height = 720,
                duration = 3240,
                size = 1024000,
                thumbnailPath = "/storage/thumbnails/bb_s01e01.jpg",
                cardImageUrl = "https://example.com/cards/bb_s01e01.jpg",
                backgroundImageUrl = "https://example.com/bg/bb_s01e01.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_bb_s01e01_003"
            ),
            MediaMessage(
                chat = chatIds[2].toInt(),
                title = "Planet Earth II",
                description = "David Attenborough narrates life in various habitats around the globe.",
                studio = "BBC Studios",
                isMedia = true,
                localPath = "/storage/docs/planet_earth_2.mp4",
                fileId = 1004,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/planet_earth_2.mp4",
                width = 3840,
                height = 2160,
                duration = 5400,
                size = 5120000,
                thumbnailPath = "/storage/thumbnails/planet_earth_2.jpg",
                cardImageUrl = "https://example.com/cards/planet_earth_2.jpg",
                backgroundImageUrl = "https://example.com/bg/planet_earth_2.jpg",
                isDownloaded = false,
                isDownloadActive = false,
                uniqueId = "uid_planet_earth_004"
            ),
            MediaMessage(
                chat = chatIds[3].toInt(),
                title = "Bohemian Rhapsody MV",
                description = "Official music video for Bohemian Rhapsody by Queen.",
                studio = "Queen Productions",
                isMedia = true,
                localPath = "/storage/music/bohemian_rhapsody.mp4",
                fileId = 1005,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/bohemian_rhapsody.mp4",
                width = 1920,
                height = 1080,
                duration = 354,
                size = 256000,
                thumbnailPath = "/storage/thumbnails/bohemian_rhapsody.jpg",
                cardImageUrl = "https://example.com/cards/bohemian_rhapsody.jpg",
                backgroundImageUrl = "https://example.com/bg/bohemian_rhapsody.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_bohemian_005"
            ),
            MediaMessage(
                chat = chatIds[4].toInt(),
                title = "The Silent Hour",
                description = "An award-winning short film about a deaf musician.",
                studio = "Indie Films Co",
                isMedia = true,
                localPath = "/storage/shorts/silent_hour.mp4",
                fileId = 1006,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/silent_hour.mp4",
                width = 1920,
                height = 1080,
                duration = 1200,
                size = 512000,
                thumbnailPath = "/storage/thumbnails/silent_hour.jpg",
                cardImageUrl = "https://example.com/cards/silent_hour.jpg",
                backgroundImageUrl = "https://example.com/bg/silent_hour.jpg",
                isDownloaded = false,
                isDownloadActive = false,
                uniqueId = "uid_silent_hour_006"
            ),
            MediaMessage(
                chat = chatIds[5].toInt(),
                title = "Attack on Titan S04E01",
                description = "The final season begins with the Marley arc.",
                studio = "MAPPA",
                isMedia = true,
                localPath = "/storage/anime/aot_s04e01.mp4",
                fileId = 1007,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/aot_s04e01.mp4",
                width = 1920,
                height = 1080,
                duration = 1440,
                size = 768000,
                thumbnailPath = "/storage/thumbnails/aot_s04e01.jpg",
                cardImageUrl = "https://example.com/cards/aot_s04e01.jpg",
                backgroundImageUrl = "https://example.com/bg/aot_s04e01.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_aot_s04e01_007"
            ),
            MediaMessage(
                chat = chatIds[6].toInt(),
                title = "World Cup 2022 Final Highlights",
                description = "Argentina vs France - the greatest final ever played.",
                studio = "FIFA",
                isMedia = true,
                localPath = "/storage/sports/wc2022_final.mp4",
                fileId = 1008,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/wc2022_final.mp4",
                width = 1920,
                height = 1080,
                duration = 900,
                size = 409600,
                thumbnailPath = "/storage/thumbnails/wc2022_final.jpg",
                cardImageUrl = "https://example.com/cards/wc2022_final.jpg",
                backgroundImageUrl = "https://example.com/bg/wc2022_final.jpg",
                isDownloaded = false,
                isDownloadActive = true,
                uniqueId = "uid_wc2022_final_008"
            )
        )
        return messages.map { db.mediaMessageDao().insert(it) }
    }

    private suspend fun seedHistory(
        db: AppDatabase,
        userIds: List<Long>,
        chatIds: List<Long>,
        messageIds: List<Long>
    ) {
        val history = listOf(
            History(user = userIds[0].toInt(), chat = chatIds[0].toInt(), message = messageIds[0].toInt()),
            History(user = userIds[0].toInt(), chat = chatIds[0].toInt(), message = messageIds[1].toInt()),
            History(user = userIds[0].toInt(), chat = chatIds[1].toInt(), message = messageIds[2].toInt()),
            History(user = userIds[1].toInt(), chat = chatIds[2].toInt(), message = messageIds[3].toInt()),
            History(user = userIds[2].toInt(), chat = chatIds[3].toInt(), message = messageIds[4].toInt()),
            History(user = userIds[2].toInt(), chat = chatIds[4].toInt(), message = messageIds[5].toInt()),
            History(user = userIds[3].toInt(), chat = chatIds[5].toInt(), message = messageIds[6].toInt()),
            History(user = userIds[4].toInt(), chat = chatIds[6].toInt(), message = messageIds[7].toInt())
        )
        history.forEach { db.historyDao().insert(it) }
    }
}