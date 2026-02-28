package com.aes.grammplayer.ui.features.dashboard

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.aes.grammplayer.R

/**
 * Loads [MainFragment].
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_fragment_container, MainFragment())
                .commitNow()
        }
    }
}