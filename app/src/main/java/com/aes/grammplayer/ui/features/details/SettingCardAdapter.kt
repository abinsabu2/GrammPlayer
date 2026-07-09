package com.aes.grammplayer.ui.features.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R

class SettingCardAdapter(private val items: List<SettingItem>) :
    RecyclerView.Adapter<SettingCardAdapter.SettingViewHolder>() {

    class SettingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardContainer: LinearLayout = view.findViewById(R.id.card_container)
        val icon: ImageView = view.findViewById(R.id.icon)
        val valueText: TextView = view.findViewById(R.id.value_text)
        val captionText: TextView = view.findViewById(R.id.caption_text)
        val subCaptionText: TextView = view.findViewById(R.id.subcaption_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting_card, parent, false)
        return SettingViewHolder(view)
    }

    override fun onBindViewHolder(holder: SettingViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.itemView.isFocusable = false
        holder.itemView.isFocusableInTouchMode = false

        if (item.iconRes != null) {
            holder.icon.visibility = View.VISIBLE
            holder.icon.setImageResource(item.iconRes)
        } else {
            holder.icon.visibility = View.GONE
        }

        holder.valueText.text = item.value
        holder.captionText.text = item.caption

        if (item.subCaption != null) {
            holder.subCaptionText.visibility = View.VISIBLE
            holder.subCaptionText.text = item.subCaption
        } else {
            holder.subCaptionText.visibility = View.GONE
        }

        val accent = ContextCompat.getColor(context, R.color.accent_teal)
        val white = ContextCompat.getColor(context, android.R.color.white)
        val muted = android.graphics.Color.parseColor("#9A9A9E")

        if (item.selected) {
            holder.cardContainer.setBackgroundResource(R.drawable.setting_card_background_selected)
            holder.valueText.setTextColor(accent)
            holder.icon.setColorFilter(accent)
            holder.subCaptionText.setTextColor(accent)
        } else {
            holder.cardContainer.setBackgroundResource(R.drawable.setting_card_background)
            holder.valueText.setTextColor(white)
            holder.icon.setColorFilter(white)
            holder.subCaptionText.setTextColor(muted)
        }
    }

    override fun getItemCount() = items.size
}