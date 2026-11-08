package com.hd.emojibattery.data

import com.hd.emojibattery.Constants
import com.hd.emojibattery.PetDataSource
import org.json.JSONArray
import org.json.JSONObject

/** Apply the same selection to downloads, bundled content, old caches and overlays. */
object PetSourcePolicy {
    fun allowsKey(key: String): Boolean = when (key) {
        "data_pet", "data_pet_moving_v3", "pet_type" -> Constants.PET_DATA_SOURCE != PetDataSource.PET_ON_SCREEN_GITHUB
        "data_character_pet_on_screen" -> Constants.PET_DATA_SOURCE != PetDataSource.EMOJI_REMOTE_CONFIG
        else -> true // Wallpaper and other non-pet features keep their own sources.
    }

    fun allows(item: JSONObject): Boolean {
        val github = item.optString("source_key") == "data_character_pet_on_screen" ||
            item.optString("id").startsWith("data_character_pet_on_screen:") ||
            item.optString("source_base_url").startsWith(SourceCatalog.PET_BASE) ||
            item.optString("image_url").startsWith(SourceCatalog.PET_BASE)
        return when (Constants.PET_DATA_SOURCE) {
            PetDataSource.EMOJI_REMOTE_CONFIG -> !github
            PetDataSource.PET_ON_SCREEN_GITHUB -> github
            PetDataSource.BOTH -> true
        }
    }

    fun filter(items: JSONArray): JSONArray = JSONArray().also { result ->
        for (index in 0 until items.length()) items.optJSONObject(index)?.takeIf(::allows)?.let(result::put)
    }

    fun filterContent(content: JSONObject): JSONObject = content.apply {
        optJSONArray("pets")?.let { put("pets", filter(it)) }
    }

    fun categoryKeys(): List<String> = when (Constants.PET_DATA_SOURCE) {
        PetDataSource.EMOJI_REMOTE_CONFIG -> listOf("pet_categories")
        PetDataSource.PET_ON_SCREEN_GITHUB -> listOf("character_categories")
        PetDataSource.BOTH -> listOf("pet_categories", "character_categories")
    }
}
