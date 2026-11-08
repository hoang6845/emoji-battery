package com.hd.emojibattery.runtime

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.hd.emojibattery.*
import org.json.JSONArray

object FeatureApply {
    fun permission(activity: Activity): Boolean {
        if (BuildConfig.UI_TEST_MODE) return false
        if (Settings.canDrawOverlays(activity)) return true
        activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
        Toast.makeText(activity, "Allow display over other apps, then tap Apply again", Toast.LENGTH_LONG).show()
        return false
    }
    fun start(activity: Activity, type: Class<out android.app.Service>): Boolean {
        if (!permission(activity)) return false
        return runCatching { ContextCompat.startForegroundService(activity, Intent(activity, type)); true }
            .getOrElse { Toast.makeText(activity, "Could not start the overlay. Check permissions and try again.", Toast.LENGTH_LONG).show(); false }
    }
    fun status(activity: Activity): Boolean {
        if (!permission(activity)) return false
        if (!statusAccessibility(activity)) return false
        activity.stopService(Intent(activity, StatusBarOverlayService::class.java))
        AppliedContent.prefs(activity).edit().putBoolean("status_overlay_enabled", true).apply()
        return start(activity, AppliedStatusService::class.java)
    }
    private fun statusAccessibility(activity: Activity): Boolean {
        if (GestureAccessibilityService.active != null) return true
        AlertDialog.Builder(activity).setTitle("Enable custom status bar")
            .setMessage("Enable Emoji Battery in Accessibility to display your custom status bar over the system status bar. The service does not read screen content. Then return and tap Apply again.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open settings") { _, _ -> activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .show()
        return false
    }
    fun decorations(activity: Activity) = start(activity, DecorationService::class.java)
    fun stickers(activity: Activity, item: org.json.JSONObject): Boolean {
        if (!permission(activity)) return false
        val prefs = AppliedContent.prefs(activity)
        val previous = prefs.getString("applied_stickers", "[]")
        val wasEnabled = prefs.getBoolean("stickers_enabled", false)
        val wasApplied = prefs.getBoolean("stickers_applied", false)
        val id = item.optString("id", "custom")
        item.put("id", id)
        if (item.optString("image_url").isBlank()) item.put("image_url", AppliedContent.url(item))
        val saved = runCatching { JSONArray(previous) }.getOrDefault(JSONArray())
        val updated = JSONArray()
        for (i in 0 until saved.length()) saved.optJSONObject(i)?.let {
            if (it.optString("id", "custom") != id) updated.put(it)
        }
        updated.put(item)
        prefs.edit().putString("applied_stickers", updated.toString())
            .putBoolean("stickers_enabled", true).putBoolean("stickers_applied", true).apply()
        if (!decorations(activity)) {
            prefs.edit().putString("applied_stickers", previous).putBoolean("stickers_enabled", wasEnabled)
                .putBoolean("stickers_applied", wasApplied).apply()
            return false
        }
        prefs.edit().putInt("sticker_size_$id", item.optInt("overlay_size", 50))
            .putInt("sticker_rotation_$id", item.optInt("overlay_rotation", 0))
            .putFloat("sticker_offset_x_$id", item.optDouble("overlay_x", 0.0).toFloat())
            .putFloat("sticker_offset_y_$id", item.optDouble("overlay_y", 0.0).toFloat()).apply()
        return true
    }
    fun gestures(activity: Activity) {
        val prefs = AppliedContent.prefs(activity)
        if (!prefs.getBoolean("gesture_enabled", false)) {
            GestureAccessibilityService.active?.refreshStatus()
            if (prefs.getBoolean("status_overlay_enabled", false)) start(activity, AppliedStatusService::class.java)
            else activity.stopService(Intent(activity, AppliedStatusService::class.java))
            return
        }
        val needsAccessibility = prefs.all.any { it.key.startsWith("gesture_action_") && it.value !in listOf("No action", "Open App") }
        if (needsAccessibility && GestureAccessibilityService.active == null) {
            AlertDialog.Builder(activity).setTitle("Enable gesture actions")
                .setMessage("Emoji Battery uses Accessibility only to perform the system action you select when you touch its overlay. It does not read screen content. Enable the service in Settings, then tap Save again.")
                .setNegativeButton("Cancel", null).setPositiveButton("Open settings") { _, _ -> activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
            return
        }
        if (statusAccessibility(activity)) start(activity, AppliedStatusService::class.java)
    }
    fun charging(activity: MainActivity, onApplied: () -> Unit) {
        if (!permission(activity)) return
        if (AppliedContent.selected(activity, "charging") == null && AppliedContent.selected(activity, "wallpaper") == null) {
            Toast.makeText(activity, "Choose an animation or wallpaper first", Toast.LENGTH_SHORT).show(); return
        }
        val urls = listOf("charging", "wallpaper").map { AppliedContent.url(AppliedContent.selected(activity, it)) }.filter { it.isNotBlank() }
        if (urls.isEmpty()) { Toast.makeText(activity, "This item has no media URL", Toast.LENGTH_LONG).show(); return }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = AlertDialog.Builder(activity).setTitle("Preparing charging media").setMessage("Downloading...")
            .setNegativeButton("Cancel") { _, _ -> cancelled.set(true) }.create()
        dialog.setOnCancelListener { cancelled.set(true) }
        val lifecycleObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { cancelled.set(true); dialog.dismiss() }
        }
        activity.lifecycle.addObserver(lifecycleObserver)
        dialog.show()
        val app = activity.applicationContext
        MediaAssets.io.execute {
            val ready = urls.all { !cancelled.get() && runCatching { MediaAssets.file(app, it) }.getOrNull() != null }
            activity.runOnUiThread {
                activity.lifecycle.removeObserver(lifecycleObserver)
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                dialog.dismiss()
                if (cancelled.get()) return@runOnUiThread
                if (ready) {
                    AppliedContent.prefs(activity).edit().putBoolean("charge_applied", true).apply()
                    activity.enableChargingMonitor()
                    onApplied()
                } else Toast.makeText(activity, "Download failed. Check your connection and try again.", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun wallpaper(activity: Activity) {
        val url = AppliedContent.url(AppliedContent.selected(activity, "wallpaper"))
        if (url.isBlank()) { Toast.makeText(activity, "Choose a wallpaper first", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(activity).setTitle("Apply wallpaper").setItems(arrayOf("Home screen", "Lock screen", "Both")) { _, index ->
            val app = activity.applicationContext
            MediaAssets.io.execute {
                val success = runCatching {
                    val file = MediaAssets.file(app, url) ?: error("Download failed")
                    val manager = android.app.WallpaperManager.getInstance(app)
                    val which = when (index) { 0 -> android.app.WallpaperManager.FLAG_SYSTEM; 1 -> android.app.WallpaperManager.FLAG_LOCK; else -> android.app.WallpaperManager.FLAG_SYSTEM or android.app.WallpaperManager.FLAG_LOCK }
                    file.inputStream().use { manager.setStream(it, null, true, which) }
                }.isSuccess
                activity.runOnUiThread { if (!activity.isFinishing) Toast.makeText(activity, if (success) "Wallpaper applied" else "Could not apply wallpaper. Check the image and connection.", Toast.LENGTH_LONG).show() }
            }
        }.show()
    }
}
