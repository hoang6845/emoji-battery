package com.hd.emojibattery.runtime

import org.json.JSONArray
import org.json.JSONObject

/** Source ordering/localized names; flat lists stay flat rather than invented categories. */
object CatalogGroups {
    private val mixedCatalogs = setOf("themes", "battery_styles", "troll_styles", "pets", "wallpapers",
        "stickers", "animations", "charging_animations", "emojis", "emotions", "backgrounds")

    /** Round-robin categories for All, keeping each category's own order unchanged. */
    fun mixCategories(key: String, items: JSONArray): JSONArray {
        if (key !in mixedCatalogs || items.length() < 2) return items
        val groups = linkedMapOf<String, MutableList<JSONObject>>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: return items
            groups.getOrPut(item.optString("category")) { mutableListOf() }.add(item)
        }
        if (groups.size < 2) return items
        val pending = java.util.ArrayDeque<Iterator<JSONObject>>()
        groups.values.forEach { pending.addLast(it.iterator()) }
        return JSONArray().apply {
            while (pending.isNotEmpty()) {
                val group = pending.removeFirst()
                put(group.next())
                if (group.hasNext()) pending.addLast(group)
            }
        }
    }

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
