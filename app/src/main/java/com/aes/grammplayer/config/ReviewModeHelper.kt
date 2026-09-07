package com.aes.grammplayer.config

import android.content.Context
import com.aes.grammplayer.helper.HistoryHelper
import com.aes.grammplayer.session.UserSession
import com.aes.grammplayer.ui.features.settings.SettingsDataStore
import kotlinx.coroutines.flow.first

/**
 * Gates UI and actions that should not be exposed during Amazon Appstore review.
 * Reviewers sign in with [TestUserConfig.AMAZON_REVIEW_PHONE] / [TestUserConfig.AMAZON_REVIEW_CODE].
 */
object ReviewModeHelper {

    /** Dashboard preference cards hidden for store reviewers. */
    private val HIDDEN_DASHBOARD_ITEMS = setOf(
        "clear_cache",
        "clear_history",
        "settings",
        "close",
        "logout"
    )

    suspend fun isReviewMode(context: Context): Boolean {
        HistoryHelper.restoreSession(context)
        if (UserSession.isTestUser()) return true
        return SettingsDataStore(context.applicationContext).isTestMode.first()
    }

    fun isDashboardItemVisible(itemId: String, reviewMode: Boolean): Boolean {
        if (!reviewMode) return true
        return itemId !in HIDDEN_DASHBOARD_ITEMS
    }

    fun isDestructiveDashboardAction(itemId: String): Boolean =
        itemId in HIDDEN_DASHBOARD_ITEMS
}