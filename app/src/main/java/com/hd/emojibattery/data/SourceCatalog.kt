package com.hd.emojibattery.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.net.URLEncoder

/** Adapts the original applications' schemas; keeps source metadata for future renderers. */
object SourceCatalog {
    const val EMOJI_BASE = "https://raw.githubusercontent.com/Braly-Danang-2/emoji_battery_data/main/"
    const val PET_BASE = "https://raw.githubusercontent.com/BaseAppTech/data_pet_on_screen/main/"
    const val PUBLIC_BASE = "https://raw.githubusercontent.com/hoang6845/data_emoji_battery/main/"
    val publicKeys = setOf("data_sticker", "data_charging_animation", "data_animation",
        "data_background", "data_emotion", "data_wallpaper_pet_on_screen", "data_battery_v3", "data_battery_troll")
    fun baseUrl(key: String): String = when {
        key in publicKeys -> PUBLIC_BASE
        key in petKeys -> PET_BASE
        else -> EMOJI_BASE
    }
    val emojiKeys = linkedMapOf(
        "data_theme_v3" to "themes", "data_emoji_v3" to "emojis",
        "data_battery_v3" to "battery_styles", "data_battery_troll" to "troll_styles", "data_background" to "backgrounds",
        "data_emotion" to "emotions", "data_animation" to "animations", "data_notch" to "notches",
        "data_sticker" to "stickers", "data_charging_animation" to "charging_animations",
        "data_pet" to "idle_pets", "data_pet_moving_v3" to "moving_pets",
        "theme_type_v3" to "theme_categories", "pet_type" to "pet_categories")
    val petKeys = linkedMapOf("data_character_pet_on_screen" to "characters",
        "data_wallpaper_pet_on_screen" to "wallpapers", "data_category_pet_on_screen" to "character_categories")

    fun url(path: String, base: String): String = when {
        path.isBlank() -> ""
        path.startsWith("https://") -> path
        path.contains("://") -> ""
        // New catalogs store literal repo paths, including spaces and Unicode.
        base == PUBLIC_BASE -> base + path.trimStart('/').split('/').joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }
        else -> base + path.trimStart('/')
    }

    fun adapt(key: String, input: JSONArray): JSONArray {
        val output = JSONArray()
        for (i in 0 until input.length()) {
            val src = input.getJSONObject(i)
            val base = if (key == "data_pet_moving_v3" && src.has("source_base_url")) {
                src.getString("source_base_url").also {
                    require(it.startsWith("https://") && it.endsWith("/")) { "Invalid pet asset base" }
                }
            } else baseUrl(key)
            val item = JSONObject(src.toString())
            val sourceId = src.optString("id").ifBlank {
                src.optString("character").ifBlank { src.optString("imagePath").ifBlank { src.optString("type") } }
            }
            require(sourceId.isNotBlank()) { "Missing source identifier: $key" }
            val image = src.optString("thumbnail").ifBlank { src.optString("imagePathThumb").ifBlank {
                src.optString("src").ifBlank { src.optString("imagePath").ifBlank { src.optString("image_url") } }
            } }
            item.put("id", "$key:$sourceId")
            item.put("emoji", "")
            item.put("source_id", sourceId)
            item.put("source_key", key)
            item.put("source_base_url", base)
            item.put("name", src.optString("name").ifBlank {
                src.optString(Locale.getDefault().language).ifBlank { src.optString("en").ifBlank { sourceId } }
            })
            item.put("category", src.optString("category").ifBlank { src.optString("type") })
            item.put("image_url", url(image, base))
            item.put("asset_url", url(src.optString("src").ifBlank { src.optString("imagePath").ifBlank { src.optString("image_url") } }, base))
            item.put("free", if (key == "data_character_pet_on_screen") src.optInt("price", 0) == 0
                else if (key == "data_sticker") src.optBoolean("free", false)
                else src.has("is_lock") && !src.isNull("is_lock") && !src.optBoolean("is_lock", true))
            // Keep walkStatuses/climb/drop/fly/gifUrls and character action objects intact.
            output.put(item)
        }
        return output
    }

    fun merge(base: JSONObject, sources: Map<String, JSONArray>): JSONObject {
        val result = JSONObject(base.toString())
        (emojiKeys + petKeys).forEach { (key, target) -> sources[key]?.let { result.put(target, adapt(key, it)) } }
        fun label(group: String, type: String): String {
            val categories = result.optJSONArray(group) ?: return type
            for (i in 0 until categories.length()) {
                val item = categories.optJSONObject(i) ?: continue
                if (item.optString("type") == type) return item.optString("name").ifBlank { type }
            }
            return type
        }
        mapOf("themes" to "theme_categories", "moving_pets" to "pet_categories").forEach { (key, group) ->
            val items = result.optJSONArray(key)
            for (i in 0 until (items?.length() ?: 0)) items?.optJSONObject(i)?.let {
                val type = it.optString("type").ifBlank { it.optString("category_type").ifBlank { it.optString("category") } }
                it.put("category_type", type).put("category", label(group, type))
            }
        }
        mapOf("battery_styles" to "battery_categories", "troll_styles" to "troll_categories",
            "stickers" to "sticker_categories", "wallpapers" to "wallpaper_categories",
            "charging_animations" to "charging_animation_categories").forEach { (key, metadata) ->
            val items = result.optJSONArray(key)
            val names = (0 until (items?.length() ?: 0)).mapNotNull { items?.optJSONObject(it)?.optString("category") }
                .filter { it.isNotBlank() }.distinct()
            result.put(metadata, JSONArray().apply { names.forEach { put(JSONObject().put("name", it)) } })
        }
        val petSources = listOf("data_pet", "data_pet_moving_v3", "data_character_pet_on_screen").filter(PetSourcePolicy::allowsKey)
        if (petSources.any { it in sources }) {
            val pets = JSONArray()
            petSources.forEach { key -> sources[key]?.let { raw ->
                val items = if (key == "data_pet_moving_v3") result.optJSONArray("moving_pets") ?: adapt(key, raw) else adapt(key, raw)
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    if (key == "data_character_pet_on_screen") {
                        val categories = result.optJSONArray("character_categories") ?: JSONArray()
                        for (c in 0 until categories.length()) {
                            val category = categories.getJSONObject(c)
                            val type = category.optString("type")
                            if (type.isNotBlank() && item.optString("thumbnail").contains("/$type/", true)) {
                                item.put("category", category.optString("name")); break
                            }
                        }
                    }
                    if (item.optString("category").isBlank()) item.put("category", if (key == "data_pet") "Idle" else "Character")
                    pets.put(item)
                }
            } }
            result.put("pets", pets)
        }
        return PetSourcePolicy.filterContent(result)
    }
}
