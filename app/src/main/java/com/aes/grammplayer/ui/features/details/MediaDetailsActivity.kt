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
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Full-screen media details view: app bar (logo + promo banner), poster,
 * title/description, metadata tag chips, download progress, settings chips
 * (autoplay/buffer/quality/storage/connection), Play/Download/Cancel
 * actions, and an activity log panel.
 *
 * fileId and uniqueId are internal identifiers used to drive playback/
 * download, not shown directly in the UI.
 *
 * TODO items below mark fields the wireframe shows that MediaMessage /
 * SettingsDataStore don't have yet (genre tags, audio format, duration,
 * connection type, video quality, promo banner content). They're stubbed
 * with placeholder data so the screen is fully wired — swap in real
 * sources as those fields land.
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
                Toast.makeText(this, "No media details available", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        bindHeader()
        setupMetadataChipRow()
        setupActionButtons()
        setupSettingsRow()
    }

    private fun bindHeader() {
        findViewById<TextView>(R.id.title).text = message.title
        findViewById<TextView>(R.id.description).text = message.description

        // TODO: bind promo_banner/promo_text to a real announcement source.
        // Static placeholder for now — safe to leave the view GONE instead
        // if there's nothing to promote.

        Glide.with(this)
            .load(R.drawable.card_background) // TODO: swap for message.thumbnailPath
            .transform(RoundedCorners(12))
            .placeholder(R.drawable.card_background)
            .error(R.drawable.card_background)
            .into(findViewById(R.id.poster_image))
    }

    private fun setupMetadataChipRow() {
        // TODO: "genre"/"audio format"/"duration" tags aren't on MediaMessage
        // yet. Format/size chips use real data; the rest are placeholders
        // until those fields exist.
        val chips = buildList {
            if (message.mimeType.isNotBlank()) {
                add(MetadataChipItem(R.drawable.ic_check, formatMimeType(message.mimeType)))
            }
            add(MetadataChipItem(R.drawable.ic_check, formatSize(message.size)))
            // Placeholders — replace once genre/audio/duration exist:
            add(MetadataChipItem(R.drawable.ic_mic, "Audio"))
            add(MetadataChipItem(R.drawable.ic_flask, "Category"))
            add(MetadataChipItem(R.drawable.ic_clock, "--:--"))
        }

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.metadata_chip_row).apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = MetadataChipAdapter(chips)
            setHasFixedSize(true)
        }
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    settingsDataStore.autoPlay,
                    settingsDataStore.bufferSizeThreshold,
                    settingsDataStore.progressThreshold
                ) { autoPlay, bufferSizeMb, progressPercent ->
                    buildList {
                        add(
                            SettingItem(
                                iconRes = R.drawable.ic_power,
                                value = if (autoPlay) "ON" else "OFF",
                                caption = "AUTO PLAY"
                            )
                        )
                        if (autoPlay) {
                            add(
                                SettingItem(
                                    iconRes = R.drawable.ic_layers,
                                    value = formatBufferSize(bufferSizeMb),
                                    caption = "BUFFER SIZE"
                                )
                            )
                        }
                        // TODO: video quality isn't in SettingsDataStore yet —
                        // add a field there once quality selection exists.
                        add(
                            SettingItem(
                                iconRes = R.drawable.ic_gear,
                                value = "AUTO",
                                caption = "QUALITY",
                                subCaption = "Current: HD",
                                selected = true
                            )
                        )
                        add(
                            SettingItem(
                                iconRes = R.drawable.ic_storage,
                                value = formatAvailableStorage(),
                                caption = "AVAILABLE STORAGE"
                            )
                        )
                        // TODO: connection type isn't tracked anywhere yet —
                        // wire to actual network-type detection.
                        add(
                            SettingItem(
                                iconRes = R.drawable.ic_wifi,
                                value = "WIFI",
                                caption = "CONNECTION"
                            )
                        )
                    }
                }.collect { items ->
                    recyclerView.adapter = SettingCardAdapter(items)
                }
            }
        }
    }

    private fun formatBufferSize(sizeMb: Int): String {
        return if (sizeMb >= 1024) String.format("%.1f GB", sizeMb / 1024.0) else "$sizeMb MB"
    }

    private fun formatAvailableStorage(): String {
        val stat = StatFs(filesDir.path)
        val availableGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.1f GB", availableGb)
    }

    /**
     * Call from wherever download progress updates land (WorkManager
     * observer, TelegramClientManager callback, etc.).
     */
    fun updateDownloadProgress(downloadedBytes: Long, totalBytes: Long) {
        val container = findViewById<View>(R.id.download_progress_container)
        val statusText = findViewById<TextView>(R.id.download_status_text)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.download_progress_bar)

        if (totalBytes <= 0) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        statusText.text = "Downloading — ${formatSize(downloadedBytes)} / ${formatSize(totalBytes)}"
        progressBar.progress = ((downloadedBytes * 100) / totalBytes).toInt()
    }

    /** Appends a line to the on-screen activity log (useful with no adb attached). */
    fun appendLog(line: String) {
        val logView = findViewById<TextView>(R.id.log_text_view)
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date())
        logView.append("\n[$timestamp] $line")
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds)
        else String.format("%d:%02d", minutes, seconds)
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "N/A"
        val mb = sizeBytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    private fun formatMimeType(mimeType: String): String = mimeType.substringAfterLast('/').uppercase()

    companion object {
        const val EXTRA_MEDIA_MESSAGE = "extra_media_message"

        fun newIntent(context: Context, message: MediaMessage): Intent {
            return Intent(context, MediaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_MESSAGE, message)
            }
        }
    }
}