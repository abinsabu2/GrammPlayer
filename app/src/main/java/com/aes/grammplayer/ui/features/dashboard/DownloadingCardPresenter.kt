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
import com.aes.grammplayer.helper.DownloadProgressTracker
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.ui.common.GridThumbnailBinder
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DownloadingCardPresenter : Presenter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.card_downloading, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is MediaMessage) return
        if (item.fileId == 0) return

        val view = viewHolder.view
        view.setTag(R.id.grid_card_file_id, item.fileId)

        val info = ReleaseTitleParser.parse(item.title)
        view.findViewById<TextView>(R.id.title).text =
            info.displayTitle.ifEmpty { item.title.ifEmpty { "Untitled" } }
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${item.fileId}"

        bindProgress(view, item)
        GridThumbnailBinder.bind(view, item, info, scope)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        GridThumbnailBinder.unbind(viewHolder.view)
    }

    companion object {
        private const val READY_STATE = -1

        fun bindProgress(view: View, message: MediaMessage) {
            if (DownloadProgressTracker.isMessageDownloading(message)) {
                bindDownloading(view, message)
            } else {
                bindReady(view)
            }
        }

        fun bindReady(view: View) {
            val lastProgress = view.getTag(R.id.grid_download_progress) as? Int
            if (lastProgress == READY_STATE) return
            view.setTag(R.id.grid_download_progress, READY_STATE)

            view.findViewById<TextView>(R.id.banner)?.apply {
                visibility = View.VISIBLE
                text = view.context.getString(R.string.grid_label_ready)
                setTextColor(ContextCompat.getColor(context, R.color.accent_teal))
            }
            view.findViewById<ProgressBar>(R.id.download_progress_bar)?.visibility = View.GONE
        }

        private fun bindDownloading(view: View, message: MediaMessage) {
            val progress = DownloadProgressTracker.progressFromMessage(message) ?: 0
            val lastProgress = view.getTag(R.id.grid_download_progress) as? Int
            if (lastProgress == progress) return
            view.setTag(R.id.grid_download_progress, progress)

            view.findViewById<TextView>(R.id.banner)?.apply {
                visibility = View.VISIBLE
                text = FormatHelper.formatGridDownloadLabel(progress)
                setTextColor(ContextCompat.getColor(context, R.color.downloading_border))
            }
            view.findViewById<ProgressBar>(R.id.download_progress_bar)?.apply {
                visibility = View.VISIBLE
                this.progress = progress
            }
        }
    }
}