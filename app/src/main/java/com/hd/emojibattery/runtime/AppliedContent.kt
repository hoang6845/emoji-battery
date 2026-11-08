package com.hd.emojibattery.runtime

import android.content.Context
import com.hd.emojibattery.data.AppContentRepository
import com.hd.emojibattery.data.SourceCatalog
import org.json.JSONObject

object AppliedContent {
    fun prefs(context: Context) = context.getSharedPreferences("emoji_battery", Context.MODE_PRIVATE)
    fun find(context: Context, key: String, id: String?): JSONObject? {
        val items = AppContentRepository.get(context).array(key) ?: return null
        return (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull { it.optString("id") == id }
    }
    fun select(context: Context, slot: String, key: String, id: String): Boolean {
        val item = find(context, key, id) ?: return false
        prefs(context).edit().putString("applied_$slot", item.toString()).apply()
        return true
    }
    fun selected(context: Context, slot: String): JSONObject? = runCatching {
        JSONObject(prefs(context).getString("applied_$slot", "").orEmpty())
    }.getOrNull()
    fun url(item: JSONObject?, field: String = "asset_url"): String {
        if (item == null) return ""
        val path = item.optString(field).ifBlank { if (field == "asset_url") item.optString("src").ifBlank { item.optString("animation_url").ifBlank { item.optString("image_url") } } else "" }
        return if (path.startsWith("content://")) path else SourceCatalog.url(path, item.optString("source_base_url"))
    }
    fun theme(context: Context, id: String): Boolean {
        val theme = find(context, "themes", id) ?: return false
        val edit = prefs(context).edit().putString("applied_theme", theme.toString())
        listOf("battery_src" to "battery", "emoji_src" to "emoji").forEach { (field, slot) ->
            edit.putString("applied_$slot", JSONObject().put("asset_url", url(theme, field)).toString())
        }
        edit.apply()
        return true
    }
}
