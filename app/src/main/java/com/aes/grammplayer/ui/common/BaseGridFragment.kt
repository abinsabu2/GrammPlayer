package com.aes.grammplayer.ui.common

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DimenRes
import androidx.annotation.IntegerRes
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.VerticalGridPresenter
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.DownloadProgressTracker
import com.aes.grammplayer.helper.GridLayoutHelper
import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.ui.features.history.HistoryItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class BaseGridFragment : VerticalGridSupportFragment() {

    protected lateinit var gridAdapter: ArrayObjectAdapter

    @IntegerRes
    protected open val gridColumnCountResId: Int = R.integer.grid_column_count_media

    @DimenRes
    protected open val gridCardWidthDimen: Int = R.dimen.grid_card_media_width

    @DimenRes
    protected open val gridCardMarginDimen: Int = R.dimen.grid_card_margin

    private var downloadProgressJob: Job? = null

    // ==================== Lazy paging ====================

    /** True while a page request is in flight; blocks duplicate triggers. */
    protected var isPageLoading = false

    /** True once the data source reports no further pages. */
    protected var endReached = false

    /** Last selected position tracked to restore after adapter clear; prevents snap to 0. */
    protected var lastSelectedPosition: Int = 0

    /**
     * Called when D-pad selection comes within [PAGE_PREFETCH_DISTANCE] items of the
     * grid end and more pages remain. Subclasses override to fetch + append the next page.
     */
    protected open fun loadNextPage() {}

    /** Appends a page of items without disturbing existing cards or focus. */
    protected fun appendItems(items: List<Any>) {
        if (items.isEmpty()) return
        gridAdapter.addAll(gridAdapter.size(), items)
    }

    private fun installPagingTrigger() {
        setOnItemViewSelectedListener { _, item, _, _ ->
            if (item != null) {
                val idx = gridAdapter.indexOf(item)
                if (idx >= 0) lastSelectedPosition = idx
            }
            if (item == null || isPageLoading || endReached) return@setOnItemViewSelectedListener
            val index = gridAdapter.indexOf(item)
            if (index >= 0 && index >= gridAdapter.size() - PAGE_PREFETCH_DISTANCE) {
                loadNextPage()
            }
        }
    }

    protected abstract fun createItemPresenter(): Presenter

    protected fun setupGrid() {
        val gridPresenter = VerticalGridPresenter()
        gridPresenter.numberOfColumns = resolveGridColumnCount()
        setGridPresenter(gridPresenter)
        gridAdapter = ArrayObjectAdapter(createItemPresenter())
        adapter = gridAdapter
        installPagingTrigger()
    }

    protected fun resolveGridColumnCount(): Int {
        val preferred = resources.getInteger(gridColumnCountResId)
        return GridLayoutHelper.resolveColumnCount(
            resources = resources,
            preferredColumns = preferred,
            cardWidthDimen = gridCardWidthDimen,
            cardMarginDimen = gridCardMarginDimen
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeDownloadProgress()
    }

    override fun onDestroyView() {
        downloadProgressJob?.cancel()
        downloadProgressJob = null
        super.onDestroyView()
    }

    private fun observeDownloadProgress() {
        downloadProgressJob?.cancel()
        downloadProgressJob = viewLifecycleOwner.lifecycleScope.launch {
            DownloadProgressTracker.updates.collect { fileId ->
                refreshCardForFileId(fileId)
            }
        }
    }

    protected fun refreshCardForFileId(fileId: Int) {
        if (fileId == 0 || !::gridAdapter.isInitialized) return
        val item = findAdapterItemForFileId(fileId) ?: return
        val root = view ?: return
        if (updateDownloadLabelInTree(root, fileId, item)) return
        // Off-screen cards pick up the latest label the next time they bind.
    }

    private fun findAdapterItemForFileId(fileId: Int): Any? {
        for (index in 0 until gridAdapter.size()) {
            val item = gridAdapter.get(index)
            if (fileIdFromItem(item) == fileId) return item
        }
        return null
    }

    private fun updateDownloadLabelInTree(root: View, fileId: Int, item: Any): Boolean {
        if (root.getTag(R.id.grid_card_file_id) == fileId) {
            GridDownloadLabelBinder.update(root, item)
            return true
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) {
                if (updateDownloadLabelInTree(root.getChildAt(index), fileId, item)) {
                    return true
                }
            }
        }
        return false
    }

    protected fun refreshAllCards() {
        if (::gridAdapter.isInitialized && gridAdapter.size() > 0) {
            gridAdapter.notifyItemRangeChanged(0, gridAdapter.size())
        }
    }

    private fun fileIdFromItem(item: Any?): Int = when (item) {
        is MediaMessage -> item.fileId
        is HistoryItem -> item.message.fileId
        else -> 0
    }

    protected val chatId: Long
        get() = arguments?.getLong(NavigationExtras.CHAT_ID) ?: 0L

    protected val chatTitle: String
        get() = arguments?.getString(NavigationExtras.CHAT_TITLE) ?: ""

    companion object {
        /** Trigger next page when selection is within this many items of the end. */
        private const val PAGE_PREFETCH_DISTANCE = 10

        fun buildChatArgs(chatId: Long, chatTitle: String): Bundle =
            Bundle().apply {
                putLong(NavigationExtras.CHAT_ID, chatId)
                putString(NavigationExtras.CHAT_TITLE, chatTitle)
            }
    }
}