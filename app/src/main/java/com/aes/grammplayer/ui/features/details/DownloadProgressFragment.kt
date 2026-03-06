package com.aes.grammplayer.ui.features.details

import android.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage

/**
 * Fragment 2 — Download Progress
 *
 * - Polls your API on a [Handler] tick (swap the mock with your real
 *   repository / WorkManager observer / BroadcastReceiver as needed).
 * - Hides itself entirely when the download is idle / complete.
 * - [startPolling] can be called externally (e.g. from the Activity) to
 *   kick off polling when the user taps Play on an un-downloaded file.
 */
class DownloadProgressFragment : Fragment() {

    companion object {
        const val TAG = "DownloadProgressFragment"
        private const val ARG_MEDIA_MESSAGE = "arg_media_message"

        /** Poll interval in milliseconds */
        private const val POLL_INTERVAL_MS = 3_000L

        fun newInstance(msg: MediaMessage) = DownloadProgressFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_MEDIA_MESSAGE, msg)
            }
        }
    }

    // ── Views ────────────────────────────────────────────────────────────────
    private lateinit var tvStatus: TextView
    private lateinit var tvPercent: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSpeed: TextView
    private lateinit var tvEta: TextView
    private lateinit var tvBytes: TextView
    private lateinit var btnAction: Button

    // ── State ────────────────────────────────────────────────────────────────
    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false

    private val pollRunnable = object : Runnable {
        override fun run() {
            fetchAndRender()
            if (isPolling) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_download_progress, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)

        @Suppress("DEPRECATION")
        val msg = arguments?.getSerializable(ARG_MEDIA_MESSAGE) as? MediaMessage

        btnAction.setOnClickListener { handleActionButton(msg) }

        // Auto-start polling only if a download is already running
        if (msg?.isDownloadActive == true) {
            view.visibility = View.VISIBLE
            startPolling()
        } else {
            view.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPolling()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Called by the Activity (or PlayerFragment via the Activity) */
    fun startPolling() {
        if (isPolling) return
        isPolling = true
        view?.visibility = View.VISIBLE
        handler.post(pollRunnable)
    }

    fun stopPolling() {
        isPolling = false
        handler.removeCallbacks(pollRunnable)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun bindViews(root: View) {
        tvStatus    = root.findViewById(R.id.tv_download_status)
        tvPercent   = root.findViewById(R.id.tv_download_percent)
        progressBar = root.findViewById(R.id.progress_bar)
        tvSpeed     = root.findViewById(R.id.tv_download_speed)
        tvEta       = root.findViewById(R.id.tv_download_eta)
        tvBytes     = root.findViewById(R.id.tv_download_bytes)
        btnAction   = root.findViewById(R.id.btn_download_action)
    }

    /**
     * Replace this with a real API/repository call.
     * e.g. call your DownloadRepository.getProgress(messageId) and
     * post the result back to the main thread.
     */
    private fun fetchAndRender() {
        // ── TODO: replace with real API call ────────────────────────────────
        // val progress = downloadRepository.getProgress(messageId)
        // For now we use a mock DownloadProgress data class:
        val mock = mockApiResponse()
        // ─────────────────────────────────────────────────────────────────────

        if (!mock.isActive) {
            // Download finished or was cancelled — hide this fragment
            stopPolling()
            view?.visibility = View.GONE
            return
        }

        renderProgress(mock)
    }

    private fun renderProgress(p: DownloadProgress) {
        tvStatus.text    = p.statusLabel
        tvPercent.text   = "${p.percent}%"
        progressBar.progress = p.percent
        tvSpeed.text     = p.speedLabel
        tvEta.text       = "ETA: ${p.etaLabel}"
        tvBytes.text     = "${p.downloadedLabel} / ${p.totalLabel}"
        btnAction.text   = if (p.isPaused) "Resume" else "Cancel"
    }

    private fun handleActionButton(msg: MediaMessage?) {
        // TODO: wire to your download manager / WorkManager cancellation
        stopPolling()
        view?.visibility = View.GONE
    }

    // ── Mock — DELETE when you wire to real API ───────────────────────────────
    private var mockPercent = 0

    private fun mockApiResponse(): DownloadProgress {
        mockPercent = (mockPercent + 7).coerceAtMost(100)
        val done = mockPercent >= 100
        return DownloadProgress(
            isActive      = !done,
            isPaused      = false,
            percent       = mockPercent,
            statusLabel   = if (done) "Done" else "Downloading…",
            speedLabel    = "2.4 MB/s",
            etaLabel      = "1m 12s",
            downloadedLabel = "${mockPercent * 7} MB",
            totalLabel    = "748 MB"
        )
    }
}

/** Simple data holder for one API poll result. Adapt fields to your API shape. */
data class DownloadProgress(
    val isActive: Boolean,
    val isPaused: Boolean,
    val percent: Int,            // 0-100
    val statusLabel: String,
    val speedLabel: String,      // e.g. "2.4 MB/s"
    val etaLabel: String,        // e.g. "1m 23s"
    val downloadedLabel: String, // e.g. "356 MB"
    val totalLabel: String       // e.g. "748 MB"
)