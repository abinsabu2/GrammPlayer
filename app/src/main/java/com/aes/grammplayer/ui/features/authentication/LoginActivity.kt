package com.aes.grammplayer.ui.features.authentication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aes.grammplayer.ui.features.dashboard.MainActivity
import com.aes.grammplayer.R
import com.aes.grammplayer.config.TestUserConfig
import com.aes.grammplayer.db.model.model.UserType
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.helper.HistoryHelper
import com.aes.grammplayer.session.UserSession
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

import kotlin.time.Duration.Companion.milliseconds

class LoginActivity : FragmentActivity() {

    private lateinit var countryCodeEditText: EditText
    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var logCardView: CardView
    private lateinit var logTextView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var loader: DialogHelper

    private var isWaitingForCode = false
    private var isTestMode = false
    private var popupJob: Job? = null

    // Tracks the in-flight delayed loader-message sequence so a new auth state
    // can cancel a stale one instead of racing it (fixes overlapping updateMessage/dismiss calls).
    private var loaderSequenceJob: Job? = null

    private lateinit var settingsDataStore: SettingsDataStore

    companion object {
        private const val TAG = "LoginActivity"
        private const val DELAY_SHORT = 500L
        private const val DELAY_MEDIUM = 1000L
        private const val DELAY_LONG = 1500L
        private const val DELAY_ERROR_DISPLAY = 3000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsDataStore = SettingsDataStore(this)

        // Observe real auth state transitions (WaitPhoneNumber, WaitCode, Ready, etc.)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.authorizationState.collect { state ->
                    if (!isTestMode) state?.let { handleAuthorizationState(it) }
                }
            }
        }

        // Observe TDLib errors separately — these are a different type (TdApi.Error),
        // not an AuthorizationState, so they need their own handler.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.authError.collect { error ->
                    if (!isTestMode) handleAuthError(error)
                }
            }
        }

        loader = DialogHelper(supportFragmentManager)
        setContentView(R.layout.activity_login)

        countryCodeEditText = findViewById(R.id.countryCodeEditText)
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        authCodeEditText = findViewById(R.id.authCodeEditText)
        submitButton = findViewById(R.id.submitButton)
        logCardView = findViewById(R.id.logCardView)
        logTextView = findViewById(R.id.logTextView)
        progressBar = findViewById(R.id.progressBar)

        if (!isTestMode) {
            if (!TelegramClientManager.isInitialized) {
                TelegramClientManager.initialize()
            }
        }

        submitButton.setOnClickListener {
            lifecycleScope.launch {
                handleSubmit()
            }
        }
        setupKeyboardActionListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        loader.dismiss()
        popupJob?.cancel()
        loaderSequenceJob?.cancel()
    }

    private suspend fun handleSubmit() {
        showPopup(true)

        if (isWaitingForCode) {
            val code = authCodeEditText.text.toString().trim()
            if (code.isNotEmpty()) {
                if (isTestMode) {
                    settingsDataStore.setTestMode(true)
                    loader.show("Verifying code...")
                    lifecycleScope.launch {
                        delay(DELAY_LONG.milliseconds)
                        loader.updateMessage("Test code accepted — logging in...")
                        delay(DELAY_LONG.milliseconds)
                        loader.updateMessage("Navigating to main app...")
                        delay(DELAY_LONG.milliseconds)
                        loader.dismiss()
                        navigateToMainApp()
                    }
                    return
                }

                loader.show("Submitting authentication code...")
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.updateMessage("Processing authentication...")
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                    TelegramClientManager.sendAuthCode(code)
                }
            } else {
                loader.show("Invalid Authentication Code!")
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                }
            }
        } else {
            val countryCode = countryCodeEditText.text.toString().trim()
            val countryCodeCleaned = "+${countryCode.replace("+", "")}"
            val phone = phoneNumberEditText.text.toString().trim()
            val fullPhoneNumber = countryCodeCleaned + phone

            if (countryCode.isNotEmpty() && phone.isNotEmpty()) {
                UserSession.initialize(fullPhoneNumber)
                UserSession.userType = if (TestUserConfig.isTestUser(fullPhoneNumber)) UserType.TEST else UserType.REAL
                lifecycleScope.launch {
                    HistoryHelper.persistActivePhone(applicationContext, fullPhoneNumber)
                }
                settingsDataStore.setTestMode(false)
                if (UserSession.userType == UserType.TEST) {
                    isTestMode = true
                    loader.show("Test account detected...")
                    lifecycleScope.launch {
                        delay(DELAY_LONG.milliseconds)
                        loader.updateMessage("Enter authentication code to continue")
                        delay(DELAY_LONG.milliseconds)
                        loader.dismiss()
                    }
                    isWaitingForCode = true
                    countryCodeEditText.visibility = View.GONE
                    phoneNumberEditText.visibility = View.GONE
                    authCodeEditText.visibility = View.VISIBLE
                    authCodeEditText.setText("12345")
                    authCodeEditText.requestFocus()
                    return
                }

                loader.show("Validating phone number...")
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.updateMessage("Sending verification code to $fullPhoneNumber...")
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                    TelegramClientManager.sendPhoneNumber(fullPhoneNumber)
                }
            } else {
                loader.show("Invalid phone number!")
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private fun showPopup(show: Boolean) {
        if (show) {
            logCardView.visibility = View.VISIBLE
            popupJob?.cancel()
            popupJob = lifecycleScope.launch {
                delay(DELAY_LONG.milliseconds)
                logCardView.visibility = View.GONE
            }
        }
    }

    /**
     * Handles AuthorizationState transitions only. TdApi.Error is handled separately
     * in handleAuthError, since it's not part of the AuthorizationState hierarchy.
     *
     * Note: runOnUiThread was removed — lifecycleScope.launch already dispatches on
     * Dispatchers.Main, so this was already running on the UI thread; the extra
     * wrapper was a redundant nested dispatch.
     */
    @SuppressLint("SetTextI18n")
    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        // Cancel any pending delayed loader updates from a previous state before reacting to the new one,
        // so fast-firing transitions (e.g. WaitPhoneNumber -> WaitCode on session resume) don't race each other.
        loaderSequenceJob?.cancel()

        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                loader.show("Initializing TDLib...")
                loader.updateMessage("Waiting for TDLib parameters...")
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                loader.dismiss()
                loaderSequenceJob = lifecycleScope.launch {
                    delay(DELAY_SHORT.milliseconds)
                    loader.updateMessage("Loading Login Screen")
                    delay(DELAY_MEDIUM.milliseconds)
                    loader.dismiss()
                }
                isWaitingForCode = false
                countryCodeEditText.visibility = View.VISIBLE
                phoneNumberEditText.visibility = View.VISIBLE
                authCodeEditText.visibility = View.GONE
                submitButton.text = "Submit"
                countryCodeEditText.requestFocus()
            }

            is TdApi.AuthorizationStateWaitCode -> {
                loader.dismiss()
                loaderSequenceJob = lifecycleScope.launch {
                    loader.updateMessage("Loading Login Screen")
                    loader.dismiss()
                }
                isWaitingForCode = true
                countryCodeEditText.visibility = View.GONE
                phoneNumberEditText.visibility = View.GONE
                authCodeEditText.visibility = View.VISIBLE
                submitButton.text = "Submit"
                authCodeEditText.requestFocus()
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                // Two-step verification (2FA). Without this branch, 2FA users previously
                // fell into `else` and got stuck on a "Processing..." message forever.
                // NOTE: there's no password input view in activity_login.xml yet — this
                // just unblocks the state machine and logs it. You'll want a real password
                // EditText + submit path wired to TelegramClientManager.sendAuthPassword(...)
                // (or whatever your manager's equivalent call is named) before this is usable.
                loader.dismiss()
                Log.w(TAG, "2FA required (AuthorizationStateWaitPassword) — no password UI wired up yet")
            }

            is TdApi.AuthorizationStateReady -> {
                loader.dismiss()
                loader.show("Login successful!")
                loaderSequenceJob = lifecycleScope.launch {
                    delay(DELAY_SHORT.milliseconds)
                    loader.updateMessage("Initializing app...")
                    delay(DELAY_MEDIUM.milliseconds)
                    navigateToMainApp()
                    loader.dismiss()
                }
            }

            is TdApi.AuthorizationStateClosing -> {
                loader.show("Closing session...")
                loader.updateMessage("Please wait...")
            }

            is TdApi.AuthorizationStateClosed -> {
                loader.updateMessage("Session closed")
                loaderSequenceJob = lifecycleScope.launch {
                    delay(DELAY_MEDIUM.milliseconds)
                    loader.dismiss()
                }
            }

            else -> {
                // Logged instead of silently showing "Processing..." so any future/unhandled
                // AuthorizationState subtype (e.g. WaitEmailAddress, WaitEmailCode,
                // WaitOtherDeviceConfirmation) is noticeable during testing rather than
                // leaving the user on a vague spinner.
                Log.w(TAG, "Unhandled authorization state: ${state.javaClass.simpleName}")
                loader.updateMessage("Processing...")
            }
        }
    }

    /**
     * Handles TdApi.Error emissions (e.g. invalid phone number, flood wait, invalid code).
     * Previously this silently dismissed the loader after 3s with no message shown to the user.
     */
    private fun handleAuthError(error: TdApi.Error) {
        Log.e(TAG, "TDLib error ${error.code}: ${error.message}")
        loaderSequenceJob?.cancel()
        loader.show(error.message ?: "Something went wrong. Please try again.")
        loaderSequenceJob = lifecycleScope.launch {
            delay(DELAY_ERROR_DISPLAY.milliseconds)
            loader.dismiss()
        }
    }

    private fun navigateToMainApp() {
        loader.dismiss()
        lifecycleScope.launch {
            HistoryHelper.persistActivePhone(applicationContext, UserSession.phoneNumber)
            HistoryHelper.syncActiveUser(applicationContext)
            val intent = Intent(this@LoginActivity, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
        }
    }

    private fun setupKeyboardActionListeners() {
        countryCodeEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) hideKeyboard()
            false
        }
        phoneNumberEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) hideKeyboard()
            false
        }
        authCodeEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) hideKeyboard()
            false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

}