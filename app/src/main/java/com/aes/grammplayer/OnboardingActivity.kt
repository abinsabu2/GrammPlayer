package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.FragmentActivity
import org.drinkless.tdlib.TdApi

class OnboardingActivity : FragmentActivity() {

    // Add a flag to track if the onboarding UI has been shown.
    private var isOnboardingLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set a simple loading view initially.
        // The user will only see this for a moment.
        // Initialize Telegram client
        TelegramClientManager.initialize(::onResult)
    }

    /**
     * Central handler for all TDLib results.
     */
    private fun onResult(update: TdApi.Object?) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(update.authorizationState)
        }
    }

    /**
     * Handles UI changes based on the current authorization state.
     */
    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        runOnUiThread {
            // If the user is already being onboarded, do nothing.
            if (isOnboardingLoaded) {
                return@runOnUiThread
            }

            when (state) {
                is TdApi.AuthorizationStateReady -> {
                    navigateToMainApp()
                }
                else -> {
                    showOnboardingFragment()
                }
            }
        }
    }

    private fun showOnboardingFragment() {

        // Check the flag before proceeding.
        if (isOnboardingLoaded) return

        // Set the flag to true to prevent this from running again.
        isOnboardingLoaded = true

        // Replace the loading layout with the onboarding layout
        setContentView(R.layout.onboarding_main)

        // The original logic to show the OnboardingFragment
        if (supportFragmentManager.findFragmentById(R.id.onboarding_fragment_container) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.onboarding_fragment_container, OnboardingFragment())
                .commit()
        }
    }

    private fun navigateToMainApp() {
        // Replace 'MainActivity' with the actual main activity of your app
        val intent = Intent(this, MainActivity::class.java).apply {
            // Prevent the user from returning to this activity with the back button
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }
}
