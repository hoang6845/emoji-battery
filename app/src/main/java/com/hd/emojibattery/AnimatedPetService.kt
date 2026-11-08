package com.hd.emojibattery

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.MotionEvent
import android.widget.ImageView
import com.hd.emojibattery.runtime.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class AnimatedPetService : OverlayService() {
    private var generation = 0
    private var batteryPercent = 100
    override fun onStopRequested() { prefs.edit().putBoolean("pet_overlay_enabled", false).apply() }
    override fun clearWindows() { generation++; super.clearWindows() }
    override fun render() {
        if (!prefs.getBoolean("pet_overlay_enabled", false)) { stopSelf(); return }
        val pets = com.hd.emojibattery.data.PetSourcePolicy.filter(runCatching { JSONArray(prefs.getString("applied_pets", "[]")) }.getOrDefault(JSONArray()))
        if (pets.length() == 0) { prefs.edit().putBoolean("pet_overlay_enabled", false).apply(); stopSelf(); return }
        handler.post(object : Runnable {
            override fun run() {
                val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
                batteryPercent = (battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100) * 100 / scale
                handler.postDelayed(this, 5000)
            }
        })
        repeat(minOf(6, pets.length())) { index -> pets.optJSONObject(index)?.let { showPet(it, index) } }
    }
    private fun showPet(pet: JSONObject, index: Int) {
        val version = generation
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = pet.optString("name") }
        val size = dp(88)
        val maxX = (resources.displayMetrics.widthPixels - size).coerceAtLeast(0)
        val maxY = (resources.displayMetrics.heightPixels - size - dp(24)).coerceAtLeast(0)
        val position = prefs.getString("pet_position", "Bottom right").orEmpty()
        val params = add(image, size, size, if (position.contains("left")) (index * size).coerceAtMost(maxX) else (maxX - index * size).coerceAtLeast(0), if (position.contains("Top")) dp(32) else maxY)
        var direction = if (index % 2 == 0) -1 else 1
        var verticalDirection = -1
        var edge = if (position.contains("Top")) "top" else "bottom"
        var dragging = false
        var action = "walk"
        var actionUntil = 0L
        var frameIndex = 0
        var currentFrames = emptyList<String>()
        var currentUrl = ""
        var loading = false
        var lastAmbient = android.os.SystemClock.uptimeMillis()
        var ambientIndex = 0
        val ambient = listOf("run", "sit", "walk", "sleep", "walk")
        fun choose(value: String, duration: Long = 2000) { action = value; actionUntil = android.os.SystemClock.uptimeMillis() + duration; frameIndex = 0 }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var downTime = 0L
        image.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; downTime = event.eventTime; dragging = true; choose("drag", 60000) }
                MotionEvent.ACTION_MOVE -> {
                    params.x = (startX + event.rawX.toInt() - downX.toInt()).coerceIn(0, maxX)
                    params.y = (startY + event.rawY.toInt() - downY.toInt()).coerceIn(0, maxY)
                    move(image, params)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    val tap = abs(event.rawX - downX) < dp(8) && abs(event.rawY - downY) < dp(8)
                    choose(if (tap) "tap" else "fall")
                    if (tap) GestureActions.dispatch(this, if (event.eventTime - downTime > 600) "Long press action" else "Single-tap action")
                }
            }
            true
        }
        val tick = object : Runnable {
            override fun run() {
                if (version != generation) return
                val now = android.os.SystemClock.uptimeMillis()
                val mode = prefs.getString("pet_motion", "Walk")
                if (!dragging && now > actionUntil) {
                    when (action) { "tap", "drag", "snap" -> choose(if (params.y < maxY) "fall" else "walk", 1000) }
                    if (mode == "Idle" || mode == "Follow touch") action = "sit"
                    else if (now - lastAmbient > 8000 && edge == "bottom" && action !in listOf("climb", "fall", "top")) { action = ambient[ambientIndex++ % ambient.size]; lastAmbient = now }
                    if (mode == "Bounce") action = "fly"
                }
                if (!dragging && mode != "Idle" && mode != "Follow touch") {
                    when (action) {
                        "walk", "run" -> {
                            val speed = dp(if (action == "run") 5 else 2).coerceAtLeast(1)
                            if (edge == "top") action = "top"
                            else {
                                params.y = maxY
                                params.x = (params.x + direction * speed).coerceIn(0, maxX)
                                if (params.x == 0 || params.x == maxX) { edge = if (params.x == 0) "left" else "right"; verticalDirection = -1; action = "climb" }
                            }
                        }
                        "climb" -> {
                            params.y = (params.y + verticalDirection * dp(3)).coerceIn(0, maxY)
                            if (params.y == 0) { edge = "top"; direction = if (params.x == 0) 1 else -1; action = "top" }
                            else if (params.y == maxY) { edge = "bottom"; direction = if (params.x == 0) 1 else -1; action = "walk"; lastAmbient = now }
                        }
                        "top" -> {
                            params.y = 0
                            params.x = (params.x + direction * dp(2)).coerceIn(0, maxX)
                            if (params.x == 0 || params.x == maxX) { edge = if (params.x == 0) "left" else "right"; verticalDirection = 1; action = "climb" }
                        }
                        "fall", "drop" -> { params.y = (params.y + dp(6)).coerceAtMost(maxY); if (params.y == maxY) { edge = "bottom"; choose("walk", 1000) } }
                        "fly" -> {
                            params.x = (params.x + direction * dp(3)).coerceIn(0, maxX)
                            params.y = (params.y + verticalDirection * dp(3)).coerceIn(0, maxY)
                            if (params.x == 0 || params.x == maxX) direction *= -1
                            if (params.y == 0 || params.y == maxY) verticalDirection *= -1
                        }
                    }
                    image.scaleX = if (direction < 0) -1f else 1f
                    move(image, params)
                }
                val frames = PetMedia.frames(pet, action, batteryPercent)
                if (frames != currentFrames) { currentFrames = frames; frameIndex = 0 }
                val url = frames.getOrNull(frameIndex % frames.size.coerceAtLeast(1)).orEmpty()
                if (!loading && url.isNotBlank() && (frames.size > 1 || url != currentUrl)) {
                    loading = true; currentUrl = url
                    MediaAssets.load(image, url) { loading = false; frameIndex++ }
                }
                handler.postDelayed(this, 100)
            }
        }
        handler.post(tick)
    }
}
