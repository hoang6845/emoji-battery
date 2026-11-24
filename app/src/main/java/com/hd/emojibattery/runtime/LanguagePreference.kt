package com.hd.emojibattery.runtime

import android.content.SharedPreferences
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguagePreference {
    private val tags = listOf("es", "en", "pt", "vi", "ja", "zh", "de", "id", "ru", "fr", "th", "ko", "ar", "hi", "pl", "ms")
    // Keep this order aligned with fragment_language_picker's radio options.
    private val names = listOf(
        "Español", "English", "Português", "Tiếng Việt", "日本", "中国", "Deutsche",
        "Indonesia", "Российская Федерация", "Français", "ไทย", "한국어", "العربية",
        "हिन्दी", "Polska", "Malaysia"
    )

    fun selectedName(preferences: SharedPreferences): String =
        if (preferences.contains("language_index")) {
            names.getOrNull(preferences.getInt("language_index", 1)) ?: "English"
        } else {
            preferences.getString("language", "English") ?: "English"
        }

    fun save(preferences: SharedPreferences, index: Int) {
        val name = names.getOrNull(index) ?: return
        preferences.edit().putInt("language_index", index).putString("language", name)
            .putBoolean("language_selected", true).apply()
    }

    private fun selectedTag(preferences: SharedPreferences): String? {
        if (preferences.contains("language_index")) {
            return tags.getOrNull(preferences.getInt("language_index", 1))
        }
        val name = preferences.getString("language", null) ?: return null
        val legacy = mapOf("Vietnamese" to "vi", "French" to "fr", "German" to "de",
            "Spanish" to "es", "Portuguese" to "pt", "Italian" to "it", "Russian" to "ru",
            "Indonesian" to "id", "Dutch" to "nl", "Romanian" to "ro")
        return tags.getOrNull(names.indexOf(name)) ?: legacy[name]
    }

    fun wrap(context: Context): Context {
        val tag = selectedTag(context.getSharedPreferences("emoji_battery", 0)) ?: return context
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    fun apply(context: Context) {
        val tag = selectedTag(context.getSharedPreferences("emoji_battery", 0)) ?: return
        Locale.setDefault(Locale.forLanguageTag(tag))
        val locales = LocaleListCompat.forLanguageTags(tag)
        if (AppCompatDelegate.getApplicationLocales() != locales) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
