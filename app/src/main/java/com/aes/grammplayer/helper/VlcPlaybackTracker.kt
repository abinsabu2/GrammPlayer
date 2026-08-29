package com.aes.grammplayer.helper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.SystemClock
import android.util.Log
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tracks VLC playhead via its exported [MediaBrowser] session so we can restore Resume
 * without [android.app.Activity.startActivityForResult] (which kills TV playback).
 */
object VlcPlaybackTracker {

    private const val TAG = "VlcPlaybackTracker"
    private const val VLC_PLAYBACK_SERVICE = "org.videolan.vlc.PlaybackService"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var browser: MediaBrowser? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null

    private var trackingMessageId: Long = 0L
    private var tracking: Boolean = false
    private var startPositionMs: Long = 0L
    private var launchElapsedMs: Long = 0L
    private var lastPositionMs: Long = 0L
    private var lastDurationMs: Long = 0L
    private var sessionConnected: Boolean = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            capture(state, controller?.metadata)
            val code = state?.state ?: return
            if (code == PlaybackState.STATE_PAUSED ||
                code == PlaybackState.STATE_STOPPED ||
                code == PlaybackState.STATE_NONE ||
                code == PlaybackState.STATE_ERROR
            ) {
                persistAsync()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            capture(controller?.playbackState, metadata)
        }

        override fun onSessionDestroyed() {
            persistAsync()
            sessionConnected = false
        }
    }

    fun begin(context: Context, messageId: Long, startPositionMs: Long) {
        disconnect()
        appContext = context.applicationContext
        trackingMessageId = messageId
        tracking = true
        this.startPositionMs = startPositionMs.coerceAtLeast(0L)
        launchElapsedMs = SystemClock.elapsedRealtime()
        lastPositionMs = this.startPositionMs
        lastDurationMs = 0L
        sessionConnected = false

        val component = ComponentName(PlayerHelper.VLC_PACKAGE, VLC_PLAYBACK_SERVICE)
        val connectionCallback = object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                val token = browser?.sessionToken
                if (token == null) {
                    Log.w(TAG, "VLC MediaBrowser connected without session token")
                    return
                }
                val ctx = appContext ?: return
                val ctrl = try {
                    MediaController(ctx, token)
                } catch (e: Exception) {
                    Log.w(TAG, "MediaController create failed", e)
                    return
                }
                controller = ctrl
                sessionConnected = true
                ctrl.registerCallback(controllerCallback)
                capture(ctrl.playbackState, ctrl.metadata)
                Log.i(TAG, "Connected to VLC session pos=${lastPositionMs}ms dur=${lastDurationMs}ms")
            }

            override fun onConnectionFailed() {
                Log.w(TAG, "VLC MediaBrowser connection failed — using elapsed-time fallback")
                sessionConnected = false
            }

            override fun onConnectionSuspended() {
                sessionConnected = false
            }
        }
        browser = MediaBrowser(context.applicationContext, component, connectionCallback, null).also {
            try {
                it.connect()
            } catch (e: Exception) {
                Log.w(TAG, "VLC MediaBrowser.connect failed", e)
            }
        }
    }

    fun isTracking(messageId: Long): Boolean = tracking && trackingMessageId == messageId

    /**
     * Best-known playhead for [messageId]. Does not clear tracking so dashboard
     * and details can both read it; call [end] after persisting UI.
     */
    fun snapshot(messageId: Long, fallbackDurationMs: Long = 0L): PlayerHelper.PlaybackExitPosition? {
        if (!tracking || trackingMessageId != messageId) return null
        controller?.let { capture(it.playbackState, it.metadata) }
        val elapsedPos = startPositionMs +
            (SystemClock.elapsedRealtime() - launchElapsedMs).coerceAtLeast(0L)
        val position = if (sessionConnected && lastPositionMs >= 1_000L) {
            lastPositionMs
        } else {
            elapsedPos
        }
        val duration = lastDurationMs.takeIf { it > 0L } ?: fallbackDurationMs
        val clamped = if (duration > 0L) position.coerceIn(0L, duration) else position.coerceAtLeast(0L)
        return PlayerHelper.PlaybackExitPosition(
            positionMs = clamped,
            durationMs = duration.coerceAtLeast(0L),
            uri = null
        )
    }

    fun end() {
        persistAsync()
        tracking = false
        disconnect()
    }

    private fun capture(state: PlaybackState?, metadata: MediaMetadata?) {
        metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }?.let {
            lastDurationMs = it
        }
        if (state == null) return
        var position = state.position
        if (position < 0L) return
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            val delta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            if (delta > 0L) {
                val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1f
                position += (delta * speed).toLong()
            }
        }
        lastPositionMs = position.coerceAtLeast(0L)
    }

    private fun persistAsync() {
        val ctx = appContext ?: return
        if (!tracking || trackingMessageId == 0L) return
        controller?.let { capture(it.playbackState, it.metadata) }
        val position = lastPositionMs
        val duration = lastDurationMs
        val messageId = trackingMessageId
        if (position <= 0L && duration <= 0L) return
        scope.launch {
            SettingsDataStore(ctx).savePlaybackPosition(
                messageId = messageId,
                positionMs = position,
                durationMs = duration
            )
        }
    }

    private fun disconnect() {
        try {
            controller?.unregisterCallback(controllerCallback)
        } catch (_: Exception) {
        }
        controller = null
        try {
            browser?.disconnect()
        } catch (_: Exception) {
        }
        browser = null
        sessionConnected = false
    }
}
