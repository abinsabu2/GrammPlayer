package com.aes.grammplayer.ui.features.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.ui.common.GridThumbnailBinder
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.ui.features.history.HistoryItem
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ContinueWatchingCardPresenter : Presenter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_downloading, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val (message, positionMs, durationMs) = when (item) {
            is ContinueWatchingItem -> Triple(item.message, item.positionMs, item.durationMs)
            is HistoryItem -> {
                // Fallback — no position info, hide progress
                Triple(item.message, 0L, 0L)
            }
            is MediaMessage -> Triple(item, 0L, 0L)
            else -> return
        }
        if (message.fileId == 0 && message.id == 0L) return

        val view = viewHolder.view
        view.setTag(R.id.grid_card_file_id, message.fileId)

        val info = ReleaseTitleParser.parse(message.title)
        view.findViewById<TextView>(R.id.title).text =
            info.displayTitle.ifEmpty { message.title.ifEmpty { "Untitled" } }
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${message.fileId}"

        bindProgress(view, positionMs, durationMs)
        GridThumbnailBinder.bind(view, message, info, scope)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        GridThumbnailBinder.unbind(viewHolder.view)
    }

    companion object {
        fun bindProgress(view: View, positionMs: Long, durationMs: Long) {
            val banner = view.findViewById<TextView>(R.id.banner) ?: return
            val progressBar = view.findViewById<ProgressBar>(R.id.download_progress_bar)
            if (positionMs <= 0L || durationMs <= 0L) {
                banner.visibility = View.VISIBLE
                banner.text = view.context.getString(R.string.grid_label_ready)
                banner.setTextColor(ContextCompat.getColor(view.context, R.color.accent_teal))
                progressBar?.visibility = View.GONE
                return
            }
            val progress = ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100)
            banner.visibility = View.VISIBLE
            banner.text = view.context.getString(R.string.continue_watching_banner, FormatHelper.formatPlaybackPosition(positionMs))
            banner.setTextColor(ContextCompat.getColor(view.context, R.color.accent_teal))
            progressBar?.apply {
                visibility = View.VISIBLE
                this.progress = progress
            }
        }
    }
}
