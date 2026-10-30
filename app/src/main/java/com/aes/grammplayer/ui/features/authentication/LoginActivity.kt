package com.aes.grammplayer.ui.features.authentication

import android.content.Context
import com.aes.grammplayer.helper.LocaleHelper
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
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

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    private lateinit var countryCodeEditText: EditText
    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var phoneStepHintTextView: TextView
    private lateinit var qrImageView: ImageView
    private lateinit var qrInstructionsTextView: TextView
    private lateinit var logCardView: CardView
    private lateinit var logTextView: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var loader: DialogHelper

    private var isWaitingForCode = false
    private var isWaitingForPassword = false
    private var currentStep: LoginStep? = null

    // Set when the user submits a phone number while a QR code is live; sent once
    // TelegramClientManager.restartLogin() brings TDLib back to WaitPhoneNumber.
    private var pendingPhoneNumber: String? = null

    // True while that phone number is in flight; if TDLib rejects it, the QR code is requested again.
    private var restoreQrOnPhoneError = false
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
        private const val QR_SIZE_PX = 512
    }

    private enum class LoginStep { PHONE, CODE, PASSWORD }

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
        passwordEditText = findViewById(R.id.passwordEditText)
        submitButton = findViewById(R.id.submitButton)
        phoneStepHintTextView = findViewById(R.id.phoneStepHintTextView)
        qrImageView = findViewById(R.id.qrImageView)
        qrInstructionsTextView = findViewById(R.id.qrInstructionsTextView)
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

        if (isWaitingForPassword) {
            // Not trimmed: spaces can be part of a 2FA password.
            val password = passwordEditText.text.toString()
            if (password.isNotEmpty()) {
                loader.show(getString(R.string.login_checking_password))
                TelegramClientManager.sendAuthPassword(password)
            } else {
                loader.show(getString(R.string.login_invalid_password))
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                }
            }
        } else if (isWaitingForCode) {
            val code = authCodeEditText.text.toString().trim()
            if (code.isNotEmpty()) {
                if (isTestMode) {
                    settingsDataStore.setTestMode(true)
                    loader.show(getString(R.string.login_verifying_code))
                    lifecycleScope.launch {
                        delay(DELAY_LONG.milliseconds)
                        loader.updateMessage(getString(R.string.login_test_code_accepted))
                        delay(DELAY_LONG.milliseconds)
                        loader.updateMessage(getString(R.string.login_navigating))
                        delay(DELAY_LONG.milliseconds)
                        loader.dismiss()
                        navigateToMainApp()
                    }
                    return
                }

                loader.show(getString(R.string.login_submitting_code))
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.updateMessage(getString(R.string.login_processing))
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                    TelegramClientManager.sendAuthCode(code)
                }
            } else {
                loader.show(getString(R.string.login_invalid_code))
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
                    loader.show(getString(R.string.login_test_account))
                    lifecycleScope.launch {
                        delay(DELAY_LONG.milliseconds)
                        loader.updateMessage(getString(R.string.login_enter_code_to_continue))
                        delay(DELAY_LONG.milliseconds)
                        loader.dismiss()
                    }
                    showLoginStep(LoginStep.CODE)
                    showQrPanel(null, R.string.qr_unavailable)
                    authCodeEditText.setText("12345")
                    return
                }

                loader.show(getString(R.string.login_validating_phone))
                lifecycleScope.launch {
                    delay(DELAY_LONG.milliseconds)
                    loader.updateMessage(getString(R.string.login_sending_code, fullPhoneNumber))
                    delay(DELAY_LONG.milliseconds)
                    loader.dismiss()
                    if (TdLibUpdateHandler.authorizationState.value is TdApi.AuthorizationStateWaitOtherDeviceConfirmation) {
                        // QR code is live: TDLib refuses phone login until the session restarts.
                        pendingPhoneNumber = fullPhoneNumber
                        TelegramClientManager.restartLogin()
                    } else {
                        TelegramClientManager.sendPhoneNumber(fullPhoneNumber)
                    }
                }
            } else {
                loader.show(getString(R.string.login_invalid_phone))
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
        restoreQrOnPhoneError = false

        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                loader.show(getString(R.string.login_initializing_tdlib))
                loader.updateMessage(getString(R.string.login_waiting_tdlib))
            }

            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                loader.dismiss()
                val pendingPhone = pendingPhoneNumber
                if (pendingPhone != null) {
                    // Back from restartLogin(): finish the phone login the user asked for.
                    pendingPhoneNumber = null
                    showQrPanel(null, R.string.qr_unavailable)
                    loader.show(getString(R.string.login_sending_code, pendingPhone))
                    restoreQrOnPhoneError = true
                    TelegramClientManager.sendPhoneNumber(pendingPhone)
                    return
                }
                loaderSequenceJob = lifecycleScope.launch {
                    delay(DELAY_SHORT.milliseconds)
                    loader.updateMessage(getString(R.string.login_loading_screen))
                    delay(DELAY_MEDIUM.milliseconds)
                    loader.dismiss()
                }
                showLoginStep(LoginStep.PHONE)
                showQrPanel(null, R.string.qr_generating)
                TelegramClientManager.requestQrLogin()
            }

            is TdApi.AuthorizationStateWaitCode -> {
                loader.dismiss()
                loaderSequenceJob = lifecycleScope.launch {
                    loader.updateMessage(getString(R.string.login_loading_screen))
                    loader.dismiss()
                }
                showLoginStep(LoginStep.CODE)
                showQrPanel(null, R.string.qr_unavailable)
            }

            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> {
                // QR login. TDLib re-emits this state with a fresh link when the old one expires.
                loader.dismiss()
                showLoginStep(LoginStep.PHONE)
                showQrPanel(state.link, R.string.qr_login_instructions)
            }

            is TdApi.AuthorizationStateWaitPassword -> {
                // Two-step verification (2FA), reached after the code or after a QR scan.
                loader.dismiss()
                showLoginStep(LoginStep.PASSWORD)
                showQrPanel(null, R.string.qr_unavailable)
                // The account's own hint goes in the wrapping step text; the field is one line.
                state.passwordHint.takeIf { !it.isNullOrBlank() }?.let {
                    phoneStepHintTextView.text =
                        getString(R.string.login_password_hint_format, getString(R.string.login_password_step_hint), it)
                }
            }

            is TdApi.AuthorizationStateReady -> {
                loader.dismiss()
                loader.show(getString(R.string.login_success))
                loaderSequenceJob = lifecycleScope.launch {
                    // QR login skips the phone step; per-user history is keyed by phone number.
                    if (UserSession.phoneNumber.isBlank()) {
                        TelegramClientManager.getMyPhoneNumber()?.let { UserSession.initialize(it) }
                    }
                    delay(DELAY_SHORT.milliseconds)
                    loader.updateMessage(getString(R.string.login_initializing_app))
                    delay(DELAY_MEDIUM.milliseconds)
                    navigateToMainApp()
                    loader.dismiss()
                }
            }

            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing -> {
                loader.show(if (pendingPhoneNumber != null) getString(R.string.login_switching_to_phone) else getString(R.string.login_closing_session))
                loader.updateMessage(getString(R.string.please_wait))
            }

            is TdApi.AuthorizationStateClosed -> {
                loader.updateMessage(getString(R.string.login_session_closed))
                loaderSequenceJob = lifecycleScope.launch {
                    delay(DELAY_MEDIUM.milliseconds)
                    loader.dismiss()
                }
            }

            else -> {
                // Logged instead of silently showing getString(R.string.login_processing_generic) so any future/unhandled
                // AuthorizationState subtype (e.g. WaitEmailAddress, WaitEmailCode)
                // is noticeable during testing rather than
                // leaving the user on a vague spinner.
                Log.w(TAG, "Unhandled authorization state: ${state.javaClass.simpleName}")
                loader.updateMessage(getString(R.string.login_processing_generic))
            }
        }
    }

    /**
     * Shows the left panel's inputs for [step]. Skipped when unchanged, so a QR link
     * refresh doesn't pull focus away from a half-typed phone number.
     */
    @SuppressLint("SetTextI18n")
    private fun showLoginStep(step: LoginStep) {
        if (step == currentStep) return
        currentStep = step
        isWaitingForCode = step == LoginStep.CODE
        isWaitingForPassword = step == LoginStep.PASSWORD
        val phoneVisibility = if (step == LoginStep.PHONE) View.VISIBLE else View.GONE
        countryCodeEditText.visibility = phoneVisibility
        phoneNumberEditText.visibility = phoneVisibility
        authCodeEditText.visibility = if (isWaitingForCode) View.VISIBLE else View.GONE
        passwordEditText.visibility = if (isWaitingForPassword) View.VISIBLE else View.GONE
        phoneStepHintTextView.setText(
            when (step) {
                LoginStep.PHONE -> R.string.login_phone_step_hint
                LoginStep.CODE -> R.string.login_code_step_hint
                LoginStep.PASSWORD -> R.string.login_password_step_hint
            }
        )
        submitButton.setText(R.string.submit)
        when (step) {
            LoginStep.PHONE -> countryCodeEditText
            LoginStep.CODE -> authCodeEditText
            LoginStep.PASSWORD -> passwordEditText
        }.requestFocus()
    }

    /** Right panel: shows [link] as a QR code (or nothing when null) above [message]. */
    private fun showQrPanel(link: String?, @StringRes message: Int) {
        if (link != null) qrImageView.setImageBitmap(QrCodeBitmap.create(link, QR_SIZE_PX))
        qrImageView.visibility = if (link != null) View.VISIBLE else View.GONE
        qrInstructionsTextView.setText(message)
    }

    /**
     * Handles TdApi.Error emissions (e.g. invalid phone number, flood wait, invalid code).
     * Previously this silently dismissed the loader after 3s with no message shown to the user.
     */
    private fun handleAuthError(error: TdApi.Error) {
        Log.e(TAG, "TDLib error ${error.code}: ${error.message}")
        loaderSequenceJob?.cancel()
        loader.show(error.message ?: getString(R.string.login_generic_error))
        if (restoreQrOnPhoneError) {
            restoreQrOnPhoneError = false
            showQrPanel(null, R.string.qr_generating)
            TelegramClientManager.requestQrLogin()
        }
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
        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
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