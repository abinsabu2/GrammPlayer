package com.aes.grammplayer.ui.features.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.ui.common.GridThumbnailBinder
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HistoryCardPresenter : Presenter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_history, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is HistoryItem) return
        val message = item.message
        if (message.fileId == 0) return

        val view = viewHolder.view
        val info = ReleaseTitleParser.parse(message.title)
        view.findViewById<TextView>(R.id.title).text =
            info.displayTitle.ifEmpty { message.title.ifEmpty { "Untitled" } }
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${message.fileId}"
        view.findViewById<TextView>(R.id.size).text =
            "Size: ${FormatHelper.formatBytesMb(message.size)}"

        val viewedBadge = view.findViewById<TextView>(R.id.badge_viewed)
        val downloadedBadge = view.findViewById<TextView>(R.id.badge_downloaded)
        val downloadingBadge = view.findViewById<TextView>(R.id.badge_downloading)
        val badgeRow = view.findViewById<View>(R.id.badge_row)

        val showDownloaded = item.isDownloaded &&
            (message.isDownloaded || MediaFileHelper.existsOnDisk(message.localPath))
        val showDownloading = item.isDownloading && !showDownloaded

        viewedBadge.visibility = if (item.isViewed) View.VISIBLE else View.GONE
        downloadedBadge.visibility = if (showDownloaded) View.VISIBLE else View.GONE
        downloadingBadge.visibility = if (showDownloading) View.VISIBLE else View.GONE
        badgeRow.visibility =
            if (item.isViewed || showDownloaded || showDownloading) View.VISIBLE else View.GONE

        GridThumbnailBinder.bind(view, message, info, scope)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        GridThumbnailBinder.unbind(viewHolder.view)
    }

}