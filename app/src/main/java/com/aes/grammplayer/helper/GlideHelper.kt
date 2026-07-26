package com.aes.grammplayer.helper

import android.graphics.drawable.ColorDrawable
import android.util.Log
import android.widget.ImageView
import com.bumptech.glide.Glide

object GlideHelper {

    private const val TAG = "GlideHelper"

    fun fillColor(imageView: ImageView, color: Int) {
        imageView.setPadding(0, 0, 0, 0)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.setImageDrawable(ColorDrawable(color))
    }

    fun clear(imageView: ImageView) {
        try {
            Glide.with(imageView).clear(imageView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping Glide clear — host context destroyed")
            imageView.setImageDrawable(null)
        }
    }
}
