package com.aes.grammplayer

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

class PlayerActivity : FragmentActivity(), MediaPlayer.EventListener {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var playPauseButton: ImageButton
    private lateinit var playerContainer: FrameLayout
    private var fileId: Int = 0

    private val controlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { playPauseButton.visibility = View.GONE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        videoLayout = findViewById(R.id.video_layout)
        playPauseButton = findViewById(R.id.play_pause_button)
        playerContainer = findViewById(R.id.player_container)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        fileId = intent.getIntExtra(EXTRA_FILE_ID, 0)

        if (filePath == null || !File(filePath).exists() || fileId == 0) {
            Log.e(TAG, "File path/ID is invalid or file does not exist: $filePath")
            finish()
            return
        }

        libVLC = LibVLC(this)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.setEventListener(this)
        mediaPlayer.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
        mediaPlayer.attachViews(videoLayout, null, false, false)

        val media = Media(libVLC, Uri.fromFile(File(filePath)))
        mediaPlayer.media = media
        media.release()

        setupControls()

        mediaPlayer.play()
        PlaybackStateManager.onPlaybackStarted(fileId) // Report playback start
    }

    private fun setupControls() {
        playerContainer.setOnClickListener { toggleControls() }
        playPauseButton.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            } else {
                mediaPlayer.play()
            }
            hideControlsWithDelay()
        }
    }

    private fun toggleControls() {
        if (playPauseButton.visibility == View.VISIBLE) {
            playPauseButton.visibility = View.GONE
            controlsHandler.removeCallbacks(hideControlsRunnable)
        } else {
            playPauseButton.visibility = View.VISIBLE
            hideControlsWithDelay()
        }
    }

    private fun hideControlsWithDelay() {
        controlsHandler.removeCallbacks(hideControlsRunnable)
        controlsHandler.postDelayed(hideControlsRunnable, 3000)
    }

    override fun onEvent(event: MediaPlayer.Event) {
        runOnUiThread {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    // --- FIX IS HERE ---
                    // When media is PLAYING, the button should show a PAUSE icon.
                    playPauseButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play_arrow))
                    hideControlsWithDelay()
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.EndReached -> {
                    // --- FIX IS HERE ---
                    // When media is PAUSED or FINISHED, the button should show a PLAY icon.
                    playPauseButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_play_arrow))
                    playPauseButton.visibility = View.VISIBLE
                    controlsHandler.removeCallbacks(hideControlsRunnable)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        PlaybackStateManager.onPlaybackStopped() // Report playback stop

        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
    }

    companion object {
        const val TAG = "PlayerActivity"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_ID = "file_id"
    }
}
