package com.aes.grammplayer.ui.features.dashboard

/**
 * Represents a single card in the dashboard UI — either the large "hero" card
 * (e.g. the Chats card with icon/eyebrow/title/description/button) or one of
 * the small icon-only cards (e.g. Clear Cache, Settings, Refresh Data, Logout).
 */
data class DashboardItem(
    val id: String,
    val iconRes: Int,
    val title: String?,
    val eyebrow: String? = null,
    val description: String? = null,
    val actionLabel: String? = null,
    val isHero: Boolean = false
)