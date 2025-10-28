package com.aes.grammplayer

import java.util.Timer
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import org.drinkless.tdlib.TdApi

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private var mBackgroundTimer: Timer? = null

    // In your Activity or Fragment\'s onCreate/onCreateView method
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        // Setup UI elements directly here
        title = getString(R.string.app_name)
        headersState = HEADERS_ENABLED // Ensure headers are enabled for the badge to show
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireActivity(), R.color.background_gradient_start)
        
        // Set the brand logo using badgeDrawable, which typically aligns to the top-right in RTL contexts
        badgeDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.gp_logo_bk_bg)

        loadRows()
        setupEventListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: " + mBackgroundTimer?.toString())
        mBackgroundTimer?.cancel()
    }

    private fun loadRows() {
        // This presenter is for the items *inside* each row.
        val mGridPresenter = GridItemPresenter()
        // This adapter holds all the rows.
        val rowsAdapter = ArrayObjectAdapter(ListRowPresenter())

        // --- 2. ADD A NEW SETTINGS ROW (new logic) ---
        val settingsHeader = HeaderItem("Preferences")
        val settingsGridPresenter = GridItemPresenter() // Can reuse the same presenter
        val settingsRowAdapter = ArrayObjectAdapter(settingsGridPresenter)

        // Add items to your settings row. We\'ll use simple strings.
        settingsRowAdapter.add("Take A break")
        settingsRowAdapter.add("Clear Cache")
        rowsAdapter.add(ListRow(settingsHeader, settingsRowAdapter))
        // --- End of new logic ---
        // --- 1. LOAD CHAT ROWS (existing logic) ---
        TelegramClientFacade.loadAllGroups { chat ->
            // This adapter holds the items for a single row.
            val gridRowAdapter = ArrayObjectAdapter(mGridPresenter)
            gridRowAdapter.add(chat) // Add the chat object to the row

            // Create the header for the row using the chat\'s ID and title.
            val header = HeaderItem(chat.id, chat.title)

            // Add the new row (header + items adapter) to the main adapter.
            rowsAdapter.add(ListRow(header, gridRowAdapter))
        }
        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked( itemViewHolder: Presenter.ViewHolder,
                                    item: Any,
                                    rowViewHolder: RowPresenter.ViewHolder,
                                    row: Row
        ) {
            when (item) {
                // Handle clicks on chat items
                is TdApi.Chat -> {
                    val intent = Intent(activity, MessageGridActivity::class.java)
                    intent.putExtra("chat_id", item.id)
                    intent.putExtra("chat_title", item.title)
                    startActivity(intent)
                }
                // Handle clicks on settings items
                is String -> {
                    when (item) {
                        "Clear Cache" -> {
                            val deletedCount = TelegramClientFacade.clearDownloadedFiles()
                            //val appDirectorySize = TelegramClientFacade.getDirectorySize()
                            val cacheClearText = "Cleared $deletedCount downloaded files from cache"
                            //val sizeClearText = "$appDirectorySize MB of app directory size saved"
                            Toast.makeText(requireContext(), cacheClearText, Toast.LENGTH_SHORT).show()
                        }
                        "Take A break" -> {
                            requireActivity().finish()
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Clicked on: $item", Toast.LENGTH_SHORT).show()
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
            // Toast.makeText(requireContext(), "Clicked on: $item", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class GridItemPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder {
            val view = TextView(parent.context)
            view.layoutParams = ViewGroup.LayoutParams(GRID_ITEM_WIDTH, GRID_ITEM_HEIGHT)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.setBackgroundColor(ContextCompat.getColor(requireActivity(), R.color.default_background))
            view.setTextColor(Color.WHITE)
            view.gravity = Gravity.CENTER
            return Presenter.ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
            // Now handle both TdApi.Chat and String types
            val textView = viewHolder.view as TextView
            when (item) {
                is TdApi.Chat -> {
                    textView.text = "Explore Chat"
                }
                is String -> {
                    textView.text = item
                }
            }
        }

        override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {}
    }

    companion object {
        private val TAG = "MainFragment"
        private val GRID_ITEM_WIDTH = 400
        private val GRID_ITEM_HEIGHT = 200 // Made it more rectangular
    }
}
