package com.aes.grammplayer.ui.features.history

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.GlideHelper
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.util.tdlib.ReleaseInfo
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import com.aes.grammplayer.util.tdlib.ThumbnailGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

        bindThumbnail(view, message, info)
    }

    private fun bindThumbnail(view: View, item: MediaMessage, info: ReleaseInfo) {
        val thumbnailView = view.findViewById<ImageView>(R.id.thumbnail)
        val key = item.uniqueId.ifEmpty { item.fileId.toString() }
        thumbnailView.setTag(R.id.thumbnail, key)

        val cornerRadius = view.resources.getDimensionPixelSize(R.dimen.detail_poster_radius)
        GlideHelper.fillColor(thumbnailView, ThumbnailGenerator.colorFor(key))

        scope.launch {
            try {
                val posterUrl = PosterFetcher.fetchPosterUrl(info)
                if (posterUrl != null) {
                    withContext(Dispatchers.Main) {
                        if (thumbnailView.getTag(R.id.thumbnail) == key) {
                            GlideHelper.loadUrlCenterCrop(thumbnailView, posterUrl, cornerRadius)
                        }
                    }
                    return@launch
                }

                val thumbnailPath = item.thumbnailPath
                val readyPath = when {
                    !thumbnailPath.isNullOrEmpty() && File(thumbnailPath).exists() -> thumbnailPath
                    else -> ThumbnailGenerator.existingThumbnail(key)
                }

                if (readyPath != null) {
                    withContext(Dispatchers.Main) {
                        if (thumbnailView.getTag(R.id.thumbnail) == key) {
                            GlideHelper.loadCenterCrop(thumbnailView, readyPath)
                        }
                    }
                    return@launch
                }

                val bitmap = ThumbnailGenerator.generatePlaceholder(seed = key)
                val path = ThumbnailGenerator.saveBitmap(bitmap, key)
                if (path != null) {
                    withContext(Dispatchers.Main) {
                        if (thumbnailView.getTag(R.id.thumbnail) == key) {
                            GlideHelper.loadCenterCrop(thumbnailView, path)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load poster/thumbnail for $key", e)
            }
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val thumbnailView = viewHolder.view.findViewById<ImageView>(R.id.thumbnail)
        GlideHelper.clear(thumbnailView)
    }

    companion object {
        private const val TAG = "HistoryCardPresenter"
    }
}