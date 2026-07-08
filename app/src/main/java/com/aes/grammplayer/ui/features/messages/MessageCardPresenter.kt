package com.aes.grammplayer.ui.features.messages

import android.util.Log
import android.view.LayoutInflater
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

class MessageCardPresenter : Presenter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_message, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is MediaMessage) return
        if (item.fileId == 0) return

        val view = viewHolder.view

        val info = ReleaseTitleParser.parse(item.title)
        view.findViewById<TextView>(R.id.title).text = info.displayTitle
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${item.fileId}"
        view.findViewById<TextView>(R.id.size).text =
            "Size: ${FormatHelper.formatBytesMb(item.size)}"

        view.findViewById<TextView>(R.id.quality_source)?.apply {
            val qualitySourceText = listOfNotNull(
                info.resolution,
                info.videoCodec,
                info.service,
                info.source
            ).joinToString(" · ")
            if (qualitySourceText.isNotBlank()) {
                visibility = android.view.View.VISIBLE
                text = qualitySourceText
            } else {
                visibility = android.view.View.GONE
            }
        }

        view.findViewById<TextView>(R.id.release_group)?.apply {
            text = info.releaseGroup ?: ""
            visibility = if (info.releaseGroup != null) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
        }

        val banner = view.findViewById<TextView>(R.id.banner)
        val isDownloaded = MediaFileHelper.existsOnDisk(item.localPath)

        if (isDownloaded) {
            banner.visibility = android.view.View.VISIBLE
            banner.text = if (item.isDownloadActive) "Downloading..." else "Ready"
        } else {
            banner.visibility = android.view.View.GONE
        }

        bindThumbnail(view, item, info)
    }

    private fun bindThumbnail(
        view: android.view.View,
        item: MediaMessage,
        info: ReleaseInfo
    ) {
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
        private const val TAG = "MessageCardPresenter"
    }
}