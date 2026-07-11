package com.aes.grammplayer.ui.features.dashboard


import androidx.leanback.widget.HeaderItem

/**
 * Sidebar header item (Chats / History / Preferences) that also carries an
 * icon, so DashboardHeaderPresenter can render icon + label instead of the
 * default text-only header row.
 */
class DashboardHeaderItem(
    id: Long,
    name: String,
    val iconRes: Int,
    val showProgressIcon: Boolean = false
) : HeaderItem(id, name) {

    /** Mutable label for rows that update in place (e.g. download progress). */
    var displayName: String = name

    /** When true, header uses ready styling instead of in-progress yellow. */
    var isReady: Boolean = false
}