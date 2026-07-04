package com.aes.grammplayer.ui.features.details


import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R

data class SettingItem(val value: String, val caption: String)

class SettingCardAdapter(private val items: List<SettingItem>) :
    RecyclerView.Adapter<SettingCardAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val value: TextView = view.findViewById(R.id.value)
        val caption: TextView = view.findViewById(R.id.caption)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.value.text = item.value
        holder.caption.text = item.caption
    }

    override fun getItemCount(): Int = items.size
}