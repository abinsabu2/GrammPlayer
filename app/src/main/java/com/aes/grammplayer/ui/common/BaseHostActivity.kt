package com.aes.grammplayer.ui.common

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

/**
 * Hosts a single fragment in a container layout.
 * Subclasses supply the layout, container id, and fragment to display.
 */
abstract class BaseHostActivity : FragmentActivity() {

    @get:LayoutRes
    protected abstract val layoutId: Int

    protected abstract val containerId: Int

    protected open fun createFragment(): Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutId)
        if (savedInstanceState == null) {
            createFragment()?.let { fragment ->
                supportFragmentManager.beginTransaction()
                    .replace(containerId, fragment)
                    .commitNow()
            }
        }
    }
}