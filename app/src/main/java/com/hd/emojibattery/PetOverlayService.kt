package com.hd.emojibattery

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.hd.emojibattery.R

class PetOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var petView: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var petEmoji = "🐱"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        petEmoji = getSharedPreferences("emoji_battery", MODE_PRIVATE).getString("pet_emoji", "🐱") ?: "🐱"
        petView = (LayoutInflater.from(this).inflate(R.layout.view_pet_overlay, null) as TextView).apply { text = petEmoji }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        params = WindowManager.LayoutParams(dp(88), dp(88), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = (resources.displayMetrics.widthPixels - dp(100)).coerceAtLeast(0)
            y = resources.displayMetrics.heightPixels / 2
        }
        petView?.setOnTouchListener { view, event ->
            val layout = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = layout.x; startY = layout.y; true }
                MotionEvent.ACTION_MOVE -> {
                    layout.x = startX + (event.rawX - downX).toInt()
                    layout.y = startY + (event.rawY - downY).toInt()
                    runCatching { windowManager?.updateViewLayout(view, layout) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (kotlin.math.abs(event.rawX - downX) < dp(8) && kotlin.math.abs(event.rawY - downY) < dp(8)) {
                        (view as TextView).text = if (view.text == "✨") petEmoji else "✨"
                    }
                    true
                }
                else -> true
            }
        }
        runCatching { petView?.let { com.hd.emojibattery.runtime.RestrictedApps.watch(it) }; windowManager?.addView(petView, params) }.onFailure { stopSelf() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onDestroy() { runCatching { petView?.let { windowManager?.removeView(it) } }; petView = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
