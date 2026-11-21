package com.hd.emojibattery

import android.widget.*
import com.hd.emojibattery.runtime.*
import org.json.JSONArray

/** Independent small windows: transparent full-screen surfaces never intercept other apps. */
class DecorationService : OverlayService() {
    override val usesAccessibilityHost = true
    override fun onStopRequested() { prefs.edit().putBoolean("notch_enabled", false).putBoolean("stickers_enabled", false).putBoolean("battery_troll_enabled", false).apply() }
    override fun render() {
        val notchActive = prefs.getBoolean("notch_enabled", false) && prefs.getBoolean("status_overlay_enabled", false)
        if (notchActive) {
            val image = ImageView(this)
            val width = dp(prefs.getInt("custom_size_${R.id.notch_width}", 120).coerceAtLeast(1))
            val height = dp(prefs.getInt("custom_size_${R.id.notch_height}", 16).coerceAtLeast(1))
            image.scaleType = ImageView.ScaleType.FIT_XY
            add(image, width, height, (resources.displayMetrics.widthPixels - width) / 2 + dp(prefs.getInt("custom_size_${R.id.notch_horizontal}", 0)), dp(prefs.getInt("custom_size_${R.id.notch_vertical}", 0)), false)
            MediaAssets.load(image, AppliedContent.url(AppliedContent.selected(this, "notch")))
        }
        if (prefs.getBoolean("stickers_enabled", false) && prefs.getBoolean("stickers_applied", false)) {
            val items = runCatching { JSONArray(prefs.getString("applied_stickers", "[]")) }.getOrDefault(JSONArray())
            repeat(items.length()) { index ->
                val item = items.optJSONObject(index) ?: return@repeat
                if (StickerEditPreview.active && StickerState.instanceId(item) == StickerEditPreview.editingId) return@repeat
                val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; rotation = item.optInt("overlay_rotation", 0).toFloat() }
                image.scaleX = item.optDouble("overlay_flip", 1.0).toFloat()
                val size = StickerState.sizePixels(this, item)
                val x = item.optDouble("overlay_x", 0.0).toInt()
                val y = StickerState.screenY(this, item).toInt()
                add(image, size, size, x, y, touch = false, unrestricted = true)
                MediaAssets.load(image, AppliedContent.url(item))
            }
        }
        // Battery Troll belongs to the accessibility layer, above the system status bar.
        GestureAccessibilityService.active?.refreshTroll()
        if (!notchActive && !prefs.getBoolean("stickers_enabled", false)) stopSelf()
    }
}
