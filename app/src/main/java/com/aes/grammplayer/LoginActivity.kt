package com.aes.grammplayer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : FragmentActivity() {

    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView

    private var isWaitingForCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Bind all views
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        authCodeEditText = findViewById(R.id.authCodeEditText)
        submitButton = findViewById(R.id.submitButton)
        logScrollView = findViewById(R.id.logScrollView)
        logTextView = findViewById(R.id.logTextView)

        // Initialize the client, which uses the global handler
        if (!TelegramClientManager.isInitialized) {
            TelegramClientManager.initialize()
            logMessage("Client Initializing...")
        }

        // Observe the authorization state from our central handler
        TdLibUpdateHandler.authorizationState.observe(this) { authState ->
            handleAuthorizationState(authState)
        }

        // Setup button click listener
        submitButton.setOnClickListener {
            if (isWaitingForCode) {
                val code = authCodeEditText.text.toString().trim()
                if (code.isNotEmpty()) {
                    logMessage("Submitting code...")
                    TelegramClientManager.sendAuthCode(code)
                }
            } else {
                val phone = phoneNumberEditText.text.toString().trim()
                if (phone.isNotEmpty()) {
                    logMessage("Submitting phone number...")
                    TelegramClientManager.sendPhoneNumber(phone)
                }
            }
        }

        // --- NEW: SET UP KEYBOARD LISTENERS ---
        setupKeyboardActionListeners()
    }

    private fun handleAuthorizationState(authState: TdApi.AuthorizationState) {
        runOnUiThread {
            when (authState) {
                is TdApi.AuthorizationStateWaitTdlibParameters -> {
                    logMessage("Waiting for TDLib parameters...")
                }
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    logMessage("Please enter your phone number.")
                    isWaitingForCode = false
                    phoneNumberEditText.visibility = View.VISIBLE
                    authCodeEditText.visibility = View.GONE
                    submitButton.text = "Submit Phone Number"
                    phoneNumberEditText.requestFocus()
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    logMessage("Please enter the code sent to you.")
                    isWaitingForCode = true
                    phoneNumberEditText.visibility = View.GONE
                    authCodeEditText.visibility = View.VISIBLE
                    submitButton.text = "Submit Code"
                    authCodeEditText.requestFocus()
                }
                is TdApi.AuthorizationStateReady -> {
                    logMessage("Login Successful! Navigating to main screen.")
                    Toast.makeText(this, "Login Successful!", Toast.LENGTH_LONG).show()
                    navigateToMainApp()
                }
                is TdApi.AuthorizationStateClosing -> logMessage("Closing session...")
                is TdApi.AuthorizationStateClosed -> logMessage("Session closed.")
                else -> logMessage("Unhandled auth state: ${authState.javaClass.simpleName}")
            }
        }
    }

    private fun navigateToMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
    }

    /**
     * Sets up listeners on the EditText fields to handle IME actions (like "Next" and "Done").
     */
    private fun setupKeyboardActionListeners() {
        // Listener for the Phone Number field
        phoneNumberEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                // When "Next" is pressed, simulate a click on the submit button.
                // The auth state handler will then move focus to the code field.
                submitButton.performClick()
                return@setOnEditorActionListener true
            }
            false
        }

        // Listener for the Auth Code field
        authCodeEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                // When "Done" is pressed, hide the keyboard and click the submit button.
                hideKeyboard()
                submitButton.performClick()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    /**
     * A common function to hide the soft keyboard.
     */
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // Find the currently focused view to get the correct window token.
        val view = currentFocus ?: View(this)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Appends a formatted message to the on-screen log area.
     */
    private fun logMessage(message: String) {
        runOnUiThread {
            if (logScrollView.visibility == View.GONE) {
                logScrollView.visibility = View.VISIBLE
            }
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val currentLog = logTextView.text.toString()
            val newLog = if (currentLog.isEmpty()) "[$timestamp] $message" else "$currentLog\n[$timestamp] $message"
            logTextView.text = newLog

            // Auto-scroll to the bottom
            logScrollView.post {
                logScrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }
}
