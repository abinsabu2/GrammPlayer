package com.aes.grammplayer

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment

class SettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val fragment = SettingsFragment()
            GuidedStepSupportFragment.addAsRoot(this, fragment, android.R.id.content)
        }
    }

    override fun onEnterAnimationComplete() {
        super.onEnterAnimationComplete()
        overridePendingTransition(R.anim.slide_up, 0)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, R.anim.slide_down)
    }
}
