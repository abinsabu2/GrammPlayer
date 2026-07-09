package com.aes.grammplayer.ui.features.messages

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.provider.MediaMessageDataProvider
import com.aes.grammplayer.ui.common.BaseGridFragment
import com.aes.grammplayer.ui.features.details.MediaDetailsActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageGridFragment : BaseGridFragment() {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var loader: DialogHelper

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
        if (::loader.isInitialized) {
            loader.dismiss()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                loader.runWithLoading("Loading messages...") { update ->
                    loadMessages(update)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
            }
        }
        refreshAllCards()
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
        withContext(Dispatchers.Main) {
            MediaMessageDataProvider.loadAllMediaMessages(
                mode = userMode,
                chatId = chatId,
                limit = 10000
            ) { mediaMessage ->
                gridAdapter.add(mediaMessage)
            }
            updateMessage("Loaded ${gridAdapter.size()} messages from chat")
            refreshAllCards()
        }
    }

    companion object {
        private const val TAG = "MessageGridFragment"

        fun newInstance(chatId: Long, chatTitle: String): MessageGridFragment =
            MessageGridFragment().apply {
                arguments = buildChatArgs(chatId, chatTitle)
            }
    }
}