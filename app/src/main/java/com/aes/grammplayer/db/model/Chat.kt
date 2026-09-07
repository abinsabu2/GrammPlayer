package com.aes.grammplayer.db.model

import java.io.Serializable

data class Chat(
    val id: Long,
    val type: Int,
    val title: String,
    val photoId: String,
    val lastMessageId: Int,
    val order: Int,
    val isPinned: Boolean,
    val isMarkedAsUnread: Boolean,
    val isBlocked: Boolean,
    val hasScheduledMessages: Boolean,
    val canBeDeletedOnlyForSelf: Boolean,
    val canBeDeletedForAllUsers: Boolean,
    val canBeReported: Boolean,
    val defaultDisableNotification: Boolean,
    val unreadCount: Int,
    val lastReadInboxMessageId: Int,
    val lastReadOutboxMessageId: Int,
    val unreadMentionCount: Int,
    val unreadReactionCount: Int,
    val notificationSettingsMuteFor: Int,
    val replyMarkupMessageId: Int,
    val draftMessageText: String,
    val clientData: String,
    val userId: Int
) : Serializable
