package com.aes.grammplayer.ui.features.messages

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.aes.grammplayer.R
import com.aes.grammplayer.db.model.MediaMessage
import com.aes.grammplayer.helper.FormatHelper
import com.aes.grammplayer.ui.common.GridDownloadLabelBinder
import com.aes.grammplayer.ui.common.GridThumbnailBinder
import com.aes.grammplayer.ui.common.makeFocusableForTv
import com.aes.grammplayer.util.tdlib.ReleaseTitleParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MessageCardPresenter : Presenter() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_message, parent, false)
        view.makeFocusableForTv()
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        if (item !is MediaMessage) return
        if (item.fileId == 0) return

        val view = viewHolder.view

        val info = ReleaseTitleParser.parse(item.title)
        view.findViewById<TextView>(R.id.title).text = info.displayTitle
        view.findViewById<TextView>(R.id.file_id).text = "File ID: ${item.fileId}"
        view.findViewById<TextView>(R.id.size).text =
            "Size: ${FormatHelper.formatBytesMb(item.size)}"

        view.findViewById<TextView>(R.id.quality_source)?.apply {
            val qualitySourceText = listOfNotNull(
                info.resolution,
                info.videoCodec,
                info.service,
                info.source
            ).joinToString(" · ")
            if (qualitySourceText.isNotBlank()) {
                visibility = android.view.View.VISIBLE
                text = "Quality: ${qualitySourceText}"
            } else {
                visibility = android.view.View.GONE
            }
        }


        GridDownloadLabelBinder.bindMessageBanner(view, item)
        GridThumbnailBinder.bind(view, item, info, scope)
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        GridThumbnailBinder.unbind(viewHolder.view)
    }

    companion object {
        private const val TAG = "MessageCardPresenter"
    }
}