package com.aes.grammplayer.ui.features.messages

import android.os.Bundle
import android.util.Log
import android.view.View
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

    override fun createItemPresenter(): Presenter = MessageCardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
        loader = DialogHelper(requireActivity().supportFragmentManager)
        title = chatTitle.ifEmpty { "Messages" }
        setupGrid()
        setupEventListeners()
    }

    override fun onResume() {
        super.onResume()
        if (!isLoadingMessages) {
            refreshAllCards()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setContentVisible(false)
        viewLifecycleOwner.lifecycleScope.launch {
            isLoadingMessages = true
            try {
                loader.runWithLoading(getString(R.string.loading_messages_progress, 0)) { update ->
                    loadMessages(update)
                }
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

    private suspend fun loadMessages(updateMessage: (String) -> Unit) {
        if (chatId == 0L) {
            Log.e(TAG, "No Chat ID provided, cannot load messages.")
            return
        }
        val userMode = settingsDataStore.isTestMode.first()
        gridAdapter.clear()
        MediaMessageDataProvider.loadAllMediaMessages(
            mode = userMode,
            chatId = chatId,
            limit = 10000,
            onMediaLoaded = { mediaMessage ->
                gridAdapter.add(mediaMessage)
            },
            onProgress = { count ->
                updateMessage(getString(R.string.loading_messages_progress, count))
            }
        )
        refreshAllCards()
    }

    companion object {
        private const val TAG = "MessageGridFragment"

        fun newInstance(chatId: Long, chatTitle: String): MessageGridFragment =
            MessageGridFragment().apply {
                arguments = buildChatArgs(chatId, chatTitle)
            }
    }
}