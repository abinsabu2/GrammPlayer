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
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.network.tmdb.TmdbMovieDetails
import com.aes.grammplayer.provider.MediaDownloadDataProvider
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.ReleaseInfo
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import android.widget.ImageView
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaDetailsActivity : AppCompatActivity() {

    private enum class ActionButtonState {
        /** A — no playable file: Download only */
        FRESH,
        /** B — download in progress: Cancel only (Play disabled until complete) */
        DOWNLOADING,
        /** File fully downloaded and playable: Play only */
        READY
    }

    private lateinit var message: MediaMessage
    private lateinit var settingsDataStore: SettingsDataStore

    // ==================== All UI Elements Collected Here ====================
    private lateinit var titleTextView: TextView
    private lateinit var taglineTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var originalTitleTextView: TextView
    private lateinit var crewInfoTextView: TextView
    private lateinit var posterImageView: ImageView
    private lateinit var detailBackdropImage: ImageView
    private lateinit var detailBackdropScrim: View
    private lateinit var detailPageContent: View
    private lateinit var promoBanner: View
    private lateinit var promoTextView: TextView
    private lateinit var backdropVideoHost: ViewGroup
    private lateinit var previewFullscreenButton: View

    private lateinit var playButton: View
    private lateinit var downloadButton: View
    private lateinit var cancelButton: View
    private lateinit var closeButton: View
    private lateinit var logToggleButton: View
    private lateinit var logToggleLabel: TextView
    private lateinit var activityLogContainer: View
    private lateinit var logTextView: TextView

    // Download progress views
    private lateinit var downloadProgressContainer: View
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar

    // Section containers
    private lateinit var movieInfoSection: View
    private lateinit var genresSection: View
    private lateinit var castSection: View
    private lateinit var crewSection: View
    private lateinit var detailsScroll: View

    // RecyclerViews
    private lateinit var movieStatsRecycler: RecyclerView
    private lateinit var genreChipRecycler: RecyclerView
    private lateinit var castChipRecycler: RecyclerView
    private lateinit var fileMetadataChipRecycler: RecyclerView
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
    private var lastDownloadProgress = 0
    private var lastDownloadedBytes = 0L
    private var isActivityLogVisible = false
    private var staticBackdropActive = false
    private var backgroundPreviewActive = false

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
        setupSettingsRow()
        focusFirstUsableButton()
        startListeningToUpdates()
        restrictFocusToActionButtons()   // ← D-pad movement limited to action buttons + close
        recordDetailPageVisit()
    }

    /**
     * Collects and initializes ALL UI elements in a single function.
     */
    private fun initializeViews() {
        titleTextView = findViewById(R.id.title)
        taglineTextView = findViewById(R.id.tagline_text)
        descriptionTextView = findViewById(R.id.description)
        originalTitleTextView = findViewById(R.id.original_title_text)
        crewInfoTextView = findViewById(R.id.crew_info_text)
        posterImageView = findViewById(R.id.poster_image)
        detailsScroll = findViewById(R.id.details_scroll)
        movieInfoSection = findViewById(R.id.movie_info_section)
        genresSection = findViewById(R.id.genres_section)
        castSection = findViewById(R.id.cast_section)
        crewSection = findViewById(R.id.crew_section)
        detailBackdropImage = findViewById(R.id.detail_backdrop)
        detailBackdropScrim = findViewById(R.id.detail_backdrop_scrim)
        detailPageContent = findViewById(R.id.detail_page_content)
        promoBanner = findViewById(R.id.promo_banner)
        promoTextView = findViewById(R.id.promo_text)
        backdropVideoHost = findViewById(R.id.detail_backdrop_video_host)
        previewFullscreenButton = findViewById(R.id.preview_fullscreen)

        playButton = findViewById(R.id.action_play)
        downloadButton = findViewById(R.id.action_download)
        cancelButton = findViewById(R.id.action_cancel)
        closeButton = findViewById(R.id.action_close)
        logToggleButton = findViewById(R.id.action_toggle_log)
        logToggleLabel = findViewById(R.id.toggle_log_label)
        activityLogContainer = findViewById(R.id.activity_log_container)
        logTextView = findViewById(R.id.log_text_view)
        setActivityLogVisible(false)

        // Download progress
        downloadProgressContainer = findViewById(R.id.download_progress_container)
        downloadStatusText = findViewById(R.id.download_status_text)
        downloadProgressBar = findViewById(R.id.download_progress_bar)

        // Recyclers
        movieStatsRecycler = findViewById(R.id.movie_stats_row)
        genreChipRecycler = findViewById(R.id.genre_chip_row)
        castChipRecycler = findViewById(R.id.cast_chip_row)
        fileMetadataChipRecycler = findViewById(R.id.file_metadata_chip_row)
        settingsRowRecycler = findViewById(R.id.settings_row)

        setupRecyclerRows()

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
                    }
                    message.isDownloaded = result.local.isDownloadingCompleted
                    lastDownloadProgress = if (result.expectedSize > 0) {
                        (result.local.downloadedSize * 100 / result.expectedSize).toInt()
                    } else {
                        0
                    }
                    lastDownloadedBytes = result.local.downloadedSize

                    val downloadComplete = isDownloadComplete(result)
                    isDownloading = when {
                        downloadComplete -> false
                        activeDownloads.contains(message.fileId) -> true
                        else -> result.local.isDownloadingActive
                    }

                    if (activeDownloads.contains(message.fileId) && downloadComplete) {
                        onDownloadComplete(result.local.path)
                        return@runOnUiThread
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
        playButton.setOnClickListener {
            if (isFullyDownloaded()) openFullScreenPlayback()
        }
        previewFullscreenButton.setOnClickListener { openFullScreenPlayback() }

        // Download button listener
        downloadButton.setOnClickListener { startDownload() }

        // Cancel button listener
        cancelButton.setOnClickListener { cancelCurrentDownload() }

        closeButton.setOnClickListener { handleClose() }
        logToggleButton.setOnClickListener { toggleActivityLog() }
    }

    private fun setActivityLogVisible(visible: Boolean) {
        isActivityLogVisible = visible
        activityLogContainer.visibility = if (visible) View.VISIBLE else View.GONE
        logToggleLabel.setText(if (visible) R.string.toggle_log_hide else R.string.toggle_log_show)
        updateActionFocusWiring()
    }

    private fun toggleActivityLog() {
        setActivityLogVisible(!isActivityLogVisible)
    }

    // ==================== Focus / D-pad Movement ====================

    /**
     * Restricts D-pad / remote "movement" to the action buttons plus the
     * close (✕) button. Close is always enabled and reachable via Up.
     */
    private fun restrictFocusToActionButtons() {

        // 1. Make every other interactive element unreachable by the focus engine.
        val nonFocusable = listOf(
            titleTextView, taglineTextView, descriptionTextView, originalTitleTextView,
            crewInfoTextView, posterImageView, detailBackdropImage, detailBackdropScrim,
            detailsScroll, movieInfoSection, genresSection, castSection, crewSection,
            backdropVideoHost, activityLogContainer,
            logTextView, downloadProgressContainer, downloadStatusText,
            downloadProgressBar, movieStatsRecycler, genreChipRecycler, castChipRecycler,
            fileMetadataChipRecycler, settingsRowRecycler
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

        // 2. Make the action buttons, log toggle, preview fullscreen, and close button focus targets.
        listOf(
            playButton,
            downloadButton,
            cancelButton,
            closeButton,
            logToggleButton,
            previewFullscreenButton
        ).forEach {
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
            add(logToggleButton)
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

        previewButton?.nextFocusDownId = R.id.action_close

        closeButton.nextFocusDownId = focusables.firstOrNull()?.id ?: R.id.action_close
        closeButton.nextFocusUpId = previewButton?.id ?: R.id.action_close
        closeButton.nextFocusLeftId = R.id.action_close
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
            listOf(playButton, downloadButton, cancelButton, logToggleButton)
                .firstOrNull { it.visibility == View.VISIBLE && it.isEnabled }
                ?.requestFocus()
        }
    }

    private fun resolvePlayablePath(): String? {
        syncLocalFileState()
        val candidates = if (isDownloading) {
            listOfNotNull(currentDownload?.localPath, message.localPath)
        } else {
            listOfNotNull(message.localPath, currentDownload?.localPath)
        }
        return candidates.distinct().firstOrNull { isLocalFilePlayable(it) }
    }

    /**
     * Auto play OFF  → preview only after the file is fully downloaded.
     * Auto play ON   → preview when either configured threshold is met first
     *                  (download % or buffer size MB).
     */
    private fun isDownloadComplete(file: TdApi.File): Boolean {
        return file.local.isDownloadingCompleted ||
            (file.expectedSize > 0L && file.local.downloadedSize >= file.expectedSize)
    }

    private fun isFullyDownloaded(file: TdApi.File? = null): Boolean {
        if (file != null) return isDownloadComplete(file)
        if (message.isDownloaded) return true
        val onDisk = MediaFileHelper.resolveFile(resolvePlayablePath()) ?: return false
        return message.size > 0L && onDisk.length() >= message.size
    }

    private fun shouldStartPreview(
        progress: Int,
        downloadedBytes: Long,
        downloadComplete: Boolean
    ): Boolean {
        if (!isLocalFilePlayable(resolvePlayablePath())) return false
        if (downloadComplete) return true
        if (!isAutoPlayEnabled) return false

        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val progressMet = progressThreshold > 0 && progress >= progressThreshold
        val bufferMet = bufferSizeThresholdMB > 0 && downloadedMB >= bufferSizeThresholdMB
        val allowed = when {
            progressThreshold > 0 && bufferSizeThresholdMB > 0 -> progressMet || bufferMet
            progressThreshold > 0 -> progressMet
            bufferSizeThresholdMB > 0 -> bufferMet
            else -> false
        }
        Log.d(
            TAG,
            "shouldStartPreview: allowed=$allowed progress=$progress% bytes=$downloadedBytes " +
                "autoPlay=$isAutoPlayEnabled thresholds=($progressThreshold%, ${bufferSizeThresholdMB}MB) " +
                "complete=$downloadComplete"
        )
        return allowed
    }

    private fun updatePreviewIfAllowed(
        path: String? = resolvePlayablePath(),
        progress: Int = lastDownloadProgress,
        downloadedBytes: Long = lastDownloadedBytes,
        downloadComplete: Boolean = isFullyDownloaded()
    ) {
        val playablePath = path?.takeIf { isLocalFilePlayable(it) } ?: resolvePlayablePath()
        if (playablePath == null) {
            hidePreviewSection()
            return
        }
        if (shouldStartPreview(progress, downloadedBytes, downloadComplete)) {
            updatePreviewSection(playablePath)
            logPreviewAutoPlayTrigger(progress, downloadedBytes)
        } else {
            hidePreviewSection()
        }
    }

    private fun logPreviewAutoPlayTrigger(progress: Int, downloadedBytes: Long) {
        if (!isAutoPlayEnabled || autoPlayStarted) return
        autoPlayStarted = true
        val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
        val progressMet = progressThreshold > 0 && progress >= progressThreshold
        val bufferMet = bufferSizeThresholdMB > 0 && downloadedMB >= bufferSizeThresholdMB
        val trigger = when {
            progressMet && bufferMet ->
                "progress $progress% and buffer ${"%.1f".format(downloadedMB)} MB"
            progressMet -> "progress $progress%"
            bufferMet -> "buffer ${"%.1f".format(downloadedMB)} MB"
            else -> "file playable"
        }
        ActivityLogHelper.prepend(
            this@MediaDetailsActivity,
            logTextView,
            "Preview auto-play at $trigger"
        )
    }

    private fun applyDownloadingState(
        progress: Int = lastDownloadProgress,
        downloadedBytes: Long = lastDownloadedBytes,
        downloadComplete: Boolean = false
    ) {
        applyActionButtonState(ActionButtonState.DOWNLOADING)
        val playablePath = resolvePlayablePath()
        if (isLocalFilePlayable(playablePath)) {
            updatePreviewIfAllowed(playablePath, progress, downloadedBytes, downloadComplete)
        } else {
            hidePreviewSection()
        }
    }

    private fun openFullScreenPlayback() {
        val playablePath = resolvePlayablePath() ?: return
        stopPreviewPlaybackOnly()
        startPlayback(playablePath)
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
            ActivityLogHelper.prepend(
                this@MediaDetailsActivity,
                logTextView,
                "Settings loaded: AutoPlay=$isAutoPlayEnabled, Threshold=$progressThreshold%, Buffer=$bufferSizeThresholdMB MB"
            )
            refreshLocalFileAndUpdateUI()
        }
    }

    private fun checkLocalFileAndUpdateUI() {
        syncLocalFileState()
        bindFileMetadataRow(ReleaseTitleParser.parse(message.title))
        when {
            isDownloading -> {
                applyDownloadingState()
            }
            isFullyDownloaded() && isLocalFilePlayable() -> {
                applyActionButtonState(ActionButtonState.READY)
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Full file available → Play enabled")
                updatePreviewIfAllowed(message.localPath, downloadComplete = true)
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
        if (!isLocalFilePlayable(path)) {
            hidePreviewSection()
            return
        }
        showBackgroundPreview()
        updateActionFocusWiring()
        if (path == lastPreviewPath && PreviewPlayerHelper.isPlaying()) return

        val playablePath = path!!
        if (PreviewPlayerHelper.play(
            this,
            backdropVideoHost,
            playablePath,
            onStarted = {
                ActivityLogHelper.prepend(
                    this@MediaDetailsActivity,
                    logTextView,
                    "Background preview playback started"
                )
            }
        )) {
            lastPreviewPath = playablePath
        } else {
            hidePreviewSection()
            ActivityLogHelper.prepend(
                this@MediaDetailsActivity,
                logTextView,
                "Background preview playback failed"
            )
        }
    }

    private fun showBackgroundPreview() {
        backgroundPreviewActive = true
        detailBackdropImage.visibility = View.GONE
        backdropVideoHost.visibility = View.VISIBLE
        detailBackdropScrim.visibility = View.VISIBLE
        detailPageContent.setBackgroundResource(android.R.color.transparent)
        previewFullscreenButton.visibility = View.VISIBLE
    }

    private fun hidePreviewSection() {
        PreviewPlayerHelper.stop()
        lastPreviewPath = null
        backgroundPreviewActive = false
        backdropVideoHost.visibility = View.GONE
        previewFullscreenButton.visibility = View.GONE
        posterImageView.visibility = View.VISIBLE
        restoreStaticBackdropOrDefault()
        updateActionFocusWiring()
    }

    private fun restoreStaticBackdropOrDefault() {
        if (staticBackdropActive) {
            detailBackdropImage.visibility = View.VISIBLE
            detailBackdropScrim.visibility = View.VISIBLE
            detailPageContent.setBackgroundResource(android.R.color.transparent)
        } else {
            clearDetailBackdrop()
        }
    }

    private fun startDownload() {
        syncLocalFileState()
        if (isFullyDownloaded() && isLocalFilePlayable()) {
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
        val downloadComplete = isDownloadComplete(file)

        runOnUiThread {
            lastDownloadProgress = progress
            lastDownloadedBytes = downloadedBytes

            val localPath = file.local.path.takeIf { it.isNotEmpty() }
            localPath?.let { message.localPath = it }
            currentDownload = localPath?.let {
                MediaFileHelper.buildDownloadingFileInfo(
                    fileId = file.id,
                    localPath = it,
                    expectedSize = totalBytes
                )
            }

            if (!downloadComplete) {
                isDownloading = true
                updateDownloadProgress(progress, downloadedBytes, totalBytes)
                recordHistoryDownloading()
                applyDownloadingState(progress, downloadedBytes, downloadComplete = false)
            }

            if (downloadComplete) {
                onDownloadComplete(file.local.path)
            }
        }
    }

    private fun onDownloadComplete(localPath: String) {
        if (hasRecordedHistoryDownload && message.isDownloaded && isLocalFilePlayable(localPath)) {
            isDownloading = false
            activeDownloads.clear()
            syncDownloadInfoFromPath(localPath)
            applyActionButtonState(ActionButtonState.READY)
            updatePreviewIfAllowed(localPath, downloadComplete = true)
            return
        }
        message.localPath = localPath
        message.isDownloaded = true
        isDownloading = false
        activeDownloads.clear()
        downloadProgressContainer.visibility = View.GONE
        syncDownloadInfoFromPath(localPath)
        ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Download completed - File saved")
        recordHistoryDownloaded()
        checkLocalFileAndUpdateUI()
    }

    private fun syncDownloadInfoFromPath(localPath: String) {
        message.localPath = localPath
        currentDownload = MediaFileHelper.syncMessageFromFile(message)
            ?: MediaFileHelper.buildDownloadingFileInfo(
                fileId = message.fileId,
                localPath = localPath,
                expectedSize = message.size
            )
    }

    private fun resetPlaybackSessionFlags() {
        hasRecordedHistoryView = false
        hasRecordedHistoryDownload = false
        hasRecordedHistoryDownloading = false
        autoPlayStarted = false
        lastDownloadProgress = 0
        lastDownloadedBytes = 0L
    }

    private fun recordDetailPageVisit() {
        if (hasRecordedHistoryView) return
        hasRecordedHistoryView = true
        lifecycleScope.launch {
            HistoryHelper.recordDetailVisit(applicationContext, message)
        }
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

    private fun startPlayback(filePath: String) {
        val fileId = currentDownload?.fileId?.takeIf { it != 0 } ?: message.fileId
        when (val result = PlayerHelper.play(this, filePath, fileId)) {
            is PlayerHelper.PlayResult.Started -> {
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,"Started VLC playback for file ID: ${result.fileId}, path: ${result.path}")
                recordHistoryViewed()
            }
            is PlayerHelper.PlayResult.Failed -> {
                ActivityLogHelper.prepend(this@MediaDetailsActivity, logTextView,result.reason)
                when {
                    isDownloading ->
                        applyActionButtonState(ActionButtonState.DOWNLOADING)
                    !isDownloading && !isLocalFilePlayable(filePath) ->
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
        val info = ReleaseTitleParser.parse(message.title)
        titleTextView.text = info.displayTitle
        taglineTextView.visibility = View.GONE
        descriptionTextView.text = message.description?.takeIf { it.isNotBlank() }
            ?: getString(R.string.detail_no_synopsis)
        promoBanner.visibility = View.GONE
        clearMovieSections()
        bindFileMetadataRow(info)
        loadTmdbMetadata(info)
    }

    private fun setupRecyclerRows() {
        val horizontal = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        listOf(
            movieStatsRecycler,
            genreChipRecycler,
            castChipRecycler,
            fileMetadataChipRecycler,
            settingsRowRecycler
        ).forEach { recycler ->
            recycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
            recycler.setHasFixedSize(true)
            recycler.isFocusable = false
            recycler.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        movieStatsRecycler.layoutManager = horizontal
    }

    private fun loadTmdbMetadata(info: ReleaseInfo) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val details = PosterFetcher.fetchDetailsForRelease(info)
                withContext(Dispatchers.Main) {
                    if (details == null) {
                        applyPosterFallback()
                        clearDetailBackdrop()
                        clearMovieSections()
                        return@withContext
                    }
                    bindTmdbHeader(details)
                    bindDetailBackdrop(details)
                    bindMovieSections(details, info)
                    bindPromoBanner(details)
                    ActivityLogHelper.prepend(
                        this@MediaDetailsActivity,
                        logTextView,
                        "TMDB metadata loaded for ${details.title}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TMDB metadata", e)
                withContext(Dispatchers.Main) {
                    applyPosterFallback()
                    clearDetailBackdrop()
                    clearMovieSections()
                }
            }
        }
    }

    private fun bindTmdbHeader(details: TmdbMovieDetails) {
        titleTextView.text = PosterFetcher.displayTitle(details)

        val tagline = details.tagline?.takeIf { it.isNotBlank() }
        if (tagline != null) {
            taglineTextView.text = tagline
            taglineTextView.visibility = View.VISIBLE
        } else {
            taglineTextView.visibility = View.GONE
        }

        descriptionTextView.text = details.overview?.takeIf { it.isNotBlank() }
            ?: message.description?.takeIf { it.isNotBlank() }
            ?: getString(R.string.detail_no_synopsis)

        val posterUrl = PosterFetcher.posterUrl(details.poster_path)
        if (posterUrl != null) {
            val cornerRadius = resources.getDimensionPixelSize(R.dimen.detail_poster_radius)
            Glide.with(this)
                .load(posterUrl)
                .transform(RoundedCorners(cornerRadius))
                .placeholder(R.drawable.card_background)
                .error(R.drawable.detail_back_drop)
                .into(posterImageView)
        } else {
            applyPosterFallback()
        }
    }

    private fun bindDetailBackdrop(details: TmdbMovieDetails) {
        val backdropUrl = PosterFetcher.backdropUrl(PosterFetcher.resolveBackdropPath(details))
        if (backdropUrl.isNullOrBlank()) {
            staticBackdropActive = false
            if (!backgroundPreviewActive) {
                clearDetailBackdrop()
            }
            return
        }

        staticBackdropActive = true
        Glide.with(this)
            .load(backdropUrl)
            .centerCrop()
            .into(detailBackdropImage)

        if (!backgroundPreviewActive) {
            detailBackdropImage.visibility = View.VISIBLE
            detailBackdropScrim.visibility = View.VISIBLE
            detailPageContent.setBackgroundResource(android.R.color.transparent)
        }
    }

    private fun clearDetailBackdrop() {
        staticBackdropActive = false
        Glide.with(this).clear(detailBackdropImage)
        detailBackdropImage.setImageDrawable(null)
        if (!backgroundPreviewActive) {
            detailBackdropImage.visibility = View.GONE
            detailBackdropScrim.visibility = View.GONE
            detailPageContent.setBackgroundResource(R.drawable.detail_page_background)
        }
    }

    private fun applyPosterFallback() {
        posterImageView.setImageDrawable(null)
        posterImageView.setBackgroundResource(R.drawable.detail_back_drop)
    }

    private fun bindPromoBanner(details: TmdbMovieDetails) {
        val castLine = details.credits?.cast
            ?.take(3)
            ?.joinToString(", ") { it.name }
            ?.takeIf { it.isNotBlank() }
            ?.let { getString(R.string.detail_promo_cast, it) }

        val genreLine = details.genres
            ?.take(3)
            ?.joinToString(" · ") { it.name }
            ?.takeIf { it.isNotBlank() }

        val runtimeLine = details.runtime
            ?.takeIf { it > 0 }
            ?.let { FormatHelper.formatRuntime(it) }

        val ratingLine = details.vote_average
            ?.takeIf { it > 0 }
            ?.let { FormatHelper.formatRating(it) }

        val promoText = castLine
            ?: listOfNotNull(genreLine, runtimeLine, ratingLine).joinToString(" · ").takeIf { it.isNotBlank() }

        if (promoText.isNullOrBlank()) {
            promoBanner.visibility = View.GONE
        } else {
            promoTextView.text = promoText
            promoBanner.visibility = View.VISIBLE
        }
    }

    private fun bindMovieSections(details: TmdbMovieDetails, info: ReleaseInfo) {
        bindMovieStats(details, info)
        bindGenreRow(details)
        bindCastRow(details)
        bindCrewSection(details)
    }

    private fun bindMovieStats(details: TmdbMovieDetails, info: ReleaseInfo) {
        val stats = buildList {
            details.vote_average
                ?.takeIf { it > 0 }
                ?.let { add(DetailStatItem(FormatHelper.formatRating(it), getString(R.string.detail_stat_rating))) }
            details.runtime
                ?.takeIf { it > 0 }
                ?.let { add(DetailStatItem(FormatHelper.formatRuntime(it), getString(R.string.detail_stat_runtime))) }
            (PosterFetcher.releaseYear(details.release_date) ?: info.year)?.let {
                add(DetailStatItem(it.toString(), getString(R.string.detail_stat_year)))
            }
            FormatHelper.formatReleaseDate(details.release_date)?.let {
                add(DetailStatItem(it, getString(R.string.detail_stat_released)))
            }
            details.status?.takeIf { it.isNotBlank() }?.let {
                add(DetailStatItem(it, getString(R.string.detail_stat_status)))
            }
            PosterFetcher.trailerLabel(details)?.let {
                add(DetailStatItem(it, getString(R.string.detail_section_movie_info)))
            }
        }

        val originalTitle = details.original_title?.takeIf {
            it.isNotBlank() && !it.equals(details.title, ignoreCase = true)
        }
        if (originalTitle != null) {
            originalTitleTextView.text = getString(R.string.detail_original_title, originalTitle)
            originalTitleTextView.visibility = View.VISIBLE
        } else {
            originalTitleTextView.visibility = View.GONE
        }

        if (stats.isEmpty() && originalTitle == null) {
            movieInfoSection.visibility = View.GONE
        } else {
            movieStatsRecycler.adapter = DetailStatAdapter(stats)
            movieInfoSection.visibility = View.VISIBLE
        }
    }

    private fun bindGenreRow(details: TmdbMovieDetails) {
        val genres = details.genres.orEmpty().map {
            MetadataChipItem(R.drawable.ic_album, it.name)
        }
        if (genres.isEmpty()) {
            genresSection.visibility = View.GONE
        } else {
            genreChipRecycler.adapter = MetadataChipAdapter(genres)
            genresSection.visibility = View.VISIBLE
        }
    }

    private fun bindCastRow(details: TmdbMovieDetails) {
        val cast = details.credits?.cast.orEmpty()
            .take(10)
            .map {
                CastChipItem(
                    name = it.name,
                    role = it.character?.takeIf { character -> character.isNotBlank() }
                        ?: getString(R.string.detail_cast_role_unknown)
                )
            }
        if (cast.isEmpty()) {
            castSection.visibility = View.GONE
        } else {
            castChipRecycler.adapter = CastChipAdapter(cast)
            castSection.visibility = View.VISIBLE
        }
    }

    private fun bindCrewSection(details: TmdbMovieDetails) {
        val lines = buildList {
            FormatHelper.joinNames(PosterFetcher.crewNames(details, "Director"))?.let {
                add(getString(R.string.detail_crew_director, it))
            }
            FormatHelper.joinNames(PosterFetcher.crewNames(details, "Screenplay"))?.let {
                add(getString(R.string.detail_crew_writer, it))
            } ?: FormatHelper.joinNames(PosterFetcher.crewNames(details, "Writer"))?.let {
                add(getString(R.string.detail_crew_writer, it))
            }
            FormatHelper.joinNames(PosterFetcher.crewNames(details, "Producer"))?.let {
                add(getString(R.string.detail_crew_producer, it))
            }
        }
        if (lines.isEmpty()) {
            crewSection.visibility = View.GONE
        } else {
            crewInfoTextView.text = lines.joinToString("\n")
            crewSection.visibility = View.VISIBLE
        }
    }

    private fun clearMovieSections() {
        movieInfoSection.visibility = View.GONE
        genresSection.visibility = View.GONE
        castSection.visibility = View.GONE
        crewSection.visibility = View.GONE
        taglineTextView.visibility = View.GONE
        originalTitleTextView.visibility = View.GONE
    }

    private fun buildFileMetadataChips(info: ReleaseInfo): List<MetadataChipItem> = buildList {
        add(MetadataChipItem(R.drawable.ic_gear, getString(R.string.format_file_id, message.fileId)))
        if (message.mimeType.isNotBlank()) {
            add(MetadataChipItem(R.drawable.ic_check, FormatHelper.formatMimeType(message.mimeType)))
        }
        add(MetadataChipItem(R.drawable.ic_storage, FormatHelper.formatBytes(message.size)))
        info.year?.let { add(MetadataChipItem(R.drawable.ic_clock, it.toString())) }
        info.resolution?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        info.service?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        info.source?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        info.videoCodec?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        info.audioCodec?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        info.container?.let { add(MetadataChipItem(R.drawable.ic_check, it.uppercase())) }
        info.releaseGroup?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        info.groupTag?.let { add(MetadataChipItem(R.drawable.ic_check, it)) }
        if (message.isDownloaded) {
            add(MetadataChipItem(R.drawable.ic_download, getString(R.string.chip_downloaded)))
        }
    }

    private fun bindFileMetadataRow(info: ReleaseInfo) {
        fileMetadataChipRecycler.adapter = MetadataChipAdapter(buildFileMetadataChips(info))
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
                    isAutoPlayEnabled = autoPlay
                    bufferSizeThresholdMB = bufferSizeMb
                    progressThreshold = progressPercent
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
                    if (isDownloading) {
                        applyDownloadingState()
                    }
                    // Adapter change can steal focus; keep it on the buttons.
                    focusFirstUsableButton()
                }
            }
        }
    }

    companion object {
        private const val TAG = "MediaDetailsActivity"
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
        if (isLocalFilePlayable(message.localPath)) {
            lastPreviewPath = null
            updatePreviewIfAllowed(message.localPath, downloadComplete = isFullyDownloaded())
        }
    }

    override fun onDestroy() {
        fileUpdateJob?.cancel()
        hidePreviewSection()
        stopPlayback()
        super.onDestroy()
    }
}