package com.hd.emojibattery

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/** No screen content access. Window package names control overlay restrictions; explicit gestures dispatch global actions. */
class GestureAccessibilityService : AccessibilityService() {
    companion object {
        var active: GestureAccessibilityService? = null; private set
        val connected = androidx.lifecycle.MutableLiveData(false)
        // A token, never an Activity/Fragment, keeps the preview ready if connection finishes after onResume.
        private var previewOwner: Any? = null
        private var preview: com.hd.emojibattery.runtime.TrollBatteryConfig? = null
        fun previewTroll(owner: Any, config: com.hd.emojibattery.runtime.TrollBatteryConfig) {
            previewOwner = owner
            preview = config
            active?.refreshTroll()
        }
        fun clearTrollPreview(owner: Any) {
            if (previewOwner !== owner) return
            previewOwner = null
            preview = null
            active?.refreshTroll()
        }
    }
    private val trollOverlay by lazy { com.hd.emojibattery.runtime.TrollBatteryOverlay(this) }
    private val statusOverlay by lazy { com.hd.emojibattery.runtime.StatusBarAccessibilityOverlay(this) }
    private val prefs by lazy { com.hd.emojibattery.runtime.AppliedContent.prefs(this) }
    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "battery_troll_enabled" || key == "applied_troll" || key == "fake_percentage" ||
            key?.startsWith("troll_") == true || key == "custom_size_${R.id.troll_size}") refreshTroll()
    }
    override fun onServiceConnected() {
        active = this
        com.hd.emojibattery.runtime.RestrictedApps.connection(this, true)
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refreshTroll()
        refreshStatus()
        connected.value = true
    }
    fun refreshStatus() { statusOverlay.refresh() }
    fun refreshTroll() {
        trollOverlay.show(preview ?: if (prefs.getBoolean("battery_troll_enabled", false))
            com.hd.emojibattery.runtime.TrollBatteryConfig.read(prefs) else null)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshTroll()
        refreshStatus()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            // Our overlay windows must not masquerade as the foreground Activity.
            if (pkg == packageName && event.className?.toString() != MainActivity::class.java.name) return
            com.hd.emojibattery.runtime.RestrictedApps.foreground(this, pkg)
        }
    }
    override fun onInterrupt() = Unit
    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        trollOverlay.close()
        statusOverlay.close()
        com.hd.emojibattery.runtime.RestrictedApps.connection(this, false)
        active = null
        connected.value = false
        super.onDestroy()
    }
}
