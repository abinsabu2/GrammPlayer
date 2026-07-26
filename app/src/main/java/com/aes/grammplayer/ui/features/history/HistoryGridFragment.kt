package com.aes.grammplayer.ui.features.history

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.history.HistoryStore
import com.aes.grammplayer.provider.HistoryDataProvider
import com.aes.grammplayer.ui.common.BaseGridFragment
import com.aes.grammplayer.ui.features.details.MediaDetailsActivity
import kotlinx.coroutines.launch

class HistoryGridFragment : BaseGridFragment() {

    private lateinit var loader: DialogHelper
    private var pageOffset = 0
    private var isLoadingHistory = false
    private var hasLoadedOnce = false

    override fun createItemPresenter(): Presenter = HistoryCardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loader = DialogHelper(requireActivity().supportFragmentManager)
        title = "History"
        setupGrid()
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            loadFirstPage()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::loader.isInitialized) {
            loader.dismiss()
        }
        // Skip the first onResume (pairs with onViewCreated); refresh later returns from details.
        if (hasLoadedOnce && !isLoadingHistory && view != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                loadFirstPage()
            }
        }
    }

    private suspend fun loadFirstPage() {
        if (!::loader.isInitialized || view == null) return
        isLoadingHistory = true
        isPageLoading = true
        try {
            loader.runWithLoading("Loading history...") { update ->
                gridAdapter.clear()
                pageOffset = 0
                endReached = false
                val page = HistoryDataProvider.loadHistoryPage(
                    offset = 0,
                    pageSize = HistoryStore.DEFAULT_PAGE_SIZE
                )
                page.items.forEach { gridAdapter.add(it) }
                pageOffset = page.nextOffset
                endReached = page.endReached
                update("Loaded ${page.items.size} items")
                refreshAllCards()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading history first page", e)
            endReached = true
        } finally {
            isPageLoading = false
            isLoadingHistory = false
            hasLoadedOnce = true
        }
    }

    override fun loadNextPage() {
        if (isPageLoading || endReached || isLoadingHistory) return
        isPageLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val page = HistoryDataProvider.loadHistoryPage(
                    offset = pageOffset,
                    pageSize = HistoryStore.DEFAULT_PAGE_SIZE
                )
                appendItems(page.items)
                pageOffset = page.nextOffset
                endReached = page.endReached
            } catch (e: Exception) {
                Log.e(TAG, "Error loading next history page", e)
            } finally {
                isPageLoading = false
            }
        }
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is HistoryItem) {
                startActivity(MediaDetailsActivity.newIntent(requireContext(), item.message))
            }
        }
    }

    companion object {
        private const val TAG = "HistoryGridFragment"

        fun newInstance(): HistoryGridFragment = HistoryGridFragment()
    }
}
