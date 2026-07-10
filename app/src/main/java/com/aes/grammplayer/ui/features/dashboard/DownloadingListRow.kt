package com.aes.grammplayer.ui.features.dashboard

import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.ObjectAdapter

class DownloadingListRow(
    id: Long,
    header: HeaderItem,
    adapter: ObjectAdapter
) : ListRow(id, header, adapter)