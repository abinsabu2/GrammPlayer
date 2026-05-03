package com.aes.grammplayer.ui.features.authentication

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.aes.grammplayer.R

class AuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        if (savedInstanceState == null) {
            GuidedStepSupportFragment.addAsRoot(
                this,
                PhoneAuthGuidedStepFragment(),
                R.id.guided_step_container
            )
        }
    }
}