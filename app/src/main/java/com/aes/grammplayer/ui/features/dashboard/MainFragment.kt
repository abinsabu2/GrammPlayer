package com.aes.grammplayer.ui.features.dashboard

import java.util.Timer
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.app.HeadersSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Presenter
import androidx.leanback.widget.Row
import androidx.leanback.widget.RowPresenter
import androidx.core.content.ContextCompat
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aes.grammplayer.ui.features.chats.ChatsGridActivity
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.ui.features.authentication.LoginActivity
import com.aes.grammplayer.ui.features.settings.SettingsActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Loads a grid of cards with movies to browse.
 */
class MainFragment : BrowseSupportFragment() {

    private var mBackgroundTimer: Timer? = null

    private lateinit var loadingDialog: DialogHelper

    private lateinit var settingsDataStore: SettingsDataStore

    private lateinit var productLogo: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        Log.i(TAG, "onCreate")
        super.onActivityCreated(savedInstanceState)

        loadingDialog = DialogHelper(childFragmentManager)

        // Browse chrome — title, sidebar, brand badge.
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = true
        brandColor = ContextCompat.getColor(requireActivity(), R.color.background_gradient_start)
        // Logo is now a fixed ImageView in activity_main.xml, pinned above the
        // sidebar — badgeDrawable floats in the shared title strip and can't
        // be reliably locked above "Chats", so we don't use it here.
        val logoDrawable = ContextCompat.getDrawable(requireActivity(), R.drawable.gp_logo_bk_bg)
        badgeDrawable = logoDrawable
        // Custom sidebar header design: icon + label, teal on focus.
        setHeaderPresenterSelector(
            ClassPresenterSelector().addClassPresenter(ListRow::class.java, DashboardHeaderPresenter())
        )
        loadRows()
        setupEventListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: " + mBackgroundTimer?.toString())
        mBackgroundTimer?.cancel()
    }

    override fun onCreateHeadersSupportFragment(): HeadersSupportFragment {
        return DashboardHeadersSupportFragment()
    }

    private fun loadRows() {
        // ZOOM_FACTOR_NONE stops Leanback from scaling the whole row on focus —
        // our cards handle their own focus state via card_background_selector,
        // so we don't want the row itself growing/shifting on top of that.
        val listRowPresenter = ListRowPresenter(FocusHighlight.ZOOM_FACTOR_NONE).apply {
            shadowEnabled = false
        }
        val rowsAdapter = ArrayObjectAdapter(listRowPresenter)

        // --- Chats row: hero cards for Chats + History ---
        val chatsHeader = DashboardHeaderItem(1, "Chats", R.drawable.ic_chat)
        val chatsRowAdapter = ArrayObjectAdapter(HeroCardPresenter())
        chatsRowAdapter.add(
            DashboardItem(
                id = "chats",
                iconRes = R.drawable.ic_chat,
                eyebrow = "Chat Management",
                title = "Chats",
                description = "Your Conversations",
                actionLabel = "Open Chats",
                isHero = true
            )
        )
        chatsRowAdapter.add(
            DashboardItem(
                id = "history",
                iconRes = R.drawable.ic_history,
                eyebrow = "History Management",
                title = "History",
                description = "Your History",
                actionLabel = "Open History",
                isHero = true
            )
        )
        rowsAdapter.add(ListRow(chatsHeader, chatsRowAdapter))

        // --- Preferences row: icon cards ---
        val settingsHeader = DashboardHeaderItem(2, "Preferences", R.drawable.ic_settings)
        val settingsRowAdapter = ArrayObjectAdapter(IconCardPresenter())
        settingsRowAdapter.add(DashboardItem("clear_cache", R.drawable.ic_clear_cache, "Clear Cache"))
        settingsRowAdapter.add(DashboardItem("settings", R.drawable.ic_settings, "Settings"))
        settingsRowAdapter.add(DashboardItem("refresh_data", R.drawable.ic_refresh, "Refresh Data"))
        settingsRowAdapter.add(DashboardItem("close", R.drawable.ic_refresh, "Close"))
        settingsRowAdapter.add(DashboardItem("logout", R.drawable.ic_logout, "Logout"))
        rowsAdapter.add(ListRow(settingsHeader, settingsRowAdapter))

        adapter = rowsAdapter
    }

    private fun setupEventListeners() {
        onItemViewClickedListener = ItemViewClickedListener()
        onItemViewSelectedListener = ItemViewSelectedListener()
    }

    private inner class ItemViewClickedListener : OnItemViewClickedListener {
        override fun onItemClicked(
            itemViewHolder: Presenter.ViewHolder,
            item: Any,
            rowViewHolder: RowPresenter.ViewHolder,
            row: Row
        ) {
            when (item) {
                is DashboardItem -> {
                    when (item.id) {
                        "clear_cache" -> {
                            loadingDialog.show("Clearing cache")
                            lifecycleScope.launch {
                                try {
                                    val deletedCount = TelegramClientManager.clearDownloadedFiles()
                                    val cacheClearText = "Cleared $deletedCount downloaded files from cache"
                                    delay(1500)
                                    loadingDialog.updateMessage(cacheClearText)
                                    delay(1500)
                                    loadingDialog.dismiss()
                                } catch (e: Exception) {
                                    loadingDialog.dismiss()
                                }
                            }
                        }
                        "settings" -> {
                            val intent = Intent(activity, SettingsActivity::class.java)
                            startActivity(intent)
                        }
                        "close" -> {
                            TelegramClientManager.clearDownloadedFiles()
                            requireActivity().finish()
                        }
                        "chats" -> {
                            val intent = Intent(activity, ChatsGridActivity::class.java)
                            intent.putExtra("chat_id", 1000)
                            intent.putExtra("chat_title", "Chats")
                            startActivity(intent)
                        }
                        "history" -> {
                            // TODO: wire to a HistoryActivity once it exists.
                            // Left as a placeholder so the hero card is clickable
                            // rather than silently falling through to "else".
                            Toast.makeText(requireContext(), "Open History — not wired up yet", Toast.LENGTH_SHORT).show()
                        }
                        "logout" -> {
                            loadingDialog.show("Logging out")
                            lifecycleScope.launch {
                                try {
                                    TelegramClientManager.logOut() // now suspends until AuthorizationStateClosed
                                    loadingDialog.updateMessage("Logged out")
                                    delay(500.milliseconds) // purely cosmetic pause so the message is readable, not a functional wait
                                    loadingDialog.dismiss()

                                    val intent = Intent(activity, LoginActivity::class.java).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    }
                                    settingsDataStore.setTestMode(false)
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Logout failed: ${e.message}", e)
                                    loadingDialog.dismiss()
                                }
                            }
                        }
                        else -> {
                            Toast.makeText(requireContext(), "Clicked on: ${item.title}", Toast.LENGTH_SHORT).show()
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
            when (item) {
                is DashboardItem -> {
                    when (item.id) {
                        "chats" -> {
                            // Reserved for selection-based preview/behavior if needed later.
                        }
                    }
                }
            }
        }
    }

    /**
     * Renders the large hero card: icon, eyebrow label, title, description, action button.
     * Used for both the "Chats" and "History" row items.
     */
    private inner class HeroCardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_hero, parent, false)
            // Must be focusable, or the card can never take D-pad focus and
            // onItemClicked/onItemSelected will never fire for it.
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val d = item as DashboardItem
            val view = viewHolder.view
            view.findViewById<ImageView>(R.id.icon).setImageResource(d.iconRes)
            view.findViewById<TextView>(R.id.eyebrow).text = d.eyebrow
            view.findViewById<TextView>(R.id.title).text = d.title
            view.findViewById<TextView>(R.id.description).text = d.description
            view.findViewById<TextView>(R.id.actionButton).text = d.actionLabel
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    /**
     * Renders the small square icon cards used for the Preferences row
     * (Clear Cache, Settings, Refresh Data, Close, Logout).
     */
    private inner class IconCardPresenter : Presenter() {
        override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.card_icon, parent, false)
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
            val d = item as DashboardItem
            val view = viewHolder.view
            view.findViewById<ImageView>(R.id.icon).setImageResource(d.iconRes)
            view.findViewById<TextView>(R.id.title).text = d.title
        }

        override fun onUnbindViewHolder(viewHolder: ViewHolder) {}
    }

    companion object {
        private val TAG = "MainFragment"
    }
}

/**
 * Sidebar headers list, with extra top padding reserved for the fixed
 * product logo overlaid in activity_main.xml — otherwise the logo would
 * sit on top of the first header row ("Chats").
 */
class DashboardHeadersSupportFragment : HeadersSupportFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val topPaddingPx = (80 * resources.displayMetrics.density).toInt()
        view.setPadding(view.paddingLeft, topPaddingPx, view.paddingRight, view.paddingBottom)
    }
}