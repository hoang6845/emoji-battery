package com.hd.emojibattery.runtime

import android.content.Context
import org.json.JSONObject

/** Catalog identity and the identity of one placed copy are deliberately separate. */
object StickerState {
    fun instanceId(item: JSONObject): String = item.optString("instance_id").ifBlank { item.optString("id", "custom") }

    fun sizePixels(context: Context, item: JSONObject): Int {
        val metrics = context.resources.displayMetrics
        val legacy = ((140 + item.optInt("overlay_size", 50).coerceIn(0, 100) * 280 / 100) * metrics.density).toInt()
        return item.optInt("overlay_size_px", legacy).coerceIn(1, minOf(metrics.widthPixels, metrics.heightPixels))
    }

    /** The persisted Y is relative to the historical 80dp overlay origin. */
    fun screenY(context: Context, item: JSONObject): Float =
        (80 * context.resources.displayMetrics.density).toInt() + item.optDouble("overlay_y", 0.0).toFloat()

    fun rotation(item: JSONObject): Int {
        val degrees = item.optInt("overlay_rotation", 0)
        return ((degrees + 180) % 360 + 360) % 360 - 180
    }
}
