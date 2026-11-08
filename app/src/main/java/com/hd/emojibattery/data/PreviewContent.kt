package com.hd.emojibattery.data

import org.json.JSONArray
import org.json.JSONObject

/** In-memory fixtures only: stable IDs allow selection/favorite/navigation without artwork. */
object PreviewContent {
    fun create(): JSONObject = JSONObject().apply {
        put("schema_version", 2)
        val categories = listOf("Halloween", "Christmas", "Premier League", "Animals", "Cute", "Food")
        for (key in listOf("themes", "stickers", "pets", "emojis", "battery_styles", "wallpapers")) {
            put(key, JSONArray().apply {
                repeat(36) { index -> put(JSONObject().apply {
                    put("id", "preview_${key}_$index")
                    put("name", "")
                    put("emoji", "")
                    put("category", categories[index / 6])
                    put("image_url", "")
                    put("animation_url", "")
                    put("wallpaper", "")
                    put("color", "#F8F6FE")
                    put("free", true)
                }) }
            })
        }
        put("animations", JSONArray((0 until 24).map { "preview_animation_$it" }))
        put("charging_animations", JSONArray((0 until 24).map { "preview_charging_animation_$it" }))
        put("links", JSONObject())
    }
}
