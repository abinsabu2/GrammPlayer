package com.aes.grammplayer.ui.features.chats

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.R
import androidx.annotation.IntegerRes
import androidx.annotation.DimenRes
import com.aes.grammplayer.db.model.Chat
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.helper.NavigationExtras
import com.aes.grammplayer.provider.ChatsDataProvider
import com.aes.grammplayer.ui.common.BaseGridFragment
import com.aes.grammplayer.ui.features.messages.MessageGridActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChatsGridFragment : BaseGridFragment() {

    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var loader: DialogHelper

    @IntegerRes
    override val gridColumnCountResId: Int = R.integer.grid_column_count_chat

    @DimenRes
    override val gridCardWidthDimen: Int = R.dimen.grid_card_chat_size

    override fun createItemPresenter(): Presenter = ChatCardPresenter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
        loader = DialogHelper(requireActivity().supportFragmentManager)
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.gp_logo_bk_bg)
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
                loader.runWithLoading("Loading chats...") {
                    loadChats()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading chats", e)
            }
        }
        refreshAllCards()
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            if (item is Chat) {
                val intent = Intent(requireActivity(), MessageGridActivity::class.java).apply {
                    putExtra(NavigationExtras.CHAT_ID, item.id)
                    putExtra(NavigationExtras.CHAT_TITLE, item.title)
                }
                startActivity(intent)
            }
        }
    }

    private suspend fun loadChats() {
        val userMode = settingsDataStore.isTestMode.first()
        ChatsDataProvider.loadAllGroups(userMode, filter = { it.title != "Telegram" }) { chat ->
            gridAdapter.add(chat)
        }
    }

    companion object {
        private const val TAG = "ChatsGridFragment"

        fun newInstance(chatId: Long, chatTitle: String): ChatsGridFragment =
            ChatsGridFragment().apply {
                arguments = buildChatArgs(chatId, chatTitle)
            }
    }
}