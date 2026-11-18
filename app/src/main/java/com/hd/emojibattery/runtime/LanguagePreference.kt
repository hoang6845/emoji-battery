package com.hd.emojibattery.runtime

import android.content.SharedPreferences

object LanguagePreference {
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
}
