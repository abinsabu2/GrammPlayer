package com.aes.grammplayer.ui.features.messages

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.graphics.drawable.ColorDrawable
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.util.tdlib.ThumbnailGenerator
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Message Card Presenter with thumbnail support
 */
class MessageCardPresenter : Presenter() {

    // Background scope for one-time thumbnail generation (kept off the UI thread).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_message, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is MediaMessage) return
        if (item.fileId == 0) return

        val view = viewHolder.view

        // Title, File ID, Size
        view.findViewById<TextView>(R.id.title).text = item.title ?: "Untitled"
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${item.fileId}"

        val fileSizeMb = if (item.size > 0)
            String.format("%.2f MB", item.size / 1024.0 / 1024.0)
        else "N/A"
        view.findViewById<TextView>(R.id.size).text = "Size: $fileSizeMb"

        // Banner (Download status)
        val banner = view.findViewById<TextView>(R.id.banner)
        val localFile = item.localPath?.let { File(it) }
        val isDownloaded = localFile != null && localFile.exists()

        if (isDownloaded) {
            banner.visibility = android.view.View.VISIBLE
            banner.text = if (item.isDownloadActive) "Downloading..." else "Ready"
        } else {
            banner.visibility = android.view.View.GONE
        }

        // ====================== THUMBNAIL ======================
        val thumbnailView = view.findViewById<ImageView>(R.id.thumbnail)  // Make sure this is ImageView in XML

        // Stable per-file key: same file always maps to the same generated image.
        val key = item.uniqueId.ifEmpty { item.fileId.toString() }
        // Tag the view so an async generation that finishes after the view has been
        // recycled onto a different item doesn't overwrite the wrong card.
        thumbnailView.setTag(R.id.thumbnail, key)

        val thumbnailPath = item.thumbnailPath
        val readyPath = when {
            !thumbnailPath.isNullOrEmpty() && File(thumbnailPath).exists() -> thumbnailPath
            else -> ThumbnailGenerator.existingThumbnail(key)
        }

        if (readyPath != null) {
            // A real or already-generated image exists on disk: fill the box with it.
            fillBox(thumbnailView, readyPath)
        } else {
            // Nothing on disk yet. Fill the box instantly with a solid seeded colour
            // (so the slot is never empty/letterboxed), then generate the unique
            // abstract image off the UI thread and swap it in.
            fillBoxColor(thumbnailView, ThumbnailGenerator.colorFor(key))
            scope.launch {
                try {
                    val bitmap = ThumbnailGenerator.generatePlaceholder(seed = key)
                    val path = ThumbnailGenerator.saveBitmap(bitmap, key)
                    if (path != null) {
                        withContext(Dispatchers.Main) {
                            // Only apply if this view still represents the same item.
                            if (thumbnailView.getTag(R.id.thumbnail) == key) {
                                fillBox(thumbnailView, path)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate thumbnail for $key", e)
                }
            }
        }
    }

    /** Loads [path] into the box filling it edge-to-edge (centerCrop, no padding). */
    private fun fillBox(imageView: ImageView, path: String) {
        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        Glide.with(imageView.context)
            .load(File(path))
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(imageView)
    }

    /** Fills the box edge-to-edge with a solid [color] (no padding, no letterbox). */
    private fun fillBoxColor(imageView: ImageView, color: Int) {
        Glide.with(imageView.context).clear(imageView)
        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageDrawable(ColorDrawable(color))
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Clear Glide to avoid memory leaks
        val thumbnailView = viewHolder.view.findViewById<ImageView>(R.id.thumbnail)
        Glide.with(viewHolder.view.context).clear(thumbnailView)
        Log.d(TAG, "onUnbindViewHolder")
    }

    companion object {
        private const val TAG = "MessageCardPresenter"
    }
}