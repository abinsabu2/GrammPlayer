package com.aes.grammplayer.ui.features.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aes.grammplayer.R
import com.aes.grammplayer.helper.LocaleHelper
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.launch

class OnboardingFragment : Fragment() {

    private lateinit var settingsDataStore: SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        settingsDataStore = SettingsDataStore(requireActivity())
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.onboarding_content, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageView = view.findViewById<ImageView>(R.id.onboarding_image)
        val getStartedButton = view.findViewById<Button>(R.id.button_get_started)
        val languageButton = view.findViewById<Button>(R.id.button_language)

        // Set the image (replace with your actual drawable)
        imageView.setImageResource(R.drawable.app_icon_your_company)

        languageButton.text = getString(
            R.string.language_button,
            LocaleHelper.currentLanguage(requireContext()).nativeName
        )
        languageButton.setOnClickListener {
            LocaleHelper.showPicker(requireActivity()) { requireActivity().recreate() }
        }

        // Handle Get Started button click
        getStartedButton.setOnClickListener {

            lifecycleScope.launch {
                settingsDataStore.setOnboardingDone(true)
            }

            val intent = Intent(requireActivity(), TermsActivity::class.java)
            startActivity(intent)
            requireActivity().finish()
        }
    }
}