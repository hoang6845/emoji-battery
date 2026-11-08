package com.hd.emojibattery.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import com.hd.emojibattery.GestureAccessibilityService
import com.hd.emojibattery.MainActivity

object GestureActions {
    fun attach(view: View) {
        val detector = GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true
            override fun onSingleTapUp(e: MotionEvent): Boolean { dispatch(view.context, "Single-tap action"); return true }
            override fun onLongPress(e: MotionEvent) { dispatch(view.context, "Long press action") }
            override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
                if (a == null) return false
                val dx = b.x - a.x; val dy = b.y - a.y
                if (kotlin.math.abs(dx) < 32 && kotlin.math.abs(dy) < 32) return false
                val name = if (kotlin.math.abs(dy) > kotlin.math.abs(dx)) "Swipe up to down" else if (dx > 0) "Swipe left to right" else "Swipe right to left"
                if (name != "Swipe up to down" || dy > 0) dispatch(view.context, name)
                return true
            }
        })
        view.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }
    fun dispatch(context: Context, gesture: String) {
        val prefs = AppliedContent.prefs(context)
        if (!prefs.getBoolean("gesture_enabled", false)) return
        val action = prefs.getString("gesture_action_$gesture", "No action")
        if (action == "No action") return
        if (prefs.getBoolean("gesture_vibration", false)) context.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE))
        if (action == "Open App") {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
        }
        val code = when (action) {
            "Back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "Home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "Recent" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "Notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "Quick Settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "Power Menu" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            "Lock screen" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "Screenshot" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            else -> return
        }
        if (com.hd.emojibattery.GestureAccessibilityService.active?.performGlobalAction(code) != true)
            Toast.makeText(context, "Enable Emoji Battery gestures in Accessibility settings, or check whether this action is supported.", Toast.LENGTH_LONG).show()
    }
}
