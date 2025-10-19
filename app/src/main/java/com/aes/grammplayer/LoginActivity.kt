package com.aes.grammplayer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import org.drinkless.tdlib.TdApi

class LoginActivity : FragmentActivity() {

    private lateinit var phoneNumberEditText: EditText
    private lateinit var authCodeEditText: EditText
    private lateinit var submitButton: Button
    private var isWaitingForCode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        phoneNumberEditText = findViewById(R.id.phoneNumberEditText)
        authCodeEditText = findViewById(R.id.authCodeEditText)
        submitButton = findViewById(R.id.submitButton)

        // Initialize the client. It automatically uses the global handler.
        TelegramClientManager.initialize()

        // Observe the authorization state from our central handler.
        TdLibUpdateHandler.authorizationState.observe(this) { authState ->
            handleAuthorizationState(authState)
        }

        submitButton.setOnClickListener {
            if (isWaitingForCode) {
                val code = authCodeEditText.text.toString().trim()
                if (code.isNotEmpty()) TelegramClientManager.sendAuthCode(code)
            } else {
                val phone = phoneNumberEditText.text.toString().trim()
                if (phone.isNotEmpty()) TelegramClientManager.sendPhoneNumber(phone)
            }
        }
    }

    private fun handleAuthorizationState(authState: TdApi.AuthorizationState) {
        when (authState) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                isWaitingForCode = false
                authCodeEditText.visibility = View.GONE
                phoneNumberEditText.visibility = View.VISIBLE
                submitButton.text = "Submit Phone Number"
            }
            is TdApi.AuthorizationStateWaitCode -> {
                isWaitingForCode = true
                phoneNumberEditText.visibility = View.GONE
                authCodeEditText.visibility = View.VISIBLE
                submitButton.text = "Submit Code"
                Toast.makeText(this, "Enter the code sent to you", Toast.LENGTH_SHORT).show()
            }
            is TdApi.AuthorizationStateReady -> {
                val intent = Intent(this, MainActivity::class.java).apply {
                    // Prevent the user from returning to this screen with the back button
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
            }
            // Handle other states if necessary
        }
    }
}
