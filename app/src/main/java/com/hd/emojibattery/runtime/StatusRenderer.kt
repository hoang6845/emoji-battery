package com.hd.emojibattery.runtime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.Gravity
import android.view.View
import android.widget.*
import com.hd.emojibattery.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StatusRenderer(
    context: Context,
    interactive: Boolean = false,
    private val prefs: android.content.SharedPreferences = AppliedContent.prefs(context),
    private val useLiveNetwork: Boolean = false
) : FrameLayout(context) {
    companion object {
        val chargeStyles = intArrayOf(R.drawable.status_charge_style_0, R.drawable.status_charge_style_1, R.drawable.status_charge_style_2, R.drawable.status_charge_style_3, R.drawable.status_charge_style_4, R.drawable.status_charge_style_5, R.drawable.status_charge_style_6, R.drawable.status_charge_style_7)
    }
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val mobileSignal = ImageView(context)
    private val mobileData = TextView(context)
    private val wifi = ImageView(context)
    private var networkCallbackRegistered = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetwork()
        override fun onLost(network: Network) = refreshNetwork()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshNetwork()
    }
    private fun refreshNetwork() {
        post { if (isAttachedToWindow) updateNetwork() }
    }
    private fun updateNetwork() {
        if (!useLiveNetwork) return
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val onWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val onMobile = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        wifi.visibility = if (onWifi) View.VISIBLE else View.GONE
        mobileSignal.visibility = if (!onWifi && onMobile) View.VISIBLE else View.GONE
        mobileData.visibility = if (!onWifi && onMobile) View.VISIBLE else View.GONE
    }
    private val percentage = TextView(context)
    private val date = TextView(context)
    private val charge = ImageView(context)
    private var chargeAnimator: android.animation.ObjectAnimator? = null
    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { update() }
    }
    private val images = mutableListOf<ImageView>()
    private var dateJob: Job? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    init {
        setBackgroundColor(prefs.getInt("status_color_0", Color.WHITE))
        val background = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
        addView(background, LayoutParams(-1, -1)); images.add(background)
        if (prefs.getBoolean("status_part_background", true)) MediaAssets.load(background, AppliedContent.url(AppliedContent.selected(context, "background")))
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        addView(row, LayoutParams(-1, -1))
        date.setTextColor(prefs.getInt("color_${R.id.date_colors}", prefs.getInt("status_color_2", 0xff27205f.toInt())))
        date.textSize = prefs.getInt("size_${R.id.date_size}", 12).coerceIn(8, 48).toFloat()
        val font = prefs.getInt("date_font_index", 0)
        date.typeface = android.graphics.Typeface.create(if (font == 4) "serif" else "sans-serif", if (font == 6) 1 else 0)
        date.visibility = if (prefs.getBoolean("status_part_date", true)) View.VISIBLE else View.GONE
        row.setPadding(dp(prefs.getInt("status_margin_left", 12)), 0, dp(prefs.getInt("status_margin_right", 12)), 0)
        row.addView(date)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        val themeSlots = listOf("emoji" to R.id.status_size_emoji,
            "emotion" to R.id.status_size_emotion, "animation" to R.id.status_size_animation)
        themeSlots.forEach { (slot, sizeId) ->
            val url = AppliedContent.url(AppliedContent.selected(context, slot))
            if (url.isNotBlank() && prefs.getBoolean("status_part_$slot", true)) {
                val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
                val size = dp(prefs.getInt("size_$sizeId", 40).coerceIn(8, 100))
                row.addView(image, LinearLayout.LayoutParams(size, size).apply { marginStart = dp(4) }); images.add(image); MediaAssets.load(image, url)
            }
        }
        percentage.textSize = prefs.getInt("icon_size", 15).coerceIn(8, 48).toFloat()
        percentage.setTextColor(prefs.getInt("color_${R.id.icon_colors}", prefs.getInt("status_color_1", 0xff27205f.toInt())))
        val iconColor = prefs.getInt("color_${R.id.icon_colors}", prefs.getInt("status_color_1", 0xff27205f.toInt()))
        val iconSize = dp(prefs.getInt("icon_size", 15).coerceIn(8, 48))
        val signal = mobileSignal.apply { setImageResource(R.drawable.ic_status_signal); setColorFilter(iconColor) }
        row.addView(signal, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginStart = dp(6) })
        val data = mobileData.apply {
            text = "${prefs.getInt("icon_style_1", 2) + 2}G"
            textSize = prefs.getInt("icon_size", 15).toFloat(); setTextColor(iconColor)
        }
        row.addView(data, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        wifi.apply {
            setImageDrawable(WifiStatusDrawable(iconColor))
            contentDescription = "Wi-Fi"
            visibility = View.GONE
        }
        row.addView(wifi, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginStart = dp(4) })
        percentage.apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
        }
        row.addView(percentage, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        val batteryHeight = dp(prefs.getInt("size_${R.id.status_size_battery}", 35).coerceIn(8, 100))
        val batteryHost = FrameLayout(context)
        row.addView(batteryHost, LinearLayout.LayoutParams((batteryHeight * 2.2f).toInt(), batteryHeight).apply { marginStart = dp(4) })
        val batteryArt = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
        batteryHost.addView(batteryArt, LayoutParams(-1, -1))
        images.add(batteryArt)
        val batteryUrl = AppliedContent.url(AppliedContent.selected(context, "battery"))
        if (prefs.getBoolean("status_part_battery", true) && batteryUrl.isNotBlank()) {
            MediaAssets.load(batteryArt, batteryUrl) { loaded ->
                if (loaded) {
                    val art = batteryArt.drawable
                    if (art.intrinsicWidth > 0 && art.intrinsicHeight > 0) {
                        batteryHost.layoutParams = (batteryHost.layoutParams as LinearLayout.LayoutParams).apply {
                            width = (batteryHeight * art.intrinsicWidth.toFloat() / art.intrinsicHeight).toInt().coerceAtLeast(1)
                        }
                    }
                }
            }
        }
        charge.apply {
            setImageResource(chargeStyles[prefs.getInt("icon_style_2", 0).coerceIn(0, chargeStyles.lastIndex)])
            setColorFilter(iconColor)
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }
        row.addView(charge, LinearLayout.LayoutParams(iconSize, iconSize).apply { marginStart = dp(4) })
        if (interactive) GestureActions.attach(this)
    }
    fun update() {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        percentage.text = "${(level * 100 / scale).coerceIn(0, 100)}%"
        val pluggedIn = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val showCharge = pluggedIn
        charge.visibility = if (showCharge) View.VISIBLE else View.GONE
        if (showCharge && isAttachedToWindow && chargeAnimator == null) {
            chargeAnimator = android.animation.ObjectAnimator.ofFloat(charge, View.ALPHA, 1f, 0.15f).apply {
                duration = 550L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }
        } else if (!showCharge) {
            chargeAnimator?.cancel()
            chargeAnimator = null
            charge.alpha = 1f
        }
        dateJob?.cancel()
        if (date.visibility == View.VISIBLE) {
            val pattern = prefs.getString("date_format", "h:mm a") ?: "h:mm a"
            dateJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                date.text = StatusDateFormatter.format(pattern)
            }
        }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        androidx.core.content.ContextCompat.registerReceiver(context, batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        if (useLiveNetwork) {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
            updateNetwork()
        }
        update()
    }
    override fun onDetachedFromWindow() {
        if (networkCallbackRegistered) {
            connectivity.unregisterNetworkCallback(networkCallback)
            networkCallbackRegistered = false
        }
        context.unregisterReceiver(batteryReceiver)
        chargeAnimator?.cancel()
        chargeAnimator = null
        dateJob?.cancel()
        dateJob = null
        images.forEach(MediaAssets::clear)
        super.onDetachedFromWindow()
    }
}
