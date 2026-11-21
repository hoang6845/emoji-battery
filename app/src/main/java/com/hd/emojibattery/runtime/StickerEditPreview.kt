package com.hd.emojibattery.runtime

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import org.json.JSONObject

/** Activity-owned draft. No preferences are written until the adjustment is confirmed. */
class StickerEditPreview(context: android.accessibilityservice.AccessibilityService) {
    companion object {
        var active = false
            private set
        var editingId: String? = null
            private set

        fun selectInline(id: String?): Boolean {
            if (editingId == id && active == (id != null)) return false
            editingId = id
            active = id != null
            return true
        }

        fun place(context: Context, params: WindowManager.LayoutParams, item: JSONObject) {
            val size = StickerState.sizePixels(context, item)
            params.width = size
            params.height = size
            params.x = item.optDouble("overlay_x", 0.0).toInt()
            params.y = StickerState.screenY(context, item).toInt()
        }
    }

    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val params = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
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
        editingId = StickerState.instanceId(item)
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
