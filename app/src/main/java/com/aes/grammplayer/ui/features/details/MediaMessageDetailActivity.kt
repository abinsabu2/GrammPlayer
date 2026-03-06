package com.aes.grammplayer.ui.features.details

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage

/**
 * Single-activity host for three sibling fragments:
 *
 *   ┌──────────────────────────┐
 *   │  PlayerFragment          │  ← VideoView (swap for LibVLC later)
 *   ├──────────────────────────┤
 *   │  DownloadProgressFragment│  ← live API polling, shows/hides itself
 *   ├──────────────────────────┤
 *   │  MediaDetailsFragment    │  ← static info from MediaMessage
 *   └──────────────────────────┘
 *
 * All three receive the same MediaMessage via newInstance(). They can
 * communicate back to the Activity via the shared interface [OnPlayerEvent].
 */
class MediaMessageDetailActivity : Activity(), OnPlayerEvent {

    companion object {
        private const val EXTRA_MEDIA_MESSAGE = "extra_media_message"

        fun newIntent(context: Context, mediaMessage: MediaMessage): Intent =
            Intent(context, MediaMessageDetailActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_MESSAGE, mediaMessage)
            }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_message_detail)

        val mediaMessage: MediaMessage? = resolveMediaMessage()
        if (mediaMessage == null) { finish(); return }

        if (savedInstanceState == null) {
            addAllFragments(mediaMessage)
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun resolveMediaMessage(): MediaMessage? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE, MediaMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE) as? MediaMessage
        }

    private fun addAllFragments(msg: MediaMessage) {
        val fm = fragmentManager
        fm.beginTransaction()
            // 1. Player
            .add(
                R.id.container_player,
                PlayerFragment.newInstance(msg),
                PlayerFragment.TAG
            )
            // 2. Download progress (hides itself when not active)
            .add(
                R.id.container_progress,
                DownloadProgressFragment.newInstance(msg),
                DownloadProgressFragment.TAG
            )
            // 3. Static details
            .add(
                R.id.container_details,
                MediaDetailsFragment.newInstance(msg),
                MediaDetailsFragment.TAG
            )
            .commit()
    }

    // ── OnPlayerEvent (called by PlayerFragment) ─────────────────────────────

    /** Called when the user taps Play — could trigger a download check, etc. */
    override fun onPlayRequested(localPath: String?) {
        // Example: if no local file tell DownloadProgressFragment to start
        if (localPath == null) {
            val progressFrag = fragmentManager
                .findFragmentByTag(DownloadProgressFragment.TAG) as? DownloadProgressFragment
            progressFrag?.startPolling()
        }
    }
}

/** Lightweight callback interface so fragments stay decoupled from the Activity. */
interface OnPlayerEvent {
    fun onPlayRequested(localPath: String?)
}