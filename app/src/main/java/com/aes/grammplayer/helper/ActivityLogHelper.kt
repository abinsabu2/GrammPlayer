package com.aes.grammplayer.helper

import android.app.Activity
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActivityLogHelper {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /** Prepends a timestamped line to the TextView (newest first). */
    fun prepend(activity: Activity, textView: TextView, message: String) {
        activity.runOnUiThread {
            val timestamp = timeFormat.format(Date())
            val current = textView.text.toString()
            textView.text = if (current.isEmpty()) {
                "[$timestamp] $message"
            } else {
                "[$timestamp] $message\n$current"
            }
        }
    }

    /** Replaces the TextView content with a single message. */
    fun set(activity: Activity, textView: TextView, message: String) {
        activity.runOnUiThread {
            textView.text = message
        }
    }
}