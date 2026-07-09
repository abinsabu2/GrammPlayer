package com.aes.grammplayer.ui.features.history

import androidx.fragment.app.Fragment
import com.aes.grammplayer.R
import com.aes.grammplayer.ui.common.BaseHostActivity

class HistoryGridActivity : BaseHostActivity() {

    override val layoutId = R.layout.activity_history_grid
    override val containerId = R.id.history_grid_fragment_container

    override fun createFragment(): Fragment = HistoryGridFragment.newInstance()
}