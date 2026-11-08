package com.hd.emojibattery

import android.widget.*
import com.hd.emojibattery.runtime.*
import org.json.JSONArray

/** Independent small windows: transparent full-screen surfaces never intercept other apps. */
class DecorationService : OverlayService() {
    override fun onStopRequested() { prefs.edit().putBoolean("notch_enabled", false).putBoolean("stickers_enabled", false).putBoolean("battery_troll_enabled", false).apply() }
    override fun render() {
        if (prefs.getBoolean("notch_enabled", false)) {
            val image = ImageView(this)
            val width = dp(prefs.getInt("custom_size_${R.id.notch_width}", 100).coerceAtLeast(1))
            val height = dp(prefs.getInt("custom_size_${R.id.notch_height}", 30).coerceAtLeast(1))
            image.scaleType = ImageView.ScaleType.FIT_XY
            add(image, width, height, (resources.displayMetrics.widthPixels - width) / 2 + dp(prefs.getInt("custom_size_${R.id.notch_horizontal}", 0)), dp(prefs.getInt("custom_size_${R.id.notch_vertical}", 0)), false)
            MediaAssets.load(image, AppliedContent.url(AppliedContent.selected(this, "notch")))
        }
        if (prefs.getBoolean("stickers_enabled", false) && prefs.getBoolean("stickers_applied", false)) {
            val items = runCatching { JSONArray(prefs.getString("applied_stickers", "[]")) }.getOrDefault(JSONArray())
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                if (StickerEditPreview.active && item.optString("id", "custom") == StickerEditPreview.editingId) return@repeat
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; rotation = item.optInt("overlay_rotation", 0).toFloat() }
                val size = dp(140 + item.optInt("overlay_size", 50).coerceIn(0, 100) * 280 / 100)
                    .coerceAtMost(minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels))
                val x = item.optDouble("overlay_x", 0.0).toInt()
                val y = dp(80) + item.optDouble("overlay_y", 0.0).toInt()
                add(image, size, size, x, y, touch = false, unrestricted = true)
                MediaAssets.load(image, AppliedContent.url(item))
            }
        }
        // Battery Troll belongs to the accessibility layer, above the system status bar.
        GestureAccessibilityService.active?.refreshTroll()
        if (listOf("notch_enabled", "stickers_enabled").none { prefs.getBoolean(it, false) }) stopSelf()
    }
}
