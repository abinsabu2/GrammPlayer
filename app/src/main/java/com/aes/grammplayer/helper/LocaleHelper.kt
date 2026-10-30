package com.aes.grammplayer.helper

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import com.aes.grammplayer.R
import java.util.Locale

/**
 * In-app language. Every activity (and the Application) wraps its base context with [wrap].
 *
 * The choice lives in SharedPreferences, not SettingsDataStore: attachBaseContext runs
 * before anything can suspend to read DataStore.
 */
object LocaleHelper {

    /** [tag] is a BCP-47 language tag; [nativeName] is shown untranslated so users can find their language. */
    data class Language(val tag: String, val nativeName: String)

    val LANGUAGES = listOf(
        Language("en", "English"),
        Language("de", "Deutsch"),
        Language("hi", "हिन्दी"),
        Language("ml", "മലയാളം"),
        Language("ru", "Русский"),
        Language("es", "Español"),
        Language("fr", "Français"),
        Language("ta", "தமிழ்"),
    )

    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE_TAG = "language_tag"

    fun currentLanguage(context: Context): Language {
        val tag = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_TAG, null)
        return LANGUAGES.firstOrNull { it.tag == tag } ?: LANGUAGES.first()
    }

    fun wrap(base: Context): Context {
        val locale = Locale.forLanguageTag(currentLanguage(base).tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Saves [language] and re-localizes the Application's resources (used by helpers through
     * GPlayerApplication.AppContext). Activities pick it up when they are recreated.
     */
    @Suppress("DEPRECATION")
    fun setLanguage(context: Context, language: Language) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LANGUAGE_TAG, language.tag)
            .commit() // synchronous: callers recreate activities right after
        val appResources = context.applicationContext.resources
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        val config = Configuration(appResources.configuration).apply { setLocale(locale) }
        appResources.updateConfiguration(config, appResources.displayMetrics)
    }

    /** Single-choice language list. [onChanged] runs only when the user picks a different language. */
    fun showPicker(activity: Activity, onChanged: () -> Unit) {
        val current = LANGUAGES.indexOf(currentLanguage(activity))
        AlertDialog.Builder(activity)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(LANGUAGES.map { it.nativeName }.toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                if (which != current) {
                    setLanguage(activity, LANGUAGES[which])
                    onChanged()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
