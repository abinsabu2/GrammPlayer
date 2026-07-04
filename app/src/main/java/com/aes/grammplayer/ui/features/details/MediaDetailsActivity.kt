package com.aes.grammplayer.ui.features.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
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
    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_details)
        settingsDataStore = SettingsDataStore(this)

        message = intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE) as? MediaMessage
            ?: run {
                // Shouldn't happen in practice — newIntent always attaches one —
                // but fall back rather than crash.
                Toast.makeText(this, "No media details available", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        bindHeader()
        setupMetadataRow()
        setupActionButtons()
        setupSettingsRow()
    }

    private fun bindHeader() {
        findViewById<TextView>(R.id.title).text = message.title
        findViewById<TextView>(R.id.description).apply {
            text = message.description
            visibility = if (message.description.isBlank()) View.GONE else View.VISIBLE
        }

        // Prefer the background image, fall back to the thumbnail, and if
        Glide.with(this)
            .load(R.drawable.detail_back_drop)
            .placeholder(R.drawable.card_background) // shown while loading
            .error(R.drawable.card_background)        // shown if the load fails
            .into(findViewById(R.id.backdrop))
    }

    private fun setupActionButtons() {
        val play = findViewById<View>(R.id.action_play)
        val download = findViewById<View>(R.id.action_download)
        val cancel = findViewById<View>(R.id.action_cancel)

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
        val recyclerView = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.settings_row).apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            setHasFixedSize(true)
        }

        // combine() re-emits whenever any one of these preferences changes,
        // so the row stays live rather than being a one-shot snapshot.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    settingsDataStore.autoPlay,
                    settingsDataStore.bufferSizeThreshold,
                    settingsDataStore.progressThreshold
                ) { autoPlay, bufferSizeMb, progressPercent ->
                    buildList {
                        add(SettingItem(value = if (autoPlay) "ON" else "OFF", caption = "AUTOPLAY"))
                        if (autoPlay) {
                            add(SettingItem(value = formatBufferSize(bufferSizeMb), caption = "BUFFER SIZE"))
                            add(SettingItem(value = "$progressPercent%", caption = "START DOWNLOAD AT"))
                        }
                        add(SettingItem(value = formatAvailableStorage(), caption = "AVAILABLE SYSTEM STORAGE"))
                    }
                }.collect { items ->
                    recyclerView.adapter = SettingCardAdapter(items)
                }
            }
        }
    }

    /**
     * bufferSizeThreshold is stored as a plain Int in SettingsDataStore with
     * no documented unit — this assumes megabytes to match the "1.5 GB"
     * style value it's replacing. Adjust the unit here if the DataStore
     * actually stores it differently (e.g. seconds of buffered playback).
     */
    private fun formatBufferSize(sizeMb: Int): String {
        return if (sizeMb >= 1024) String.format("%.1f GB", sizeMb / 1024.0) else "$sizeMb MB"
    }

    /**
     * Available space isn't tracked in SettingsDataStore — it's read live
     * from the filesystem each time this row rebinds.
     */
    private fun formatAvailableStorage(): String {
        val stat = StatFs(filesDir.path)
        val availableGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.1f GB", availableGb)
    }

    private fun setupMetadataRow() {
        val items = mutableListOf(
            SettingItem(value = formatSize(message.size), caption = "SIZE")
        )
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