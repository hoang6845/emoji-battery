package com.hd.emojibattery

import com.hd.emojibattery.runtime.OverlayService

class AppliedStatusService : OverlayService() {
    override val usesAccessibilityHost = true
    override fun onStopRequested() {
        prefs.edit().putBoolean("status_overlay_enabled", false)
            .putBoolean("gesture_enabled", false).apply()
        GestureAccessibilityService.active?.refreshStatus()
    }
    override fun render() {
        if (!prefs.getBoolean("status_overlay_enabled", false)) { stopSelf(); return }
        val accessibility = GestureAccessibilityService.active
        if (accessibility == null) { stopSelf(); return }
        accessibility.refreshStatus()
    }
}
