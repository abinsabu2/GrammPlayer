package com.aes.grammplayer.ui.features.settings

import android.content.Context
import com.aes.grammplayer.helper.LocaleHelper
import android.R
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment

class SettingsActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment = SettingsFragment()
            GuidedStepSupportFragment.addAsRoot(this, fragment, R.id.content)
        }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        overridePendingTransition(com.aes.grammplayer.R.anim.slide_up, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, com.aes.grammplayer.R.anim.slide_down)
    }
}
