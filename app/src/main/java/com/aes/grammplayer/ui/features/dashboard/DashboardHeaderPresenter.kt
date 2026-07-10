package com.aes.grammplayer.ui.features.dashboard

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        val view = holder.view
        val title = view.findViewById<TextView>(R.id.title) ?: return
        val icon = view.findViewById<ImageView>(R.id.icon) ?: return

        title.setTextColor(ColorUtils.blendARGB(TITLE_UNSELECTED, TITLE_SELECTED, level))
        icon.setColorFilter(ColorUtils.blendARGB(ICON_UNSELECTED, ICON_SELECTED, level))
        applyGlassBackground(view, level)
    }

    private fun applyGlassBackground(view: View, level: Float) {
        val context = view.context
        val density = view.resources.displayMetrics.density
        val cornerRadius = view.resources.getDimension(R.dimen.dashboard_header_corner_radius)

        val glassColor = ContextCompat.getColor(context, R.color.dashboard_header_glass_background)
        val selectedColor = ContextCompat.getColor(context, R.color.dashboard_header_selected_background)
        val glassBorder = ContextCompat.getColor(context, R.color.dashboard_header_glass_border)
        val selectedBorder = ContextCompat.getColor(context, R.color.accent_teal)

        val background = (view.background?.mutate() as? GradientDrawable) ?: GradientDrawable().also {
            it.cornerRadius = cornerRadius
            view.background = it
        }

        background.setColor(ColorUtils.blendARGB(glassColor, selectedColor, level))
        val strokeWidth = ((1f + level) * density).toInt().coerceAtLeast(1)
        background.setStroke(strokeWidth, ColorUtils.blendARGB(glassBorder, selectedBorder, level))
    }
}