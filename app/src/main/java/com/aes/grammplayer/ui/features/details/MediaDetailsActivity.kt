package com.aes.grammplayer.ui.features.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import java.util.concurrent.TimeUnit

/**
 * Full-screen media details view: backdrop + title + description,
 * Play/Download/Cancel actions (driven by isDownloaded/isDownloadActive),
 * and a metadata row (Duration/Size/Studio/Resolution/Format).
 *
 * fileId and uniqueId are internal identifiers used to drive playback/
 * download, not shown directly in the UI — there's no user-facing meaning
 * to printing a raw numeric ID on a details screen like this.
 *
 * TODO: load real backdrop/thumbnail images (backgroundImageUrl /
 * thumbnailPath) via whatever image-loading library this project already
 * uses (Glide/Coil/etc.) instead of the placeholder drawable.
 */
class MediaDetailsActivity : AppCompatActivity() {

    private lateinit var message: MediaMessage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_details)

        message = intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE) as? MediaMessage
            ?: run {
                // Shouldn't happen in practice — newIntent always attaches one —
                // but fall back rather than crash.
                Toast.makeText(this, "No media details available", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        bindHeader()
        setupActionButtons()
        setupSettingsRow()
        setupMetadataRow()
    }

    private fun bindHeader() {
        findViewById<TextView>(R.id.title).text = message.title
        findViewById<TextView>(R.id.description).apply {
            text = message.description
            visibility = if (message.description.isBlank()) View.GONE else View.VISIBLE
        }
        // TODO: Glide.with(this).load(message.backgroundImageUrl.ifBlank { message.thumbnailPath })
        //   .into(findViewById(R.id.backdrop))
    }

    private fun setupActionButtons() {
        val play = findViewById<View>(R.id.action_play)
        val download = findViewById<View>(R.id.action_download)
        val cancel = findViewById<View>(R.id.action_cancel)

        when {
            message.isDownloadActive -> {
                play.visibility = View.GONE
                download.visibility = View.GONE
                cancel.visibility = View.VISIBLE
            }
            message.isDownloaded -> {
                play.visibility = View.VISIBLE
                download.visibility = View.GONE
                cancel.visibility = View.GONE
            }
            else -> {
                play.visibility = View.GONE
                download.visibility = View.VISIBLE
                cancel.visibility = View.GONE
            }
        }

        play.setOnClickListener {
            // TODO: start playback using message.localPath / message.fileId.
            Toast.makeText(this, "Play: ${message.title}", Toast.LENGTH_SHORT).show()
        }
        download.setOnClickListener {
            // TODO: kick off download using message.fileId.
            Toast.makeText(this, "Download: ${message.title}", Toast.LENGTH_SHORT).show()
        }
        cancel.setOnClickListener {
            // TODO: cancel in-progress download using message.fileId.
            Toast.makeText(this, "Cancel: ${message.title}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSettingsRow() {
        // Static app-config style settings (not derived from MediaMessage —
        // these are placeholders for real player/app settings once wired up).
        val settings = listOf(
            SettingItem(value = "ON", caption = "AUTOPLAY"),
            SettingItem(value = "1.5 GB", caption = "BUFFER SIZE"),
            SettingItem(value = "85%", caption = "START DOWNLOAD AT"),
            SettingItem(value = "English", caption = "DEFAULT AUDIO")
        )
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.settings_row).apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = SettingCardAdapter(settings)
            setHasFixedSize(true)
        }
    }

    private fun setupMetadataRow() {
        val items = mutableListOf(
            SettingItem(value = formatDuration(message.duration), caption = "DURATION"),
            SettingItem(value = formatSize(message.size), caption = "SIZE")
        )
        if (message.studio.isNotBlank()) {
            items += SettingItem(value = message.studio, caption = "STUDIO")
        }
        if (message.width > 0 && message.height > 0) {
            items += SettingItem(value = "${message.width}×${message.height}", caption = "RESOLUTION")
        }
        if (message.mimeType.isNotBlank()) {
            items += SettingItem(value = formatMimeType(message.mimeType), caption = "FORMAT")
        }

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.metadata_row).apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = SettingCardAdapter(items)
            setHasFixedSize(true)
        }
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "N/A"
        val mb = sizeBytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    private fun formatMimeType(mimeType: String): String {
        // e.g. "video/mp4" -> "MP4"
        return mimeType.substringAfterLast('/').uppercase()
    }

    companion object {
        const val EXTRA_MEDIA_MESSAGE = "extra_media_message"

        fun newIntent(context: Context, message: MediaMessage): Intent {
            return Intent(context, MediaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_MESSAGE, message)
            }
        }
    }
}