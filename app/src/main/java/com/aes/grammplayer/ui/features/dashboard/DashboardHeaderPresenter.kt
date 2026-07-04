package com.aes.grammplayer.ui.features.dashboard

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowHeaderPresenter
import com.aes.grammplayer.R

/**
 * Renders the left sidebar rows (Chats / History / Preferences) as an
 * icon + label, with the icon and text fading from gray to teal/white as
 * the header gains focus (selectLevel goes 0f -> 1f during scroll/transition).
 *
 * Note: Leanback passes the [Row] itself into onBindViewHolder (not the
 * HeaderItem directly) — the header item is reached via row.headerItem.
 */
class DashboardHeaderPresenter : RowHeaderPresenter() {

    companion object {
        private const val TITLE_UNSELECTED = 0xFF9E9E9E.toInt()
        private const val TITLE_SELECTED = 0xFFFFFFFF.toInt()
        private const val ICON_UNSELECTED = 0xFF6E6E6E.toInt()
        private const val ICON_SELECTED = 0xFF4ECDC4.toInt() // accent_teal
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.header_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        val row = item as? Row ?: return
        val header = row.headerItem as? DashboardHeaderItem ?: return

        val view = viewHolder.view
        view.findViewById<ImageView>(R.id.icon).setImageResource(header.iconRes)
        view.findViewById<TextView>(R.id.title).text = header.name

        (viewHolder as? ViewHolder)?.let { applySelectLevel(it) }
    }

    override fun onSelectLevelChanged(holder: ViewHolder) {
        applySelectLevel(holder)
    }

    private fun applySelectLevel(holder: ViewHolder) {
        val level = holder.selectLevel
        val title = holder.view.findViewById<TextView>(R.id.title) ?: return
        val icon = holder.view.findViewById<ImageView>(R.id.icon) ?: return

        title.setTextColor(ColorUtils.blendARGB(TITLE_UNSELECTED, TITLE_SELECTED, level))
        icon.setColorFilter(ColorUtils.blendARGB(ICON_UNSELECTED, ICON_SELECTED, level))

        // Drives header_item_background's selector (state_activated) to show
        // the rounded teal highlight only on the currently selected row.
        holder.view.isActivated = level >= 1f
    }
}