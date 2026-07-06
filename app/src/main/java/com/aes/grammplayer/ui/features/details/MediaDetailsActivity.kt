package com.aes.grammplayer.ui.features.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
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

    // ==================== All UI Elements Collected Here ====================
    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var posterImageView: View

    private lateinit var playButton: View
    private lateinit var downloadButton: View
    private lateinit var cancelButton: View
    private lateinit var logTextView: TextView

    // Download progress views
    private lateinit var downloadProgressContainer: View
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar

    // RecyclerViews
    private lateinit var metadataChipRecycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var settingsRowRecycler: androidx.recyclerview.widget.RecyclerView

    private var fileUpdateJob: Job? = null
    private var hasAutoPlayed = false
    private var isDownloading = false

    // Reused from BottomSheet
    private var currentDownload: DownloadingFileInfo? = null
    val activeDownloads = mutableSetOf<Int>()

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

        initializeViews()
        setupListeners()           // ← All listeners now in one place

        loadSettings()
        bindHeader()
        setupMetadataChipRow()
        setupSettingsRow()

        checkLocalFileAndUpdateUI()
        startListeningToUpdates()

        appendLog("Activity opened for: ${message.title}")
    }

    /**
     * Collects and initializes ALL UI elements in a single function.
     */
    private fun initializeViews() {
        titleTextView = findViewById(R.id.title)
        descriptionTextView = findViewById(R.id.description)
        posterImageView = findViewById(R.id.poster_image)

        playButton = findViewById(R.id.action_play)
        downloadButton = findViewById(R.id.action_download)
        cancelButton = findViewById(R.id.action_cancel)
        logTextView = findViewById(R.id.log_text_view)

        // Download progress
        downloadProgressContainer = findViewById(R.id.download_progress_container)
        downloadStatusText = findViewById(R.id.download_status_text)
        downloadProgressBar = findViewById(R.id.download_progress_bar)

        // Recyclers
        metadataChipRecycler = findViewById(R.id.metadata_chip_row)
        settingsRowRecycler = findViewById(R.id.settings_row)
    }

    /**
     * Collects ALL listeners in a single function.
     */
    private fun setupListeners() {
        // Play button listener (enabled/disabled dynamically)
        playButton.setOnClickListener {
            if (isDownloading) {
                appendLog("Cannot play while downloading")
                return@setOnClickListener
            }
        }

        // Download button listener
        downloadButton.setOnClickListener { startDownload() }

        // Cancel button listener
        cancelButton.setOnClickListener { cancelCurrentDownload() }
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            isAutoPlayEnabled = settingsDataStore.autoPlay.first()
            progressThreshold = 2
            bufferSizeThresholdMB = 10
            appendLog("Settings loaded: AutoPlay=$isAutoPlayEnabled, Threshold=$progressThreshold%, Buffer=$bufferSizeThresholdMB MB")
        }
    }

    private fun checkLocalFileAndUpdateUI() {

        // Initial check for play button based on *actual* local file
        val localFile = message.localPath?.let { File(it) }
        val downloadedSizeMb = localFile?.length()?.toFloat()?.div(1024 * 1024) ?: 0f
        val totalSizeMb = message.size.toFloat() / (1024 * 1024)
        val progress = if (totalSizeMb > 0) ((downloadedSizeMb / totalSizeMb) * 100).toInt() else 0
        val localFileExistsAndIsPlayable = localFile != null && localFile.exists()
        val hasLocalFile = localFile?.exists() == true && localFile.length() > 0

        if (hasLocalFile && localFileExistsAndIsPlayable) {
            playButton.isEnabled = true
            downloadButton.visibility = View.GONE
            appendLog("Local file found → Play button enabled")
        } else {
            playButton.isEnabled = false
            downloadButton.visibility = View.VISIBLE
            appendLog("No local file → Download available")
        }
    }

    private fun startDownload() {
        downloadButton.isEnabled = false
        isDownloading = true
        activeDownloads.add(message.fileId)
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
                        if(isTestMode){
                            message = updatedMessage
                            appendLog("Download completed successfully")
                            checkLocalFileAndUpdateUI()
                                hasAutoPlayed = false
                                currentDownload = DownloadingFileInfo(
                                    fileId = message.fileId,
                                    downloadedSize = 100.toFloat(),
                                    totalSize = 100.toFloat() / (1024 * 1024),
                                    progress = 100,
                                    localPath = message.localPath.takeIf { it.isNotEmpty() }
                                )
                                val shouldAutoPlay = isAutoPlayEnabled && !hasAutoPlayed
                                val localFile = message.localPath?.let { File(it) }
                                val localFileExistsAndIsPlayable = localFile != null && localFile.exists()
                                val hasLocalFile = localFile?.exists() == true && localFile.length() > 0

                                if (shouldAutoPlay && hasLocalFile && localFileExistsAndIsPlayable) {
                                    hasAutoPlayed = true
                                    playWithVLC(currentDownload)
                                }
                            }
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
        activeDownloads.clear()
        downloadButton.isEnabled = true
        downloadButton.visibility = View.VISIBLE
        currentDownload = null
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

            currentDownload = DownloadingFileInfo(
                fileId = file.id,
                downloadedSize = downloadedMB.toFloat(),
                totalSize = totalBytes.toFloat() / (1024 * 1024),
                progress = progress,
                localPath = file.local.path.takeIf { it.isNotEmpty() }
            )

            val shouldAutoPlay = isAutoPlayEnabled && !hasAutoPlayed &&
                    (progress >= progressThreshold || downloadedMB >= bufferSizeThresholdMB)

            val localFile = file.local.path?.let { File(it) }
            val localFileExistsAndIsPlayable = localFile != null && localFile.exists()
            val hasLocalFile = localFile?.exists() == true && localFile.length() > 0


            if (shouldAutoPlay && hasLocalFile && localFileExistsAndIsPlayable) {
                hasAutoPlayed = true
                appendLog("Auto-play triggered at $progress%")
                playWithVLC(currentDownload)
            }

            if (file.local.isDownloadingCompleted) {
                message.localPath = file.local.path
                message.isDownloaded = true
                appendLog("Download completed - File saved")
                checkLocalFileAndUpdateUI()
                isDownloading = false
                activeDownloads.clear()
            }
        }
    }

    /**
     * Helper function to launch the internal player.
     */
    fun playWithVLC(currentDownload: DownloadingFileInfo?) {
        val context = this@MediaDetailsActivity

        val fileId = currentDownload?.fileId ?: 0
        val file = currentDownload?.localPath?.let { File(it) }
        val localPath = currentDownload?.localPath ?: "N/A"
        if (file == null || !file.exists() || fileId == 0) {
            appendLog("Cannot play: File path invalid, does not exist, or too small: $localPath")
            playButton.isEnabled = false
            return
        }
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",  // Must match manifest
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "video/*")
                setPackage("org.videolan.vlc") // Force open with VLC only
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            appendLog("Started VLC playback for file ID: $fileId, path: $localPath")
        } catch (e: Exception) {
            appendLog("Error while launching VLC Media Player for $localPath: ${e.message}")
        }
    }
    private fun updateDownloadProgress(progress: Int, downloadedBytes: Long = 0, totalBytes: Long = 0) {
        downloadProgressContainer.visibility = View.VISIBLE
        downloadProgressBar.progress = progress

        val df = DecimalFormat("0.0")
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val totalMB = totalBytes / (1024.0 * 1024.0)

        val status = if (totalBytes > 0) {
            "Downloading : $progress% (${df.format(downloadedMB)} MB / ${df.format(totalMB)} MB)"
        } else {
            "$progress%"
        }

        downloadStatusText.text = status
    }



    private fun cancelCurrentDownload() {
        appendLog("User cancelled download")

        TelegramClientManager.cancelDownloadAndDelete(activeDownloads)

        message.localPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.delete()) {
                appendLog("Temporary file deleted")
            }
        }

        isDownloading = false
        hasAutoPlayed = false
        activeDownloads.clear()
        currentDownload = null

        downloadButton.isEnabled = true
        downloadButton.visibility = View.VISIBLE
        downloadProgressContainer.visibility = View.GONE

        appendLog("Download cancelled and cleaned up")
    }

    private fun bindHeader() {
        titleTextView.text = message.title
        descriptionTextView.text = message.description

        /* Glide.with(this)
            .load(R.drawable.card_background)
            .transform(RoundedCorners(12))
            .placeholder(R.drawable.card_background)
            .error(R.drawable.card_background)
            .into(posterImageView) */
    }

    private fun setupMetadataChipRow() {
        val chips = buildList {
            add(MetadataChipItem(R.drawable.ic_gear, message.fileId.toString()))
            if (message.mimeType.isNotBlank()) {
                add(MetadataChipItem(R.drawable.ic_check, formatMimeType(message.mimeType)))
            }
            add(MetadataChipItem(R.drawable.ic_check, formatSize(message.size)))
        }

        metadataChipRecycler.apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = MetadataChipAdapter(chips)
            setHasFixedSize(true)
        }
    }

    private fun setupSettingsRow() {
        settingsRowRecycler.apply {
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
                    settingsRowRecycler.adapter = SettingCardAdapter(items)
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