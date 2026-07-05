package com.aes.grammplayer.ui.features.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.util.Log
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
import com.aes.grammplayer.provider.MediaDownloadDataProvider
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaDetailsActivity : AppCompatActivity() {

    private lateinit var message: MediaMessage
    private lateinit var settingsDataStore: SettingsDataStore

    private lateinit var playButton: View
    private lateinit var downloadButton: View
    private lateinit var logTextView: TextView

    private var fileUpdateJob: Job? = null
    private var hasAutoPlayed = false
    private var isDownloading = false

    // Settings
    private var isAutoPlayEnabled = false
    private var progressThreshold = 30
    private var bufferSizeThresholdMB = 300

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_details)
        settingsDataStore = SettingsDataStore(this)

        message = intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE) as? MediaMessage
            ?: run {
                finish()
                return
            }

        playButton = findViewById(R.id.action_play)
        downloadButton = findViewById(R.id.action_download)
        logTextView = findViewById(R.id.log_text_view)

        loadSettings()
        bindHeader()
        setupMetadataChipRow()
        setupActionButtons()
        setupSettingsRow()

        checkLocalFileAndUpdateUI()
        startListeningToUpdates()

        appendLog("Activity opened for: ${message.title}")
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            isAutoPlayEnabled = settingsDataStore.autoPlay.first()
            progressThreshold = settingsDataStore.progressThreshold.first()
            bufferSizeThresholdMB = settingsDataStore.bufferSizeThreshold.first()
            appendLog("Settings loaded: AutoPlay=$isAutoPlayEnabled, Threshold=$progressThreshold%, Buffer=$bufferSizeThresholdMB MB")
        }
    }

    private fun checkLocalFileAndUpdateUI() {
        val localFile = message.localPath?.let { File(it) }
        val hasLocalFile = localFile?.exists() == true && localFile.length() > 0

        if (hasLocalFile) {
            playButton.isEnabled = true
            playButton.setOnClickListener { playWithVLC() }
            downloadButton.visibility = View.GONE
            appendLog("Local file found → Play button enabled")
        } else {
            playButton.isEnabled = false
            downloadButton.visibility = View.VISIBLE
            downloadButton.setOnClickListener { startDownload() }
            appendLog("No local file → Download available")
        }
    }

    private fun startDownload() {
        downloadButton.isEnabled = false
        isDownloading = true
        appendLog("Download started for fileId: ${message.fileId}")

        lifecycleScope.launch {
            try {
                val isTestMode = settingsDataStore.isTestMode.first()
                appendLog("Mode: ${if (isTestMode) "Test Server" else "Telegram"}")

                MediaDownloadDataProvider.downloadMedia(
                    mode = isTestMode,
                    mediaMessage = message,
                    onProgress = { progress ->
                        runOnUiThread { updateDownloadProgress(progress) }
                    }
                )?.let { updatedMessage ->
                    runOnUiThread {
                        message = updatedMessage
                        appendLog("Download completed successfully")
                        checkLocalFileAndUpdateUI()
                        if (!hasAutoPlayed && isAutoPlayEnabled) {
                            hasAutoPlayed = true
                            playWithVLC()
                        }
                    }
                } ?: run {
                    runOnUiThread {
                        appendLog("Download failed")
                        resetDownloadUI()
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaDetailsActivity", "Download error", e)
                runOnUiThread {
                    appendLog("Error: ${e.message}")
                    resetDownloadUI()
                }
            }
        }
    }

    private fun resetDownloadUI() {
        isDownloading = false
        downloadButton.isEnabled = true
        downloadButton.visibility = View.VISIBLE
    }

    private fun startListeningToUpdates() {
        fileUpdateJob?.cancel()
        fileUpdateJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.fileUpdate.collect { update ->
                    if (update.file.id == message.fileId) {
                        handleFileUpdate(update.file)
                    }
                }
            }
        }
    }

    private fun handleFileUpdate(file: TdApi.File) {
        val downloadedBytes = file.local.downloadedSize
        val totalBytes = file.expectedSize
        val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)

        runOnUiThread {
            updateDownloadProgress(progress, downloadedBytes, totalBytes)

            val shouldAutoPlay = isAutoPlayEnabled && !hasAutoPlayed &&
                    (progress >= progressThreshold || downloadedMB >= bufferSizeThresholdMB)

            if (shouldAutoPlay) {
                hasAutoPlayed = true
                appendLog("Auto-play triggered at $progress%")
                playWithVLC()
            }

            if (file.local.isDownloadingCompleted) {
                message.localPath = file.local.path
                message.isDownloaded = true
                appendLog("Download completed - File saved")
                checkLocalFileAndUpdateUI()
                isDownloading = false
            }
        }
    }

    private fun updateDownloadProgress(progress: Int, downloadedBytes: Long = 0, totalBytes: Long = 0) {
        val container = findViewById<View>(R.id.download_progress_container)
        val statusText = findViewById<TextView>(R.id.download_status_text)
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.download_progress_bar)

        container.visibility = View.VISIBLE
        progressBar.progress = progress

        val df = DecimalFormat("0.0")
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)

        val status = if (totalBytes > 0) {
            "Downloading : $progress% (${df.format(downloadedMB)} MB / ${df.format(totalMB)} MB)"
        } else {
            "$progress%"
        }

        statusText.text = status
    }

    private fun playWithVLC() {
        val localFile = File(message.localPath.orEmpty())
        if (!localFile.exists()) {
            appendLog("Play failed: File not found")
            return
        }

        appendLog("Opening file in VLC Player")

        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                localFile
            )

            val mimeType = message.mimeType.ifBlank { "video/*" }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage("org.videolan.vlc")
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MediaDetailsActivity", "Error opening video", e)
            appendLog("VLC launch failed: ${e.message}")
        }
    }

    private fun showVLCInstallPrompt() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("VLC Player Recommended")
            .setMessage("VLC Player is recommended for best playback.\n\nInstall it now?")
            .setPositiveButton("Install VLC") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.videolan.vlc")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.videolan.vlc")))
                }
            }
            .setNegativeButton("Use Default") { _, _ -> playWithDefaultPlayer() }
            .show()
    }

    private fun playWithDefaultPlayer() {
        val localFile = File(message.localPath.orEmpty())
        if (!localFile.exists()) return

        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                localFile
            )
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, message.mimeType.ifBlank { "video/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
            appendLog("Opened with default player")
        } catch (e: Exception) {
            Log.e("MediaDetailsActivity", "No player found", e)
            appendLog("No player found")
        }
    }

    private fun setupActionButtons() {
        findViewById<View>(R.id.action_cancel).setOnClickListener {
            cancelCurrentDownload()
        }
    }

    private fun cancelCurrentDownload() {
        appendLog("User cancelled download")

        // Cancel Telegram download
        TelegramClientManager.cancelDownloadAndDelete(mutableSetOf(message.fileId))

        // Clean up local test file if exists
        message.localPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.delete()) {
                appendLog("Temporary file deleted")
            }
        }

        // Reset UI
        isDownloading = false
        hasAutoPlayed = false
        downloadButton.isEnabled = true
        downloadButton.visibility = View.VISIBLE
        findViewById<View>(R.id.download_progress_container).visibility = View.GONE

        appendLog("Download cancelled and cleaned up")
    }

    private fun bindHeader() {
        findViewById<TextView>(R.id.title).text = message.title
        findViewById<TextView>(R.id.description).text = message.description

        Glide.with(this)
            .load(R.drawable.card_background)
            .transform(RoundedCorners(12))
            .placeholder(R.drawable.card_background)
            .error(R.drawable.card_background)
            .into(findViewById(R.id.poster_image))
    }

    private fun setupMetadataChipRow() {
        val chips = buildList {
            add(MetadataChipItem(R.drawable.ic_gear, message.fileId.toString()))
            if (message.mimeType.isNotBlank()) {
                add(MetadataChipItem(R.drawable.ic_check, formatMimeType(message.mimeType)))
            }
            add(MetadataChipItem(R.drawable.ic_check, formatSize(message.size)))
        }

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.metadata_chip_row).apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = MetadataChipAdapter(chips)
            setHasFixedSize(true)
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
                        add(SettingItem(R.drawable.ic_power, if (autoPlay) "ON" else "OFF", "AUTO PLAY"))
                        if (autoPlay) {
                            add(SettingItem(R.drawable.ic_layers, formatBufferSize(bufferSizeMb), "BUFFER SIZE"))
                            add(SettingItem(R.drawable.ic_play, "$progressPercent%", "AUTO PLAY AT"))
                        }
                        add(SettingItem(R.drawable.ic_storage, formatAvailableStorage(), "AVAILABLE STORAGE"))
                    }
                }.collect { items ->
                    recyclerView.adapter = SettingCardAdapter(items)
                }
            }
        }
    }

    private fun appendLog(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val current = logTextView.text.toString()
            val newLog = if (current.isEmpty()) {
                "[$timestamp] $message"
            } else {
                "[$timestamp] $message\n$current"
            }
            logTextView.text = newLog
        }
    }

    private fun formatBufferSize(sizeMb: Int): String =
        if (sizeMb >= 1024) String.format("%.1f GB", sizeMb / 1024.0) else "$sizeMb MB"

    private fun formatAvailableStorage(): String {
        val stat = StatFs(filesDir.path)
        val availableGb = stat.availableBytes / 1024.0 / 1024.0 / 1024.0
        return String.format("%.1f GB", availableGb)
    }

    private fun formatSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "N/A"
        val mb = sizeBytes / 1024.0 / 1024.0
        return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
    }

    private fun formatMimeType(mimeType: String): String = mimeType.substringAfterLast('/').uppercase()

    companion object {
        const val EXTRA_MEDIA_MESSAGE = "extra_media_message"

        fun newIntent(context: Context, message: MediaMessage): Intent =
            Intent(context, MediaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_MESSAGE, message)
            }
    }

    override fun onDestroy() {
        fileUpdateJob?.cancel()
        super.onDestroy()
    }
}