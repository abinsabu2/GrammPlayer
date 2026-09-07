package com.aes.grammplayer.ui.features.messages

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.provider.MediaMessageDataProvider
import com.aes.grammplayer.ui.common.BaseGridFragment
import com.aes.grammplayer.ui.features.details.MediaDetailsActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MessageGridFragment : BaseGridFragment() {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var loader: DialogHelper
    private var isLoadingMessages = false

    private var userMode = true
    private var pageOffset = 0
    private var pageCursor = 0L
    private var pageSize = 50

    override fun createItemPresenter(): Presenter = MessageCardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
        loader = DialogHelper(childFragmentManager)
        title = chatTitle.ifEmpty { "Messages" }
        setupGrid()
        setupEventListeners()
    }

    // ponytail: removed refreshAllCards onResume — full range notify resets Leanback scroll; per-card binder handles labels

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setContentVisible(false)
        viewLifecycleOwner.lifecycleScope.launch {
            isLoadingMessages = true
            try {
                loadFirstPage()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
            } finally {
                isLoadingMessages = false
                setContentVisible(true)
            }
        }
    }

    private fun setContentVisible(visible: Boolean) {
        view?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is MediaMessage) {
                startActivity(MediaDetailsActivity.newIntent(requireContext(), item))
            }
        }
    }

    private suspend fun loadFirstPage() {
        if (chatId == 0L) {
            Log.e(TAG, "No Chat ID provided, cannot load messages.")
            endReached = true
            return
        }
        userMode = settingsDataStore.isTestMode.first()
        pageSize = settingsDataStore.messagesPageSize.first()
        // ponytail: save position before clear so future reloads don't snap to 0
        val savedPos = lastSelectedPosition
        gridAdapter.clear()
        pageOffset = 0
        pageCursor = 0L
        endReached = false
        fetchAndAppendPage()
        if (savedPos > 0) setSelectedPosition(savedPos.coerceAtMost((gridAdapter.size() - 1).coerceAtLeast(0)))
    }

    override fun loadNextPage() {
        if (isPageLoading || endReached) return
        isPageLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                fetchAndAppendPage()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading next message page", e)
            } finally {
                isPageLoading = false
            }
        }
    }

    private suspend fun fetchAndAppendPage() {
        val page = MediaMessageDataProvider.loadMediaMessagesPage(
            mode = userMode,
            chatId = chatId,
            offset = pageOffset,
            cursor = pageCursor,
            pageSize = pageSize
        )
        pageOffset = page.nextOffset
        pageCursor = page.nextCursor
        endReached = page.endReached
        appendItems(page.items)
    }

    companion object {
        private const val TAG = "MessageGridFragment"

        fun newInstance(chatId: Long, chatTitle: String): MessageGridFragment =
            MessageGridFragment().apply {
                arguments = buildChatArgs(chatId, chatTitle)
            }
    }
}
