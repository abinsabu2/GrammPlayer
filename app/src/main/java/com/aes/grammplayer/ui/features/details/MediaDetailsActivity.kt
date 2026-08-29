package com.aes.grammplayer.ui.features.details

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aes.grammplayer.R
import androidx.core.content.FileProvider
import android.webkit.MimeTypeMap
import com.aes.grammplayer.db.AppDatabase
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.ActiveDownloadManager
import com.aes.grammplayer.helper.ApplicationHelper
import com.aes.grammplayer.helper.DownloadProgressTracker
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.GlideHelper
import com.aes.grammplayer.helper.MediaFileHelper
import com.aes.grammplayer.helper.PlayerHelper
import com.aes.grammplayer.history.HistoryStore
import com.aes.grammplayer.network.tmdb.PosterFetcher
import com.aes.grammplayer.network.tmdb.TmdbMovieDetails
import com.aes.grammplayer.provider.MediaDownloadDataProvider
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.ReleaseInfo
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi

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
    private var previewFullscreenLabel: TextView? = null
    private lateinit var trailerButton: View
    private var trailerKey: String? = null

    private lateinit var resumeButton: View
    private lateinit var resumeButtonLabel: TextView
    private lateinit var playButton: View
    private lateinit var playButtonLabel: TextView
    private lateinit var downloadButton: View
    private lateinit var cancelButton: View
    private lateinit var closeButton: View

    // Download progress views
    private lateinit var downloadProgressContainer: View
    private lateinit var downloadStatusText: TextView
    private lateinit var downloadProgressBar: ProgressBar
    private lateinit var bottomDownloadStatus: View
    private lateinit var bottomDownloadStatusText: TextView

    // Section containers
    private lateinit var movieInfoSection: View
    private lateinit var castSection: View
    private lateinit var detailsScroll: View

    // RecyclerViews
    private lateinit var movieStatsRecycler: RecyclerView
    private lateinit var castChipRecycler: RecyclerView
    private lateinit var fileMetadataChipRecycler: RecyclerView
    private lateinit var settingsRowRecycler: RecyclerView
    private var storageReceiver: BroadcastReceiver? = null
    private var storageWarningText: TextView? = null

    private var fileUpdateJob: Job? = null
    private var downloadProgressObserverJob: Job? = null
    private var isDownloading = false
    private var autoPlayStarted = false
    private var lastPreviewPath: String? = null
    private var hasRecordedHistoryView = false
    /** Prevents re-entering [onDownloadComplete] UI path after a successful finish. */
    private var downloadCompleteHandled = false

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

    /** Temporary resume offset for this message (Settings DataStore); 0 = none. */
    private var savedPositionMs: Long = 0L
    private var vlcLaunchMs: Long = 0L
    private var lastVlcContentUri: Uri? = null
    private var lastVlcMime: String? = null

    private val vlcPlaybackLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleVlcPlaybackResult(result.resultCode, result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_details)

        settingsDataStore = SettingsDataStore(this)

        message = intent.getSerializableExtra(EXTRA_MEDIA_MESSAGE) as? MediaMessage
            ?: MediaMessage(id = 1, chat = 1, title = "Test Storage Check", description = "Storage verification", studio = "", width = 0, height = 0, duration = 0, size = 1024, isMedia = true, localPath = "", fileId = 1, mimeType = "video/mp4", videoUrl = "", thumbnailPath = "", cardImageUrl = "", backgroundImageUrl = "", isDownloaded = false, isDownloadActive = false, uniqueId = "test")
                .also { Log.i(TAG, "ponytail: dummy MediaMessage for storage test, no extra provided") }

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
        registerStorageReceiver()
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
        previewFullscreenLabel = previewFullscreenButton.findViewById(R.id.preview_fullscreen_label)
        trailerButton = findViewById(R.id.action_trailer)

        resumeButton = findViewById(R.id.action_resume)
        resumeButtonLabel = findViewById(R.id.action_resume_label)
        playButton = findViewById(R.id.action_play)
        playButtonLabel = findViewById(R.id.action_play_label)
        downloadButton = findViewById(R.id.action_download)
        cancelButton = findViewById(R.id.action_cancel)
        closeButton = findViewById(R.id.action_close)

        // Download progress
        downloadProgressContainer = findViewById(R.id.download_progress_container)
        downloadStatusText = findViewById(R.id.download_status_text)
        downloadProgressBar = findViewById(R.id.download_progress_bar)
        bottomDownloadStatus = findViewById(R.id.bottom_download_status)
        bottomDownloadStatusText = findViewById(R.id.bottom_download_status_text)

        // Recyclers
        movieStatsRecycler = findViewById(R.id.movie_stats_row)
        castChipRecycler = findViewById(R.id.cast_chip_row)
        fileMetadataChipRecycler = findViewById(R.id.file_metadata_chip_row)
        settingsRowRecycler = findViewById(R.id.settings_row)
        storageWarningText = findViewById(R.id.storage_warning_text)

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
    private fun isInsufficientStorage(): Boolean {
        if (ApplicationHelper.isExternalStorageAvailable()) return false
        val internalFree = ApplicationHelper.getInternalFreeBytes()
        val size = message.size.takeIf { it > 0 } ?: return false
        return internalFree < size
    }

    private fun syncStorageWarning() {
        if (!isDownloading && !isFullyDownloaded() && isInsufficientStorage()) {
            downloadButton.visibility = View.GONE
            downloadButton.isEnabled = false
            storageWarningText?.visibility = View.VISIBLE
        } else {
            storageWarningText?.visibility = View.GONE
        }
        updateActionFocusWiring()
    }

    private fun buildSettingsItems(autoPlay: Boolean, bufferSizeMb: Int, progressPercent: Int): List<SettingItem> = buildList {
        add(SettingItem(R.drawable.ic_power, if (autoPlay) "ON" else "OFF", "AUTO PLAY"))
        if (autoPlay) {
            add(SettingItem(R.drawable.ic_layers, FormatHelper.formatBufferSizeMb(bufferSizeMb), "BUFFER SIZE"))
            add(SettingItem(R.drawable.ic_play, "$progressPercent%", "AUTO PLAY AT"))
        }
        val internalFree = ApplicationHelper.getInternalFreeBytes()
        add(SettingItem(R.drawable.ic_storage, ApplicationHelper.formatFreeBytes(internalFree), "Storage(Int)", enabled = true))
        if (ApplicationHelper.isExternalStorageAvailable()) {
            val externalFree = ApplicationHelper.getExternalFreeBytes()
            add(SettingItem(R.drawable.ic_storage, ApplicationHelper.formatFreeBytes(externalFree), "Storage(SD)", enabled = true))
        }
    }

    private fun applyActionButtonState(state: ActionButtonState) {
        when (state) {
            ActionButtonState.FRESH -> {
                hideResumeButton()
                playButton.visibility = View.GONE
                playButton.isEnabled = false
                downloadButton.visibility = View.VISIBLE
                downloadButton.isEnabled = true
                cancelButton.visibility = View.GONE
                cancelButton.isEnabled = false
            }
            ActionButtonState.DOWNLOADING -> {
                hideResumeButton()
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
                updateResumeButtonVisibility()
            }
        }
        syncStorageWarning()
    }

    private fun hideResumeButton() {
        resumeButton.visibility = View.GONE
        resumeButton.isEnabled = false
        playButtonLabel.setText(R.string.play)
    }

    private fun updateResumeButtonVisibility() {
        if (savedPositionMs > 0L) {
            resumeButton.visibility = View.VISIBLE
            resumeButton.isEnabled = true
            resumeButtonLabel.text = getString(
                R.string.resume_with_time,
                FormatHelper.formatPlaybackPosition(savedPositionMs)
            )
            playButtonLabel.setText(R.string.play_from_start)
        } else {
            hideResumeButton()
        }
    }

    private fun updatePreviewFullscreenLabel() {
        val label = previewFullscreenLabel ?: previewFullscreenButton.findViewById<TextView>(R.id.preview_fullscreen_label) ?: return
        label.text = if (savedPositionMs > 0L) getString(R.string.resume) else getString(R.string.play)
    }

    private fun loadSavedPlaybackPosition(rebindButtons: Boolean = true) {
        lifecycleScope.launch {
            val position = settingsDataStore.getPlaybackPosition(message.id) ?: 0L
            savedPositionMs = position
            if (backgroundPreviewActive) updatePreviewFullscreenLabel()
            if (rebindButtons && playButton.isVisible) {
                updateResumeButtonVisibility()
                updateActionFocusWiring()
            }
        }
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
        bindActionButton(resumeButton) { openFullScreenPlayback(resume = true) }
        bindActionButton(playButton) { openFullScreenPlayback(resume = false) }
        bindActionButton(previewFullscreenButton) {
            openFullScreenPlayback(resume = savedPositionMs > 0L)
        }
        bindActionButton(trailerButton) { openTrailer() }
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
            downloadProgressBar, bottomDownloadStatus, bottomDownloadStatusText,
            movieStatsRecycler, castChipRecycler,
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
            trailerButton,
            previewFullscreenButton,
            resumeButton,
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
            if (trailerButton.isVisible) add(trailerButton)
            if (previewFullscreenButton.isVisible) add(previewFullscreenButton)
            addAll(
                listOf(resumeButton, playButton, secondButton)
                    .filter { it.isVisible && it.isEnabled }
            )
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
     * in order: Resume → Play → Download → Cancel. Posted so it runs after layout.
     */
    private fun focusFirstUsableButton() {
        val root = findViewById<View>(android.R.id.content)
        root.post {
            // Only one of download/cancel is visible at a time; filtering by
            // visibility keeps focus off the hidden second-slot button.
            buildList {
                if (previewFullscreenButton.isVisible) add(previewFullscreenButton)
                add(resumeButton)
                add(playButton)
                add(downloadButton)
                add(cancelButton)
                add(closeButton)
                if (trailerButton.isVisible) add(trailerButton)
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

    private fun openFullScreenPlayback(resume: Boolean = false) {
        val playablePath = resolvePlayablePath()
        if (playablePath == null) {
            Toast.makeText(this, R.string.playback_file_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val freshFile = MediaFileHelper.resolveFile(playablePath)
        if (freshFile == null) {
            Log.w(TAG, "playablePath stale, refresh: $playablePath")
            refreshLocalFileAndUpdateUI()
            Toast.makeText(this, R.string.playback_file_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        val playbackPath = freshFile.absolutePath
        if (!resume) {
            // Play from start: clear temporary bookmark first.
            savedPositionMs = 0L
            updateResumeButtonVisibility()
            lifecycleScope.launch {
                settingsDataStore.clearPlaybackPosition()
            }
            startPlayback(playbackPath, startPositionMs = 0L)
        } else {
            var effectiveStart = savedPositionMs.coerceAtLeast(0L)
            // ponytail: no duration here; avoid clamping tiny files (<1MB) where length check is unreliable
            if (effectiveStart > 0 && freshFile.length() < 1024 * 1024) {
                // keep as is for tiny/partial files
            }
            startPlayback(playbackPath, startPositionMs = effectiveStart)
        }
    }

    private fun openTrailer() {
        val key = trailerKey
        if (key.isNullOrBlank()) {
            Toast.makeText(this, R.string.trailer_not_available, Toast.LENGTH_SHORT).show()
            return
        }
        val primary = Intent(Intent.ACTION_VIEW, Uri.parse(PosterFetcher.trailerUrl(key))).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(primary)
            return
        } catch (_: ActivityNotFoundException) {
        }
        // ponytail: reuse bundled videos, no new OkHttp client, fallback to browser intents
        val browserUrls = listOf(
            "https://www.youtube.com/watch?v=$key",
            "https://youtu.be/$key"
        )
        for (url in browserUrls) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return
            } catch (_: ActivityNotFoundException) {
            }
        }
        Toast.makeText(this, R.string.trailer_not_available, Toast.LENGTH_SHORT).show()
    }

    private fun stopPreviewPlaybackOnly() {
        lastPreviewPath = null
    }

    private fun loadSettings() {
        lifecycleScope.launch {
            isAutoPlayEnabled = settingsDataStore.autoPlay.first()
            progressThreshold = settingsDataStore.progressThreshold.first()
            bufferSizeThresholdMB = settingsDataStore.bufferSizeThreshold.first()
            savedPositionMs = settingsDataStore.getPlaybackPosition(message.id) ?: 0L
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
                hidePreviewSection() // ponytail: preview only for downloading autoplay, READY uses Resume/Play in action bar
            }
            else -> {
                applyActionButtonState(ActionButtonState.FRESH)
                hidePreviewSection()
            }
        }
        if (backgroundPreviewActive) updatePreviewFullscreenLabel()
        syncStorageWarning()
        focusFirstUsableButton()
    }

    private fun updatePreviewSection(path: String?) {
        if (!isLocalFilePlayable(path)) {
            hidePreviewSection()
            return
        }
        showBackgroundPreview()
        updateActionFocusWiring()
        lastPreviewPath = path
    }

    private fun showBackgroundPreview() {
        if (!isDownloading) return // ponytail: preview only for downloading autoplay, READY uses Resume/Play in action bar
        if (isFullyDownloaded()) return
        backgroundPreviewActive = true
        backdropVideoHost.visibility = View.GONE
        detailBackdropImage.visibility = View.VISIBLE
        detailBackdropScrim.visibility = View.VISIBLE
        detailPageContent.setBackgroundResource(android.R.color.transparent)
        previewFullscreenButton.visibility = View.VISIBLE
        updatePreviewFullscreenLabel()
        previewFullscreenButton.isEnabled = true
        updateActionFocusWiring()
    }

    private fun hidePreviewSection() {
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

        if (isDownloading || ActiveDownloadManager.isActive(message.fileId)) {
            return
        }

        ActiveDownloadManager.otherActiveSession(message.fileId)?.let { other ->
            showReplaceDownloadDialog(other)
            return
        }

        performDownloadStart()
    }

    private fun showReplaceDownloadDialog(other: ActiveDownloadManager.Session) {
        AlertDialog.Builder(this)
            .setTitle(R.string.download_replace_title)
            .setMessage(getString(R.string.download_replace_message, other.displayTitle))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.download_replace_confirm) { _, _ ->
                lifecycleScope.launch {
                    try {
                        ActiveDownloadManager.cancelActiveDownload(applicationContext, other)
                        performDownloadStart()
                    } catch (e: Exception) {
                        Log.e("MediaDetailsActivity", "Failed to replace active download", e)
                        Toast.makeText(
                            this@MediaDetailsActivity,
                            R.string.error_fragment_message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            .show()
    }

    private fun performDownloadStart() {
        // A → B: hide Download, show Cancel, then start download.
        resetPlaybackSessionFlags()
        isDownloading = true
        applyActionButtonState(ActionButtonState.DOWNLOADING)
        cancelButton.requestFocus()
        activeDownloads.add(message.fileId)
        ActiveDownloadManager.begin(message)
        refreshBottomDownloadStatus(0)

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
        ActiveDownloadManager.release(message.fileId)
        DownloadProgressTracker.clear(message.fileId)
        currentDownload = null
        refreshBottomDownloadStatus()
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
                    ActiveDownloadManager.currentSession()?.let { active ->
                        if (update.file.id == active.fileId) {
                            refreshBottomDownloadStatus()
                        }
                    }
                }
            }
        }

        downloadProgressObserverJob?.cancel()
        downloadProgressObserverJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadProgressTracker.updates.collect {
                    refreshBottomDownloadStatus()
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
            localPath?.let {
                message.localPath = it
                ActiveDownloadManager.updateLocalPath(message.fileId, it)
            }
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
                applyDownloadingState(progress, downloadedBytes, downloadComplete = false)
            }

            if (downloadComplete) {
                onDownloadComplete(file.local.path)
            }
        }
    }

    private fun onDownloadComplete(localPath: String) {
        if (downloadCompleteHandled && message.isDownloaded && isLocalFilePlayable(localPath)) {
            isDownloading = false
            activeDownloads.clear()
            ActiveDownloadManager.complete(message.fileId)
            refreshBottomDownloadStatus()
            syncDownloadInfoFromPath(localPath)
            applyActionButtonState(ActionButtonState.READY)
            hidePreviewSection() // ponytail: preview only for downloading autoplay, READY uses Resume/Play in action bar
            return
        }
        downloadCompleteHandled = true
        message.localPath = localPath
        message.isDownloaded = true
        isDownloading = false
        activeDownloads.clear()
        ActiveDownloadManager.complete(message.fileId)
        refreshBottomDownloadStatus()
        syncDownloadInfoFromPath(localPath)
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
        downloadCompleteHandled = false
        autoPlayStarted = false
        lastDownloadProgress = 0
        lastDownloadedBytes = 0L
    }

    private fun recordDetailPageVisit() {
        if (hasRecordedHistoryView) return
        hasRecordedHistoryView = true
        lifecycleScope.launch {
            HistoryStore.recordVisit(applicationContext, message)
        }
    }

    private fun startPlayback(filePath: String, startPositionMs: Long = 0L) {
        if (!ensureVlcInstalled()) return
        val fileId = currentDownload?.fileId?.takeIf { it != 0 } ?: message.fileId
        // Store fallback uri/mime for mkv quick-kill (<1.5s) -> system player
        try {
            val f = java.io.File(filePath)
            lastVlcContentUri = FileProvider.getUriForFile(this, "${packageName}.provider", f)
            val ext = f.extension.lowercase()
            lastVlcMime = if (ext.isNotEmpty()) MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/*" else "video/*"
            // grant VLC permission preemptively mirrors PlayerHelper
            try { grantUriPermission(PlayerHelper.VLC_PACKAGE, lastVlcContentUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION) } catch (_: Exception) {}
        } catch (_: Exception) {
            lastVlcContentUri = null
            lastVlcMime = null
        }
        // Prefer for-result so VLC can return extra_position when the user exits.
        when (
            val result = PlayerHelper.preparePlay(
                this,
                filePath,
                fileId,
                startPositionMs = startPositionMs,
                forActivityResult = true
            )
        ) {
            is PlayerHelper.PlayResult.Ready -> {
                try {
                    vlcLaunchMs = SystemClock.elapsedRealtime()
                    vlcPlaybackLauncher.launch(result.intent)
                } catch (e: IllegalArgumentException) {
                    Log.e(TAG, "Failed to launch VLC (IllegalArgumentException) for $filePath", e)
                    handlePlaybackFailure(PlayerHelper.PlayResult.Failed("Share failed: Failed to find configured root for $filePath"), filePath)
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException launching VLC for $filePath", e)
                    handlePlaybackFailure(PlayerHelper.PlayResult.Failed("Permission denied launching VLC: ${e.message}"), filePath)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch VLC for result", e)
                    // Fallback: fire-and-forget (no position capture)
                    when (val fallback = PlayerHelper.play(this, filePath, fileId, startPositionMs)) {
                        is PlayerHelper.PlayResult.Started -> { }
                        is PlayerHelper.PlayResult.Failed -> handlePlaybackFailure(fallback, filePath)
                        is PlayerHelper.PlayResult.Ready -> { }
                    }
                }
            }
            is PlayerHelper.PlayResult.Failed -> handlePlaybackFailure(result, filePath)
            is PlayerHelper.PlayResult.Started -> { }
        }
    }

    private fun handleVlcPlaybackResult(resultCode: Int, data: Intent?) {
        val exit = PlayerHelper.parseExitPosition(data)
        if (exit == null) {
            val elapsed = SystemClock.elapsedRealtime() - vlcLaunchMs
            if (resultCode == 0 && elapsed < 1500 && lastVlcContentUri != null) {
                Log.w(TAG, "VLC quick kill ${elapsed}ms (resultCode=$resultCode), trying system player uri=$lastVlcContentUri mime=$lastVlcMime")
                Toast.makeText(this, "Trying system player...", Toast.LENGTH_SHORT).show()
                val contentUri = lastVlcContentUri
                val mimeType = lastVlcMime ?: "video/*"
                try {
                    val sysIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                        addCategory(Intent.CATEGORY_DEFAULT)
                    }
                    val chooser = Intent.createChooser(sysIntent, "Play video").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(chooser)
                    return
                } catch (e: ActivityNotFoundException) {
                    try {
                        val sysIntent2 = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(contentUri, mimeType)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            addCategory(Intent.CATEGORY_DEFAULT)
                        }
                        startActivity(sysIntent2)
                        return
                    } catch (e2: Exception) {
                        Log.e(TAG, "System player fallback failed", e2)
                        showPlaybackError("Playback failed")
                        loadSavedPlaybackPosition()
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "System player fallback failed", e)
                    showPlaybackError("Playback failed")
                    loadSavedPlaybackPosition()
                    return
                }
            }
            Log.d(TAG, "VLC returned no position (resultCode=$resultCode)")
            loadSavedPlaybackPosition()
            return
        }
        Log.i(
            TAG,
            "VLC exit position=${exit.positionMs}ms duration=${exit.durationMs}ms uri=${exit.uri}"
        )
        lifecycleScope.launch {
            settingsDataStore.savePlaybackPosition(
                messageId = message.id,
                positionMs = exit.positionMs,
                durationMs = exit.durationMs
            )
            savedPositionMs = settingsDataStore.getPlaybackPosition(message.id) ?: 0L
            if (backgroundPreviewActive) updatePreviewFullscreenLabel()
            if (playButton.isVisible) {
                updateResumeButtonVisibility()
                updateActionFocusWiring()
                if (savedPositionMs > 0L) {
                    focusFirstUsableButton()
                }
            }
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
        lastDownloadProgress = progress
        lastDownloadedBytes = downloadedBytes
        downloadProgressBar.progress = progress
        downloadStatusText.text =
            FormatHelper.formatDownloadProgress(progress, downloadedBytes, totalBytes)
        if (ActiveDownloadManager.isActive(message.fileId)) {
            downloadProgressContainer.visibility = View.VISIBLE
        }
        refreshBottomDownloadStatus(progress)
    }

    private fun refreshBottomDownloadStatus(progress: Int? = null) {
        val session = ActiveDownloadManager.currentSession()
        if (session == null) {
            bottomDownloadStatus.visibility = View.GONE
            if (!isDownloading) {
                downloadProgressContainer.visibility = View.GONE
            }
            return
        }

        if (session.fileId == message.fileId) {
            bottomDownloadStatus.visibility = View.GONE
            if (isDownloading) {
                downloadProgressContainer.visibility = View.VISIBLE
                val resolvedProgress = progress
                    ?: DownloadProgressTracker.progressFor(session.fileId)
                    ?: lastDownloadProgress
                downloadProgressBar.progress = resolvedProgress
            } else {
                downloadProgressContainer.visibility = View.GONE
            }
            return
        }

        // Another file is downloading — bottom bar only, not the in-page progress UI.
        downloadProgressContainer.visibility = View.GONE
        val resolvedProgress = progress
            ?: DownloadProgressTracker.progressFor(session.fileId)
            ?: 0
        bottomDownloadStatus.visibility = View.VISIBLE
        bottomDownloadStatusText.text = getString(
            R.string.detail_bottom_download_status,
            session.displayTitle,
            session.fileId,
            resolvedProgress
        )
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
        ActiveDownloadManager.release(message.fileId)
        DownloadProgressTracker.clear(message.fileId)
        currentDownload = null
        refreshBottomDownloadStatus()

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
                        trailerKey = null
                        trailerButton.isVisible = false
                        updateActionFocusWiring()
                        return@withContext
                    }
                    bindTmdbHeader(details)
                    bindDetailBackdrop(details)
                    bindMovieSections(details, info)
                    bindPromoBanner(details)
                    val key = PosterFetcher.trailerKey(details)
                    trailerKey = key
                    trailerButton.isVisible = !key.isNullOrBlank()
                    updateActionFocusWiring()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading TMDB metadata", e)
                withContext(Dispatchers.Main) {
                    applyPosterFallback()
                    clearDetailBackdrop()
                    clearMovieSections()
                    trailerKey = null
                    trailerButton.isVisible = false
                    updateActionFocusWiring()
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
        persistBackdropUrl(backdropUrl)
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

    private fun persistBackdropUrl(backdropUrl: String) {
        if (backdropUrl.isBlank() || message.backgroundImageUrl == backdropUrl) return
        message = message.copy(backgroundImageUrl = backdropUrl)
        lifecycleScope.launch(Dispatchers.IO) {
            AppDatabase.getDatabase(applicationContext).mediaMessageDao().insert(message)
            // Refresh history snapshot with backdrop so dashboard can use it.
            HistoryStore.recordVisit(applicationContext, message)
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

    private fun refreshStorageCards() {
        val adapter = settingsRowRecycler.adapter as? SettingCardAdapter ?: return
        val newItems = buildSettingsItems(isAutoPlayEnabled, bufferSizeThresholdMB, progressThreshold)
        adapter.setItems(newItems)
        syncStorageWarning()
    }

    private fun registerStorageReceiver() {
        if (storageReceiver != null) return
        storageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                refreshStorageCards()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        try { registerReceiver(storageReceiver, filter) } catch (_: Exception) { }
    }

    private fun unregisterStorageReceiver() {
        storageReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) { } }
        storageReceiver = null
    }

    private fun setupSettingsRow() {
        settingsRowRecycler.apply {
            layoutManager = LinearLayoutManager(this@MediaDetailsActivity, LinearLayoutManager.HORIZONTAL, false)
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
                    buildSettingsItems(autoPlay, bufferSizeMb, progressPercent)
                }.collect { items ->
                    settingsRowRecycler.adapter = SettingCardAdapter(items)
                    syncStorageWarning()
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
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        syncDownloadStateWithManager()
        loadSavedPlaybackPosition(rebindButtons = true)
        if (isLocalFilePlayable(message.localPath)) {
            lastPreviewPath = null
            updatePreviewIfAllowed(message.localPath, downloadComplete = isFullyDownloaded())
        }
        refreshStorageCards()
        syncStorageWarning()
    }

    private fun syncDownloadStateWithManager() {
        val active = ActiveDownloadManager.currentSession()
        when {
            isDownloading && active?.fileId != message.fileId -> {
                isDownloading = false
                activeDownloads.clear()
                currentDownload = null
                checkLocalFileAndUpdateUI()
            }
            !isDownloading && active?.fileId == message.fileId -> {
                isDownloading = true
                activeDownloads.add(message.fileId)
                applyActionButtonState(ActionButtonState.DOWNLOADING)
            }
        }
        refreshBottomDownloadStatus()
    }

    override fun onDestroy() {
        fileUpdateJob?.cancel()
        downloadProgressObserverJob?.cancel()
        unregisterStorageReceiver()
        stopPlayback()
        super.onDestroy()
    }
}