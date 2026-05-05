package com.aes.grammplayer.ui.features.authentication

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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
import com.aes.grammplayer.util.tdlib.TdLibUpdateHandler
import com.aes.grammplayer.util.tdlib.TelegramClientManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : FragmentActivity() {

    companion object {
        private const val TEST_PHONE_NUMBER = "+00123456789"
    }

    private lateinit var countryCodeEditText: EditText
    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var logCardView: CardView
    private lateinit var logTextView: TextView
    private lateinit var progressBar: ProgressBar

    private var isWaitingForCode = false
    private var isTestMode = false
    private var popupJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Bind all views
        countryCodeEditText = findViewById(R.id.countryCodeEditText)
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        authCodeEditText = findViewById(R.id.authCodeEditText)
        submitButton = findViewById(R.id.submitButton)
        logCardView = findViewById(R.id.logCardView)
        logTextView = findViewById(R.id.logTextView)
        progressBar = findViewById(R.id.progressBar)
        // Initialize the real Telegram client only when not in test mode
        if (!isTestMode) {
            if (!TelegramClientManager.isInitialized) {
                TelegramClientManager.initialize()
            }
        }

        // Observe real auth states
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.authError.collect { response ->
                    if (!isTestMode) handleAuthorizationState(response)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TdLibUpdateHandler.authorizationState.collect { response ->
                    if (!isTestMode) handleAuthorizationState(response)
                }
            }
        }

        submitButton.setOnClickListener {
            handleRealModeSubmit()
        }

        setupKeyboardActionListeners()
    }

    // -------------------------------------------------------------------------
    // Shared helpers (unchanged)
    // -------------------------------------------------------------------------

    private fun handleRealModeSubmit() {
        showPopup(true)
        if (isWaitingForCode) {
            val code = authCodeEditText.text.toString().trim()
            if (code.isNotEmpty()) {
                if (isTestMode) {
                    logMessage("Test code accepted — logging in...")
                    lifecycleScope.launch {
                        delay(800)
                        navigateToMainApp()
                    }
                    return
                }
                logMessage("Submitting code...")
                lifecycleScope.launch {
                    delay(5000)
                    TelegramClientManager.sendAuthCode(code)
                }
            } else {
                logMessage("Invalid Authentication Code!")
            }
        } else {
            val countryCode = countryCodeEditText.text.toString().trim()
            val countryCodeCleaned = "+${countryCode.replace("+", "")}"
            val phone = phoneNumberEditText.text.toString().trim()
            val fullPhoneNumber = countryCodeCleaned + phone
            if (countryCode.isNotEmpty() && phone.isNotEmpty()) {
                if (fullPhoneNumber == TEST_PHONE_NUMBER) {
                    isTestMode = true
                    logMessage("Test account detected — enter code to continue")
                    isWaitingForCode = true
                    countryCodeEditText.visibility = View.GONE
                    phoneNumberEditText.visibility = View.GONE
                    authCodeEditText.visibility = View.VISIBLE
                    authCodeEditText.setText("12345")
                    authCodeEditText.requestFocus()
                    showPopup(true)
                    return
                }
                logMessage("Processing: $fullPhoneNumber")
                lifecycleScope.launch {
                    delay(1000)
                    TelegramClientManager.sendPhoneNumber(fullPhoneNumber)
                }
            } else {
                logMessage("Invalid phone number!")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared helpers (unchanged)
    // -------------------------------------------------------------------------

    private fun showPopup(show: Boolean) {
        popupJob?.cancel()
        if (show) {
            logCardView.visibility = View.VISIBLE
            popupJob = lifecycleScope.launch {
                delay(3000)
                logCardView.visibility = View.GONE
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun handleAuthorizationState(response: TdApi.Object?) {
        runOnUiThread {
            when (response) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    logMessage("Waiting for TDLib parameters...")
                    showPopup(true)
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    logMessage("Initiating the Login Process")
                    isWaitingForCode = false
                    showPopup(true)
                    countryCodeEditText.visibility = View.VISIBLE
                    phoneNumberEditText.visibility = View.VISIBLE
                    authCodeEditText.visibility = View.GONE
                    submitButton.text = "Submit"
                    countryCodeEditText.requestFocus()
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    logMessage("Waiting for code.")
                    isWaitingForCode = true
                    showPopup(true)
                    countryCodeEditText.visibility = View.GONE
                    phoneNumberEditText.visibility = View.GONE
                    authCodeEditText.visibility = View.VISIBLE
                    submitButton.text = "Submit"
                    authCodeEditText.requestFocus()
                }
                is TdApi.AuthorizationStateReady -> {
                    showPopup(false)
                    logMessage("Authentication Completed")
                    showPopup(true)
                    navigateToMainApp()
                }
                is TdApi.Error -> {
                    logMessage(response.message.toString())
                    showPopup(true)
                }
                is TdApi.AuthorizationStateClosing -> {
                    logMessage("Closing session...")
                    showPopup(true)
                }
                is TdApi.AuthorizationStateClosed -> {
                    logMessage("Session closed.")
                    showPopup(true)
                }
                else -> {
                    logMessage("Unhandled auth state")
                    showPopup(true)
                }
            }
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
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

    private fun logMessage(message: String) {
        runOnUiThread {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            logTextView.text = message
        }
    }
}