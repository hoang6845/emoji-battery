package com.hd.emojibattery.runtime

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.*

/** GIF/WebP via ImageDecoder; MP4/WebM via VideoView, all from the authenticated disk cache. */
class ChargingMediaView(context: Context, private val showInfo: Boolean = true) : FrameLayout(context) {
    private val background = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val animation = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val video = VideoView(context)
    private val info = android.view.LayoutInflater.from(context)
        .inflate(com.hd.emojibattery.R.layout.layout_charging_full_preview, this, false).let { preview ->
            val group = preview.findViewById<android.view.View>(com.hd.emojibattery.R.id.charging_full_percent).parent as android.view.ViewGroup
            (group.parent as android.view.ViewGroup).removeView(group)
            group
        }
    private var closed = false
    init {
        setBackgroundColor(Color.BLACK)
        addView(background, LayoutParams(-1, -1))
        addView(animation, LayoutParams(-1, -1))
        addView(video, LayoutParams(-1, -1, Gravity.CENTER)); video.visibility = GONE
        // Preview owns its information overlay. Do not attach duplicate IDs to
        // its view tree when this instance is only rendering the media.
        if (showInfo) addView(info, LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = (104 * resources.displayMetrics.density).toInt() })
        val fullSize = maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels).coerceAtMost(2160)
        MediaAssets.load(background, AppliedContent.url(AppliedContent.selected(context, "wallpaper")), fullSize)
        val item = AppliedContent.selected(context, "charging")
        val url = AppliedContent.url(item).ifBlank { item?.optString("animation_url").orEmpty() }
        val isVideo = item?.optString("media_type") == "video" || url.substringBefore('?').endsWith(".mp4", true) || url.substringBefore('?').endsWith(".webm", true)
        if (isVideo) {
            val app = context.applicationContext
            MediaAssets.io.execute {
                val file = runCatching { MediaAssets.file(app, url) }.getOrNull()
                post {
                    if (!closed && file != null) {
                        video.visibility = VISIBLE
                        video.setVideoURI(Uri.fromFile(file))
                        video.setOnPreparedListener { it.isLooping = true; it.setVolume(0f, 0f); video.start() }
                        video.setOnErrorListener { _, _, _ -> Toast.makeText(context, "Cannot play this video format", Toast.LENGTH_LONG).show(); true }
                    }
                }
            }
        } else MediaAssets.load(animation, url, fullSize)
        updateInfo()
    }
    fun updateInfo() {
        info.visibility = if (showInfo && AppliedContent.prefs(context).getBoolean("charging_show_info", true)) VISIBLE else GONE
        if (info.visibility != VISIBLE) return
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val scale = battery?.getIntExtra("scale", 100)?.coerceAtLeast(1) ?: 100
        info.findViewById<TextView>(com.hd.emojibattery.R.id.charging_full_percent).text = "${(battery?.getIntExtra("level", 0) ?: 0) * 100 / scale}%"
        val plugged = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        info.findViewById<TextView>(com.hd.emojibattery.R.id.charging_full_status).text = if (plugged) "Charging in progress" else "Not charging in progress"
        val remaining = if (plugged && android.os.Build.VERSION.SDK_INT >= 28) context.getSystemService(android.os.BatteryManager::class.java).computeChargeTimeRemaining() else -1L
        info.findViewById<TextView>(com.hd.emojibattery.R.id.charging_full_temp).text = if (remaining > 0) "${(remaining / 60000).coerceAtLeast(1)} min" else "Unknown"
        val voltage = (battery?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        info.findViewById<TextView>(com.hd.emojibattery.R.id.charging_full_voltage).text = if (voltage > 0) String.format("%.2fV", voltage) else "—"
    }
    fun close() { closed = true; MediaAssets.clear(background); MediaAssets.clear(animation); video.stopPlayback() }
    override fun onDetachedFromWindow() { close(); super.onDetachedFromWindow() }
}
