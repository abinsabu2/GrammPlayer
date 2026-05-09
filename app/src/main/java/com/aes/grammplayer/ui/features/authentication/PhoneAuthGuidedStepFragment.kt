package com.aes.grammplayer.ui.features.authentication

import android.content.Intent
import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.R
import com.aes.grammplayer.ui.features.dashboard.MainActivity
import com.aes.grammplayer.db.view.SettingsViewModel

import com.aes.grammplayer.helper.LoginHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PhoneAuthGuidedStepFragment : GuidedStepSupportFragment() {

    private val viewModel: SettingsViewModel by lazy {
        ViewModelProvider(requireActivity())[SettingsViewModel::class.java]
    }

    // Data holders
    private var countryCode: String = ""
    private var phoneNumber: String = ""
    private var authCode: String = ""
    private var currentStep = STEP_PHONE  // 0 = Phone, 1 = Auth Code, 2 = Login

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = when (currentStep) {
            STEP_PHONE -> "Enter Phone Number"
            STEP_AUTH_CODE -> "Verify Your Code"
            STEP_LOGIN -> "Login Complete"
            else -> "Authentication"
        }

        val description = when (currentStep) {
            STEP_PHONE -> "Enter country code and phone number"
            STEP_AUTH_CODE -> "Enter the verification code sent to your phone"
            STEP_LOGIN -> "You're all set! Ready to continue?"
            else -> "Phone Number Authentication"
        }

        return Guidance(
            title,
            description,
            "gPlayer",
            requireActivity().getDrawable(R.drawable.ic_music_note)
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        when (currentStep) {
            STEP_PHONE -> createPhoneActions(actions)
            STEP_AUTH_CODE -> createAuthCodeActions(actions)
            STEP_LOGIN -> createLoginActions(actions)
        }
    }

    // ==================== STEP 1: Phone Number ====================
    private fun createPhoneActions(actions: MutableList<GuidedAction>) {
        // Country Code Input with clear hint
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_COUNTRY_CODE)
                .title("Country Code")
                .description("e.g., +1 (USA), +44 (UK), +91 (India)")
                .editTitle(countryCode)  // ← SET INITIAL VALUE
                .descriptionEditable(true)
                .infoOnly(false)
                .editable(true)
                .editInputType(android.text.InputType.TYPE_CLASS_PHONE)
                .build()
        )

        // Phone Number Input
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_PHONE_NUMBER)
                .title("Phone Number")
                .description("e.g., 9876543210")
                .editTitle(phoneNumber)  // ← SET INITIAL VALUE
                .descriptionEditable(true)
                .infoOnly(false)
                .editable(true)
                .editInputType(android.text.InputType.TYPE_CLASS_PHONE)
                .build()
        )

        // Next Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_NEXT)
                .title("Next")
                .description("Continue to verification")
                .build()
        )

        // Cancel Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CANCEL)
                .title("Cancel")
                .build()
        )
    }

    // ==================== STEP 2: Auth Code ====================
    private fun createAuthCodeActions(actions: MutableList<GuidedAction>) {
        // Normalize country code for display (ensure + prefix)
        val displayCountryCode = normalizeCountryCode(countryCode)

        // Display phone number (read-only)
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_PHONE_DISPLAY)
                .title("Phone Number")
                .description("$displayCountryCode $phoneNumber")
                .infoOnly(true)
                .build()
        )

        // Auth Code Input
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_AUTH_CODE)
                .title("Verification Code")
                .description("Enter the 6-digit code sent to your phone")
                .editTitle(authCode)  // ← SET INITIAL VALUE
                .descriptionEditable(true)
                .infoOnly(false)
                .editable(true)
                .editInputType(android.text.InputType.TYPE_CLASS_NUMBER)
                .build()
        )

        // Next Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_NEXT)
                .title("Verify")
                .description("Confirm your code")
                .build()
        )

        // Previous Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_PREVIOUS)
                .title("Previous")
                .build()
        )

        // Cancel Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CANCEL)
                .title("Cancel")
                .build()
        )
    }

    // ==================== STEP 3: Login Complete ====================
    private fun createLoginActions(actions: MutableList<GuidedAction>) {
        // Normalize country code for display
        val displayCountryCode = normalizeCountryCode(countryCode)
        val fullNumber = "$displayCountryCode$phoneNumber"

        // Summary: Country Code
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SUMMARY_COUNTRY)
                .title("Country Code")
                .description(displayCountryCode)
                .infoOnly(true)
                .build()
        )

        // Summary: Phone Number
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SUMMARY_PHONE)
                .title("Phone Number")
                .description(phoneNumber)
                .infoOnly(true)
                .build()
        )

        // Summary: Full Number
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SUMMARY_FULL)
                .title("Full Number")
                .description(fullNumber)
                .infoOnly(true)
                .build()
        )

        // Summary: Auth Code Status
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_SUMMARY_AUTH)
                .title("Verification Status")
                .description("✓ Verified")
                .infoOnly(true)
                .build()
        )

        // Complete Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_COMPLETE)
                .title("Complete & Login")
                .description("Finish authentication")
                .build()
        )

        // Previous Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_PREVIOUS)
                .title("Previous")
                .build()
        )

        // Cancel Button
        actions.add(
            GuidedAction.Builder(requireContext())
                .id(ACTION_CANCEL)
                .title("Cancel")
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_NEXT -> {
                when (currentStep) {
                    STEP_PHONE -> {
                        // Capture values from current actions before validating
                        val currentActions = actions
                        for (act in currentActions) {
                            when (act.id) {
                                ACTION_COUNTRY_CODE -> countryCode = act.editTitle.toString().trim()
                                ACTION_PHONE_NUMBER -> phoneNumber = act.editTitle.toString().trim()
                            }
                        }

                        if (validatePhoneStep()) {
                            lifecycleScope.launch {
                                delay(1000)
                                LoginHelper.sendPhoneNumber(normalizeCountryCode(countryCode) + phoneNumber)
                            }
                            currentStep = STEP_AUTH_CODE
                            updateFragment()
                        }
                    }
                    STEP_AUTH_CODE -> {
                        // Capture auth code before validating
                        val currentActions = actions
                        for (act in currentActions) {
                            if (act.id == ACTION_AUTH_CODE) {
                                authCode = act.editTitle.toString().trim()
                            }
                        }

                        if (validateAuthCodeStep()) {
                            currentStep = STEP_LOGIN
                            updateFragment()
                        }
                    }
                }
            }
            ACTION_PREVIOUS -> {
                when (currentStep) {
                    STEP_AUTH_CODE -> {
                        currentStep = STEP_PHONE
                        updateFragment()
                    }
                    STEP_LOGIN -> {
                        currentStep = STEP_AUTH_CODE
                        updateFragment()
                    }
                }
            }

            // Complete Action
            ACTION_COMPLETE -> {
                completeAuthentication()
            }

            // Cancel Action
            ACTION_CANCEL -> {
                requireActivity().finish()
            }
        }
    }

    /**
     * Validate phone number step
     */
    private fun validatePhoneStep(): Boolean {
        return when {
            countryCode.isEmpty() -> {
                showError("Please enter country code (e.g., +1 or 1)")
                false
            }
            !isValidCountryCode(countryCode) -> {
                showError("Invalid country code. Use format: +1 or just 1")
                false
            }
            phoneNumber.isEmpty() -> {
                showError("Please enter your phone number")
                false
            }
            !isValidPhoneNumber(phoneNumber) -> {
                showError("Phone number must be at least 10 digits")
                false
            }
            else -> true
        }
    }

    /**
     * Validate auth code step
     */
    private fun validateAuthCodeStep(): Boolean {
        return when {
            authCode.isEmpty() -> {
                showError("Please enter the verification code")
                false
            }
            authCode.length < 4 -> {
                showError("Verification code must be at least 4 digits")
                false
            }
            else -> true
        }
    }

    /**
     * Check if country code is valid
     * Accepts formats like: +1, +44, 1, 44, etc.
     */
    private fun isValidCountryCode(code: String): Boolean {
        val cleanCode = code.replace("+", "")
        return cleanCode.isNotEmpty() && cleanCode.all { it.isDigit() } && cleanCode.length <= 3
    }

    /**
     * Check if phone number is valid
     */
    private fun isValidPhoneNumber(phone: String): Boolean {
        val cleanPhone = phone.replace(Regex("[^\\d]"), "")
        return cleanPhone.length >= 10
    }

    /**
     * Normalize country code to always have + prefix
     * Input: "1" or "+1" → Output: "+1"
     */
    private fun normalizeCountryCode(code: String): String {
        val cleanCode = code.replace("+", "").trim()
        return if (cleanCode.isNotEmpty()) "+$cleanCode" else "+"
    }

    private fun showError(message: String) {
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun updateFragment() {
        val newFragment = PhoneAuthGuidedStepFragment().apply {
            this.countryCode = this@PhoneAuthGuidedStepFragment.countryCode
            this.phoneNumber = this@PhoneAuthGuidedStepFragment.phoneNumber
            this.authCode = this@PhoneAuthGuidedStepFragment.authCode
            this.currentStep = this@PhoneAuthGuidedStepFragment.currentStep
        }

        GuidedStepSupportFragment.add(
            requireActivity().supportFragmentManager,
            newFragment
        )
    }

    private fun completeAuthentication() {
        // TODO: Call your API to authenticate with phone number and auth code
        // Example:
        val fullPhoneNumber = normalizeCountryCode(countryCode) + phoneNumber
        // viewModel.authenticateWithPhone(fullPhoneNumber, authCode)

        viewModel.updateOnboarding()

        val intent = Intent(requireActivity(), MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }

    companion object {
        // Step Constants
        private const val STEP_PHONE = 0
        private const val STEP_AUTH_CODE = 1
        private const val STEP_LOGIN = 2

        // Action IDs - Step 1
        private const val ACTION_COUNTRY_CODE = 1L
        private const val ACTION_PHONE_NUMBER = 2L

        // Action IDs - Step 2
        private const val ACTION_PHONE_DISPLAY = 3L
        private const val ACTION_AUTH_CODE = 4L

        // Action IDs - Step 3
        private const val ACTION_SUMMARY_COUNTRY = 5L
        private const val ACTION_SUMMARY_PHONE = 6L
        private const val ACTION_SUMMARY_AUTH = 7L
        private const val ACTION_SUMMARY_FULL = 8L

        // Common Action IDs
        private const val ACTION_NEXT = 10L
        private const val ACTION_PREVIOUS = 11L
        private const val ACTION_COMPLETE = 12L
        private const val ACTION_CANCEL = 13L
    }
}