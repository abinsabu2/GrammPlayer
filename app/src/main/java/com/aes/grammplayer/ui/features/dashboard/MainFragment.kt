package com.aes.grammplayer.ui.features.dashboard

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.HeadersSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aes.grammplayer.config.ReviewModeHelper
import com.aes.grammplayer.ui.features.chats.ChatsGridActivity
import com.aes.grammplayer.ui.features.history.HistoryGridActivity
import com.aes.grammplayer.R
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.ActiveDownloadManager
import com.aes.grammplayer.helper.ApplicationHelper
import com.aes.grammplayer.helper.DashboardBackdropHelper
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.helper.DownloadProgressTracker
import com.aes.grammplayer.helper.DownloadingDashboardHelper
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.helper.GlideHelper
import com.aes.grammplayer.helper.HistoryHelper

import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.ui.features.details.MediaDetailsActivity

import com.bumptech.glide.Glide
import com.aes.grammplayer.ui.features.authentication.LoginActivity
import com.aes.grammplayer.ui.features.settings.SettingsActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private lateinit var loadingDialog: DialogHelper

    private lateinit var settingsDataStore: SettingsDataStore

    private lateinit var productLogo: ImageView

    private var welcomeTextView: TextView? = null
    private var welcomeSubtitleIndex = 0
    private val welcomeSubtitles = listOf(
        "How are you?",
        "Whats your plan for today?",
        "What are you going to watch today?",
        "New movies are out there, Please check your chats."
    )

    private var reviewMode = false

    private var rowsAdapter: ArrayObjectAdapter? = null
    private var inProgressListRow: DownloadingListRow? = null
    private var inProgressRowAdapter: ArrayObjectAdapter? = null
    private var inProgressHeader: DashboardHeaderItem? = null
    private var downloadingShowReady = false
    private val downloadingCardPresenter = DownloadingCardPresenter()
    private val continueWatchingCardPresenter = ContinueWatchingCardPresenter()
    private val inProgressRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply {
        shadowEnabled = false
    }
    // Keep old names as aliases for minimal diff compat
    private var downloadingListRow: DownloadingListRow?
        get() = inProgressListRow
        set(v) { inProgressListRow = v }
    private var downloadingRowAdapter: ArrayObjectAdapter?
        get() = inProgressRowAdapter
        set(v) { inProgressRowAdapter = v }
    private var downloadingHeader: DashboardHeaderItem?
        get() = inProgressHeader
        set(v) { inProgressHeader = v }
    private val downloadingRowPresenter: ListRowPresenter
        get() = inProgressRowPresenter
    private var downloadProgressJob: Job? = null
    private var chatsAdapter: ArrayObjectAdapter? = null
    private var storageReceiver: BroadcastReceiver? = null
    private var storageDashboardItem: DashboardItem? = null
    private var cachedContinueItem: ContinueWatchingItem? = null
    private var cachedPlaybackInfo: SettingsDataStore.PlaybackInfo? = null
    private var lastInProgressRefreshMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        makeBrowseChromeTransparent(view)
        welcomeTextView = activity?.findViewById(R.id.welcome_message)
        updateWelcomeMessage()
        registerStorageReceiver()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        loadingDialog = DialogHelper(requireActivity().supportFragmentManager)

        // Browse chrome — title, sidebar, brand badge.
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireActivity(), android.R.color.transparent)
        // Logo is now a fixed ImageView in activity_main.xml, pinned above the
        // sidebar — badgeDrawable floats in the shared title strip and can't
        // be reliably locked above "Chats", so we don't use it here.
        val logoDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.gp_logo_bk_bg)
        badgeDrawable = logoDrawable
        // Custom sidebar header design: icon + label, teal on focus.
        setHeaderPresenterSelector(
            ClassPresenterSelector().addClassPresenter(ListRow::class.java, DashboardHeaderPresenter())
        )
        setupEventListeners()
        lifecycleScope.launch {
            HistoryHelper.prepareSession(requireContext())
            reviewMode = ReviewModeHelper.isReviewMode(requireContext())
            loadRows()
            updateWelcomeMessage()
            observeDownloadProgress()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardBackdrop()
        refreshInProgressRow()
        updateStorageCard()
        updateWelcomeMessage()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val autoDelete = settingsDataStore.storageAutoDelete.first()
                val threshold = settingsDataStore.storageThresholdMb.first()
                if (autoDelete && ApplicationHelper.getInternalFreeBytes() < threshold.toLong() * 1024L * 1024L) {
                    com.aes.grammplayer.helper.StorageAutoManager.ensureFreeSpace(requireContext(), threshold)
                    if (isAdded) updateStorageCard()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        downloadProgressJob?.cancel()
        downloadProgressJob = null
        backdropImageView()?.let { GlideHelper.clear(it) }
        unregisterStorageReceiver()
        super.onDestroyView()
    }

    private fun buildShortStorageDesc(): String {
        val internalFree = ApplicationHelper.getInternalFreeBytes()
        val externalFree = ApplicationHelper.getExternalFreeBytes()
        val internalShort = ApplicationHelper.formatFreeBytes(internalFree)
        val externalShort = if (ApplicationHelper.isExternalStorageAvailable()) ApplicationHelper.formatFreeBytes(externalFree) else "—"
        return "Internal: $internalShort • External: $externalShort"
        // ponytail: short single line for hero card; use icons if needed
    }

    private fun updateStorageCard() {
        val adapter = chatsAdapter ?: return
        val old = storageDashboardItem ?: return
        val newDesc = buildShortStorageDesc()
        if (old.description == newDesc) return
        val updated = old.copy(description = newDesc)
        storageDashboardItem = updated
        val idx = adapter.indexOf(old)
        if (idx >= 0) adapter.replace(idx, updated)
    }

    private fun registerStorageReceiver() {
        if (storageReceiver != null) return
        storageReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateStorageCard()
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
        try {
            requireContext().registerReceiver(storageReceiver, filter)
        } catch (_: Exception) { }
    }

    private fun unregisterStorageReceiver() {
        storageReceiver?.let {
            try { requireContext().unregisterReceiver(it) } catch (_: Exception) { }
        }
        storageReceiver = null
    }

    private fun refreshDashboardBackdrop() {
        viewLifecycleOwner.lifecycleScope.launch {
            val backdropUrl = try {
                DashboardBackdropHelper.resolveLastItemBackdropUrl(requireContext())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resolve dashboard backdrop", e)
                null
            }
            if (!isAdded) return@launch
            if (backdropUrl.isNullOrBlank()) {
                applyDefaultBackdrop()
            } else {
                loadBackdropFromUrl(backdropUrl)
            }
        }
    }

    private fun backdropImageView(): ImageView? =
        activity?.findViewById(R.id.dashboard_backdrop)

    private fun applyDefaultBackdrop() {
        val imageView = backdropImageView() ?: return
        GlideHelper.clear(imageView)
        imageView.setImageResource(R.drawable.detail_back_drop)
    }

    private fun loadBackdropFromUrl(url: String) {
        val imageView = backdropImageView() ?: return
        try {
            Glide.with(imageView)
                .load(url)
                .centerCrop()
                .error(R.drawable.detail_back_drop)
                .into(imageView)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Skipping backdrop load — host context destroyed")
            applyDefaultBackdrop()
        }
    }

    private fun makeBrowseChromeTransparent(root: View) {
        root.setBackgroundResource(android.R.color.transparent)
        intArrayOf(
            androidx.leanback.R.id.browse_frame,
            androidx.leanback.R.id.browse_container_dock,
            androidx.leanback.R.id.browse_headers_dock,
            androidx.leanback.R.id.scale_frame
        ).forEach { viewId ->
            root.findViewById<View?>(viewId)?.setBackgroundResource(android.R.color.transparent)
        }
    }

    private fun updateWelcomeMessage() {
        val view = welcomeTextView ?: activity?.findViewById(R.id.welcome_message) ?: return
        if (welcomeTextView == null) welcomeTextView = view
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Evening"
        }
        lifecycleScope.launch {
            val telegramName = try {
                suspendCancellableCoroutine<String?> { cont ->
                    TelegramClientManager.client?.send(TdApi.GetMe()) { obj ->
                        if (obj is TdApi.User) cont.resume("${obj.firstName} ${obj.lastName}".trim().ifEmpty { obj.usernames?.activeUsernames?.firstOrNull() ?: "User" })
                        else cont.resume(null)
                    } ?: cont.resume(null)
                }
            } catch (_: Exception) { null } ?: "User"
            val subtitle = welcomeSubtitles[welcomeSubtitleIndex % welcomeSubtitles.size].also { welcomeSubtitleIndex++ }
            view.text = "$greeting $telegramName,\n$subtitle"
            view.visibility = View.VISIBLE
        }
    }

    override fun onCreateHeadersSupportFragment(): HeadersSupportFragment {
        return DashboardHeadersSupportFragment()
    }

    private fun loadRows() {
        // ZOOM_FACTOR_NONE stops Leanback from scaling the whole row on focus —
        // our cards handle their own focus state via card_background_selector,
        // so we don't want the row itself growing/shifting on top of that.
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply {
            shadowEnabled = false
        }
        val rowPresenterSelector = ClassPresenterSelector().apply {
            addClassPresenter(ListRow::class.java, listRowPresenter)
            addClassPresenter(DownloadingListRow::class.java, inProgressRowPresenter)
        }
        rowsAdapter = ArrayObjectAdapter(rowPresenterSelector)

        // --- Chats row: hero cards for Chats + History ---
        val chatsHeader = DashboardHeaderItem(1, "Chats", R.drawable.ic_chat)
        val chatsAdapter = ArrayObjectAdapter(HeroCardPresenter())
        chatsAdapter.add(
            DashboardItem(
                id = "chats",
                iconRes = R.drawable.ic_chat,
                eyebrow = "Chat Management",
                title = "Chats",
                description = "Your Conversations",
                actionLabel = "Open Chats",
                isHero = true
            )
        )
        chatsAdapter.add(
            DashboardItem(
                id = "history",
                iconRes = R.drawable.ic_history,
                eyebrow = "History Management",
                title = "History",
                description = "Your History",
                actionLabel = "Open History",
                isHero = true
            )
        )
        val storageItem = DashboardItem(
            id = "storage_manager",
            iconRes = R.drawable.ic_storage,
            eyebrow = "Storage",
            title = "Storage Manager",
            description = buildShortStorageDesc(),
            actionLabel = "Clear Cache",
            isHero = true
        )
        chatsAdapter.add(storageItem)
        this.chatsAdapter = chatsAdapter
        storageDashboardItem = storageItem
        rowsAdapter!!.add(ListRow(chatsHeader, chatsAdapter))

        // --- Preferences row: icon cards (trimmed for store review accounts) ---
        val preferenceItems = listOf(
            DashboardItem("clear_history", R.drawable.ic_history, "Clear History"),
            DashboardItem("settings", R.drawable.ic_settings, "Settings"),
            DashboardItem("close", R.drawable.ic_refresh, "Close"),
            DashboardItem("logout", R.drawable.ic_logout, "Logout")
        ).filter { ReviewModeHelper.isDashboardItemVisible(it.id, reviewMode) }

        if (preferenceItems.isNotEmpty()) {
            val settingsHeader = DashboardHeaderItem(2, "Preferences", R.drawable.ic_settings)
            val settingsRowAdapter = ArrayObjectAdapter(IconCardPresenter())
            preferenceItems.forEach { settingsRowAdapter.add(it) }
            rowsAdapter!!.add(ListRow(settingsHeader, settingsRowAdapter))
        }

        adapter = rowsAdapter
        refreshInProgressRow()
    }

    private fun observeDownloadProgress() {
        downloadProgressJob?.cancel()
        downloadProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadProgressTracker.updates.collect { fileId ->
                    val activeFileId = ActiveDownloadManager.currentSession()?.fileId
                    when {
                        fileId == activeFileId -> {
                            // ponytail: adapter clear only when ids/positions change; progress ticks update views in-place to avoid Glide unbind/reload ceiling.
                            if (!updateVisibleDownloadingProgressByFileId(fileId)) {
                                refreshInProgressRow()
                            } else {
                                // also refresh continue watching progress in-place if row exists (no adapter clear)
                                cachedContinueItem?.let { item ->
                                    updateContinueWatchingProgressInTree(view ?: return@collect, item.message.fileId, item.positionMs, item.durationMs)
                                }
                            }
                        }
                        activeFileId == null -> refreshInProgressRow()
                        ActiveDownloadManager.wasRecentlyCompleted(fileId) -> refreshInProgressRow()
                    }
                }
            }
        }
    }

    private fun refreshDownloadingRow() {
        refreshInProgressRow()
    }

    private fun refreshDownloadingCard() {
        refreshInProgressRow()
    }

    private fun refreshInProgressRow() {
        val adapter = rowsAdapter ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val downloadItem = try {
                DownloadingDashboardHelper.loadDashboardDownloadItem(requireContext())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dashboard download item", e)
                null
            }
            val continueItem = try {
                loadContinueWatchingItem()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load continue watching item", e)
                null
            }
            if (!isAdded) return@launch

            // Handle download Ready vs InProgress for header styling
            when (downloadItem) {
                is DownloadingDashboardHelper.DashboardDownloadItem.Ready -> {
                    if (continueItem == null) {
                        ensureInProgressRow(adapter, listOf(downloadItem.message))
                        showDownloadingReadyState(downloadItem.message)
                        ActiveDownloadManager.clearCompletedSession()
                        // auto-check storage after download complete
                        viewLifecycleOwner.lifecycleScope.launch {
                            try {
                                val autoDelete = settingsDataStore.storageAutoDelete.first()
                                val threshold = settingsDataStore.storageThresholdMb.first()
                                if (autoDelete && ApplicationHelper.getInternalFreeBytes() < threshold.toLong() * 1024L * 1024L) {
                                    com.aes.grammplayer.helper.StorageAutoManager.ensureFreeSpace(requireContext(), threshold)
                                    if (isAdded) updateStorageCard()
                                }
                            } catch (_: Exception) {}
                        }
                        return@launch
                    } else {
                        resetDownloadingReadyState()
                    }
                }
                is DownloadingDashboardHelper.DashboardDownloadItem.InProgress -> {
                    resetDownloadingReadyState()
                    updateVisibleDownloadingProgress(downloadItem.message)
                }
                null -> {
                    if (downloadingShowReady && continueItem == null) {
                        // keep ready row until cleared; else remove if no continue item
                    } else if (downloadItem == null && continueItem == null) {
                        if (!downloadingShowReady) {
                            removeInProgressRow(adapter)
                        } else if (continueItem == null) {
                            // if ready but no continue and no download, let next download clear it
                        }
                        if (continueItem == null) {
                            // no items at all
                        }
                    }
                }
            }

            val items = buildList<Any> {
                when (downloadItem) {
                    is DownloadingDashboardHelper.DashboardDownloadItem.InProgress -> add(downloadItem.message)
                    is DownloadingDashboardHelper.DashboardDownloadItem.Ready -> add(downloadItem.message)
                    null -> {}
                }
                continueItem?.let { add(it) }
            }

            // Prevent duplicate continue item if same as download (same fileId/messageId)
            val deduped = dedupeInProgressItems(items)

            if (deduped.isEmpty()) {
                if (!downloadingShowReady) {
                    removeInProgressRow(adapter)
                } else if (downloadItem == null) {
                    // keep ready until new download or clear
                    // if we have no continue and ready was showing, remove when no download
                    removeInProgressRow(adapter)
                }
            } else {
                ensureInProgressRow(adapter, deduped)
                // re-apply ready state styling if needed
                if (downloadItem is DownloadingDashboardHelper.DashboardDownloadItem.Ready && deduped.size == 1) {
                    showDownloadingReadyState(downloadItem.message)
                    ActiveDownloadManager.clearCompletedSession()
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            val autoDelete = settingsDataStore.storageAutoDelete.first()
                            val threshold = settingsDataStore.storageThresholdMb.first()
                            if (autoDelete && ApplicationHelper.getInternalFreeBytes() < threshold.toLong() * 1024L * 1024L) {
                                com.aes.grammplayer.helper.StorageAutoManager.ensureFreeSpace(requireContext(), threshold)
                                if (isAdded) updateStorageCard()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun dedupeInProgressItems(items: List<Any>): List<Any> {
        if (items.size < 2) return items
        val downloadMsg = items[0] as? MediaMessage ?: return items
        val continueItem = items[1] as? ContinueWatchingItem ?: return items
        if (downloadMsg.fileId != 0 && downloadMsg.fileId == continueItem.message.fileId) return listOf(downloadMsg)
        if (downloadMsg.id != 0L && downloadMsg.id == continueItem.message.id) return listOf(downloadMsg)
        return items.take(2)
    }

    private suspend fun loadContinueWatchingItem(): ContinueWatchingItem? = withContext(Dispatchers.IO) {
        val info = settingsDataStore.getLastPlaybackInfo() ?: run {
            cachedPlaybackInfo = null
            cachedContinueItem = null
            return@withContext null
        }
        if (info == cachedPlaybackInfo && cachedContinueItem != null) {
            return@withContext cachedContinueItem
        }
        val pos = info.positionMs
        val dur = info.durationMs
        if (pos < SettingsDataStore.MIN_RESUME_POSITION_MS) {
            cachedPlaybackInfo = null
            cachedContinueItem = null
            return@withContext null
        }
        if (dur > 0L && dur - pos < SettingsDataStore.END_RESUME_CLEAR_MS) {
            cachedPlaybackInfo = null
            cachedContinueItem = null
            return@withContext null
        }
        val page = try {
            com.aes.grammplayer.history.HistoryStore.loadPage(requireContext(), 0, com.aes.grammplayer.history.HistoryStore.MAX_ENTRIES)
        } catch (_: Exception) { null } ?: return@withContext null
        var hist = page.items.find { it.message.id == info.messageId }
        if (hist == null) {
            // Try to resolve from DB if not in history yet
            // ponytail: DB removed — fallback to HistoryStore only; live TMDB fetch not needed for continue
}
        val target = hist ?: run {
            cachedPlaybackInfo = null
            cachedContinueItem = null
            return@withContext null
        }
        val result = ContinueWatchingItem(
            message = target.message,
            positionMs = pos,
            durationMs = dur,
            historyItem = target
        )
        cachedPlaybackInfo = info
        cachedContinueItem = result
        result
    }

    private fun ensureInProgressRow(adapter: ArrayObjectAdapter, items: List<Any>) {
        val capped = items.take(2)
        val hasDownload = capped.any { it is MediaMessage }
        val hasContinue = capped.any { it is ContinueWatchingItem }
        val headerTitle = when {
            hasDownload && hasContinue -> getString(R.string.in_progress_combined)
            hasDownload -> if (downloadingShowReady) getString(R.string.dashboard_downloading_row_ready) else getString(R.string.dashboard_downloading_row_title)
            hasContinue -> getString(R.string.in_progress_continue_watching)
            else -> getString(R.string.dashboard_downloading_row_title)
        }
        val showProgress = capped.any { it is MediaMessage && DownloadProgressTracker.isDownloading(it.fileId) || it is MediaMessage && it.isDownloadActive }

        if (inProgressListRow == null) {
            inProgressHeader = DashboardHeaderItem(
                IN_PROGRESS_ROW_ID,
                headerTitle,
                R.drawable.ic_download,
                showProgressIcon = showProgress && !downloadingShowReady
            ).apply {
                displayName = headerTitle
                isReady = downloadingShowReady
            }
            val selector = ClassPresenterSelector().apply {
                addClassPresenter(MediaMessage::class.java, downloadingCardPresenter)
                addClassPresenter(ContinueWatchingItem::class.java, continueWatchingCardPresenter)
                addClassPresenter(com.aes.grammplayer.ui.features.history.HistoryItem::class.java, continueWatchingCardPresenter)
            }
            inProgressRowAdapter = ArrayObjectAdapter(selector).apply {
                capped.forEach { add(it) }
            }
            inProgressListRow = DownloadingListRow(
                IN_PROGRESS_ROW_ID,
                inProgressHeader!!,
                inProgressRowAdapter!!
            )
            adapter.add(IN_PROGRESS_ROW_INDEX, inProgressListRow!!)
            lastInProgressRefreshMs = android.os.SystemClock.elapsedRealtime()
        } else {
            val rowAdapter = inProgressRowAdapter ?: return
            val existing: List<Any> = (0 until rowAdapter.size()).mapNotNull { rowAdapter.get(it) as? Any }
            // ponytail: adapter clear only when ids/positions change; progress ticks update views in-place to avoid Glide unbind/reload ceiling.
            if (areInProgressItemsEqual(existing, capped)) {
                var headerChanged = false
                if (inProgressHeader?.displayName != headerTitle) {
                    inProgressHeader?.displayName = headerTitle
                    headerChanged = true
                }
                if (inProgressHeader?.isReady != downloadingShowReady) {
                    inProgressHeader?.isReady = downloadingShowReady
                    headerChanged = true
                }
                updateVisibleInProgressProgress(capped)
                if (headerChanged) notifyInProgressRowChanged()
                // debounce: no adapter change, still update timestamp for progress ticks
                lastInProgressRefreshMs = android.os.SystemClock.elapsedRealtime()
                return
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastInProgressRefreshMs < 200) {
                // debounce rapid refresh when items changed very quickly; still proceed if header title changes
                // items have changed, so don't skip — just update timestamp and continue
            }
            inProgressHeader?.displayName = headerTitle
            inProgressHeader?.isReady = downloadingShowReady
            rowAdapter.clear()
            capped.forEach { rowAdapter.add(it) }
            notifyInProgressRowChanged()
            lastInProgressRefreshMs = now
        }
    }

    private fun areInProgressItemsEqual(existing: List<Any>, new: List<Any>): Boolean {
        if (existing.size != new.size) return false
        for (i in existing.indices) {
            val a = existing[i]
            val b = new[i]
            if (a::class != b::class) return false
            when {
                a is MediaMessage && b is MediaMessage -> if (a.fileId != b.fileId || a.id != b.id) return false
                a is ContinueWatchingItem && b is ContinueWatchingItem -> if (a.message.id != b.message.id || a.message.fileId != b.message.fileId || a.positionMs != b.positionMs || a.durationMs != b.durationMs) return false
                a is com.aes.grammplayer.ui.features.history.HistoryItem && b is com.aes.grammplayer.ui.features.history.HistoryItem -> if (a.message.id != b.message.id || a.message.fileId != b.message.fileId) return false
                else -> return false
            }
        }
        return true
    }

    private fun updateVisibleInProgressProgress(items: List<Any>) {
        val root = view ?: return
        items.forEach { item ->
            when (item) {
                is MediaMessage -> updateDownloadingProgressInTree(root, item.fileId, item)
                is ContinueWatchingItem -> updateContinueWatchingProgressInTree(root, item.message.fileId, item.positionMs, item.durationMs)
                is com.aes.grammplayer.ui.features.history.HistoryItem -> updateContinueWatchingProgressInTree(root, item.message.fileId, 0L, 0L)
            }
        }
    }

    private fun updateContinueWatchingProgressInTree(root: View, fileId: Int, positionMs: Long, durationMs: Long): Boolean {
        if (root.getTag(R.id.grid_card_file_id) == fileId) {
            ContinueWatchingCardPresenter.bindProgress(root, positionMs, durationMs)
            return true
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                if (updateContinueWatchingProgressInTree(root.getChildAt(index), fileId, positionMs, durationMs)) return true
            }
        }
        return false
    }

    private fun updateVisibleDownloadingProgressByFileId(fileId: Int): Boolean {
        val root = view ?: return false
        // try downloading card first
        if (updateDownloadingProgressByFileIdInTree(root, fileId)) return true
        // fallback: if we have a cached download message in adapter, use it
        val adapter = inProgressRowAdapter
        if (adapter != null) {
            for (i in 0 until adapter.size()) {
                val item = adapter.get(i)
                if (item is MediaMessage && item.fileId == fileId) {
                    return updateDownloadingProgressInTree(root, fileId, item)
                }
            }
        }
        // also try to load current session message lightweight without DB if possible
        return false
    }

    private fun updateDownloadingProgressByFileIdInTree(root: View, fileId: Int): Boolean {
        if (root.getTag(R.id.grid_card_file_id) == fileId) {
            val progress = DownloadProgressTracker.progressFor(fileId)
            if (progress != null) {
                // use presenter helper via synthetic message-like path: directly update banner/progress
                val banner = root.findViewById<TextView>(R.id.banner)
                val progressBar = root.findViewById<android.widget.ProgressBar>(R.id.download_progress_bar)
                val last = root.getTag(R.id.grid_download_progress) as? Int
                if (last != progress) {
                    root.setTag(R.id.grid_download_progress, progress)
                    banner?.apply {
                        visibility = View.VISIBLE
                        text = com.aes.grammplayer.helper.FormatHelper.formatGridDownloadLabel(progress)
                        setTextColor(ContextCompat.getColor(context, R.color.downloading_border))
                    }
                    progressBar?.apply {
                        visibility = View.VISIBLE
                        this.progress = progress
                    }
                }
            } else {
                // fallback to ready check
                val session = ActiveDownloadManager.currentSession()
                if (session?.fileId == fileId) {
                    // still downloading but progress not yet tracked; keep current UI
                }
            }
            return true
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                if (updateDownloadingProgressByFileIdInTree(root.getChildAt(index), fileId)) return true
            }
        }
        return false
    }

    private fun ensureDownloadingRow(adapter: ArrayObjectAdapter, message: MediaMessage) {
        ensureInProgressRow(adapter, listOf(message))
    }

    private fun showDownloadingReadyState(message: MediaMessage) {
        downloadingRowAdapter?.replace(0, message)
        if (!downloadingShowReady) {
            downloadingShowReady = true
            downloadingHeader?.displayName = getString(R.string.dashboard_downloading_row_ready)
            downloadingHeader?.isReady = true
            notifyDownloadingRowChanged()
            Log.i(TAG, "Download complete — dashboard row now Ready for fileId=${message.fileId}")
        }
        updateVisibleDownloadingReady(message)
    }

    private fun resetDownloadingReadyState() {
        if (!downloadingShowReady) return
        downloadingShowReady = false
        downloadingHeader?.displayName = getString(R.string.dashboard_downloading_row_title)
        downloadingHeader?.isReady = false
    }

    private fun notifyDownloadingRowChanged() {
        notifyInProgressRowChanged()
    }

    private fun notifyInProgressRowChanged() {
        val adapter = rowsAdapter ?: return
        val row = inProgressListRow ?: return
        val index = adapter.indexOf(row)
        if (index >= 0) {
            adapter.notifyArrayItemRangeChanged(index, 1)
        }
    }

    private fun updateVisibleDownloadingReady(message: MediaMessage): Boolean {
        val root = view ?: return false
        return updateDownloadingReadyInTree(root, message.fileId)
    }

    private fun updateDownloadingReadyInTree(root: View, fileId: Int): Boolean {
        if (root.getTag(R.id.grid_card_file_id) == fileId) {
            DownloadingCardPresenter.bindReady(root)
            return true
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                if (updateDownloadingReadyInTree(root.getChildAt(index), fileId)) {
                    return true
                }
            }
        }
        return false
    }

    private fun updateVisibleDownloadingProgress(message: MediaMessage): Boolean {
        val root = view ?: return false
        return updateDownloadingProgressInTree(root, message.fileId, message)
    }

    private fun updateDownloadingProgressInTree(root: View, fileId: Int, message: MediaMessage): Boolean {
        if (root.getTag(R.id.grid_card_file_id) == fileId) {
            DownloadingCardPresenter.bindProgress(root, message)
            return true
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                if (updateDownloadingProgressInTree(root.getChildAt(index), fileId, message)) {
                    return true
                }
            }
        }
        return false
    }

    private fun removeDownloadingRow(adapter: ArrayObjectAdapter) {
        removeInProgressRow(adapter)
    }

    private fun removeInProgressRow(adapter: ArrayObjectAdapter) {
        val row = inProgressListRow ?: return
        val index = adapter.indexOf(row)
        if (index >= 0) {
            adapter.removeItems(index, 1)
        }
        inProgressListRow = null
        inProgressRowAdapter = null
        inProgressHeader = null
        downloadingShowReady = false
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_history)
            .setMessage(R.string.clear_history_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        loadingDialog.runWithLoading("Clearing history...") {
                            HistoryHelper.clear(requireContext())
                        }
                        if (isAdded) {
                            Toast.makeText(requireContext(), R.string.clear_history_done, Toast.LENGTH_SHORT)
                                .show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Clear history failed", e)
                        if (isAdded) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to clear history",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            when (item) {
                is ContinueWatchingItem -> {
                    startActivity(MediaDetailsActivity.newIntent(requireContext(), item.message))
                }
                is com.aes.grammplayer.ui.features.history.HistoryItem -> {
                    startActivity(MediaDetailsActivity.newIntent(requireContext(), item.message))
                }
                is MediaMessage -> {
                    startActivity(MediaDetailsActivity.newIntent(requireContext(), item))
                }
                is DashboardItem -> {
                    if (reviewMode && ReviewModeHelper.isDestructiveDashboardAction(item.id)) {
                        return
                    }
                    when (item.id) {
                        "storage_manager" -> {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    val result = loadingDialog.runWithLoading("Clearing cache...") {
                                        withContext(Dispatchers.IO) {
                                            val r = TelegramClientManager.clearDownloadCache(requireContext())
                                            SettingsDataStore(requireContext()).clearPlaybackPosition()
                                            r
                                        }
                                    }
                                    if (!isAdded) return@launch
                                    Toast.makeText(requireContext(), "Cleared ${result.count} files • ${FormatHelper.formatBytes(result.bytes)} freed", Toast.LENGTH_LONG).show()
                                    updateStorageCard()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Clear cache failed", e)
                                    if (isAdded) Toast.makeText(requireContext(), "Failed to clear cache", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        "clear_history" -> confirmClearHistory()
                        "settings" -> {
                            val intent = Intent(activity, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                        "close" -> {
                            TelegramClientManager.clearDownloadedFiles()
                            requireActivity().finish()
                        }
                        "chats" -> {
                            val intent = Intent(activity, ChatsGridActivity::class.java).apply {
                                putExtra(NavigationExtras.CHAT_ID, 1000L)
                                putExtra(NavigationExtras.CHAT_TITLE, "Chats")
                            }
                            startActivity(intent)
                        }
                        "history" -> {
                            startActivity(Intent(activity, HistoryGridActivity::class.java))
                        }
                        "logout" -> {
                            lifecycleScope.launch {
                                try {
                                    loadingDialog.runWithLoading("Logging out") { update ->
                                        // Delete this login's history file before leaving the session.
                                        HistoryHelper.clear(requireContext())
                                        update("Clearing history…")
                                        TelegramClientManager.logOut()
                                        update("Logged out")
                                        delay(500.milliseconds)
                                    }
                                    val intent = Intent(activity, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    settingsDataStore.setTestMode(false)
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Logout failed: ${e.message}", e)
                                }
                            }
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Clicked on: ${item.title}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private inner class ItemViewSelectedListener : OnItemViewSelectedListener {
        override fun onItemSelected(
            itemViewHolder: Presenter.ViewHolder?, item: Any?,
            rowViewHolder: RowPresenter.ViewHolder, row: Row
        ) {
            when (item) {
                is DashboardItem -> {
                    when (item.id) {
                        "chats" -> {
                            // Reserved for selection-based preview/behavior if needed later.
                        }
                    }
                }
            }
        }
    }

    /**
     * Renders the large hero card: icon, eyebrow label, title, description, action button.
     * Used for both the "Chats" and "History" row items.
     */
    private inner class HeroCardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_hero, parent, false)
            // Must be focusable, or the card can never take D-pad focus and
            // onItemClicked/onItemSelected will never fire for it.
            view.makeFocusableForTv()
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val d = item as DashboardItem
            val view = viewHolder.view
            view.findViewById<ImageView>(R.id.icon).setImageResource(d.iconRes)
            view.findViewById<TextView>(R.id.eyebrow).text = d.eyebrow
            view.findViewById<TextView>(R.id.title).text = d.title
            view.findViewById<TextView>(R.id.description).text = d.description
            view.findViewById<TextView>(R.id.actionButton).text = d.actionLabel
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    /**
     * Renders the small square icon cards used for the Preferences row
     * (Clear Cache, Settings, Refresh Data, Close, Logout).
     */
    private inner class IconCardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_icon, parent, false)
            view.makeFocusableForTv()
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val d = item as DashboardItem
            val view = viewHolder.view
            view.findViewById<ImageView>(R.id.icon).setImageResource(d.iconRes)
            view.findViewById<TextView>(R.id.title).text = d.title
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    companion object {
        private val TAG = "MainFragment"
        private const val DOWNLOADING_ROW_INDEX = 0
        private const val DOWNLOADING_ROW_ID = 3L
        private const val IN_PROGRESS_ROW_INDEX = DOWNLOADING_ROW_INDEX
        private const val IN_PROGRESS_ROW_ID = DOWNLOADING_ROW_ID
    }
}

/**
 * Sidebar headers list, with extra top padding reserved for the fixed
 * product logo overlaid in activity_main.xml — otherwise the logo would
 * sit on top of the first header row ("Chats").
 */
class DashboardHeadersSupportFragment : HeadersSupportFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val topPaddingPx = (80 * resources.displayMetrics.density).toInt()
        view.setPadding(view.paddingLeft, topPaddingPx, view.paddingRight, view.paddingBottom)
        view.setBackgroundResource(android.R.color.transparent)
        listOf(
            androidx.leanback.R.id.browse_headers_root,
            androidx.leanback.R.id.browse_headers
        ).forEach { viewId ->
            view.findViewById<View>(viewId)?.apply {
                setBackgroundResource(android.R.color.transparent)
                backgroundTintList = null
            }
        }
        view.findViewById<View>(androidx.leanback.R.id.fade_out_edge)?.visibility = View.GONE
    }
}