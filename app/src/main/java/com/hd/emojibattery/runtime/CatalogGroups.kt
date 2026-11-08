package com.hd.emojibattery.runtime

import org.json.JSONObject

/** Source ordering/localized names; flat lists stay flat rather than invented categories. */
object CatalogGroups {
    fun names(content: JSONObject, key: String): List<String> {
        val metadata = metadataKeys(key)
        val names = mutableListOf<String>()
        metadata.forEach { group ->
            val array = content.optJSONArray(group)
            for (i in 0 until (array?.length() ?: 0)) array?.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }?.let(names::add)
        }
        return listOf("All") + names.distinct()
    }
    fun metadataKeys(key: String): List<String> = when (key) {
        "themes" -> listOf("theme_categories")
        "battery_styles" -> listOf("battery_categories")
        "troll_styles" -> listOf("troll_categories")
        "pets" -> com.hd.emojibattery.data.PetSourcePolicy.categoryKeys()
        "wallpapers" -> listOf("wallpaper_categories")
        "stickers" -> listOf("sticker_categories")
        "animations" -> listOf("animation_categories")
        "charging_animations" -> listOf("charging_animation_categories")
        else -> emptyList()
    }
    fun ids(content: JSONObject, key: String, group: String?): Set<String> {
        val array = content.optJSONArray(key)
        return (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
            .filter { group == null || group == "All" || it.optString("category") == group }.map { it.optString("id") }.toSet()
    }
}
