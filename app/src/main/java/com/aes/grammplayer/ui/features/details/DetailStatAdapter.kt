package com.aes.grammplayer.ui.features.details

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R

class DetailStatAdapter(private val items: List<DetailStatItem>) :
    RecyclerView.Adapter<DetailStatAdapter.StatViewHolder>() {

    class StatViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val value: TextView = view.findViewById(R.id.stat_value)
        val label: TextView = view.findViewById(R.id.stat_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detail_stat, parent, false)
        return StatViewHolder(view)
    }

    override fun onBindViewHolder(holder: StatViewHolder, position: Int) {
        val item = items[position]
        holder.value.text = item.value
        holder.label.text = item.label
    }

    override fun getItemCount() = items.size
}