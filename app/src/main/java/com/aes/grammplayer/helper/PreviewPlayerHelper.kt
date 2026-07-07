package com.aes.grammplayer.helper

import android.content.Context
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Inline preview playback using the VLC engine (libvlc).
 * Full-screen playback is handled separately via the installed VLC app.
 */
object PreviewPlayerHelper {

    private const val TAG = "PreviewPlayerHelper"

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null
    private var videoHost: ViewGroup? = null

    fun play(
        context: Context,
        host: ViewGroup,
        filePath: String,
        onStarted: (() -> Unit)? = null
    ): Boolean {
        val file = MediaFileHelper.resolveFile(filePath) ?: return false
        if (!PlayerHelper.isVlcInstalled(context)) {
            Log.w(TAG, "VLC is not installed; preview unavailable")
            return false
        }
        return try {
            stop()
            videoHost = host
            val layout = VLCVideoLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            host.removeAllViews()
            host.addView(layout)
            videoLayout = layout

            val options = arrayListOf(
                "--intf", "dummy",
                "--no-video-title-show",
                "--no-stats"
            )
            libVlc = LibVLC(context.applicationContext, options)
            mediaPlayer = MediaPlayer(libVlc).apply {
                attachViews(layout, null, false, false)
                val media = Media(libVlc, file.absolutePath)
                media.setHWDecoderEnabled(true, false)
                media.addOption(":input-repeat=65535")
                setMedia(media)
                media.release()
                setEventListener { event ->
                    if (event.type == MediaPlayer.Event.Playing) {
                        onStarted?.invoke()
                    }
                }
                play()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed VLC preview for $filePath", e)
            stop()
            false
        }
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    fun resume() {
        if (mediaPlayer?.isPlaying != true) {
            mediaPlayer?.play()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun stop() {
        try {
            mediaPlayer?.stop()
            videoLayout?.let {
                mediaPlayer?.detachViews()
            }
            mediaPlayer?.release()
            libVlc?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping VLC preview", e)
        }
        videoHost?.removeAllViews()
        mediaPlayer = null
        libVlc = null
        videoLayout = null
        videoHost = null
    }
}