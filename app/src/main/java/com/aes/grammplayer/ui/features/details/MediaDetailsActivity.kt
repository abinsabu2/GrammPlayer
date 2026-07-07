package com.aes.grammplayer.ui.features.details

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.ActivityLogHelper
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.HistoryHelper
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.helper.PlayerHelper
import com.aes.grammplayer.helper.PreviewPlayerHelper
import com.aes.grammplayer.provider.MediaDownloadDataProvider
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class MediaDetailsActivity : AppCompatActivity() {

    private enum class ActionButtonState {
        /** A — no playable file: Download only */
        FRESH,
        /** B — download in progress: Cancel only */
        DOWNLOADING,
        /** File complete and playable: Play only */
        READY
    }

    private lateinit var message: MediaMessage
    private lateinit var settingsDataStore: SettingsDataStore

    // ==================== All UI Elements Collected Here ====================
    private lateinit var titleTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var posterImageView: View
    private lateinit var previewContainer: View
    private lateinit var previewVideoHost: ViewGroup
    private lateinit var previewFullscreenButton: View

    private lateinit var playButton: View
    private lateinit var downloadButton: View
    private lateinit var cancelButton: View
    private lateinit var closeButton: View
    private lateinit var logTextView: TextView

    // Download progress views
    private lateinit var downloadProgressContainer: View
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar

    // RecyclerViews
    private lateinit var metadataChipRecycler: RecyclerView
    private lateinit var settingsRowRecycler: RecyclerView

    private var fileUpdateJob: Job? = null
    private var isDownloading = false
    private var autoPlayStarted = false
    private var lastPreviewPath: String? = null
    private var hasRecordedHistoryView = false
    private var hasRecordedHistoryDownload = false
    private var hasRecordedHistoryDownloading = false

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
        setupBackNavigation()
        setupListeners()
        loadSettings()
        bindHeader()
        setupMetadataChipRow()
        setupSettingsRow()
        focusFirstUsableButton()
        refreshLocalFileAndUpdateUI()
        startListeningToUpdates()
        restrictFocusToActionButtons()   // ← D-pad movement limited to action buttons + close
    }

    /**
     * Collects and initializes ALL UI elements in a single function.
     */
    private fun initializeViews() {
        titleTextView = findViewById(R.id.title)
        descriptionTextView = findViewById(R.id.description)
        posterImageView = findViewById(R.id.poster_image)
        previewContainer = findViewById(R.id.preview_container)
        previewVideoHost = findViewById(R.id.preview_video_host)
        previewFullscreenButton = findViewById(R.id.preview_fullscreen)

        playButton = findViewById(R.id.action_play)
        downloadButton = findViewById(R.id.action_download)
        cancelButton = findViewById(R.id.action_cancel)
        closeButton = findViewById(R.id.action_close)
        logTextView = findViewById(R.id.log_text_view)

        // Download progress
        downloadProgressContainer = findViewById(R.id.download_progress_container)
        downloadStatusText = findViewById(R.id.download_status_text)
        downloadProgressBar = findViewById(R.id.download_progress_bar)

        // Recyclers
        metadataChipRecycler = findViewById(R.id.metadata_chip_row)
        settingsRowRecycler = findViewById(R.id.settings_row)

        applyActionButtonState(ActionButtonState.FRESH)
    }

    // ==================== Action button states ====================

    /**
     * Applies one of the three mutually exclusive action-button states.
     * Only the buttons for the active state are visible and enabled.
     */
    private fun applyActionButtonState(state: ActionButtonState) {
        when (state) {
            ActionButtonState.FRESH -> {
                playButton.visibility = View.GONE
                playButton.isEnabled = false
                downloadButton.visibility = View.VISIBLE
                downloadButton.isEnabled = true
                cancelButton.visibility = View.GONE
                cancelButton.isEnabled = false
            }
            ActionButtonState.DOWNLOADING -> {
                playButton.visibility = View.GONE
                playButton.isEnabled = false
                downloadButton.visibility = View.GONE
                downloadButton.isEnabled = false
                cancelButton.visibility = View.VISIBLE
                cancelButton.isEnabled = true
            }
            ActionButtonState.READY -> {
                playButton.visibility = View.VISIBLE
                playButton.isEnabled = true
                downloadButton.visibility = View.GONE
                downloadButton.isEnabled = false
                cancelButton.visibility = View.GONE
                cancelButton.isEnabled = false
                downloadProgressContainer.visibility = View.GONE
            }
        }
        updateActionFocusWiring()
    }

    private fun isLocalFilePlayable(path: String? = message.localPath): Boolean =
        MediaFileHelper.isPlayable(path)

    private fun syncLocalFileState() {
        val synced = MediaFileHelper.syncMessageFromFile(message)
        currentDownload = synced ?: currentDownload
    }

    /**
     * Resolves the real TDLib path (if available), checks the physical file,
     * then updates the action buttons.
     */
    private fun refreshLocalFileAndUpdateUI() {
        val fileId = message.fileId
        if (fileId == 0) {
            syncLocalFileState()
            checkLocalFileAndUpdateUI()
            return
        }
        TelegramClientManager.client?.send(TdApi.GetFile(fileId)) { result ->
            runOnUiThread {
                if (result is TdApi.File) {
                    val path = result.local.path
                    if (!path.isNullOrEmpty()) {
                        message.localPath = path
                        message.isDownloaded = result.local.isDownloadingCompleted
                    }
                    isDownloading = result.local.isDownloadingActive
                    if (activeDownloads.contains(message.fileId) && isDownloadComplete(result)) {
                        onDownloadComplete(result.local.path)
                    }
                }
                syncLocalFileState()
                checkLocalFileAndUpdateUI()
            }
        } ?: run {
            syncLocalFileState()
            checkLocalFileAndUpdateUI()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleClose()
            }
        })
    }

    /** Stops playback and returns to the previous screen. */
    private fun handleClose() {
        stopPlayback()
        finish()
    }

    /**
     * Collects ALL listeners in a single function.
     */
    private fun setupListeners() {
        // Play button listener (enabled/disabled dynamically)
        playButton.setOnClickListener { openFullScreenPlayback() }
        previewFullscreenButton.setOnClickListener { openFullScreenPlayback() }

        // Download button listener
        downloadButton.setOnClickListener { startDownload() }

        // Cancel button listener
        cancelButton.setOnClickListener { cancelCurrentDownload() }

        closeButton.setOnClickListener { handleClose() }
    }

    // ==================== Focus / D-pad Movement ====================

    /**
     * Restricts D-pad / remote "movement" to the action buttons plus the
     * close (✕) button. Close is always enabled and reachable via Up.
     */
    private fun restrictFocusToActionButtons() {

        // 1. Make every other interactive element unreachable by the focus engine.
        val nonFocusable = listOf(
            titleTextView, descriptionTextView, posterImageView,
            previewContainer, previewVideoHost,
            logTextView, downloadProgressContainer, downloadStatusText,
            downloadProgressBar, metadataChipRecycler, settingsRowRecycler
        )
        nonFocusable.forEach {
            it.isFocusable = false
            it.isFocusableInTouchMode = false
            // RecyclerViews also try to grab focus for their children:
            (it as? RecyclerView)?.apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                isFocusable = false
            }
        }

        // 2. Make the action buttons, preview fullscreen, and close button focus targets.
        listOf(playButton, downloadButton, cancelButton, closeButton, previewFullscreenButton).forEach {
            it.isFocusable = true
            it.isFocusableInTouchMode = true
        }

        // Close is always usable.
        closeButton.isEnabled = true

        // 3. Wire the D-pad targets based on the buttons currently on screen.
        updateActionFocusWiring()
    }

    /**
     * Wires the D-pad focus targets among the action buttons and the close (✕)
     * button, using ONLY the buttons that can currently take focus
     * (visible + enabled).
     *
     * This fixes the bug where moving focus to the close button trapped it there:
     * previously "Down" from close was hard-wired to Play, so when Play was
     * disabled (or the second-slot button was hidden) focus could not return to
     * any active button. Now close always points down to the first button that
     * can actually receive focus, and the horizontal loop skips hidden/disabled
     * buttons too.
     *
     * Must be called whenever a button's visibility or enabled state changes.
     */
    private fun updateActionFocusWiring() {
        val secondButton = if (cancelButton.visibility == View.VISIBLE) cancelButton else downloadButton

        val previewButton = previewFullscreenButton.takeIf { it.visibility == View.VISIBLE }
        val actionButtons = listOf(playButton, secondButton)
            .filter { it.visibility == View.VISIBLE && it.isEnabled }
        val focusables = buildList {
            previewButton?.let { add(it) }
            addAll(actionButtons)
        }

        focusables.forEachIndexed { index, button ->
            val left = focusables[(index - 1 + focusables.size) % focusables.size]
            val right = focusables[(index + 1) % focusables.size]
            button.nextFocusLeftId = left.id
            button.nextFocusRightId = right.id
            button.nextFocusUpId = R.id.action_close
        }

        closeButton.nextFocusDownId = focusables.firstOrNull()?.id ?: R.id.action_close
        closeButton.nextFocusLeftId  = R.id.action_close
        closeButton.nextFocusRightId = R.id.action_close
    }

    /**
     * Requests focus on the first button that is both visible and enabled,
     * in order: Play → Download → Cancel. Posted so it runs after layout.
     */
    private fun focusFirstUsableButton() {
        val root = findViewById<View>(android.R.id.content)
        root.post {
            // Only one of download/cancel is visible at a time; filtering by
            // visibility keeps focus off the hidden second-slot button.
            listOf(playButton, downloadButton, cancelButton)
                .firstOrNull { it.visibility == View.VISIBLE && it.isEnabled }
                ?.requestFocus()
        }
    }

    private fun openFullScreenPlayback() {
        syncLocalFileState()
        val playablePath = currentDownload?.localPath ?: message.localPath
        if (!isLocalFilePlayable(playablePath)) return
        stopPreviewPlaybackOnly()
        startPlayback(currentDownload)
    }

    private fun stopPreviewPlaybackOnly() {
        PreviewPlayerHelper.stop()
        lastPreviewPath = null
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            isAutoPlayEnabled = settingsDataStore.autoPlay.first()
            progressThreshold = settingsDataStore.progressThreshold.first()
            bufferSizeThresholdMB = settingsDataStore.bufferSizeThreshold.first()
            ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Settings loaded: AutoPlay=$isAutoPlayEnabled, Threshold=$progressThreshold%, Buffer=$bufferSizeThresholdMB MB")
        }
    }

    private fun checkLocalFileAndUpdateUI() {
        syncLocalFileState()
        when {
            isDownloading -> {
                applyActionButtonState(ActionButtonState.DOWNLOADING)
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Download in progress → Cancel only")
                updatePreviewSection(message.localPath)
            }
            isLocalFilePlayable() -> {
                applyActionButtonState(ActionButtonState.READY)
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Physical file found → Play enabled")
                updatePreviewSection(message.localPath)
            }
            else -> {
                applyActionButtonState(ActionButtonState.FRESH)
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"No physical file → Download only")
                hidePreviewSection()
            }
        }
        focusFirstUsableButton()
    }

    private fun updatePreviewSection(path: String?) {
        if (!PlayerHelper.isVlcInstalled(this) || !isLocalFilePlayable(path)) {
            hidePreviewSection()
            return
        }
        previewContainer.visibility = View.VISIBLE
        previewFullscreenButton.visibility = View.VISIBLE
        posterImageView.visibility = View.GONE
        updateActionFocusWiring()
        if (path == lastPreviewPath && PreviewPlayerHelper.isPlaying()) return

        val playablePath = path!!
        if (PreviewPlayerHelper.play(
            this,
            previewVideoHost,
            playablePath,
            onStarted = {
                ActivityLogHelper.prepend(
                    this@MediaDetailsActivity,
                    logTextView,
                    "VLC preview playback started"
                )
                recordHistoryViewed()
            }
        )) {
            lastPreviewPath = playablePath
        } else {
            hidePreviewSection()
            ActivityLogHelper.prepend(
                this@MediaDetailsActivity,
                logTextView,
                "VLC preview playback failed"
            )
        }
    }

    private fun hidePreviewSection() {
        PreviewPlayerHelper.stop()
        lastPreviewPath = null
        previewContainer.visibility = View.GONE
        previewFullscreenButton.visibility = View.GONE
        posterImageView.visibility = View.VISIBLE
        updateActionFocusWiring()
    }

    private fun startDownload() {
        syncLocalFileState()
        if (isLocalFilePlayable()) {
            applyActionButtonState(ActionButtonState.READY)
            focusFirstUsableButton()
            return
        }

        // A → B: hide Download, show Cancel, then start download.
        resetPlaybackSessionFlags()
        isDownloading = true
        applyActionButtonState(ActionButtonState.DOWNLOADING)
        cancelButton.requestFocus()
        activeDownloads.add(message.fileId)
        recordHistoryDownloading()
        ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Download started for fileId: ${message.fileId}")

        lifecycleScope.launch {
            try {
                val isTestMode = settingsDataStore.isTestMode.first()
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Mode: ${if (isTestMode) "Test Server" else "Telegram"}")
                MediaDownloadDataProvider.downloadMedia(
                    mode = isTestMode,
                    mediaMessage = message,
                    onProgress = { progress ->
                        runOnUiThread { updateDownloadProgress(progress) }
                    }
                )?.let { updatedMessage ->
                    runOnUiThread {
                        if (isTestMode) {
                            message = updatedMessage
                            ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Download completed successfully")
                            onDownloadComplete(message.localPath)
                            currentDownload = MediaFileHelper.buildDownloadingFileInfo(
                                fileId = message.fileId,
                                localPath = message.localPath,
                                expectedSize = message.size
                            )
                            if (isLocalFilePlayable()) {
                                updatePreviewSection(message.localPath)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MediaDetailsActivity", "Download error", e)
                runOnUiThread {
                    ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Error: ${e.message}")
                    resetDownloadUI()
                }
            }
        }
    }

    private fun resetDownloadUI() {
        isDownloading = false
        activeDownloads.clear()
        currentDownload = null
        downloadProgressContainer.visibility = View.GONE
        lifecycleScope.launch {
            HistoryHelper.clearDownloading(applicationContext, message)
        }
        applyActionButtonState(ActionButtonState.FRESH)
        focusFirstUsableButton()
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
        if (!activeDownloads.contains(message.fileId)) {
            activeDownloads.add(message.fileId)
        }

        val downloadedBytes = file.local.downloadedSize
        val totalBytes = file.expectedSize
        val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val downloadComplete = isDownloadComplete(file)

        runOnUiThread {
            if (!downloadComplete) {
                isDownloading = true
                applyActionButtonState(ActionButtonState.DOWNLOADING)
                updateDownloadProgress(progress, downloadedBytes, totalBytes)
                recordHistoryDownloading()
            }

            val localPath = file.local.path.takeIf { it.isNotEmpty() }
            currentDownload = localPath?.let {
                MediaFileHelper.buildDownloadingFileInfo(
                    fileId = file.id,
                    localPath = it,
                    expectedSize = totalBytes
                )
            }

            if (isLocalFilePlayable(file.local.path)) {
                val shouldAutoPlay = !isAutoPlayEnabled ||
                    progress >= progressThreshold ||
                    downloadedMB >= bufferSizeThresholdMB
                if (shouldAutoPlay && !PreviewPlayerHelper.isPlaying()) {
                    updatePreviewSection(file.local.path)
                    if (isAutoPlayEnabled && !autoPlayStarted) {
                        autoPlayStarted = true
                        ActivityLogHelper.prepend(
                            this@MediaDetailsActivity,
                            logTextView,
                            "Preview auto-play at $progress%"
                        )
                    }
                }
            }

            if (downloadComplete) {
                onDownloadComplete(file.local.path)
            }
        }
    }

    private fun isDownloadComplete(file: TdApi.File): Boolean {
        val path = file.local.path
        val bytesComplete = file.expectedSize > 0 && file.local.downloadedSize >= file.expectedSize
        return file.local.isDownloadingCompleted ||
            bytesComplete ||
            (path.isNotEmpty() && MediaFileHelper.isPlayable(path) && !file.local.isDownloadingActive)
    }

    private fun onDownloadComplete(localPath: String) {
        if (hasRecordedHistoryDownload && message.isDownloaded && isLocalFilePlayable(localPath)) {
            isDownloading = false
            activeDownloads.clear()
            applyActionButtonState(ActionButtonState.READY)
            return
        }
        message.localPath = localPath
        message.isDownloaded = true
        isDownloading = false
        activeDownloads.clear()
        downloadProgressContainer.visibility = View.GONE
        ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Download completed - File saved")
        recordHistoryDownloaded()
        checkLocalFileAndUpdateUI()
    }

    private fun resetPlaybackSessionFlags() {
        hasRecordedHistoryView = false
        hasRecordedHistoryDownload = false
        hasRecordedHistoryDownloading = false
    }

    private fun recordHistoryViewed() {
        if (hasRecordedHistoryView) return
        hasRecordedHistoryView = true
        lifecycleScope.launch {
            HistoryHelper.record(applicationContext, message, viewed = true)
        }
    }

    private fun recordHistoryDownloaded() {
        if (hasRecordedHistoryDownload) return
        hasRecordedHistoryDownload = true
        hasRecordedHistoryDownloading = true
        lifecycleScope.launch {
            HistoryHelper.record(applicationContext, message, downloaded = true)
        }
    }

    private fun recordHistoryDownloading() {
        if (hasRecordedHistoryDownloading) return
        hasRecordedHistoryDownloading = true
        lifecycleScope.launch {
            HistoryHelper.record(
                applicationContext,
                message.copy(isDownloadActive = true),
                downloading = true
            )
        }
    }

    private fun startPlayback(download: DownloadingFileInfo?) {
        when (val result = PlayerHelper.play(this, download?.localPath, download?.fileId ?: message.fileId)) {
            is PlayerHelper.PlayResult.Started -> {
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Started VLC playback for file ID: ${result.fileId}, path: ${result.path}")
                recordHistoryViewed()
            }
            is PlayerHelper.PlayResult.Failed -> {
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,result.reason)
                if (!isDownloading) {
                    applyActionButtonState(ActionButtonState.FRESH)
                }
            }
        }
    }

    private fun stopVlcOnly() {
        PlayerHelper.stop(this)
    }

    private fun stopPlayback() {
        stopVlcOnly()
        stopPreviewPlaybackOnly()
        ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Player Playback Stoped!")
    }

    private fun updateDownloadProgress(progress: Int, downloadedBytes: Long = 0, totalBytes: Long = 0) {
        downloadProgressContainer.visibility = View.VISIBLE
        downloadProgressBar.progress = progress

        downloadStatusText.text =
            FormatHelper.formatDownloadProgress(progress, downloadedBytes, totalBytes)
    }

    private fun cancelCurrentDownload() {
        // B → A: stop playback, stop download, delete partial file, hide Cancel/Play, show Download.
        stopPlayback()
        ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"User cancelled download")
        TelegramClientManager.cancelDownloadAndDelete(activeDownloads)

        val deletedCount = MediaFileHelper.deleteFiles(
            listOfNotNull(message.localPath, currentDownload?.localPath)
        )
        if (deletedCount > 0) {
            ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Deleted $deletedCount file(s)")
        }

        message.localPath = ""
        message.isDownloaded = false
        isDownloading = false
        hidePreviewSection()
        resetPlaybackSessionFlags()
        activeDownloads.clear()
        currentDownload = null
        downloadProgressContainer.visibility = View.GONE

        lifecycleScope.launch {
            HistoryHelper.clearDownloading(applicationContext, message)
        }

        applyActionButtonState(ActionButtonState.FRESH)
        focusFirstUsableButton()
        ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Download cancelled and cleaned up")
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
                add(MetadataChipItem(R.drawable.ic_check, FormatHelper.formatMimeType(message.mimeType)))
            }
            add(MetadataChipItem(R.drawable.ic_check, FormatHelper.formatBytes(message.size)))
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
                            add(SettingItem(R.drawable.ic_layers, FormatHelper.formatBufferSizeMb(bufferSizeMb), "BUFFER SIZE"))
                            add(SettingItem(R.drawable.ic_play, "$progressPercent%", "AUTO PLAY AT"))
                        }
                        add(SettingItem(R.drawable.ic_storage, FormatHelper.formatAvailableStorage(filesDir), "AVAILABLE STORAGE"))
                    }
                }.collect { items ->
                    settingsRowRecycler.adapter = SettingCardAdapter(items)
                    // Adapter change can steal focus; keep it on the buttons.
                    focusFirstUsableButton()
                }
            }
        }
    }

    companion object {
        const val EXTRA_MEDIA_MESSAGE = "extra_media_message"

        fun newIntent(context: Context, message: MediaMessage): Intent =
            Intent(context, MediaDetailsActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_MESSAGE, message)
            }
    }

    override fun onPause() {
        PreviewPlayerHelper.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (previewContainer.visibility == View.VISIBLE && isLocalFilePlayable(message.localPath)) {
            lastPreviewPath = null
            updatePreviewSection(message.localPath)
        }
    }

    override fun onDestroy() {
        fileUpdateJob?.cancel()
        hidePreviewSection()
        stopPlayback()
        super.onDestroy()
    }
}