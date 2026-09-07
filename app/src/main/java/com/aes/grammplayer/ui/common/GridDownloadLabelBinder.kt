package com.aes.grammplayer.ui.common

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.DownloadProgressTracker
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.ui.features.history.HistoryItem

object GridDownloadLabelBinder {

    fun update(view: View, item: Any) {
        when (item) {
            is MediaMessage -> bindMessageBanner(view, item)
            is HistoryItem -> bindHistoryBadges(view, item)
        }
    }

    fun bindMessageBanner(view: View, message: MediaMessage) {
        view.setTag(R.id.grid_card_file_id, message.fileId)

        val banner = view.findViewById<TextView>(R.id.banner) ?: return
        val isDownloading = DownloadProgressTracker.isMessageDownloading(message)

        when {
            isDownloading -> {
                val progress = DownloadProgressTracker.progressFromMessage(message) ?: 0
                banner.visibility = View.VISIBLE
                banner.text = FormatHelper.formatGridDownloadLabel(progress)
                banner.setTextColor(
                    ContextCompat.getColor(view.context, R.color.downloading_border)
                )
            }
            message.isDownloaded || MediaFileHelper.isPlayable(message.localPath) -> {
                banner.visibility = View.VISIBLE
                banner.text = "Ready"
                banner.setTextColor(
                    ContextCompat.getColor(view.context, R.color.accent_teal)
                )
            }
            else -> banner.visibility = View.GONE
        }
    }

    fun bindHistoryBadges(view: View, item: HistoryItem) {
        val message = item.message
        view.setTag(R.id.grid_card_file_id, message.fileId)

        val viewedBadge = view.findViewById<TextView>(R.id.badge_viewed) ?: return
        val downloadedBadge = view.findViewById<TextView>(R.id.badge_downloaded) ?: return
        val downloadingBadge = view.findViewById<TextView>(R.id.badge_downloading) ?: return
        val badgeRow = view.findViewById<View>(R.id.badge_row) ?: return

        val showDownloaded = item.isDownloaded &&
            (message.isDownloaded || MediaFileHelper.existsOnDisk(message.localPath))
        val showDownloading = !showDownloaded &&
            (item.isDownloading || DownloadProgressTracker.isMessageDownloading(message))

        viewedBadge.visibility = if (item.isViewed) View.VISIBLE else View.GONE
        downloadedBadge.visibility = if (showDownloaded) View.VISIBLE else View.GONE
        if (showDownloading) {
            val progress = DownloadProgressTracker.progressFromMessage(message) ?: 0
            downloadingBadge.text = FormatHelper.formatGridDownloadLabel(progress)
            downloadingBadge.visibility = View.VISIBLE
        } else {
            downloadingBadge.visibility = View.GONE
        }
        badgeRow.visibility =
            if (item.isViewed || showDownloaded || showDownloading) View.VISIBLE else View.GONE
    }
}