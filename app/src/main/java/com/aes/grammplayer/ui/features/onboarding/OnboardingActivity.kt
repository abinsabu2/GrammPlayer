package com.aes.grammplayer.ui.features.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.ui.features.login.LoginActivity
import com.aes.grammplayer.MainActivity
import com.aes.grammplayer.R
import kotlinx.coroutines.launch
import com.aes.grammplayer.db.view.SettingsViewModel

class OnboardingActivity : FragmentActivity() {

    // Flag to prevent multiple navigations or UI changes.
    private var isDecisionMade = false
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set a simple loading view initially while we check the auth state.
        setContentView(R.layout.activity_splash)
        observeData()

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


    private fun navigateToToc() {
        // Navigate to the main content of your app
        val intent = Intent(this, TermsActivity::class.java).apply {
            // Prevent the user from returning to this screen with the back button
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }


    private fun navigateToLogin() {
        // Navigate to the main content of your app
        val intent = Intent(this, LoginActivity::class.java).apply {
            // Prevent the user from returning to this screen with the back button
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }


    private fun observeData() {
        lifecycleScope.launch {
            viewModel.getSettings(1).collect { settings ->
                isDecisionMade = true
                when {
                    settings == null || !settings.isOnBoard -> {
                        showOnboardingFragment()
                    }
                    !settings.isTocAccepted -> {
                        navigateToToc()
                    }
                    !settings.userConnected!! -> {
                        navigateToLogin()
                    }
                    else -> {
                        navigateToMainApp()
                    }
                }
            }
        }
    }
}
