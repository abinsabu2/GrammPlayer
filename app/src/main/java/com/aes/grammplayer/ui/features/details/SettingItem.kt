package com.aes.grammplayer.ui.features.details

data class SettingItem(
    val iconRes: Int? = null,
    val value: String,
    val caption: String,
    val subCaption: String? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true
)