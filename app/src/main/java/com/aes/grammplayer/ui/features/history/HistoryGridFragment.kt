package com.aes.grammplayer.ui.features.history

import android.os.Bundle
import android.util.Log
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.provider.HistoryDataProvider
import com.aes.grammplayer.ui.common.BaseGridFragment
import com.aes.grammplayer.ui.features.details.MediaDetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryGridFragment : BaseGridFragment() {

    private lateinit var loader: DialogHelper

    override fun createItemPresenter(): Presenter = HistoryCardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loader = DialogHelper(requireActivity().supportFragmentManager)
        title = "History"
        setupGrid()
        setupEventListeners()
    }

    override fun onResume() {
        super.onResume()
        if (::loader.isInitialized) {
            loader.dismiss()
        }
        reloadHistory()
    }

    fun reloadHistory() {
        if (!::loader.isInitialized || view == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loader.runWithLoading("Loading history...") { update ->
                    val items = HistoryDataProvider.loadHistory()
                    withContext(Dispatchers.Main) {
                        gridAdapter.clear()
                        items.forEach { gridAdapter.add(it) }
                        update("Loaded ${items.size} items")
                        refreshAllCards()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
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