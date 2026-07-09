package com.aes.grammplayer.helper

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.aes.grammplayer.ui.common.widgets.ModernLoadingDialogFragment

/**
 * Reusable helper class to manage loading dialogs across all activities/fragments.
 * Use in any fragment with just: loader.show() and loader.dismiss()
 */
class DialogHelper(private val fragmentManager: FragmentManager) {

    private var loadingDialog: ModernLoadingDialogFragment? = null
    private val tag = "loading_dialog_${System.currentTimeMillis()}"

    /**
     * Show loading dialog with message
     */
    fun show(message: String = "Loading...") {
        try {
            dismiss()
            loadingDialog = ModernLoadingDialogFragment.newInstance(message)
            loadingDialog?.show(fragmentManager, tag)
            Log.d(TAG, "Loading dialog shown: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing loading dialog", e)
        }
    }

    /**
     * Update the loading dialog message
     */
    fun updateMessage(message: String) {
        try {
            if (loadingDialog?.isAdded == true) {
                loadingDialog?.setMessage(message)
                Log.d(TAG, "Loading message updated: $message")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating loading message", e)
        }
    }

    /**
     * Dismiss the loading dialog
     */
    fun dismiss() {
        try {
            loadingDialog?.dismissLoading()
            fragmentManager.findFragmentByTag(tag)?.let { fragment ->
                if (fragment is ModernLoadingDialogFragment) {
                    fragment.dismissLoading()
                }
            }
            Log.d(TAG, "Loading dialog dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing loading dialog", e)
        }
        loadingDialog = null
    }

    /**
     * Check if dialog is currently shown
     */
    fun isShowing(): Boolean = loadingDialog?.isAdded == true

    /**
     * Shows the loading dialog, runs [block] (optionally updating the message), then dismisses.
     */
    suspend fun <T> runWithLoading(
        message: String,
        block: suspend (updateMessage: (String) -> Unit) -> T
    ): T {
        show(message)
        return try {
            block(::updateMessage)
        } finally {
            dismiss()
        }
    }

    companion object {
        private const val TAG = "LoadingDialogManager"
    }
}