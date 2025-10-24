package com.aes.grammplayer

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import android.content.Intent

class TermsActivity : Activity() {  // Or extend Activity if you prefer

    private lateinit var checkBox: CheckBox
    private lateinit var proceedButton: Button
    private lateinit var termsText: TextView
    private lateinit var privacyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_toc)

        checkBox = findViewById(R.id.checkbox_accept)
        proceedButton = findViewById(R.id.button_proceed)
        termsText = findViewById(R.id.text_terms_link)
        privacyText = findViewById(R.id.text_privacy_link)

        // Initially disable the button
        proceedButton.isEnabled = false

        // Set up checkbox listener
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            proceedButton.isEnabled = isChecked
        }

        // Set plain text for links
        termsText.text = "Terms and Conditions"
        privacyText.text = "Privacy Policy"

        // Make links focusable and clickable for D-pad
        termsText.isFocusable = true
        termsText.isClickable = true
        privacyText.isFocusable = true
        privacyText.isClickable = true

        // Set click listeners to show popup with WebView
        termsText.setOnClickListener {
            showWebViewDialog("https://abinsabu2.github.io/GrammPlayer/terms-conditions.html")  // Replace <your-username> with your GitHub username
        }
        privacyText.setOnClickListener {
            showWebViewDialog("https://abinsabu2.github.io/GrammPlayer/privacy-policy.html")  // Replace <your-username> with your GitHub username
        }

        // Proceed button click
        proceedButton.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()  // Optional: Prevent back to terms
        }
    }

    private fun showWebViewDialog(url: String) {
        val webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.settings.javaScriptEnabled = false  // Enable if your page needs JS
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true  // For pinch-zoom, but on TV use D-pad
        webView.settings.displayZoomControls = false  // Hide zoom buttons on TV

        webView.webViewClient = WebViewClient()  // Keep navigation inside WebView

        webView.loadUrl(url)

        val dialog = AlertDialog.Builder(this)
            .setView(webView)
            .setPositiveButton("Close") { _, _ -> }
            .create()

        dialog.show()

        // Optional: Handle load errors
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Toast.makeText(this@TermsActivity, "Error loading page: $description", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
        }
    }
}