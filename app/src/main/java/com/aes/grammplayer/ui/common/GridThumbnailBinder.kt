package com.aes.grammplayer.ui.common

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.aes.grammplayer.GPlayerApplication
import com.aes.grammplayer.R
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.GlideHelper
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.util.tdlib.ReleaseInfo
import com.aes.grammplayer.util.tdlib.ThumbnailGenerator
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object GridThumbnailBinder {

    private const val TAG = "GridThumbnailBinder"

    fun bind(
        view: View,
        message: MediaMessage,
        info: ReleaseInfo,
        scope: CoroutineScope
    ) {
        val thumbnailView = view.findViewById<ImageView>(R.id.thumbnail) ?: return
        val key = message.uniqueId.ifEmpty { message.fileId.toString() }

        if (shouldSkipRebind(thumbnailView, key)) return

        (thumbnailView.getTag(R.id.grid_thumbnail_bind_job) as? Job)?.cancel()
        thumbnailView.setTag(R.id.grid_thumbnail_loaded, false)

        thumbnailView.setTag(R.id.grid_thumbnail_bind_key, key)
        val placeholderColor = ThumbnailGenerator.colorFor(key)
        val cornerRadius = view.resources.getDimensionPixelSize(R.dimen.grid_card_thumbnail_radius)
        GlideHelper.fillColor(thumbnailView, placeholderColor)

        val job = scope.launch {
            try {
                val remoteUrls = withContext(Dispatchers.IO) {
                    resolveRemoteUrls(message, info)
                }
                val localPath = withContext(Dispatchers.IO) {
                    resolveLocalPath(message, key)
                }

                withContext(Dispatchers.Main) {
                    if (!isCurrentBind(thumbnailView, key)) return@withContext
                    if (remoteUrls.isNotEmpty()) {
                        tryRemoteUrls(
                            thumbnailView = thumbnailView,
                            urls = remoteUrls,
                            index = 0,
                            cornerRadius = cornerRadius,
                            placeholderColor = placeholderColor,
                            localPath = localPath,
                            scope = scope,
                            message = message
                        )
                    } else if (localPath != null) {
                        loadLocal(thumbnailView, localPath, cornerRadius, placeholderColor)
                    } else {
                        GlideHelper.fillColor(thumbnailView, placeholderColor)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load thumbnail for $key", e)
                withContext(Dispatchers.Main) {
                    if (isCurrentBind(thumbnailView, key)) {
                        GlideHelper.fillColor(thumbnailView, placeholderColor)
                    }
                }
            }
        }
        thumbnailView.setTag(R.id.grid_thumbnail_bind_job, job)
    }

    fun unbind(view: View) {
        val thumbnailView = view.findViewById<ImageView>(R.id.thumbnail) ?: return
        (thumbnailView.getTag(R.id.grid_thumbnail_bind_job) as? Job)?.cancel()
        thumbnailView.setTag(R.id.grid_thumbnail_bind_job, null)
        thumbnailView.setTag(R.id.grid_thumbnail_bind_key, null)
        thumbnailView.setTag(R.id.grid_thumbnail_loaded, null)
        GlideHelper.clear(thumbnailView)
    }

    private fun shouldSkipRebind(thumbnailView: ImageView, key: String): Boolean {
        if (!isCurrentBind(thumbnailView, key)) return false
        if (thumbnailView.getTag(R.id.grid_thumbnail_loaded) == true) return true
        return (thumbnailView.getTag(R.id.grid_thumbnail_bind_job) as? Job)?.isActive == true
    }

    private fun markThumbnailLoaded(thumbnailView: ImageView) {
        thumbnailView.setTag(R.id.grid_thumbnail_loaded, true)
    }

    private suspend fun resolveRemoteUrls(message: MediaMessage, info: ReleaseInfo): List<String> =
        buildList {
            message.cardImageUrl
                .takeIf(PosterFetcher::isTrustedImageUrl)
                ?.let { add(it) }

            PosterFetcher.fetchGridThumbnailUrl(info, rawTitle = message.title)
                ?.let { add(it) }

            message.backgroundImageUrl
                .takeIf(PosterFetcher::isTrustedImageUrl)
                ?.let { add(it) }
        }.distinct()

    private suspend fun resolveLocalPath(message: MediaMessage, key: String): String? {
        message.thumbnailPath
            .takeIf { it.isNotBlank() && File(it).exists() && !ThumbnailGenerator.isGeneratedPlaceholder(it) }
            ?.let { return it }

        ThumbnailGenerator.existingThumbnail(key)?.let { return it }

        return ThumbnailGenerator.saveBitmap(
            ThumbnailGenerator.generatePlaceholder(seed = key),
            key
        )
    }

    private fun tryRemoteUrls(
        thumbnailView: ImageView,
        urls: List<String>,
        index: Int,
        cornerRadius: Int,
        placeholderColor: Int,
        localPath: String?,
        scope: CoroutineScope,
        message: MediaMessage
    ) {
        if (index >= urls.size) {
            if (localPath != null) {
                loadLocal(thumbnailView, localPath, cornerRadius, placeholderColor)
            } else {
                GlideHelper.fillColor(thumbnailView, placeholderColor)
            }
            return
        }

        val url = urls[index]
        loadRemote(
            thumbnailView = thumbnailView,
            url = url,
            cornerRadius = cornerRadius,
            placeholderColor = placeholderColor,
            onSuccess = { persistPosterUrl(scope, message, url) },
            onFailure = {
                tryRemoteUrls(
                    thumbnailView = thumbnailView,
                    urls = urls,
                    index = index + 1,
                    cornerRadius = cornerRadius,
                    placeholderColor = placeholderColor,
                    localPath = localPath,
                    scope = scope,
                    message = message
                )
            }
        )
    }

    private fun loadRemote(
        thumbnailView: ImageView,
        url: String,
        cornerRadius: Int,
        placeholderColor: Int,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val placeholder = ColorDrawable(placeholderColor)
        try {
            Glide.with(thumbnailView)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .placeholder(placeholder)
                .error(placeholder)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        Log.w(TAG, "Remote thumbnail failed: $url", e)
                        onFailure()
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        markThumbnailLoaded(thumbnailView)
                        onSuccess()
                        return false
                    }
                })
                .into(thumbnailView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping remote thumbnail load — context destroyed", e)
            onFailure()
        }
    }

    private fun loadLocal(
        thumbnailView: ImageView,
        path: String,
        cornerRadius: Int,
        placeholderColor: Int
    ) {
        val placeholder = ColorDrawable(placeholderColor)
        try {
            Glide.with(thumbnailView)
                .load(File(path))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))
                .placeholder(placeholder)
                .error(placeholder)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean = false

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        markThumbnailLoaded(thumbnailView)
                        return false
                    }
                })
                .into(thumbnailView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping local thumbnail load — context destroyed", e)
            GlideHelper.fillColor(thumbnailView, placeholderColor)
        }
    }

    private fun persistPosterUrl(scope: CoroutineScope, message: MediaMessage, url: String) {
        if (message.cardImageUrl == url) return
        scope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(GPlayerApplication.AppContext)
                val existing = db.mediaMessageDao().getById(message.id).first()
                val base = existing ?: message
                if (base.cardImageUrl == url) return@launch
                db.mediaMessageDao().insert(base.copy(cardImageUrl = url))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist poster url for message ${message.id}", e)
            }
        }
    }

    private fun isCurrentBind(thumbnailView: ImageView, key: String): Boolean =
        thumbnailView.getTag(R.id.grid_thumbnail_bind_key) == key

}