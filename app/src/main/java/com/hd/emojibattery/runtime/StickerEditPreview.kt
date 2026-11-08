package com.hd.emojibattery.runtime

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import org.json.JSONObject

/** Activity-owned draft. No preferences are written until the adjustment is confirmed. */
class StickerEditPreview(context: Context) {
    companion object {
        var active = false
            private set
        var editingId: String? = null
            private set

        fun sizePixels(context: Context, progress: Int): Int {
            val metrics = context.resources.displayMetrics
            return ((140 + progress.coerceIn(0, 100) * 280 / 100) * metrics.density).toInt()
                .coerceAtMost(minOf(metrics.widthPixels, metrics.heightPixels))
        }

        fun place(context: Context, params: WindowManager.LayoutParams, item: JSONObject) {
            val metrics = context.resources.displayMetrics
            val size = sizePixels(context, item.optInt("overlay_size", 50))
            params.width = size
            params.height = size
            params.x = item.optDouble("overlay_x", 0.0).toInt()
            params.y = ((80 * metrics.density).toInt() + item.optDouble("overlay_y", 0.0).toInt())
        }
    }

    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val params = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
        gravity = Gravity.TOP or Gravity.LEFT
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    private var attached = false
    private var closed = false
    var ready = false
        private set

    fun show(item: JSONObject, loaded: (Boolean) -> Unit): Boolean = runCatching {
        place(image.context, params, item)
        image.rotation = item.optInt("overlay_rotation").toFloat()
        RestrictedApps.watch(image)
        windows.addView(image, params)
        attached = true
        active = true
        editingId = item.optString("id", "custom")
        MediaAssets.load(image, AppliedContent.url(item)) { success ->
            if (!closed) { ready = success; loaded(success) }
        }
        true
    }.getOrDefault(false)

    fun update(item: JSONObject) {
        if (!attached || closed) return
        place(image.context, params, item)
        image.rotation = item.optInt("overlay_rotation").toFloat()
        windows.updateViewLayout(image, params)
    }

    fun close() {
        if (closed) return
        closed = true
        MediaAssets.clear(image)
        if (attached) runCatching { windows.removeViewImmediate(image) }
        attached = false
        active = false
        editingId = null
    }
}
