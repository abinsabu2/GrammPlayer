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
import org.drinkless.tdlib.TdApi
import kotlin.text.append

/**
 * A Presenter used to generate Views and bind ChatGroup objects to them on demand.
 * This version displays chat group titles as text inside a card.
 * It reuses the animation and styling logic from the existing CardPresenter.
 */
class ChatCardPresenter : Presenter() {

    private var sSelectedBackgroundColor: Int = 0
    private var sDefaultBackgroundColor: Int = 0
    private var sSelectedBorderColor: Int = 0
    private var sTitleTextColor: Int = 0
    // sDetailTextColor is not strictly needed for this simplified chat card,
    // but kept for consistency if more details are added later.
    private var sDetailTextColor: Int = 0

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        Log.d(TAG, "onCreateViewHolder")
        val context = parent.context

        // Initialize colors once
        if (sDefaultBackgroundColor == 0) {
            sDefaultBackgroundColor = ContextCompat.getColor(context, R.color.default_background)
            sSelectedBackgroundColor = ContextCompat.getColor(context, R.color.selected_background)
            sSelectedBorderColor = ContextCompat.getColor(context, R.color.selected_background) // Using same as selected_background for border
            sTitleTextColor = ContextCompat.getColor(context, android.R.color.white)
            sDetailTextColor = ContextCompat.getColor(context, R.color.card_detail_text) // Assume a lighter gray color resource
        }

        // --- Create a TextView for our content ---
        val textView = TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            gravity = Gravity.CENTER // Center the text within the card
            setPadding(24, 24, 24, 24) // Add some padding
            textSize = 20f // Larger text for chat titles
            setTextColor(sTitleTextColor)
            maxLines = 3 // Allow title to wrap
            ellipsize = android.text.TextUtils.TruncateAt.END // Add ellipsis if too long
            typeface = typeface // Default, but we can bold title via Spannable
        }

        // --- Create a FrameLayout to act as the card ---
        val cardLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(CHAT_CARD_WIDTH, CHAT_CARD_HEIGHT).apply {
                // Add margins between cards for visual separation
                setMargins(16, 16, 16, 16)
            }
            isFocusable = true
            isFocusableInTouchMode = true
            addView(textView) // Add the TextView to the card
            // OPTIMIZATION: Set the focus listener only once when the view is created.
            setOnFocusChangeListener { view, hasFocus ->
                animateCardFocus(view, hasFocus)
            }
            // Add initial elevation for shadow effect
            elevation = UNFOCUSED_ELEVATION
        }

        // Set the initial, un-focused state
        animateCardFocus(cardLayout, false)
        // Return a custom ViewHolder that holds our card and text view
        return ChatCardViewHolder(cardLayout, textView)
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
        // Ensure the item is a ChatGroup before proceeding
        if (item !is TdApi.Chat || viewHolder !is ChatCardViewHolder) {
            return
        }
        val chatType = item.type
        val chatTypeReadable = when (chatType) {
            is TdApi.ChatTypeSupergroup -> "Supergroup"
            is TdApi.ChatTypePrivate -> "Private"
            is TdApi.ChatTypeBasicGroup -> "Basic Group"
            else -> {}
        }
        val title = item.title

        val spannable = SpannableStringBuilder().apply {
            // Title value: Bold, larger size, primary color
            val tStart = length
            append(chatTypeReadable.toString())
            val tEnd = length
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), tStart, tEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(10, true), tStart, tEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // Adjust size for chat title
            setSpan(ForegroundColorSpan(sTitleTextColor), tStart, tEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

            append("\n") // Spacing

            val titleStart = length
            append(title)
            val titleEnd = length
            setSpan(StyleSpan(android.graphics.Typeface.BOLD), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(14, true), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) // Adjust size for chat title
            setSpan(ForegroundColorSpan(sTitleTextColor), titleStart, titleEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)




        }

        // Set the formatted styled text on our TextView
        viewHolder.textView.text = spannable
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        Log.d(TAG, "onUnbindViewHolder")
        // Reset to default unfocused state
        if (viewHolder is ChatCardViewHolder) {
            animateCardFocus(viewHolder.view, false)
        }
    }

    /**
     * Custom ViewHolder to hold references to our views.
     */
    class ChatCardViewHolder(view: View, val textView: TextView) : Presenter.ViewHolder(view)

    companion object {
        private const val TAG = "ChatCardPresenter"
        // Let's use a more square shape for chat cards
        private const val CHAT_CARD_WIDTH = 400 // dp
        private const val CHAT_CARD_HEIGHT = 200 // dp

        private const val FOCUSED_SCALE = 1.1f
        private const val UNFOCUSED_SCALE = 1.0f
        private const val FOCUSED_ELEVATION = 12f // dp
        private const val UNFOCUSED_ELEVATION = 4f // dp
    }
}