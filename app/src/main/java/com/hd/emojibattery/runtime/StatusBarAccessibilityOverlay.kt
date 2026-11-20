package com.hd.emojibattery.runtime

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager

/** The connected accessibility service owns the token needed to cover system status icons. */
class StatusBarAccessibilityOverlay(private val service: AccessibilityService) {
    private val windows = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: StatusRenderer? = null
    private val ticker = object : Runnable {
        override fun run() {
            view?.update()
            if (view != null) handler.postDelayed(this, 1000)
        }
    }

    fun refresh() {
        close()
        val prefs = AppliedContent.prefs(service)
        if (!prefs.getBoolean("status_overlay_enabled", false)) return
        val density = service.resources.displayMetrics.density
        val systemHeight = if (Build.VERSION.SDK_INT >= 30) {
            windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        } else {
            val id = service.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (id != 0) service.resources.getDimensionPixelSize(id) else 0
        }
        val height = (prefs.getInt("status_height", 45).coerceIn(24, 100) * density).toInt().coerceAtLeast(systemHeight)
        val touch = prefs.getBoolean("gesture_enabled", false)
        val content = StatusRenderer(service, interactive = touch, useLiveNetwork = true)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            setTitle("Custom Status Bar")
        }
        RestrictedApps.watch(content)
        windows.addView(content, params)
        view = content
        handler.post(ticker)
    }

    fun close() {
        handler.removeCallbacksAndMessages(null)
        view?.let { windows.removeView(it) }
        view = null
    }
}
