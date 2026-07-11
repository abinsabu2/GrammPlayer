package com.aes.grammplayer.ui.features.dashboard

import android.app.AlertDialog
import android.content.Intent
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
import com.aes.grammplayer.helper.DashboardBackdropHelper
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.helper.DownloadProgressTracker
import com.aes.grammplayer.helper.DownloadingDashboardHelper
import com.aes.grammplayer.helper.HistoryHelper
import com.aes.grammplayer.helper.GlideHelper

import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.ui.features.details.MediaDetailsActivity

import com.bumptech.glide.Glide
import com.aes.grammplayer.ui.features.authentication.LoginActivity
import com.aes.grammplayer.ui.features.settings.SettingsActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private lateinit var loadingDialog: DialogHelper

    private lateinit var settingsDataStore: SettingsDataStore

    private lateinit var productLogo: ImageView

    private var reviewMode = false

    private var rowsAdapter: ArrayObjectAdapter? = null
    private var downloadingListRow: DownloadingListRow? = null
    private var downloadingRowAdapter: ArrayObjectAdapter? = null
    private var downloadingHeader: DashboardHeaderItem? = null
    private var downloadingShowReady = false
    private val downloadingCardPresenter = DownloadingCardPresenter()
    private val downloadingRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply {
        shadowEnabled = false
    }
    private var downloadProgressJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        makeBrowseChromeTransparent(view)
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
            observeDownloadProgress()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboardBackdrop()
        refreshDownloadingRow()
    }

    override fun onDestroyView() {
        downloadProgressJob?.cancel()
        downloadProgressJob = null
        backdropImageView()?.let { GlideHelper.clear(it) }
        super.onDestroyView()
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
            addClassPresenter(DownloadingListRow::class.java, downloadingRowPresenter)
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
        rowsAdapter!!.add(ListRow(chatsHeader, chatsAdapter))

        // --- Preferences row: icon cards (trimmed for store review accounts) ---
        val preferenceItems = listOf(
            DashboardItem("clear_cache", R.drawable.ic_clear_cache, "Clear Cache"),
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
        refreshDownloadingRow()
    }

    private fun observeDownloadProgress() {
        downloadProgressJob?.cancel()
        downloadProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                DownloadProgressTracker.updates.collect { fileId ->
                    val activeFileId = ActiveDownloadManager.currentSession()?.fileId
                    when {
                        fileId == activeFileId -> refreshDownloadingCard()
                        activeFileId == null -> refreshDownloadingRow()
                        ActiveDownloadManager.wasRecentlyCompleted(fileId) -> refreshDownloadingRow()
                    }
                }
            }
        }
    }

    private fun refreshDownloadingRow() {
        val adapter = rowsAdapter ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val item = try {
                DownloadingDashboardHelper.loadDashboardDownloadItem(requireContext())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load dashboard download item", e)
                null
            }
            if (!isAdded) return@launch

            when (item) {
                null -> {
                    if (!downloadingShowReady) {
                        removeDownloadingRow(adapter)
                    }
                }
                is DownloadingDashboardHelper.DashboardDownloadItem.Ready -> {
                    ensureDownloadingRow(adapter, item.message)
                    showDownloadingReadyState(item.message)
                    ActiveDownloadManager.clearCompletedSession()
                }
                is DownloadingDashboardHelper.DashboardDownloadItem.InProgress -> {
                    resetDownloadingReadyState()
                    ensureDownloadingRow(adapter, item.message)
                    updateVisibleDownloadingProgress(item.message)
                }
            }
        }
    }

    private fun refreshDownloadingCard() {
        refreshDownloadingRow()
    }

    private fun ensureDownloadingRow(adapter: ArrayObjectAdapter, message: MediaMessage) {
        val headerTitle = if (downloadingShowReady) {
            getString(R.string.dashboard_downloading_row_ready)
        } else {
            getString(R.string.dashboard_downloading_row_title)
        }

        if (downloadingListRow == null) {
            downloadingHeader = DashboardHeaderItem(
                DOWNLOADING_ROW_ID,
                headerTitle,
                R.drawable.ic_download,
                showProgressIcon = !downloadingShowReady
            ).apply {
                displayName = headerTitle
                isReady = downloadingShowReady
            }
            downloadingRowAdapter = ArrayObjectAdapter(downloadingCardPresenter).apply {
                add(message)
            }
            downloadingListRow = DownloadingListRow(
                DOWNLOADING_ROW_ID,
                downloadingHeader!!,
                downloadingRowAdapter!!
            )
            adapter.add(DOWNLOADING_ROW_INDEX, downloadingListRow!!)
        } else {
            downloadingRowAdapter?.replace(0, message)
        }
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
        val adapter = rowsAdapter ?: return
        val row = downloadingListRow ?: return
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
        val row = downloadingListRow ?: return
        val index = adapter.indexOf(row)
        if (index >= 0) {
            adapter.removeItems(index, 1)
        }
        downloadingListRow = null
        downloadingRowAdapter = null
        downloadingHeader = null
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
                is MediaMessage -> {
                    startActivity(MediaDetailsActivity.newIntent(requireContext(), item))
                }
                is DashboardItem -> {
                    if (reviewMode && ReviewModeHelper.isDestructiveDashboardAction(item.id)) {
                        return
                    }
                    when (item.id) {
                        "clear_cache" -> {
                            viewLifecycleOwner.lifecycleScope.launch {
                                try {
                                    val deletedCount = loadingDialog.runWithLoading("Clearing cache...") {
                                        withContext(Dispatchers.IO) {
                                            TelegramClientManager.clearDownloadCache(requireContext())
                                        }
                                    }
                                    if (!isAdded) return@launch
                                    val cacheClearText =
                                        "Cleared $deletedCount downloaded files from cache"
                                    Toast.makeText(requireContext(), cacheClearText, Toast.LENGTH_SHORT)
                                        .show()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Clear cache failed", e)
                                    if (isAdded) {
                                        Toast.makeText(
                                            requireContext(),
                                            "Failed to clear cache",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
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