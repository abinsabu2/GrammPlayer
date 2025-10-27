package com.aes.grammplayer

import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
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
    private var sTitleTextColor: Int = 0
    private var sDetailTextColor: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val context = parent.context
        // Initialize colors once
        if (sDefaultBackgroundColor == 0) {
            sDefaultBackgroundColor = ContextCompat.getColor(context, R.color.default_background)
            sSelectedBackgroundColor = ContextCompat.getColor(context, R.color.selected_background)
            sSelectedBorderColor = ContextCompat.getColor(context, R.color.selected_background)
            sTitleTextColor = ContextCompat.getColor(context, android.R.color.white)
            sDetailTextColor = ContextCompat.getColor(context, R.color.card_detail_text) // Assume a lighter gray color resource
        }

        // --- Create a TextView for our content ---
        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(24, 24, 24, 24) // Add some padding
            textSize = 14f // Base text size
            setTextColor(sTitleTextColor)
            typeface = typeface // Default, but we can bold title via Spannable
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
            // Add elevation for shadow effect
            elevation = 4f
        }

        // Set the initial, un-focused state
        animateCardFocus(cardLayout, false)
        // Return a custom ViewHolder that holds our card and text view
        return CardViewHolder(cardLayout, textView)
    }

    /**
     * DYNAMIC ANIMATION: Animates the card's scale, elevation, and updates its background/border
     * based on the focus state.
     */
    private fun animateCardFocus(view: View, hasFocus: Boolean) {
        val scale = if (hasFocus) FOCUSED_SCALE else UNFOCUSED_SCALE
        val elevation = if (hasFocus) FOCUSED_ELEVATION else UNFOCUSED_ELEVATION
        val duration = 150L // Animation duration in milliseconds

        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationZ(elevation)
            .setDuration(duration)
            .start()

        updateCardStyling(view, hasFocus)
    }

    /**
     * Updates the card's background color, border, and corner radius.
     */
    private fun updateCardStyling(view: View, isSelected: Boolean) {
        val backgroundColor = if (isSelected) sSelectedBackgroundColor else sDefaultBackgroundColor
        val borderWidth = if (isSelected) 6 else 0 // Border width in pixels
        val borderColor = sSelectedBorderColor
        var cornerRadius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 12f,
            view.context.resources.displayMetrics
        ).toFloat() // 12dp rounded corners

        val border = GradientDrawable().apply {
            setColor(backgroundColor)
            setStroke(borderWidth.toInt(), borderColor)
            cornerRadius = cornerRadius
        }
        view.background = border
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        // Ensure the item is a MediaMessage before proceeding
        if (item !is MediaMessage || viewHolder !is CardViewHolder) {
            return
        }

        if (item.fileId == 0) {
            return
        }

        // --- Format the styled text to be displayed ---
        val fileSizeMb = if (item.size > 0) String.format("%.2f MB", item.size / 1024.0 / 1024.0) else "N/A"
        val title = item.title ?: "N/A"
        val fileId = item.fileId.toString()
        val size = fileSizeMb

        val spannable = SpannableStringBuilder().apply {
            // Title value: Bold, larger size, primary color
            val titleStart = length
            append(title)
            val titleEnd = length
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(14, true), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(sTitleTextColor), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            append("\n\n") // Spacing

            // File ID label
            append("File ID: ")
            // File ID value: Normal, medium size, secondary color
            val fileIdStart = length
            append(fileId)
            val fileIdEnd = length
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), fileIdStart, fileIdEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(14, true), fileIdStart, fileIdEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(sDetailTextColor), fileIdStart, fileIdEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            append("\n\n") // Spacing

            // Size label
            append("Size: ")
            // Size value: Italic, small size, secondary color
            val sizeStart = length
            append(size)
            val sizeEnd = length
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), sizeStart, sizeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(12, true), sizeStart, sizeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(sDetailTextColor), sizeStart, sizeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Set the formatted styled text on our TextView
        viewHolder.textView.text = spannable

        // Ensure the focus listener is not overridden; it's already set in onCreateViewHolder
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        // Reset to default state
        if (viewHolder is CardViewHolder) {
            animateCardFocus(viewHolder.view, false)
        }
    }

    /**
     * Custom ViewHolder to hold references to our views.
     */
    class CardViewHolder(view: View, val textView: TextView) : Presenter.ViewHolder(view)

    companion object {
        private const val TAG = "CardPresenter"
        // Let's use a more rectangular shape for text
        private const val CARD_WIDTH = 580
        private const val CARD_HEIGHT = 400

        // Add these two lines
        private const val FOCUSED_SCALE = 1.1f
        private const val UNFOCUSED_SCALE = 1.0f
        private const val FOCUSED_ELEVATION = 12f
        private const val UNFOCUSED_ELEVATION = 4f
    }
}