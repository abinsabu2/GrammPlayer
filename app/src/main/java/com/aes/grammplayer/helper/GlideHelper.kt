package com.aes.grammplayer.helper

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object GlideHelper {

    private const val TAG = "GlideHelper"
    private const val LOAD_TIMEOUT_MS = 4_000L

    suspend fun loadCenterCrop(
        imageView: ImageView,
        path: String,
        cornerRadiusPx: Int = 0,
        placeholderColor: Int? = null
    ): Boolean {
        if (!File(path).exists()) return false
        return loadInto(
            imageView = imageView,
            model = File(path),
            diskCacheStrategy = DiskCacheStrategy.NONE,
            cornerRadiusPx = cornerRadiusPx,
            placeholderColor = placeholderColor
        )
    }

    suspend fun loadUrlCenterCrop(
        imageView: ImageView,
        url: String,
        cornerRadiusPx: Int = 0,
        placeholderColor: Int? = null
    ): Boolean {
        if (url.isBlank()) return false
        return loadInto(
            imageView = imageView,
            model = url,
            diskCacheStrategy = DiskCacheStrategy.ALL,
            cornerRadiusPx = cornerRadiusPx,
            placeholderColor = placeholderColor
        )
    }

    fun fillColor(imageView: ImageView, color: Int) {
        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageDrawable(ColorDrawable(color))
    }

    fun clear(imageView: ImageView) {
        runSafely(imageView) { Glide.with(imageView).clear(imageView) }
    }

    private suspend fun loadInto(
        imageView: ImageView,
        model: Any,
        diskCacheStrategy: DiskCacheStrategy,
        cornerRadiusPx: Int,
        placeholderColor: Int?
    ): Boolean = suspendCancellableCoroutine { cont ->
        if (!imageView.isAttachedToWindow) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }

        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.clearColorFilter()

        val placeholder = placeholderColor?.let { ColorDrawable(it) }
        val transforms = if (cornerRadiusPx > 0) {
            arrayOf(CenterCrop(), RoundedCorners(cornerRadiusPx))
        } else {
            arrayOf(CenterCrop())
        }

        val timeoutRunnable = object : Runnable {
            override fun run() {
                imageView.removeCallbacks(this)
                if (cont.isActive) cont.resume(false)
            }
        }
        fun finish(result: Boolean) {
            imageView.removeCallbacks(timeoutRunnable)
            if (cont.isActive) cont.resume(result)
        }
        imageView.postDelayed(timeoutRunnable, LOAD_TIMEOUT_MS)
        cont.invokeOnCancellation {
            imageView.removeCallbacks(timeoutRunnable)
            runSafely(imageView) { Glide.with(imageView).clear(imageView) }
        }

        try {
            var request = Glide.with(imageView)
                .load(model)
                .diskCacheStrategy(diskCacheStrategy)
                .transform(*transforms)
                .listener(object : RequestListener<Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<Drawable>,
                        isFirstResource: Boolean
                    ): Boolean {
                        finish(false)
                        return false
                    }

                    override fun onResourceReady(
                        resource: Drawable,
                        model: Any,
                        target: Target<Drawable>?,
                        dataSource: DataSource,
                        isFirstResource: Boolean
                    ): Boolean {
                        finish(true)
                        return false
                    }
                })
            placeholder?.let { request = request.placeholder(it).error(it) }
            request.into(imageView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping Glide — host context destroyed")
            imageView.setImageDrawable(placeholder)
            finish(false)
        }
    }

    private inline fun runSafely(imageView: ImageView, block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping Glide — host context destroyed")
            imageView.setImageDrawable(null)
        }
    }
}