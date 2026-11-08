package com.hd.emojibattery.runtime

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.hd.emojibattery.R

/** The editor owns a draft; only Apply writes it to the active configuration. */
data class TrollBatteryConfig(
    val artwork: String = "",
    val size: Int = 30,
    val textSize: Int = 15,
    val number: Int = 999,
    val emoji: Boolean = true,
    val showNumber: Boolean = true,
    val random: Boolean = false,
    val real: Boolean = false
) : java.io.Serializable {
    fun save(prefs: SharedPreferences) {
        prefs.edit().putString("applied_troll", artwork)
            .putInt("custom_size_${R.id.troll_size}", size).putInt("troll_text_size", textSize)
            .putInt("fake_percentage", number).putBoolean("troll_emoji_enabled", emoji)
            .putBoolean("troll_show_percentage", showNumber).putBoolean("troll_random", random)
            .putBoolean("troll_edit_real", real).putBoolean("battery_troll_enabled", true).apply()
    }

    companion object {
        fun read(prefs: SharedPreferences) = TrollBatteryConfig(
            artwork = prefs.getString("applied_troll", "").orEmpty(),
            size = prefs.getInt("custom_size_${R.id.troll_size}", 30).coerceIn(15, 120),
            textSize = prefs.getInt("troll_text_size", 15).coerceIn(8, 32),
            number = prefs.getInt("fake_percentage", 999),
            emoji = prefs.getBoolean("troll_emoji_enabled", true),
            showNumber = prefs.getBoolean("troll_show_percentage", true),
            random = prefs.getBoolean("troll_random", false),
            real = prefs.getBoolean("troll_edit_real", false)
        )
    }
}

/** Transparent artwork with a number painted on top, never beside the artwork. */
class TrollBatteryView(context: Context) : FrameLayout(context) {
    private val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val number = TextView(context).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(Color.BLACK)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setSingleLine()
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var loadedUrl: String? = null
    init {
        addView(icon, LayoutParams(-1, -1))
        addView(number, LayoutParams(-1, -1))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    fun render(config: TrollBatteryConfig) {
        val url = AppliedContent.url(runCatching { org.json.JSONObject(config.artwork) }.getOrNull())
        if (loadedUrl != url) {
            MediaAssets.clear(icon)
            loadedUrl = url
            if (url.isNotBlank()) MediaAssets.load(icon, url)
        }
        icon.visibility = if (config.emoji) VISIBLE else GONE
        number.visibility = if (config.showNumber) VISIBLE else GONE
        // Scale the number together with the icon; the separate text slider adjusts the ratio.
        number.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, config.textSize * config.size / 30f * 0.8f)
        number.translationY = config.size * resources.displayMetrics.density * 0.08f
        updateNumber(config)
    }
    fun updateNumber(config: TrollBatteryConfig) {
        val value = if (config.real) {
            val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val scale = battery?.getIntExtra("scale", 100)?.coerceAtLeast(1) ?: 100
            (battery?.getIntExtra("level", 100) ?: 100) * 100 / scale
        } else if (config.random) kotlin.random.Random.nextInt(0, 1000) else config.number
        number.text = value.toString()
    }
    fun release() { MediaAssets.clear(icon); loadedUrl = null }
}

/** Owned by the connected AccessibilityService, so this window is above system status icons. */
class TrollBatteryOverlay(private val context: Context) {
    private val windows = context.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var view: TrollBatteryView? = null
    private var config: TrollBatteryConfig? = null
    private var charging = false
    private var batteryReceiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val nextCharging = isCharging(intent)
            if (charging == nextCharging) return
            charging = nextCharging
            val current = config ?: return
            view?.let { windows.updateViewLayout(it, position(current.size)) }
        }
    }
    private fun isCharging(intent: Intent?): Boolean {
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL &&
                (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0)
    }
    private val ticker = object : Runnable {
        override fun run() {
            config?.let { view?.updateNumber(it) }
            handler.postDelayed(this, 3000)
        }
    }
    fun show(next: TrollBatteryConfig?) {
        if (next == null) { close(); return }
        config = next
        if (!batteryReceiverRegistered) {
            val battery = context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
            charging = isCharging(battery)
        }
        val content = view ?: TrollBatteryView(context)
        content.render(next)
        val params = position(next.size)
        if (view == null) {
            RestrictedApps.watch(content)
            windows.addView(content, params)
            view = content
            handler.postDelayed(ticker, 3000)
        } else windows.updateViewLayout(content, params)
    }
    private fun position(sizeDp: Int): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        fun systemDimen(name: String, fallback: Int): Int {
            val id = context.resources.getIdentifier(name, "dimen", "android")
            return if (id != 0) context.resources.getDimensionPixelSize(id) else (fallback * density).toInt()
        }
        val insets = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.windowInsets else null
        val barHeight = insets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())?.top
            ?.takeIf { it > 0 } ?: systemDimen("status_bar_height", 24)
        val size = (sizeDp * density).toInt()
        return WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            // Physical right margin, independent of the app language and artwork size.
            gravity = Gravity.TOP or Gravity.RIGHT
            x = ((if (charging) 26 else 22) * density).toInt()
            // Keep the vertical centre on the status bar when scaling.
            y = (barHeight - size) / 2
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            if (Build.VERSION.SDK_INT >= 30) setFitInsetsTypes(0)
            setTitle("Battery Troll")
        }
    }
    fun close() {
        handler.removeCallbacksAndMessages(null)
        if (batteryReceiverRegistered) {
            context.unregisterReceiver(batteryReceiver)
            batteryReceiverRegistered = false
        }
        view?.let { it.release(); windows.removeView(it) }
        view = null
        config = null
    }
}
