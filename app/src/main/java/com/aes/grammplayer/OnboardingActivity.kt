package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class OnboardingActivity : FragmentActivity() {

    // Flag to prevent multiple navigations or UI changes.
    private var isDecisionMade = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set a simple loading view initially while we check the auth state.
        setContentView(R.layout.activity_splash)

        // Ensure the client is initialized. This uses the global handler automatically.
        // It's safe to call this multiple times.
        if (!TelegramClientManager.isInitialized) {
            TelegramClientManager.initialize()
        }

        // Observe the authorization state LiveData from our global handler.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.authorizationState.collect { authState ->
                    handleAuthorizationState(authState)
                }
            }
        }
    }

    /**
     * Handles routing based on the current authorization state.
     * This is called whenever the auth state changes.
     */
    private fun handleAuthorizationState(state: TdApi.AuthorizationState?) {
        // If a navigation decision has already been made, do nothing.
        // This prevents issues if the state updates multiple times quickly.
        if (isDecisionMade) {
            return
        }

        // Use runOnUiThread to be safe, although LiveData usually posts on the main thread.
        runOnUiThread {
            when (state) {
                is TdApi.AuthorizationStateReady -> {
                    // USER IS LOGGED IN
                    isDecisionMade = true
                    navigateToMainApp()
                }
                is TdApi.AuthorizationStateWaitPhoneNumber,
                is TdApi.AuthorizationStateClosed,
                is TdApi.AuthorizationStateWaitCode -> {
                    // USER IS NOT LOGGED IN and needs to take action
                    isDecisionMade = true
                    showOnboardingFragment()
                }
                // For other states like Closing, Closed, WaitTdlibParameters,
                // we do nothing and wait for a more definitive state.
            }
        }
    }

    private fun showOnboardingFragment() {
        // Replace the loading layout with the main onboarding container
        setContentView(R.layout.onboarding_main)

        // Show the OnboardingFragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.onboarding_fragment_container, OnboardingFragment())
            .commit()
    }

    private fun navigateToMainApp() {
        // Navigate to the main content of your app
        val intent = Intent(this, MainActivity::class.java).apply {
            // Prevent the user from returning to this screen with the back button
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
