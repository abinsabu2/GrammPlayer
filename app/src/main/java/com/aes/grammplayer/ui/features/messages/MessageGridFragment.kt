package com.aes.grammplayer.ui.features.messages

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.VerticalGridPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row // Make sure this import is correct
import androidx.leanback.widget.RowPresenter
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.ui.features.details.MediaDetailsBottomSheetFragment
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.provider.MediaMessageDataProvider
import com.aes.grammplayer.ui.features.details.MediaMessageDetailActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.flow.first

/**
 * A fragment to display messages of a specific chat in a grid.
 */
class MessageGridFragment : VerticalGridSupportFragment() {

    private lateinit var gridAdapter: ArrayObjectAdapter
    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
        title = arguments?.getString(ARG_CHAT_TITLE) ?: "Messages"
        // Set the brand logo using badgeDrawable
        //badgeDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.gp_logo_bk_bg)

        setupGrid()
        lifecycleScope.launch {
            loadMessages()
        }
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

        gridPresenter.numberOfColumns = 3 // You can adjust the number of columns here
        setGridPresenter(gridPresenter)

        // The rest of your code remains the same.
        gridAdapter = ArrayObjectAdapter(MessageCardPresenter())
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
                if (item is MediaMessage) {
                    val intent = MediaMessageDetailActivity.newIntent(requireContext(), item)
                    startActivity(intent)
                }
            }
        }
    }



    private suspend fun loadMessages() {
        val chatId = arguments?.getLong(ARG_CHAT_ID) ?: 0L
        if (chatId == 0L) {
            Log.e(TAG, "No Chat ID provided, cannot load messages.")
            return
        }
        val userMode = settingsDataStore.isTestMode.first()
        // Use Coroutines to call the suspend function on the main thread.
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Call the suspend function from the provider with a callback to process each message
                MediaMessageDataProvider.loadAllMediaMessages(
                    mode = userMode,
                    chatId = chatId,
                    limit = 10000
                ) { mediaMessage ->
                    gridAdapter.add(mediaMessage)
                }

                refreshAllCards()
                Log.d(
                    TAG,
                    "Loaded ${gridAdapter.size()} messages from chat $chatId"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages", e)
                // You could display an error to the user here.
            }
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
        fun newInstance(chatId: Long, chatTitle: String): MessageGridFragment {
            val fragment = MessageGridFragment()
            val args = Bundle()
            args.putLong(ARG_CHAT_ID, chatId)
            args.putString(ARG_CHAT_TITLE, chatTitle)
            fragment.arguments = args
            return fragment
        }
    }

}