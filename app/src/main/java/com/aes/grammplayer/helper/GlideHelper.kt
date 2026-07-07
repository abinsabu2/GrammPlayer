package com.aes.grammplayer.helper

import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import java.io.File

object GlideHelper {

    private const val TAG = "GlideHelper"

    fun loadCenterCrop(imageView: ImageView, path: String) {
        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        runSafely(imageView) {
            Glide.with(imageView)
                .load(File(path))
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .into(imageView)
        }
    }

    fun fillColor(imageView: ImageView, color: Int) {
        clear(imageView)
        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageDrawable(ColorDrawable(color))
    }

    fun clear(imageView: ImageView) {
        runSafely(imageView) { Glide.with(imageView).clear(imageView) }
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