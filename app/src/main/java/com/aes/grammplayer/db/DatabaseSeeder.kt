package com.aes.grammplayer.db

import com.aes.grammplayer.db.model.*

object DatabaseSeeder {

    suspend fun seed(db: AppDatabase) {
        val userIds = seedUsers(db)
        seedSettings(db, userIds)
        val chatIds = seedChats(db, userIds)
        val messageIds = seedMediaMessages(db, chatIds)
        seedHistory(db, userIds, chatIds, messageIds)
    }

    private suspend fun seedSettings(db: AppDatabase, userIds: List<Long>) {
        if (db.settingsDao().count() > 0) return
        val settings = listOf(
            Settings(
                id = 1,
                bufferSize = 1024,
                bufferPercentage = 25,
                autoplay = true,
                isTocAccepted = false,
                isOnBoard = false,
                gridSize = 4,
                activeUserId = userIds.firstOrNull()?.toInt() ?: 1,
                userConnected = false
            )
        )
        settings.forEach { db.settingsDao().insert(it) }
    }

    private suspend fun seedUsers(db: AppDatabase): List<Long> {
        if (db.userDao().count() > 0) return emptyList()
        val users = listOf(
            User(id = 1, phone = "+1234567890", isTestUser = false, isConnected = true),
            User(id = 2, phone = "+0987654321", isTestUser = true, isConnected = false),
            User(id = 3, phone = "+1122334455", isTestUser = false, isConnected = true),
            User(id = 4, phone = "+9988776655", isTestUser = true, isConnected = true),
            User(id = 5, phone = "+5544332211", isTestUser = false, isConnected = false)
        )
        return users.map { db.userDao().insert(it) }
    }

    private suspend fun seedChats(db: AppDatabase, userIds: List<Long>): List<Long> {
        if (userIds.isEmpty() || db.chatDao().count() > 0) return emptyList()
        val chats = listOf(
            Chat(
                id = 1,
                type = 1,
                title = "Movies Channel",
                photoId = "photo_001",
                lastMessageId = 0,
                order = 1,
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
                userId = userIds[0].toInt()
            ),
            Chat(
                id = 2,
                type = 1,
                title = "Series Hub",
                photoId = "photo_002",
                lastMessageId = 0,
                order = 2,
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
                userId = userIds[0].toInt()
            ),
            Chat(
                id = 3,
                type = 2,
                title = "Documentaries",
                photoId = "photo_003",
                lastMessageId = 0,
                order = 3,
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
                userId = userIds[1].toInt()
            ),
            Chat(
                id = 4,
                type = 2,
                title = "Music Videos",
                photoId = "photo_004",
                lastMessageId = 0,
                order = 4,
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
                userId = userIds[2].toInt()
            ),
            Chat(
                id = 5,
                type = 3,
                title = "Short Films",
                photoId = "photo_005",
                lastMessageId = 0,
                order = 5,
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
                userId = userIds[2].toInt()
            ),
            Chat(
                id = 6,
                type = 1,
                title = "Anime Collection",
                photoId = "photo_006",
                lastMessageId = 0,
                order = 6,
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
                userId = userIds[3].toInt()
            ),
            Chat(
                id = 7,
                type = 2,
                title = "Sports Highlights",
                photoId = "photo_007",
                lastMessageId = 0,
                order = 7,
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
                userId = userIds[4].toInt()
            )
        )
        return chats.map { db.chatDao().insert(it) }
    }

    private suspend fun seedMediaMessages(db: AppDatabase, chatIds: List<Long>): List<Long> {
        if (chatIds.isEmpty() || db.mediaMessageDao().count() > 0) return emptyList()
        val messages = listOf(
            MediaMessage(
                id = 1,
                chat = 1,
                title = "Inception",
                description = "A thief who steals corporate secrets through dream-sharing technology.",
                studio = "Warner Bros",
                width = 1920,
                height = 1080,
                duration = 8880,
                size = 2048000,
                isMedia = true,
                localPath = "/storage/movies/inception.mp4",
                fileId = 1001,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/inception.mp4",
                thumbnailPath = "/storage/thumbnails/inception.jpg",
                cardImageUrl = "https://example.com/cards/inception.jpg",
                backgroundImageUrl = "https://example.com/bg/inception.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_inception_001"
            ),
            MediaMessage(
                id = 2,
                chat = 1,
                title = "Interstellar",
                description = "A team of explorers travel through a wormhole in space.",
                studio = "Paramount Pictures",
                width = 1920,
                height = 1080,
                duration = 10140,
                size = 3072000,
                isMedia = true,
                localPath = "/storage/movies/interstellar.mp4",
                fileId = 1002,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/interstellar.mp4",
                thumbnailPath = "/storage/thumbnails/interstellar.jpg",
                cardImageUrl = "https://example.com/cards/interstellar.jpg",
                backgroundImageUrl = "https://example.com/bg/interstellar.jpg",
                isDownloaded = false,
                isDownloadActive = true,
                uniqueId = "uid_interstellar_002"
            ),
            MediaMessage(
                id = 3,
                chat = 2,
                title = "Breaking Bad S01E01",
                description = "A chemistry teacher diagnosed with cancer turns to making meth.",
                studio = "AMC Studios",
                width = 1280,
                height = 720,
                duration = 3240,
                size = 1024000,
                isMedia = true,
                localPath = "/storage/series/bb_s01e01.mp4",
                fileId = 1003,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/bb_s01e01.mp4",
                thumbnailPath = "/storage/thumbnails/bb_s01e01.jpg",
                cardImageUrl = "https://example.com/cards/bb_s01e01.jpg",
                backgroundImageUrl = "https://example.com/bg/bb_s01e01.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_bb_s01e01_003"
            ),
            MediaMessage(
                id = 4,
                chat = 3,
                title = "Planet Earth II",
                description = "David Attenborough narrates life in various habitats around the globe.",
                studio = "BBC Studios",
                width = 3840,
                height = 2160,
                duration = 5400,
                size = 5120000,
                isMedia = true,
                localPath = "/storage/docs/planet_earth_2.mp4",
                fileId = 1004,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/planet_earth_2.mp4",
                thumbnailPath = "/storage/thumbnails/planet_earth_2.jpg",
                cardImageUrl = "https://example.com/cards/planet_earth_2.jpg",
                backgroundImageUrl = "https://example.com/bg/planet_earth_2.jpg",
                isDownloaded = false,
                isDownloadActive = false,
                uniqueId = "uid_planet_earth_004"
            ),
            MediaMessage(
                id = 5,
                chat = 4,
                title = "Bohemian Rhapsody MV",
                description = "Official music video for Bohemian Rhapsody by Queen.",
                studio = "Queen Productions",
                width = 1920,
                height = 1080,
                duration = 354,
                size = 256000,
                isMedia = true,
                localPath = "/storage/music/bohemian_rhapsody.mp4",
                fileId = 1005,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/bohemian_rhapsody.mp4",
                thumbnailPath = "/storage/thumbnails/bohemian_rhapsody.jpg",
                cardImageUrl = "https://example.com/cards/bohemian_rhapsody.jpg",
                backgroundImageUrl = "https://example.com/bg/bohemian_rhapsody.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_bohemian_005"
            ),
            MediaMessage(
                id = 6,
                chat = 5,
                title = "The Silent Hour",
                description = "An award-winning short film about a deaf musician.",
                studio = "Indie Films Co",
                width = 1920,
                height = 1080,
                duration = 1200,
                size = 512000,
                isMedia = true,
                localPath = "/storage/shorts/silent_hour.mp4",
                fileId = 1006,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/silent_hour.mp4",
                thumbnailPath = "/storage/thumbnails/silent_hour.jpg",
                cardImageUrl = "https://example.com/cards/silent_hour.jpg",
                backgroundImageUrl = "https://example.com/bg/silent_hour.jpg",
                isDownloaded = false,
                isDownloadActive = false,
                uniqueId = "uid_silent_hour_006"
            ),
            MediaMessage(
                id = 7,
                chat = 6,
                title = "Attack on Titan S04E01",
                description = "The final season begins with the Marley arc.",
                studio = "MAPPA",
                width = 1920,
                height = 1080,
                duration = 1440,
                size = 768000,
                isMedia = true,
                localPath = "/storage/anime/aot_s04e01.mp4",
                fileId = 1007,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/aot_s04e01.mp4",
                thumbnailPath = "/storage/thumbnails/aot_s04e01.jpg",
                cardImageUrl = "https://example.com/cards/aot_s04e01.jpg",
                backgroundImageUrl = "https://example.com/bg/aot_s04e01.jpg",
                isDownloaded = true,
                isDownloadActive = false,
                uniqueId = "uid_aot_s04e01_007"
            ),
            MediaMessage(
                id = 8,
                chat = 7,
                title = "World Cup 2022 Final Highlights",
                description = "Argentina vs France - the greatest final ever played.",
                studio = "FIFA",
                width = 1920,
                height = 1080,
                duration = 900,
                size = 409600,
                isMedia = true,
                localPath = "/storage/sports/wc2022_final.mp4",
                fileId = 1008,
                mimeType = "video/mp4",
                videoUrl = "https://example.com/videos/wc2022_final.mp4",
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
        if (messageIds.isEmpty() || db.historyDao().count() > 0) return
        val history = listOf(
            History(user = userIds[0].toInt(), chat = 1, message = messageIds[0].toInt()),
            History(user = userIds[0].toInt(), chat = 1, message = messageIds[1].toInt()),
            History(user = userIds[0].toInt(), chat = 2, message = messageIds[2].toInt()),
            History(user = userIds[1].toInt(), chat = 3, message = messageIds[3].toInt()),
            History(user = userIds[2].toInt(), chat = 4, message = messageIds[4].toInt()),
            History(user = userIds[2].toInt(), chat = 5, message = messageIds[5].toInt()),
            History(user = userIds[3].toInt(), chat = 6, message = messageIds[6].toInt()),
            History(user = userIds[4].toInt(), chat = 7, message = messageIds[7].toInt())
        )
        history.forEach { db.historyDao().insert(it) }
    }
}