package com.aes.grammplayer.ui.features.chats

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.db.model.Chat

/**
 * A Presenter used to generate Views and bind Chat objects to them on demand.
 *
 * Mirrors the pattern used by HeroCardPresenter / IconCardPresenter in the
 * dashboard: inflate a real layout, bind fields via findViewById, and let
 * card_background_selector.xml (rounded background + focus border) handle
 * all focus visuals. No manual scale/elevation animation or GradientDrawable
 * construction — that logic used to live here but duplicated what the shared
 * card layouts/selectors already do, and fought with them on focus.
 */
class ChatCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_chat, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is Chat) return

        val chatTypeReadable = when (item.type) {
            0 -> "Private"
            1 -> "Basic Group"
            2 -> "Supergroup"
            3 -> "Secret"
            else -> "Unknown"
        }

        val view = viewHolder.view
        view.findViewById<TextView>(R.id.type).text = chatTypeReadable
        view.findViewById<TextView>(R.id.title).text = item.title
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
    }

    companion object {
        private const val TAG = "ChatCardPresenter"
    }
}