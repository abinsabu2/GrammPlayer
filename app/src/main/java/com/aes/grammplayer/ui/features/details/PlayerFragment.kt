package com.aes.grammplayer.ui.features.details

import android.app.Fragment
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Fragment 1 — Player
 *
 * Uses the built-in [VideoView] / [MediaPlayer] stack.
 *
 * ┌─── VLC MIGRATION NOTE ───────────────────────────────────────────────┐
 * │ When you add the VLC dependency, replace VideoView with              │
 * │ org.videolan.libvlc.util.VLCVideoLayout and drive it via            │
 * │ LibVLC + MediaPlayer from the libvlc-android library.               │
 * │ The rest of the fragment (controls, seekbar, polling) stays the same.│
 * └──────────────────────────────────────────────────────────────────────┘
 */
class PlayerFragment : Fragment() {

    companion object {
        const val TAG = "PlayerFragment"
        private const val ARG_MEDIA_MESSAGE = "arg_media_message"

        fun newInstance(msg: MediaMessage) = PlayerFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_MEDIA_MESSAGE, msg)
            }
        }
    }

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var videoView: VideoView
    private lateinit var layoutNoVideo: View
    private lateinit var tvNoVideoHint: TextView
    private lateinit var layoutControls: View
    private lateinit var btnPlayPause: Button
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView

    // ── State ────────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false
    private val seekRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_player, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)

        @Suppress("DEPRECATION")
        val msg = arguments?.getSerializable(ARG_MEDIA_MESSAGE) as? MediaMessage
        val localPath = msg?.localPath   // adjust to your actual field name

        val file = localPath?.let { File(it) }
        if (file != null && file.exists()) {
            setupVideoView(file)
            layoutNoVideo.visibility = View.GONE
        } else {
            layoutNoVideo.visibility = View.VISIBLE
            layoutControls.visibility = View.GONE
            tvNoVideoHint.text = if (msg?.isDownloadActive == true)
                "Downloading… play when ready"
            else
                "Download to watch offline"
        }

        btnPlayPause.setOnClickListener { togglePlayback() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) videoView.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
    }

    override fun onPause() {
        super.onPause()
        if (videoView.isPlaying) videoView.pause()
        handler.removeCallbacks(seekRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(seekRunnable)
        videoView.stopPlayback()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun bindViews(root: View) {
        videoView      = root.findViewById(R.id.video_view)
        layoutNoVideo  = root.findViewById(R.id.layout_no_video)
        tvNoVideoHint  = root.findViewById(R.id.tv_no_video_hint)
        layoutControls = root.findViewById(R.id.layout_controls)
        btnPlayPause   = root.findViewById(R.id.btn_play_pause)
        seekBar        = root.findViewById(R.id.seek_bar)
        tvCurrentTime  = root.findViewById(R.id.tv_current_time)
        tvTotalTime    = root.findViewById(R.id.tv_total_time)
    }

    private fun setupVideoView(file: File) {
        videoView.setVideoURI(Uri.fromFile(file))
        videoView.setOnPreparedListener { mp ->
            seekBar.max = mp.duration
            tvTotalTime.text = formatMs(mp.duration.toLong())
            layoutControls.visibility = View.VISIBLE
        }
        videoView.setOnCompletionListener {
            btnPlayPause.text = "Play"
            isPlaying = false
            handler.removeCallbacks(seekRunnable)
        }
    }

    private fun togglePlayback() {
        if (videoView.isPlaying) {
            videoView.pause()
            btnPlayPause.text = "Play"
            handler.removeCallbacks(seekRunnable)
        } else {
            videoView.start()
            btnPlayPause.text = "Pause"
            handler.post(seekRunnable)

            // Notify the Activity (which may check download state, etc.)
            (activity as? OnPlayerEvent)?.onPlayRequested(
                (arguments?.getSerializable(ARG_MEDIA_MESSAGE) as? MediaMessage)?.localPath
            )
        }
        isPlaying = videoView.isPlaying
    }

    private fun updateProgress() {
        if (!videoView.isPlaying) return
        val pos = videoView.currentPosition
        seekBar.progress = pos
        tvCurrentTime.text = formatMs(pos.toLong())
    }

    private fun formatMs(ms: Long): String {
        val h  = TimeUnit.MILLISECONDS.toHours(ms)
        val m  = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s  = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else             "%d:%02d".format(m, s)
    }
}