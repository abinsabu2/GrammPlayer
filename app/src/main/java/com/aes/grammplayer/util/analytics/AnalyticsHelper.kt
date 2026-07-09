package com.aes.grammplayer.util.analytics

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

object AnalyticsHelper {

    private const val TAG = "AnalyticsHelper"

    private var analytics: FirebaseAnalytics? = null

    fun initialize(application: Application) {
        if (FirebaseApp.getApps(application).isEmpty()) {
            Log.w(TAG, "Firebase is not configured. Add app/google-services.json to enable analytics.")
            return
        }

        analytics = FirebaseAnalytics.getInstance(application).also {
            it.setAnalyticsCollectionEnabled(true)
        }
        application.registerActivityLifecycleCallbacks(ScreenTrackingCallbacks())
        Log.i(TAG, "Firebase Analytics initialized")
    }

    fun logScreen(activity: Activity) {
        val screenName = activity.javaClass.simpleName
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
            param(FirebaseAnalytics.Param.SCREEN_CLASS, activity.javaClass.name)
        }
    }

    fun logLogin(method: String = "telegram") {
        analytics?.logEvent(FirebaseAnalytics.Event.LOGIN) {
            param(FirebaseAnalytics.Param.METHOD, method)
        }
    }

    fun logMediaPlay(fileId: Int, player: String) {
        analytics?.logEvent("media_play") {
            param("file_id", fileId.toLong())
            param("player", player)
        }
    }

    fun logMediaDownload(fileId: Int, source: String) {
        analytics?.logEvent("media_download_complete") {
            param("file_id", fileId.toLong())
            param("source", source)
        }
    }

    fun logEvent(name: String, params: Map<String, String> = emptyMap()) {
        analytics?.logEvent(name) {
            params.forEach { (key, value) -> param(key, value) }
        }
    }

    private class ScreenTrackingCallbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            logScreen(activity)
        }
    }
}