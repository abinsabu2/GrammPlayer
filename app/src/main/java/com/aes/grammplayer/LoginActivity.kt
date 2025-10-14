package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isGone
import androidx.fragment.app.FragmentActivity
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LoginActivity : FragmentActivity() {

    // --- Class properties for views ---
    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var logScrollView: ScrollView
    private lateinit var logTextView: TextView

    // --- State property ---
    private var isWaitingForCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // --- Initialize view properties once ---
        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        authCodeEditText = findViewById(R.id.authCodeEditText)
        submitButton = findViewById(R.id.submitButton)
        logScrollView = findViewById(R.id.logScrollView)
        logTextView = findViewById(R.id.logTextView)

        // Initialize Telegram client
        TelegramClientManager.close()
        TelegramClientManager.initialize(::onResult)

        // Set a click listener on the submit button
        submitButton.setOnClickListener {
            submitButton.isEnabled = false // Prevent multiple clicks

            if (isWaitingForCode) {
                val code = authCodeEditText.text.toString().trim()
                if (code.isNotEmpty()) {
                    appendLog("Submitting code...")
                    TelegramClientManager.sendAuthCode(code, ::onResult)
                } else {
                    appendLog("Error: Authentication code cannot be empty.")
                    submitButton.isEnabled = true
                }
            } else {
                val phoneNumber = phoneNumberEditText.text.toString().trim()
                if (phoneNumber.isNotEmpty()) {
                    appendLog("Submitting phone number...")
                    TelegramClientManager.sendPhoneNumber(phoneNumber, ::onResult)
                } else {
                    appendLog("Error: Phone number cannot be empty.")
                    submitButton.isEnabled = true
                }
            }
        }
    }

    /**
     * Appends a new message to the log TextView and ensures it's visible.
     */
    private fun appendLog(message: String) {
        runOnUiThread {
            if (logScrollView.isGone) {
                logScrollView.visibility = View.VISIBLE
            }
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val currentLog = logTextView.text.toString()
            val newLog = if (currentLog.isEmpty()) "[$timestamp] $message" else "$currentLog\n[$timestamp] $message"
            logTextView.text = newLog
            logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    /**
     * Central handler for all TDLib results.
     */
    private fun onResult(update: TdApi.Object?) {
        when (update) {
            is TdApi.Error -> appendLog("Error: ${update.message}")
            is TdApi.UpdateAuthorizationState -> onAuthorizationStateUpdated(update.authorizationState)
            is TdApi.AuthorizationStateReady -> appendLog("Authorization completed")
            is TdApi.Ok -> appendLog("Success!")
            else -> Log.d("TDLib", "Result: $update")
        }
    }

    /**
     * Handles UI changes based on the current authorization state.
     */
    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        runOnUiThread {
            when (state) {
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    isWaitingForCode = false
                    phoneNumberEditText.visibility = View.VISIBLE
                    authCodeEditText.visibility = View.GONE
                    submitButton.isEnabled = true
                    appendLog("Please enter your phone number.")
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    isWaitingForCode = true
                    phoneNumberEditText.visibility = View.GONE // Hide phone field
                    authCodeEditText.visibility = View.VISIBLE   // Show code field
                    submitButton.isEnabled = true
                    appendLog("Success! Please check your messages for the code.")
                }
                is TdApi.AuthorizationStateReady -> {
                    appendLog("Authorization successful! You are logged in.")
                    // TODO: Navigate to the main part of your application
                    val intent = Intent(this, MainActivity::class.java).apply {
                        // Prevent the user from returning to this activity with the back button
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                }
                else -> Log.d("TDLib", "Unhandled Auth State: $state")
            }
        }
    }
}
