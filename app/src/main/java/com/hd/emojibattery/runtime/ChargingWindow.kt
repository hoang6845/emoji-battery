package com.hd.emojibattery.runtime

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/** Owned by the already running monitor service; no background Activity launch. */
class ChargingWindow(private val context: Context) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ChargingMediaView? = null
    fun show() {
        close()
        if (!Settings.canDrawOverlays(context)) return
        val media = ChargingMediaView(context)
        val params = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.CENTER }
        if (runCatching { RestrictedApps.watch(media); manager.addView(media, params) }.isFailure) { media.close(); return }
        view = media
        media.setOnClickListener { close() }
        val delay = AppliedContent.prefs(context).getInt("charging_hide_after", 15).coerceAtLeast(1) * 1000L
        handler.postDelayed({ close() }, delay)
        handler.post(object : Runnable { override fun run() { view?.updateInfo(); if (view != null) handler.postDelayed(this, 1000) } })
    }
    fun close() { handler.removeCallbacksAndMessages(null); view?.let { it.close(); runCatching { manager.removeView(it) } }; view = null }
}
