package com.aes.grammplayer.ui.common

import android.view.View

/** Enables D-pad / TV remote focus on a leanback card or control. */
fun View.makeFocusableForTv(clickable: Boolean = false) {
    isFocusable = true
    isFocusableInTouchMode = true
    if (clickable) isClickable = true
}