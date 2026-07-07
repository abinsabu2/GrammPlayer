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

        val chatTitles = listOf(
            "Movies Channel", "Series Hub", "Documentaries", "Music Videos", "Short Films",
            "Anime Collection", "Sports Highlights", "Comedy Central", "Action Packed",
            "Horror Nights", "Sci-Fi Universe", "Romance Movies", "Thriller Zone",
            "Classic Cinema", "Indie Films", "Foreign Films", "Award Winners",
            "Box Office Hits", "Cult Classics", "Family Movies", "Adventure Time",
            "Fantasy Realm", "Mystery Files", "Biography Channel", "History Docs",
            "Nature & Wildlife", "Travel Shows", "Food Network", "Tech Reviews",
            "Gaming World", "K-Drama Hub", "Bollywood Mix", "Hollywood Blockbusters",
            "Netflix Originals", "HBO Classics", "Disney Magic", "Marvel Universe",
            "DC Comics", "Star Wars Galaxy", "Jurassic World", "Fast & Furious",
            "Mission Impossible", "James Bond", "Harry Potter", "Lord of the Rings",
            "The Hobbit", "Pirates Collection", "Transformers", "Avengers Assemble",
            "X-Men Series", "Spider-Man Vault", "Batman Files", "Superman Legacy",
            "Wonder Woman", "Aquaman Depths", "Flash Collection", "Arrow Universe",
            "The Office Clips", "Friends Forever", "Breaking Bad Best", "Game of Thrones",
            "Stranger Things", "Black Mirror", "Westworld", "The Crown",
            "Peaky Blinders", "Narcos World", "Money Heist", "Dark Series",
            "Mindhunter", "Ozark Files", "Better Call Saul", "The Boys",
            "Mandalorian", "Loki Files", "WandaVision", "Hawkeye",
            "Moon Knight", "She-Hulk", "Ms. Marvel", "What If",
            "Invincible", "The Umbrella Academy", "Witcher World", "Bridgerton",
            "Emily in Paris", "Lupin Files", "Squid Game", "All of Us Are Dead",
            "Hellbound", "Kingdom Series", "Sweet Home", "DP World",
            "Vincenzo", "My Mister", "Signal Series", "Mouse Thriller",
            "Beyond Evil", "Taxi Driver", "Voice Series", "Bad Guys"
        )

        val chatTypes = listOf(1, 2, 3)
        val photoIds = (1..100).map { "photo_%03d".format(it) }

        val chats = (1..100).map { i ->
            Chat(
                id = i.toLong(),
                type = chatTypes[(i - 1) % chatTypes.size],
                title = chatTitles[i - 1],
                photoId = photoIds[i - 1],
                lastMessageId = 0,
                order = i,
                isPinned = i <= 5,
                isMarkedAsUnread = i % 10 == 0,
                isBlocked = false,
                hasScheduledMessages = i % 7 == 0,
                canBeDeletedOnlyForSelf = true,
                canBeDeletedForAllUsers = i % 3 == 0,
                canBeReported = true,
                defaultDisableNotification = i % 8 == 0,
                unreadCount = (i % 20),
                lastReadInboxMessageId = 0,
                lastReadOutboxMessageId = 0,
                unreadMentionCount = i % 5,
                unreadReactionCount = i % 3,
                notificationSettingsMuteFor = 0,
                replyMarkupMessageId = 0,
                draftMessageText = "",
                clientData = "",
                userId = userIds[(i - 1) % userIds.size].toInt()
            )
        }

        return chats.map { db.chatDao().insert(it) }
    }

    private suspend fun seedMediaMessages(db: AppDatabase, chatIds: List<Long>): List<Long> {
        if (chatIds.isEmpty() || db.mediaMessageDao().count() > 0) return emptyList()

        val mediaTitles = listOf(
            "Episode", "Chapter", "Part", "Volume", "Season Finale",
            "Pilot", "Special", "Extended Cut", "Director's Cut", "Unrated"
        )

        val studios = listOf(
            "Warner Bros", "Paramount Pictures", "AMC Studios", "BBC Studios",
            "Queen Productions", "Indie Films Co", "MAPPA", "FIFA",
            "Netflix Studios", "HBO Productions", "Disney Studios", "Amazon Originals",
            "Apple TV+", "Hulu Originals", "Sony Pictures", "Universal Studios",
            "20th Century Fox", "Lionsgate", "MGM Studios", "DreamWorks"
        )

        val resolutions = listOf(
            Pair(1920, 1080),
            Pair(1280, 720),
            Pair(3840, 2160),
            Pair(2560, 1440),
            Pair(854, 480)
        )

        val mimeType = "video/mp4"
        val insertedIds = mutableListOf<Long>()
        var messageId = 1

        for ((chatIndex, chatId) in chatIds.withIndex()) {
            val chatNum = chatIndex + 1

            for (msgIndex in 1..1000) {
                val titlePrefix = mediaTitles[(msgIndex - 1) % mediaTitles.size]
                val studio = studios[(messageId - 1) % studios.size]
                val res = resolutions[(messageId - 1) % resolutions.size]
                val uniqueSuffix = "%03d_%04d".format(chatNum, msgIndex)
                val slug = "chat${chatNum}_msg${msgIndex}"

                val message = MediaMessage(
                    id = messageId.toLong(),
                    chat = chatId.toInt(),
                    title = "$titlePrefix $msgIndex - Chat $chatNum",
                    description = "Media content #$msgIndex from channel $chatNum. Produced by $studio.",
                    studio = studio,
                    width = res.first,
                    height = res.second,
                    duration = 600 + (messageId.toLong() % 9000),
                    size = 204800L + ((messageId.toLong() % 50) * 102400L),
                    isMedia = true,
                    localPath = "/storage/chat$chatNum/$slug.mp4",
                    fileId = 1000 + messageId,
                    mimeType = mimeType,
                    videoUrl = "https://example.com/videos/$slug.mp4",
                    thumbnailPath = "/storage/thumbnails/$slug.jpg",
                    cardImageUrl = "https://example.com/cards/$slug.jpg",
                    backgroundImageUrl = "https://example.com/bg/$slug.jpg",
                    isDownloaded = messageId % 3 == 0,
                    isDownloadActive = messageId % 7 == 0,
                    uniqueId = "uid_${uniqueSuffix}"
                )

                val id = db.mediaMessageDao().insert(message)
                insertedIds.add(id)
                messageId++
            }
        }

        return insertedIds
    }

    private suspend fun seedHistory(
        db: AppDatabase,
        userIds: List<Long>,
        chatIds: List<Long>,
        messageIds: List<Long>
    ) {
        if (messageIds.isEmpty() || db.historyDao().count() > 0) return

        // Seed one history entry per chat, pointing to the first message of that chat
        // Messages are ordered: chat 1 has messageIds[0..999], chat 2 has [1000..1999], etc.
        val history = chatIds.mapIndexed { chatIndex, chatId ->
            val userId = userIds[chatIndex % userIds.size].toInt()
            val firstMessageOfChat = messageIds[chatIndex * 1000]
            History(
                user = userId,
                chat = chatId.toInt(),
                message = firstMessageOfChat,
                viewed = true,
                downloaded = chatIndex % 2 == 0
            )
        }

        history.forEach { db.historyDao().insert(it) }
    }
}