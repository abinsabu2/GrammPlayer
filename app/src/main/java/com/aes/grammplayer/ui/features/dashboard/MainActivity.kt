package com.aes.grammplayer.ui.features.dashboard

import androidx.fragment.app.Fragment
import com.aes.grammplayer.R
import com.aes.grammplayer.ui.common.BaseHostActivity

/**
 * Loads [MainFragment].
 */
class MainActivity : BaseHostActivity() {

    override val layoutId = R.layout.activity_main
    override val containerId = R.id.main_fragment_container

    override fun createFragment(): Fragment = MainFragment()
}