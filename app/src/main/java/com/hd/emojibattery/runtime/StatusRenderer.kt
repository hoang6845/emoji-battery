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
    private fun selected(slot: String): org.json.JSONObject? =
        runCatching { org.json.JSONObject(prefs.getString("applied_$slot", "").orEmpty()) }.getOrNull()
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
    private val backgroundArt = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val emoji = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val animationArt = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val batteryHost = FrameLayout(context)
    private val batteryArt = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val emotion = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val rendered = mutableMapOf<String, Any?>()
    private val loadedUrls = mutableMapOf<ImageView, String>()

    private fun <T> changed(key: String, value: T, apply: (T) -> Unit) {
        if (!rendered.containsKey(key) || rendered[key] != value) {
            rendered[key] = value
            apply(value)
        }
    }

    private fun media(view: ImageView, url: String) {
        if (loadedUrls[view] == url) return
        loadedUrls[view] = url
        if (url.isBlank()) MediaAssets.clear(view) else MediaAssets.load(view, url)
    }

    init {
        addView(backgroundArt, LayoutParams(-1, -1))
        addView(row, LayoutParams(-1, -1))
        row.addView(date)
        row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        fun addIcon(view: View, margin: Int = 4) {
            row.addView(view, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(margin) })
        }
        addIcon(emoji)
        addIcon(animationArt)
        mobileSignal.setImageResource(R.drawable.status_reference_signal)
        addIcon(mobileSignal, 6)
        addIcon(mobileData)
        wifi.setImageResource(R.drawable.status_reference_wifi)
        wifi.contentDescription = "Wi-Fi"
        wifi.visibility = View.GONE
        addIcon(wifi)
        percentage.apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
        }
        addIcon(percentage)
        addIcon(batteryHost)
        batteryHost.addView(batteryArt, LayoutParams(1, 1, Gravity.CENTER))
        batteryHost.addView(emotion, LayoutParams(1, 1, Gravity.CENTER))
        charge.scaleType = ImageView.ScaleType.FIT_CENTER
        charge.visibility = View.GONE
        addIcon(charge)
        images.addAll(listOf(backgroundArt, emoji, animationArt, batteryArt, emotion))
        refreshSettings()
        if (interactive) GestureActions.attach(this)
    }

    /** Update existing children; unchanged media keeps its drawable and animation position. */
    fun refreshSettings() {
        changed("backgroundColor", prefs.getInt("status_color_0", Color.WHITE)) { setBackgroundColor(it) }
        media(backgroundArt, if (prefs.getBoolean("status_part_background", true)) AppliedContent.url(selected("background")) else "")
        changed("dateColor", prefs.getInt("color_${R.id.date_colors}", prefs.getInt("status_color_2", 0xff27205f.toInt()))) { date.setTextColor(it) }
        changed("dateSize", prefs.getInt("size_${R.id.date_size}", 12).coerceIn(8, 48)) { date.textSize = it.toFloat() }
        changed("dateFont", prefs.getInt("date_font_index", 0)) {
            date.typeface = StatusDateFonts.typeface(context, it)
        }
        changed("dateVisible", prefs.getBoolean("status_part_date", true)) {
            date.visibility = if (it) View.VISIBLE else View.GONE
            updateDate()
        }
        changed("dateFormat", prefs.getString("date_format", "h:mm a")) { updateDate() }
        changed("margins", prefs.getInt("status_margin_left", 12) to prefs.getInt("status_margin_right", 12)) {
            row.setPadding(dp(it.first), 0, dp(it.second), 0)
        }
        // Fit the entire artwork inside the fixed bar instead of clipping its edges.
        val barHeight = height.takeIf { it > 0 } ?: dp(prefs.getInt("status_height", 45).coerceIn(24, 100))
        fun size(id: Int, default: Int) = dp(prefs.getInt("size_$id", default).coerceIn(8, 100)).coerceAtMost(barHeight)
        fun dimensions(view: View, width: Int, height: Int) {
            if (view.layoutParams.width != width || view.layoutParams.height != height) {
                view.layoutParams = view.layoutParams.apply { this.width = width; this.height = height }
            }
        }
        listOf(Triple("emoji", R.id.status_size_emoji, emoji), Triple("animation", R.id.status_size_animation, animationArt)).forEach { (slot, id, view) ->
            val url = AppliedContent.url(selected(slot))
            view.visibility = if (url.isNotBlank() && prefs.getBoolean("status_part_$slot", true)) View.VISIBLE else View.GONE
            dimensions(view, size(id, 40), size(id, 40))
            media(view, url)
        }
        val iconSize = prefs.getInt("icon_size", 15).coerceIn(8, 48)
        changed("iconSize", iconSize) {
            percentage.textSize = it.toFloat()
            mobileData.textSize = it.toFloat()
        }
        listOf(mobileSignal, wifi, charge).forEach { dimensions(it, dp(iconSize).coerceAtMost(barHeight), dp(iconSize).coerceAtMost(barHeight)) }
        changed("percentageColor", prefs.getInt("status_percentage_color", prefs.getInt("status_color_1", 0xff27205f.toInt()))) { percentage.setTextColor(it) }
        changed("signalColor", prefs.getInt("status_icon_color_0", 0xff27205f.toInt())) { mobileSignal.setColorFilter(it); wifi.setColorFilter(it) }
        changed("dataColor", prefs.getInt("status_icon_color_1", 0xff27205f.toInt())) { mobileData.setTextColor(it) }
        changed("dataStyle", prefs.getInt("icon_style_1", 2)) { mobileData.text = "${it + 2}G" }
        changed("chargeStyle", prefs.getInt("icon_style_2", 0).coerceIn(0, chargeStyles.lastIndex)) { charge.setImageResource(chargeStyles[it]) }
        changed("chargeColor", prefs.getInt("status_icon_color_2", 0xff27205f.toInt())) { charge.setColorFilter(it) }
        // Preserve the original combined src until an individual layer needs editing.
        val hasComponentArtwork = AppliedContent.url(selected("battery_component")).isNotBlank() &&
            AppliedContent.url(selected("emotion")).isNotBlank()
        val needsSeparateLayers = prefs.getInt("size_${R.id.status_size_battery}", 35) != 35 ||
            prefs.getInt("size_${R.id.status_size_emotion}", 40) != 40 ||
            !prefs.getBoolean("status_part_emotion", true)
        val composite = prefs.getBoolean("status_battery_composite", false) ||
            (hasComponentArtwork && (needsSeparateLayers || AppliedContent.url(selected("battery")).isBlank()))
        val showBattery = prefs.getBoolean("status_part_battery", true)
        val emotionUrl = AppliedContent.url(selected("emotion"))
        val showEmotion = composite && showBattery && prefs.getBoolean("status_part_emotion", true) && emotionUrl.isNotBlank()
        val batterySize = size(R.id.status_size_battery, 35)
        val batteryWidth = (batterySize * 2.2f).toInt()
        val emotionSize = size(R.id.status_size_emotion, 40)
        // The emoji is a sibling over the battery artwork, with enough host space of its own.
        // Its bounds must not be clipped to the smaller battery's dimensions.
        dimensions(batteryHost, maxOf(batteryWidth, if (showEmotion) emotionSize else 0), maxOf(batterySize, if (showEmotion) emotionSize else 0))
        dimensions(batteryArt, batteryWidth, batterySize)
        dimensions(emotion, emotionSize, emotionSize)
        batteryHost.visibility = if (showBattery) View.VISIBLE else View.GONE
        emotion.visibility = if (showEmotion) View.VISIBLE else View.GONE
        media(batteryArt, if (showBattery) AppliedContent.url(selected(if (composite) "battery_component" else "battery")) else "")
        media(emotion, if (showEmotion) emotionUrl else "")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) refreshSettings()
    }

    private fun updateDate() {
        dateJob?.cancel()
        if (date.visibility == View.VISIBLE) {
            val pattern = prefs.getString("date_format", "h:mm a") ?: "h:mm a"
            dateJob = CoroutineScope(Dispatchers.Main.immediate).launch {
                date.text = StatusDateFormatter.format(pattern)
            }
        }
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
        updateDate()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshSettings()
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
        loadedUrls.clear()
        super.onDetachedFromWindow()
    }
}
