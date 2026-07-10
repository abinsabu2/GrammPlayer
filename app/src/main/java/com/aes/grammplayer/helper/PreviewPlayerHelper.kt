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
 * Full-screen playback is handled separately via [InAppPlaybackActivity] or external VLC.
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

            layout.post {
                if (videoLayout !== layout) return@post
                if (!startPlaybackOnLayout(context, layout, file.absolutePath, onStarted, hwDecode = true)) {
                    stop()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed VLC preview setup for $filePath", e)
            stop()
            false
        }
    }

    private fun startPlaybackOnLayout(
        context: Context,
        layout: VLCVideoLayout,
        filePath: String,
        onStarted: (() -> Unit)?,
        hwDecode: Boolean
    ): Boolean {
        return try {
            releasePlayerInstances()
            val options = VlcPlaybackOptions.build(hwDecode)
            libVlc = LibVLC(context.applicationContext, options)
            mediaPlayer = MediaPlayer(libVlc).apply {
                attachViews(layout, null, false, false)
                val media = Media(libVlc, filePath)
                media.setHWDecoderEnabled(hwDecode, false)
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
            if (hwDecode) {
                Log.w(TAG, "HW decode preview failed, retrying software decoder", e)
                startPlaybackOnLayout(context, layout, filePath, onStarted, hwDecode = false)
            } else {
                Log.e(TAG, "Failed VLC preview for $filePath", e)
                false
            }
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

    fun stop(onReleased: (() -> Unit)? = null) {
        val host = videoHost
        releasePlayerInstances()
        host?.removeAllViews()
        videoHost = null
        videoLayout = null
        host?.post { onReleased?.invoke() } ?: onReleased?.invoke()
    }

    private fun releasePlayerInstances() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                videoLayout?.let { player.detachViews() }
                player.release()
            }
            libVlc?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping VLC preview", e)
        }
        mediaPlayer = null
        libVlc = null
    }
}