package com.aes.grammplayer.ui.features.onboarding

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aes.grammplayer.ui.features.authentication.LoginActivity
import com.aes.grammplayer.ui.features.dashboard.MainActivity
import com.aes.grammplayer.R
import com.aes.grammplayer.db.view.SettingsViewModel
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

class OnboardingActivity : FragmentActivity() {

    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var settingsDataStore: SettingsDataStore
    private lateinit var loader: DialogHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        settingsDataStore = SettingsDataStore(this)

        // ✅ Initialize LoadingDialogManager with supportFragmentManager
        loader = DialogHelper(supportFragmentManager)
        if (!TelegramClientManager.isInitialized) {
            TelegramClientManager.initialize()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.authorizationState.collect { response ->
                    handleAuthorizationState(response)
                }
            }
        }
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
        val intent = Intent(this, LoginActivity::class.java).apply {
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
            loader.updateMessage("Waiting for Parameters...")
            val isOnboardingDone = settingsDataStore.isOnboardingDone.first()
            val isTocAccepted = settingsDataStore.isTocAccepted.first()
            loader.dismiss()
            when {
                !isOnboardingDone -> showOnboardingFragment()
                !isTocAccepted -> navigateToToc()
                else -> navigateToLogin()
            }
        }
    }
    @SuppressLint("SetTextI18n")
    private fun handleAuthorizationState(response: TdApi.Object?) {
        runOnUiThread {
            when (response) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    loader.show("Initializing The App")
                    loader.updateMessage("Waiting for Parameters...")
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    observeData()
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    observeData()
                }
                is TdApi.AuthorizationStateReady -> {
                    navigateToMainApp()
                }
                is TdApi.Error -> {

                }
                is TdApi.AuthorizationStateClosing -> {

                }
                is TdApi.AuthorizationStateClosed -> {

                }
                else -> {
                }
            }
        }
    }
}