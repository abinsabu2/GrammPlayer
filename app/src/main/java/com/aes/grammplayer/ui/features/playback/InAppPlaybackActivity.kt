package com.aes.grammplayer.ui.features.playback

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.MediaFileHelper
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Full-screen playback using the bundled libVLC engine.
 * Used for store-review / test accounts where the external VLC app may not be installed.
 */
class InAppPlaybackActivity : AppCompatActivity() {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoLayout: VLCVideoLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_in_app_playback)

        val path = intent.getStringExtra(EXTRA_FILE_PATH)
        val file = MediaFileHelper.resolveFile(path)
        if (file == null) {
            Toast.makeText(this, R.string.playback_file_not_ready, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        startPlayback(file.absolutePath)
    }

    private fun startPlayback(filePath: String) {
        val host = findViewById<FrameLayout>(R.id.playback_root)
        val layout = VLCVideoLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
            requestFocus()
        }
        host.addView(layout)
        videoLayout = layout

        try {
            val options = arrayListOf(
                "--intf", "dummy",
                "--no-video-title-show",
                "--no-stats"
            )
            libVlc = LibVLC(applicationContext, options)
            mediaPlayer = MediaPlayer(libVlc).apply {
                attachViews(layout, null, true, false)
                val media = Media(libVlc, filePath)
                media.setHWDecoderEnabled(true, false)
                setMedia(media)
                media.release()
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "In-app playback failed for $filePath", e)
            Toast.makeText(this, R.string.playback_in_app_failed, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onStop() {
        super.onStop()
        mediaPlayer?.pause()
    }

    override fun onDestroy() {
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
            videoLayout?.let { mediaPlayer?.detachViews() }
            mediaPlayer?.release()
            libVlc?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing in-app player", e)
        }
        mediaPlayer = null
        libVlc = null
        videoLayout = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        private const val TAG = "InAppPlaybackActivity"
        const val EXTRA_FILE_PATH = "extra_file_path"
    }
}