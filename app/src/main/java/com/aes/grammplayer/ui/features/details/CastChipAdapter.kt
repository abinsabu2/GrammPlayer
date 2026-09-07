package com.aes.grammplayer.ui.features.details

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R

class CastChipAdapter(private val items: List<CastChipItem>) :
    RecyclerView.Adapter<CastChipAdapter.CastViewHolder>() {

    class CastViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.cast_name)
        val role: TextView = view.findViewById(R.id.cast_role)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CastViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cast_chip, parent, false)
        return CastViewHolder(view)
    }

    override fun onBindViewHolder(holder: CastViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.role.text = item.role
    }

    override fun getItemCount() = items.size
}