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
import com.aes.grammplayer.config.TestUserConfig
import com.aes.grammplayer.db.model.model.UserType
import com.aes.grammplayer.helper.DialogHelper
import com.aes.grammplayer.session.UserSession
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

    private lateinit var countryCodeEditText: EditText
    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var logCardView: CardView
    private lateinit var logTextView: TextView
    private lateinit var progressBar: ProgressBar

    // ✅ Use LoadingDialogManager instead of DialogHelper
    private lateinit var loader: DialogHelper

    private var isWaitingForCode = false
    private var isTestMode = false
    private var popupJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // ✅ Initialize LoadingDialogManager with supportFragmentManager
        loader = DialogHelper(supportFragmentManager)

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
            handleSubmit()
        }

        setupKeyboardActionListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ Ensure loading dialog is dismissed
        loader.dismiss()
        popupJob?.cancel()
    }

    private fun handleSubmit() {
        showPopup(true)

        if (isWaitingForCode) {
            val code = authCodeEditText.text.toString().trim()
            if (code.isNotEmpty()) {
                if (isTestMode) {
                    // ✅ Show loading dialog for test mode
                    loader.show("Verifying code...")
                    lifecycleScope.launch {
                        delay(800)
                        loader.updateMessage("Test code accepted — logging in...")
                        delay(500)
                        loader.updateMessage("Navigating to main app...")
                        delay(500)
                        loader.dismiss()
                        navigateToMainApp()
                    }
                    return
                }

                // ✅ Show loading dialog for real auth code submission
                loader.show("Submitting authentication code...")
                lifecycleScope.launch {
                    delay(1000)
                    loader.updateMessage("Processing authentication...")
                    delay(1500)
                    loader.dismiss()
                    TelegramClientManager.sendAuthCode(code)
                }
            } else {
                loader.show("Invalid Authentication Code!")
                lifecycleScope.launch {
                    delay(2000)
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

                if (UserSession.userType == UserType.TEST) {
                    isTestMode = true
                    loader.show("Test account detected...")
                    lifecycleScope.launch {
                        delay(800)
                        loader.updateMessage("Enter authentication code to continue")
                        delay(1000)
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

                // ✅ Show loading dialog for phone number submission
                loader.show("Validating phone number...")
                lifecycleScope.launch {
                    delay(1000)
                    loader.updateMessage("Sending verification code to $fullPhoneNumber...")
                    delay(1500)
                    loader.dismiss()
                    TelegramClientManager.sendPhoneNumber(fullPhoneNumber)
                }
            } else {
                loader.show("Invalid phone number!")
                lifecycleScope.launch {
                    delay(2000)
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
                    loader.show("Initializing TDLib...")
                    loader.updateMessage("Waiting for TDLib parameters...")
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    loader.dismiss()
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
                    loader.dismiss()
                    logMessage("Code verification required")
                    isWaitingForCode = true
                    showPopup(true)
                    countryCodeEditText.visibility = View.GONE
                    phoneNumberEditText.visibility = View.GONE
                    authCodeEditText.visibility = View.VISIBLE
                    submitButton.text = "Submit"
                    authCodeEditText.requestFocus()
                }
                is TdApi.AuthorizationStateReady -> {
                    loader.show("Login successful!")
                    lifecycleScope.launch {
                        delay(500)
                        loader.updateMessage("Initializing app...")
                        delay(1000)
                        navigateToMainApp()
                        loader.dismiss()
                    }
                }
                is TdApi.Error -> {
                    loader.show("❌ Error")
                    loader.updateMessage(response.message.toString())
                    logMessage(response.message.toString())
                    showPopup(true)
                    lifecycleScope.launch {
                        delay(3000)
                        loader.dismiss()
                    }
                }
                is TdApi.AuthorizationStateClosing -> {
                    loader.show("Closing session...")
                    loader.updateMessage("Please wait...")
                }
                is TdApi.AuthorizationStateClosed -> {
                    loader.updateMessage("Session closed")
                    lifecycleScope.launch {
                        delay(1000)
                        loader.dismiss()
                    }
                }
                else -> {
                    loader.updateMessage("Processing...")
                }
            }
        }
    }

    private fun navigateToMainApp() {
        loader.dismiss()
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