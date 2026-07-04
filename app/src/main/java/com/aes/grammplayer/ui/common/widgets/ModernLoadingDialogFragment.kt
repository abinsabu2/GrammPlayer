package com.aes.grammplayer.ui.common.widgets

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.aes.grammplayer.R
import androidx.core.graphics.toColorInt

// Extension function for converting dp to pixels - at file level, not inside class
private fun Float.dpToPx(context: Context): Int =
    (this * context.resources.displayMetrics.density).toInt()

/**
 * Modern Material Design loading dialog.
 * More refined appearance suitable for modern TV apps.
 *
 * REQUIREMENTS:
 * Add to build.gradle:
 * implementation 'com.google.android.material:material:1.9.0'
 */
class ModernLoadingDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_MESSAGE = "message"
        private const val ARG_CANCELLABLE = "cancellable"

        fun newInstance(
            message: String = "Loading...",
            isCancellable: Boolean = true
        ): ModernLoadingDialogFragment {
            return ModernLoadingDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                    putBoolean(ARG_CANCELLABLE, isCancellable)
                }
            }
        }
    }

    private var loadingMessage: String = "Loading..."
    private var isCancellable: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadingMessage = arguments?.getString(ARG_MESSAGE) ?: "Loading..."
        isCancellable = arguments?.getBoolean(ARG_CANCELLABLE) ?: false
        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Translucent_NoTitleBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()

        return LinearLayout(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor("#000000".toColorInt()) // Darker overlay

            // Card-like dialog container
            val dialogCard = LinearLayout(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    500f.dpToPx(ctx),
                    280f.dpToPx(ctx)
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER

                // Material Design elevation effect
                elevation = 12f
                setBackgroundColor(Color.WHITE)

                // Set padding
                val padding = 32f.dpToPx(ctx)
                setPadding(padding, padding, padding, padding)
            }

            // Progress Indicator
            val progressBar = ProgressBar(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    80f.dpToPx(ctx),
                    80f.dpToPx(ctx)
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    bottomMargin = 20f.dpToPx(ctx)
                }
                isIndeterminate = true
                indeterminateDrawable.setTint(
                    ContextCompat.getColor(ctx, R.color.background_gradient_start)
                )
            }

            // Loading Text
            val textView = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                }
                text = loadingMessage
                textSize = 16f
                setTextColor(Color.parseColor("#212121")) // Dark gray/black
                gravity = android.view.Gravity.CENTER
                tag = "loading_text_modern"
            }

            // Optional: Subtitle text
            val subtitleView = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    topMargin = 8f.dpToPx(ctx)
                }
                text = "Please wait..."
                textSize = 12f
                setTextColor(Color.parseColor("#757575")) // Medium gray
                gravity = android.view.Gravity.CENTER
            }

            dialogCard.addView(progressBar)
            dialogCard.addView(textView)
            dialogCard.addView(subtitleView)
            addView(dialogCard)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(isCancellable)
        dialog.setCanceledOnTouchOutside(isCancellable)
        return dialog
    }

    fun setMessage(message: String) {
        loadingMessage = message
        view?.findViewWithTag<TextView>("loading_text_modern")?.text = message
    }

    fun dismissLoading() {
        if (isAdded) {
            dismissAllowingStateLoss()
        }
    }
}