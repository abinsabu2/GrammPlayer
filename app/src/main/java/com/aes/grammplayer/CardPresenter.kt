package com.aes.grammplayer

import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter

/**
 * A Presenter used to generate Views and bind MediaMessage objects to them on demand.
 * This version displays media details as text inside a card.
 */
class CardPresenter : Presenter() {

    private var sSelectedBackgroundColor: Int = 0
    private var sDefaultBackgroundColor: Int = 0
    private var sSelectedBorderColor: Int = 0


    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val context = parent.context

        // Initialize colors once
        if (sDefaultBackgroundColor == 0) {
            sDefaultBackgroundColor = ContextCompat.getColor(context, R.color.default_background)
            sSelectedBackgroundColor = ContextCompat.getColor(context, R.color.selected_background)
            sSelectedBorderColor = ContextCompat.getColor(context, R.color.selected_background)
        }

        // --- Create a TextView for our content ---
        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(24, 24, 24, 24) // Add some padding
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            textSize = 12f
        }

        // --- Create a FrameLayout to act as the card ---
        val cardLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(CARD_WIDTH, CARD_HEIGHT)
            isFocusable = true
            isFocusableInTouchMode = true
            addView(textView) // Add the TextView to the card
            // OPTIMIZATION: Set the focus listener only once when the view is created.
            setOnFocusChangeListener { view, hasFocus ->
                animateCardFocus(view, hasFocus)
            }
        }

        // Set the initial, un-focused state
        animateCardFocus(cardLayout, false)
        // Return a custom ViewHolder that holds our card and text view
        return CardViewHolder(cardLayout, textView)
    }

    /**
     * DYNAMIC ANIMATION: Animates the card's scale and updates its background/border
     * based on the focus state.
     */
    private fun animateCardFocus(view: View, hasFocus: Boolean) {
        val scale = if (hasFocus) FOCUSED_SCALE else UNFOCUSED_SCALE
        val duration = 150L // Animation duration in milliseconds

        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .setDuration(duration)
            .start()

        updateCardStyling(view, hasFocus)
    }

    /**
     * Updates the card's background color and adds/removes a border.
     */
    private fun updateCardStyling(view: View, isSelected: Boolean) {
        val backgroundColor = if (isSelected) sSelectedBackgroundColor else sDefaultBackgroundColor
        val borderWidth = if (isSelected) 6 else 0 // Border width in pixels
        val borderColor = sSelectedBorderColor

        val border = GradientDrawable().apply {
            setColor(backgroundColor)
            setStroke(borderWidth, borderColor)
        }
        view.background = border
    }
    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        // Ensure the item is a MediaMessage before proceeding
        if (item !is MediaMessage || viewHolder !is CardViewHolder) {
            return
        }

        if ( item.fileId == 0) {
            return
        }

        // --- Format the text to be displayed ---
        val fileSizeMb = if (item.size > 0) String.format("%.2f MB", item.size / 1024.0 / 1024.0) else "N/A"
        val cardText = """
            Title: ${item.title ?: "N/A"}
            File ID: ${item.fileId}
            Size: $fileSizeMb
        """.trimIndent()

        // Set the formatted text on our TextView
        viewHolder.textView.text = cardText

        // --- Handle focus change for background color ---
        viewHolder.view.setOnFocusChangeListener { v, hasFocus ->
            updateCardBackgroundColor(v, hasFocus)
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        // No special cleanup needed for TextView
    }

    private fun updateCardBackgroundColor(view: View, selected: Boolean) {
        val color = if (selected) sSelectedBackgroundColor else sDefaultBackgroundColor

        // Create a rounded rectangle drawable
        val border = GradientDrawable().apply {
            setColor(color)
        }
        view.background = border
    }


    /**
     * Custom ViewHolder to hold references to our views.
     */
    class CardViewHolder(view: View, val textView: TextView) : Presenter.ViewHolder(view)

    companion object {
        private const val TAG = "CardPresenter"
        // Let's use a more rectangular shape for text
        private const val CARD_WIDTH = 600
        private const val CARD_HEIGHT = 400

        // Add these two lines
        private const val FOCUSED_SCALE = 1.1f
        private const val UNFOCUSED_SCALE = 1.0f
    }
}
