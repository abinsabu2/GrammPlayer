package com.aes.grammplayer.ui.features.details

import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import java.util.concurrent.TimeUnit

/**
 * Fragment 3 — Media Details
 *
 * All fields are read directly from [MediaMessage] using their exact types:
 *   - title, description, studio, mimeType, localPath, videoUrl, uniqueId → String (non-null)
 *   - duration → Long (milliseconds)
 *   - size     → Long (bytes)
 *   - width, height, fileId, chat → Int (non-null)
 *   - id       → Long (non-null)
 *   - isMedia, isDownloaded, isDownloadActive → Boolean
 */
class MediaDetailsFragment : Fragment() {

    companion object {
        const val TAG = "MediaDetailsFragment"
        private const val ARG_MEDIA_MESSAGE = "arg_media_message"

        fun newInstance(msg: MediaMessage) = MediaDetailsFragment().apply {
            arguments = Bundle().apply {
                putSerializable(ARG_MEDIA_MESSAGE, msg)
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_media_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("DEPRECATION")
        val msg = arguments?.getSerializable(ARG_MEDIA_MESSAGE) as? MediaMessage
            ?: return

        bindViews(view, msg)
    }

    // ── Binding ───────────────────────────────────────────────────────────────

    private fun bindViews(root: View, msg: MediaMessage) {

        // ── Header ────────────────────────────────────────────────────────────
        root.tv(R.id.tv_title).text  = msg.title
        root.tv(R.id.tv_studio).text = msg.studio

        // ── Badges ────────────────────────────────────────────────────────────
        root.tv(R.id.chip_is_media).apply {
            text       = if (msg.isMedia) "Media" else "File"
            visibility = View.VISIBLE          // always show type badge
        }

        root.tv(R.id.chip_downloaded).apply {
            text       = "Downloaded"
            visibility = if (msg.isDownloaded) View.VISIBLE else View.GONE
        }

        root.tv(R.id.chip_download_active).apply {
            text       = if (msg.isDownloadActive) "Downloading" else "Idle"
            visibility = View.VISIBLE
        }

        // ── Description ───────────────────────────────────────────────────────
        root.tv(R.id.tv_description).text =
            msg.description.ifBlank { "No description." }

        // ── Media info ────────────────────────────────────────────────────────
        // duration: Long (ms) → "H:MM:SS" or "M:SS"
        root.tv(R.id.tv_duration).text = formatDuration(msg.duration)

        // resolution: width × height (both Int)
        root.tv(R.id.tv_resolution).text =
            if (msg.width > 0 && msg.height > 0) "${msg.width} × ${msg.height}"
            else "—"

        // size: Long (bytes) → human-readable
        root.tv(R.id.tv_size).text = formatSize(msg.size)

        root.tv(R.id.tv_mime_type).text = msg.mimeType.ifBlank { "—" }

        // ── Paths ─────────────────────────────────────────────────────────────
        root.tv(R.id.tv_local_path).text = msg.localPath.ifBlank { "—" }
        root.tv(R.id.tv_video_url).text  = msg.videoUrl.ifBlank  { "—" }

        // ── Identifiers ───────────────────────────────────────────────────────
        root.tv(R.id.tv_message_id).text = msg.id.toString()
        root.tv(R.id.tv_file_id).text    = "#${msg.fileId}"
        root.tv(R.id.tv_chat_id).text    = msg.chat.toString()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Long (ms) → "1:24:33" or "4:07" */
    private fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "—"
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else              "%d:%02d".format(m, s)
    }

    /** Long (bytes) → "1.4 GB", "748 MB", "512 KB" */
    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "—"
        return when {
            bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
            bytes >= 1_048_576L     -> "%.0f MB".format(bytes / 1_048_576.0)
            bytes >= 1_024L         -> "%.0f KB".format(bytes / 1_024.0)
            else                    -> "$bytes B"
        }
    }

    /** Convenience: avoid repetitive findViewById casts. */
    private fun View.tv(id: Int): TextView = findViewById(id)
}