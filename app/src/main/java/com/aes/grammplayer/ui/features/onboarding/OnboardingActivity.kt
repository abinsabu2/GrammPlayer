package com.aes.grammplayer.ui.features.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.ui.features.authentication.LoginActivity
import com.aes.grammplayer.ui.features.dashboard.MainActivity
import com.aes.grammplayer.R
import com.aes.grammplayer.db.view.SettingsViewModel
import com.aes.grammplayer.ui.features.authentication.AuthActivity
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingActivity : FragmentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        settingsDataStore = SettingsDataStore(this)
        observeData()
    }

    private fun showOnboardingFragment() {
        setContentView(R.layout.onboarding_main)
        supportFragmentManager.beginTransaction()
            .replace(R.id.onboarding_fragment_container, OnboardingFragment())
            .commit()
    }

    private fun navigateToToc() {
        val intent = Intent(this, TermsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun observeData() {
        lifecycleScope.launch {
            val isOnboardingDone = settingsDataStore.isOnboardingDone.first()
            val isTocAccepted = settingsDataStore.isTocAccepted.first()

            when {
                !isOnboardingDone -> showOnboardingFragment()
                !isTocAccepted -> navigateToToc()
                else -> navigateToLogin()
            }
        }
    }
}