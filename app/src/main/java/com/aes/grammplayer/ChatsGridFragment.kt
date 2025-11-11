package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row // Make sure this import is correct
import androidx.leanback.widget.RowPresenter
import androidx.core.content.ContextCompat // Added import for ContextCompat
import androidx.fragment.app.DialogFragment

/**
 * A fragment to display messages of a specific chat in a grid.
 */
class ChatsGridFragment : VerticalGridSupportFragment() {

    private lateinit var gridAdapter: ArrayObjectAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //title = arguments?.getString(ARG_CHAT_TITLE) ?: "Messages"
        // Set the brand logo using badgeDrawable
        badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.gp_logo_bk_bg)

        setupGrid()
        loadMessages()
        setupEventListeners()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Refresh all cards to ensure latest CardPresenter styling is applied
        refreshAllCards()
    }

    private fun setupGrid() {
        // We need a custom presenter to access the grid view.
        val gridPresenter = object : VerticalGridPresenter() {
            override fun initializeGridViewHolder(vh: ViewHolder) {
                super.initializeGridViewHolder(vh)
            }
        }

        gridPresenter.numberOfColumns = 5 // You can adjust the number of columns here
        setGridPresenter(gridPresenter)

        // The rest of your code remains the same.
        gridAdapter = ArrayObjectAdapter(ChatCardPresenter())
        adapter = gridAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = object : OnItemViewClickedListener {
            override fun onItemClicked(
                itemViewHolder: Presenter.ViewHolder?,
                item: Any?,
                rowViewHolder: RowPresenter.ViewHolder?,
                row: Row?
            ) {
                if (item is TdApi.Chat) {
                    val intent = Intent(activity, MessageGridActivity::class.java)
                    intent.putExtra("chat_id", item.id)
                    intent.putExtra("chat_title", item.title)
                    startActivity(intent)
                }
            }
        }
    }

    private fun loadMessages() {
        // Use Coroutines to call the suspend function on the main thread.
        TelegramClientManager.loadAllGroups { chat ->

            val chatTitle = chat.title
            if(chatTitle == "Telegram"){
                return@loadAllGroups
            }

            val lastMessage = chat.lastMessage
            if (lastMessage != null) {
                val messageContent = lastMessage.content
                when (messageContent) {
                    is TdApi.MessageContactRegistered -> {
                        return@loadAllGroups
                    }

                }
            }
            gridAdapter.add(chat)
        }

        }

    private fun refreshAllCards() {
        // Notify the adapter that the entire dataset might have changed,
        // forcing all visible items to be re-bound and re-rendered.
        gridAdapter.notifyItemRangeChanged(0, gridAdapter.size())
    }

    companion object {
        private const val TAG = "MessageGridFragment"
        const val ARG_CHAT_ID = "chat_id"
        private const val ARG_CHAT_TITLE = "chat_title"

        /**
         * Factory method to create a new instance of this fragment with the required arguments.
         */
        fun newInstance(chatId: Long, chatTitle: String): ChatsGridFragment {
            val fragment = ChatsGridFragment()
            val args = Bundle()
            args.putLong(ARG_CHAT_ID, chatId)
            args.putString(ARG_CHAT_TITLE, chatTitle)
            fragment.arguments = args
            return fragment
        }
    }

}