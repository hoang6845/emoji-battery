package com.hd.emojibattery

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.hd.emojibattery.R

class StatusBarOverlayService : Service() {
    private var manager: WindowManager? = null
    private var strip: LinearLayout? = null
    private var batteryView: TextView? = null
    private var receiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return }
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val prefs = getSharedPreferences("emoji_battery", MODE_PRIVATE)
        strip = LayoutInflater.from(this).inflate(R.layout.view_status_bar_overlay, null) as LinearLayout
        batteryView = strip?.findViewById(R.id.status_overlay_battery)
        strip?.findViewById<TextView>(R.id.status_overlay_emoji)?.apply {
            text = prefs.getString("status_emoji", prefs.getString("emoji", "🐶"))
            textSize = prefs.getInt("status_emoji_size", 15).toFloat()
        }
        strip?.findViewById<TextView>(R.id.status_overlay_clock)?.setTextColor(prefs.getInt("status_icon_color", 0xff27205f.toInt()))
        batteryView?.setTextColor(prefs.getInt("status_icon_color", 0xff27205f.toInt()))
        val position = prefs.getString("status_position", "Left")
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, dp(30),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or when (position) { "Center" -> Gravity.CENTER_HORIZONTAL; "Right" -> Gravity.RIGHT; else -> Gravity.LEFT }
            y = 0
        }
        runCatching { manager?.addView(strip, params) }.onFailure { stopSelf(); return }
        receiver = object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { updateBattery(intent) } }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(receiver, filter)
        updateBattery(null)
    }

    private fun updateBattery(intent: Intent?) {
        val battery = intent ?: registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, 100)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        batteryView?.text = "${if (scale > 0) level * 100 / scale else 100}%  ▰"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        runCatching { strip?.let { manager?.removeView(it) } }
        receiver = null; strip = null; batteryView = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
