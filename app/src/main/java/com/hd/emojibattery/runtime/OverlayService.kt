package com.hd.emojibattery.runtime

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R

abstract class OverlayService : Service() {
    protected open val usesAccessibilityHost = false
    protected val windows by lazy {
        (if (usesAccessibilityHost) com.hd.emojibattery.GestureAccessibilityService.active!! else this)
            .getSystemService(WINDOW_SERVICE) as WindowManager
    }
    protected val handler = Handler(Looper.getMainLooper())
    protected val prefs by lazy { AppliedContent.prefs(this) }
    private val views = mutableListOf<View>()
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("active_overlays", "Active overlays", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, javaClass.name.hashCode(), Intent(this, javaClass).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(500 + (javaClass.name.hashCode() and 0xffff), NotificationCompat.Builder(this, "active_overlays")
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("Emoji Battery")
            .setContentText("${javaClass.simpleName.removeSuffix("Service")} active")
            .setContentIntent(open).addAction(0, "Stop", stop).setOngoing(true).build())
    }
    final override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { onStopRequested(); stopSelf(); return START_NOT_STICKY }
        val permitted = if (usesAccessibilityHost) com.hd.emojibattery.GestureAccessibilityService.active != null
            else Settings.canDrawOverlays(this)
        if (!permitted) { stopSelf(); return START_NOT_STICKY }
        clearWindows()
        render()
        return START_NOT_STICKY
    }
    protected abstract fun render()
    protected open fun onStopRequested() = Unit
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        clearWindows(); render()
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun add(view: View, width: Int, height: Int, x: Int = 0, y: Int = 0, touch: Boolean = true, unrestricted: Boolean = false): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(width, height,
            if (usesAccessibilityHost) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                (if (touch) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) or
                (if (unrestricted) WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            this.x = x; this.y = y
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { RestrictedApps.watch(view, requiresAccessibility = usesAccessibilityHost); windows.addView(view, params); views.add(view) }.onFailure { stopSelf() }
        return params
    }
    protected fun move(view: View, params: WindowManager.LayoutParams) { runCatching { windows.updateViewLayout(view, params) } }
    protected open fun clearWindows() {
        handler.removeCallbacksAndMessages(null)
        fun release(view: View) {
            if (view is ImageView) MediaAssets.clear(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) release(view.getChildAt(i))
        }
        views.forEach { release(it); runCatching { windows.removeView(it) } }; views.clear()
    }
    override fun onDestroy() { clearWindows(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
