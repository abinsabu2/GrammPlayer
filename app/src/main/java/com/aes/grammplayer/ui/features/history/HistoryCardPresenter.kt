package com.aes.grammplayer.ui.features.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.ui.common.GridDownloadLabelBinder
import com.aes.grammplayer.ui.common.GridThumbnailBinder
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class HistoryCardPresenter : Presenter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_history, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is HistoryItem) return
        val message = item.message
        if (message.fileId == 0) return

        val view = viewHolder.view
        val info = ReleaseTitleParser.parse(message.title)
        view.findViewById<TextView>(R.id.title).text =
            info.displayTitle.ifEmpty { message.title.ifEmpty { "Untitled" } }
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${message.fileId}"
        view.findViewById<TextView>(R.id.size).text =
            "Size: ${FormatHelper.formatBytesMb(message.size)}"

        GridDownloadLabelBinder.bindHistoryBadges(view, item)
        GridThumbnailBinder.bind(view, message, info, scope)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        GridThumbnailBinder.unbind(viewHolder.view)
    }

}