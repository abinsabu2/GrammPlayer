package com.aes.grammplayer.ui.features.messages

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import java.io.File

/**
 * A Presenter used to generate Views and bind MediaMessage objects to them on demand.
 *
 * Mirrors the pattern used by HeroCardPresenter / ChatCardPresenter: inflate
 * a real layout, bind fields via findViewById, and let
 * card_background_selector.xml (rounded background + focus border) handle
 * focus visuals — no manual scale/elevation animation or GradientDrawable
 * construction.
 *
 * Note: the old version force-highlighted the whole card (via
 * updateCardStyling(view, true)) whenever a locally-downloaded file existed,
 * independent of focus. The shared selector only knows focused/unfocused,
 * so that persistent "available locally" highlight is now signaled via the
 * banner text instead of repainting the card border. If you want the full
 * persistent card highlight back, card_background_selector.xml would need
 * an extra state (e.g. state_activated) wired to that condition.
 */
class MessageCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_message, parent, false)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is MediaMessage) return
        if (item.fileId == 0) return

        val view = viewHolder.view
        val localFile = item.localPath?.let { File(it) }
        val localFileExistsAndIsPlayable = localFile != null && localFile.exists()

        val banner = view.findViewById<TextView>(R.id.banner)
        if (localFileExistsAndIsPlayable) {
            banner.visibility = android.view.View.VISIBLE
            banner.text = if (item.isDownloadActive) "Downloading" else "Available locally"
        } else {
            banner.visibility = android.view.View.GONE
        }

        val fileSizeMb = if (item.size > 0) String.format("%.2f MB", item.size / 1024.0 / 1024.0) else "N/A"
        view.findViewById<TextView>(R.id.title).text = item.title ?: "N/A"
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${item.fileId}"
        view.findViewById<TextView>(R.id.size).text = "Size: $fileSizeMb"
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
    }

    companion object {
        private const val TAG = "MessageCardPresenter"
    }
}