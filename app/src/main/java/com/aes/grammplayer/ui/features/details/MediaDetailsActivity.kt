package com.aes.grammplayer.ui.features.details

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R
import com.aes.grammplayer.config.ReviewModeHelper
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.GlideHelper
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
import com.aes.grammplayer.util.analytics.AnalyticsHelper
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import android.widget.ImageView
import android.widget.Toast
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

    // Download progress views
    private lateinit var downloadProgressContainer: View
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar

    // Section containers
    private lateinit var movieInfoSection: View
    private lateinit var castSection: View
    private lateinit var detailsScroll: View

    // RecyclerViews
    private lateinit var movieStatsRecycler: RecyclerView
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
        applyResponsiveLayout()
        setupBackNavigation()
        setupListeners()
        loadSettings()
        bindHeader()
        setupSettingsRow()
        startListeningToUpdates()
        restrictFocusToActionButtons()
        installActionOnlyFocusGuard()
        focusFirstUsableButton()
        recordDetailPageVisit()
    }

    /**
     * Collects and initializes ALL UI elements in a single function.
     */
    private fun initializeViews() {
        titleTextView = findViewById(R.id.title)
        taglineTextView = findViewById(R.id.tagline_text)
        descriptionTextView = findViewById(R.id.description)
        posterImageView = findViewById(R.id.poster_image)
        detailsScroll = findViewById(R.id.details_scroll)
        movieInfoSection = findViewById(R.id.movie_info_section)
        castSection = findViewById(R.id.cast_section)
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

        // Download progress
        downloadProgressContainer = findViewById(R.id.download_progress_container)
        downloadStatusText = findViewById(R.id.download_status_text)
        downloadProgressBar = findViewById(R.id.download_progress_bar)

        // Recyclers
        movieStatsRecycler = findViewById(R.id.movie_stats_row)
        castChipRecycler = findViewById(R.id.cast_chip_row)
        fileMetadataChipRecycler = findViewById(R.id.file_metadata_chip_row)
        settingsRowRecycler = findViewById(R.id.settings_row)

        setupRecyclerRows()

        applyActionButtonState(ActionButtonState.FRESH)
    }

    /** Scales text truncation to the available screen height across TV resolutions. */
    private fun applyResponsiveLayout() {
        val config = resources.configuration
        val heightDp = config.screenHeightDp
        val configuredDescriptionLines = resources.getInteger(R.integer.detail_description_max_lines)
        val descriptionLines = when {
            heightDp < 420 -> minOf(configuredDescriptionLines, 2)
            heightDp < 520 -> minOf(configuredDescriptionLines, 3)
            else -> configuredDescriptionLines
        }
        descriptionTextView.maxLines = descriptionLines
        titleTextView.maxLines = resources.getInteger(R.integer.detail_title_max_lines)
        taglineTextView.maxLines = resources.getInteger(R.integer.detail_tagline_max_lines)
        Log.d(
            TAG,
            "Detail layout: ${config.screenWidthDp}x${heightDp}dp, sw=${config.smallestScreenWidthDp}dp, " +
                "descLines=$descriptionLines, title=${titleTextView.maxLines}"
        )
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
        bindActionButton(playButton) { openFullScreenPlayback() }
        bindActionButton(previewFullscreenButton) { openFullScreenPlayback() }
        bindActionButton(downloadButton) { startDownload() }
        bindActionButton(cancelButton) { cancelCurrentDownload() }
        bindActionButton(closeButton) { handleClose() }
    }

    private fun bindActionButton(button: View, action: () -> Unit) {
        button.isClickable = true
        button.setOnClickListener { action() }
        button.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP &&
                (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                action()
                true
            } else {
                false
            }
        }
    }

    // ==================== Focus / D-pad Movement ====================

    private fun isActionFocusTarget(view: View?): Boolean {
        if (view == null) return false
        val actionBar = findViewById<View>(R.id.details_actions_bar) ?: return false
        var current: View? = view
        while (current != null) {
            if (current === actionBar) return true
            current = current.parent as? View
        }
        return false
    }

    /**
     * Restricts D-pad / remote movement to the bottom action bar only.
     */
    private fun restrictFocusToActionButtons() {
        val fileDetailsSection = findViewById<View>(R.id.file_details_section)

        val nonFocusable = listOf(
            titleTextView, taglineTextView, descriptionTextView,
            posterImageView, detailBackdropImage, detailBackdropScrim,
            detailsScroll, fileDetailsSection, movieInfoSection, castSection,
            backdropVideoHost, downloadProgressContainer, downloadStatusText,
            downloadProgressBar, movieStatsRecycler, castChipRecycler,
            fileMetadataChipRecycler, settingsRowRecycler
        )
        nonFocusable.forEach {
            it.isFocusable = false
            it.isFocusableInTouchMode = false
            (it as? RecyclerView)?.apply {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                isFocusable = false
            }
        }
        (detailsScroll as ViewGroup).descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        listOf(
            closeButton,
            previewFullscreenButton,
            playButton,
            downloadButton,
            cancelButton
        ).forEach {
            it.isFocusable = true
            it.isFocusableInTouchMode = true
        }

        closeButton.isEnabled = true
        updateActionFocusWiring()
    }

    /** Snaps focus back to the action bar if it escapes into scroll content. */
    private fun installActionOnlyFocusGuard() {
        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null && !isActionFocusTarget(newFocus)) {
                window.decorView.post { focusFirstUsableButton() }
            }
        }
    }

    /**
     * Wires horizontal D-pad movement among visible action-bar buttons.
     * Up/Down stay on the action bar so focus cannot enter the scrollable content.
     */
    private fun updateActionFocusWiring() {
        val secondButton = if (cancelButton.visibility == View.VISIBLE) cancelButton else downloadButton
        val focusables = buildList {
            add(closeButton)
            if (previewFullscreenButton.isVisible) add(previewFullscreenButton)
            addAll(listOf(playButton, secondButton).filter { it.isVisible && it.isEnabled })
        }

        focusables.forEachIndexed { index, button ->
            val left = focusables[(index - 1 + focusables.size) % focusables.size]
            val right = focusables[(index + 1) % focusables.size]
            button.nextFocusLeftId = left.id
            button.nextFocusRightId = right.id
            button.nextFocusUpId = button.id
            button.nextFocusDownId = button.id
        }
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
            buildList {
                if (previewFullscreenButton.isVisible) add(previewFullscreenButton)
                add(playButton)
                add(downloadButton)
                add(cancelButton)
                add(closeButton)
            }.firstOrNull { it.isVisible && it.isEnabled }
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
        val playablePath = resolvePlayablePath() ?: return false
        val onDisk = MediaFileHelper.resolveFile(playablePath) ?: return false
        val complete = message.size <= 0L || onDisk.length() >= message.size
        if (complete) message.isDownloaded = true
        return complete
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
            markPreviewAutoPlayStarted()
        } else {
            hidePreviewSection()
        }
    }

    private fun markPreviewAutoPlayStarted() {
        if (!isAutoPlayEnabled || autoPlayStarted) return
        autoPlayStarted = true
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
        val playablePath = resolvePlayablePath()
        if (playablePath == null) {
            Toast.makeText(this, R.string.playback_file_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val shouldResumePreview = backgroundPreviewActive
        stopPreviewPlaybackOnly()
        lifecycleScope.launch {
            val useInAppPlayer = ReviewModeHelper.isReviewMode(this@MediaDetailsActivity) ||
                !PlayerHelper.isVlcInstalled(this@MediaDetailsActivity)
            val result = if (useInAppPlayer) {
                PlayerHelper.playInApp(this@MediaDetailsActivity, playablePath, message.fileId)
            } else {
                launchPlayback(playablePath)
            }
            when (result) {
                is PlayerHelper.PlayResult.Started -> recordHistoryViewed()
                is PlayerHelper.PlayResult.Failed -> {
                    showPlaybackError(result.reason)
                    if (shouldResumePreview) {
                        updatePreviewIfAllowed(
                            playablePath,
                            lastDownloadProgress,
                            lastDownloadedBytes,
                            isFullyDownloaded()
                        )
                    }
                }
            }
        }
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
                updatePreviewIfAllowed(message.localPath, downloadComplete = true)
            }
            else -> {
                applyActionButtonState(ActionButtonState.FRESH)
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
            playablePath
        )) {
            lastPreviewPath = playablePath
        } else {
            hidePreviewSection()
        }
    }

    private fun showBackgroundPreview() {
        backgroundPreviewActive = true
        detailBackdropImage.visibility = View.GONE
        backdropVideoHost.visibility = View.VISIBLE
        detailBackdropScrim.visibility = View.VISIBLE
        detailPageContent.setBackgroundResource(android.R.color.transparent)
        previewFullscreenButton.visibility = View.VISIBLE
        previewFullscreenButton.isEnabled = true
        updateActionFocusWiring()
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

        lifecycleScope.launch {
            try {
                val isTestMode = settingsDataStore.isTestMode.first()
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
                            onDownloadComplete(message.localPath.orEmpty())
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
        AnalyticsHelper.logMediaDownload(message.fileId, "telegram")
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

    private fun launchPlayback(filePath: String): PlayerHelper.PlayResult {
        val fileId = currentDownload?.fileId?.takeIf { it != 0 } ?: message.fileId
        return PlayerHelper.play(this, filePath, fileId)
    }

    private fun startPlayback(filePath: String) {
        if (!ensureVlcInstalled()) return
        when (val result = launchPlayback(filePath)) {
            is PlayerHelper.PlayResult.Started -> recordHistoryViewed()
            is PlayerHelper.PlayResult.Failed -> handlePlaybackFailure(result, filePath)
        }
    }

    private fun handlePlaybackFailure(result: PlayerHelper.PlayResult.Failed, filePath: String) {
        showPlaybackError(result.reason)
        when {
            isDownloading -> applyActionButtonState(ActionButtonState.DOWNLOADING)
            !isDownloading && !isLocalFilePlayable(filePath) ->
                applyActionButtonState(ActionButtonState.FRESH)
        }
    }

    private fun ensureVlcInstalled(): Boolean {
        if (PlayerHelper.isVlcInstalled(this)) return true
        showInstallVlcDialog()
        return false
    }

    private fun showInstallVlcDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.playback_vlc_required_title)
            .setMessage(PlayerHelper.vlcRequiredMessage(this))
            .setPositiveButton(PlayerHelper.vlcInstallButtonLabel(this)) { _, _ ->
                PlayerHelper.openVlcInstallPage(this)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPlaybackError(reason: String) {
        if (reason.contains("not installed", ignoreCase = true)) {
            showInstallVlcDialog()
            return
        }
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
    }

    private fun stopVlcOnly() {
        PlayerHelper.stop(this)
    }

    private fun stopPlayback() {
        stopVlcOnly()
        stopPreviewPlaybackOnly()
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
        TelegramClientManager.cancelDownloadAndDelete(activeDownloads)

        MediaFileHelper.deleteFiles(
            listOfNotNull(message.localPath, currentDownload?.localPath)
        )
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
        titleTextView.text = formatTitleWithOriginal(
            PosterFetcher.displayTitle(details),
            details.title,
            details.original_title
        )

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
        GlideHelper.clear(detailBackdropImage)
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
        bindCastRow(details)
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

        if (stats.isEmpty()) {
            movieInfoSection.visibility = View.GONE
        } else {
            movieStatsRecycler.adapter = DetailStatAdapter(stats)
            movieInfoSection.visibility = View.VISIBLE
        }
    }

    private fun bindCastRow(details: TmdbMovieDetails) {
        val cast = details.credits?.cast.orEmpty()
            .take(5)
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

    private fun clearMovieSections() {
        movieInfoSection.visibility = View.GONE
        castSection.visibility = View.GONE
        taglineTextView.visibility = View.GONE
    }

    private fun formatTitleWithOriginal(
        displayTitle: String,
        canonicalTitle: String?,
        originalTitle: String?
    ): String {
        val original = originalTitle?.takeIf {
            it.isNotBlank() &&
                !it.equals(canonicalTitle ?: displayTitle, ignoreCase = true)
        }
        return if (original != null) "$displayTitle ($original)" else displayTitle
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
        PreviewPlayerHelper.stop()
        stopPlayback()
        super.onDestroy()
    }
}