package com.aes.grammplayer.helper

import android.content.res.Resources
import androidx.annotation.DimenRes

object GridLayoutHelper {

    fun resolveColumnCount(
        resources: Resources,
        preferredColumns: Int,
        @DimenRes cardWidthDimen: Int,
        @DimenRes cardMarginDimen: Int
    ): Int {
        val maxFit = maxColumnsForCard(resources, cardWidthDimen, cardMarginDimen)
        return preferredColumns.coerceAtMost(maxFit).coerceAtLeast(1)
    }

    fun maxColumnsForCard(
        resources: Resources,
        @DimenRes cardWidthDimen: Int,
        @DimenRes cardMarginDimen: Int
    ): Int {
        val screenWidthPx = resources.displayMetrics.widthPixels
        val cardWidthPx = resources.getDimensionPixelSize(cardWidthDimen)
        val marginPx = resources.getDimensionPixelSize(cardMarginDimen) * 2
        val itemWidthPx = cardWidthPx + marginPx
        if (itemWidthPx <= 0) return 1
        return (screenWidthPx / itemWidthPx).coerceAtLeast(1)
    }
}