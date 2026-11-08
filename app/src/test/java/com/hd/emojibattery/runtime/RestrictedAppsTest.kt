package com.hd.emojibattery.runtime

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class RestrictedAppsTest {
    private fun prefs(values: Map<String, Boolean>): SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
    ) { _, method, args ->
        when (method.name) {
            "contains" -> values.containsKey(args!![0])
            "getBoolean" -> values[args!![0]] ?: args[1]
            else -> error("Unexpected call: ${method.name}")
        }
    } as SharedPreferences

    @Test fun bankingDefaultsApplyBeforeOpeningSettings() {
        assertTrue(RestrictedApps.selected(prefs(emptyMap()), "com.vcb.mobile", "Vietcombank"))
        assertTrue(RestrictedApps.selected(prefs(emptyMap()), "com.mbmobile", "MB Bank"))
        assertFalse(RestrictedApps.selected(prefs(emptyMap()), "com.example.camera", "Camera"))
    }
    @Test fun explicitPackageChoiceOverridesBankingDefault() {
        assertFalse(RestrictedApps.selected(prefs(mapOf(RestrictedApps.key("com.vcb.mobile") to false)),
            "com.vcb.mobile", "Vietcombank"))
    }
    @Test fun legacySelectionMigratesAndPackageChoiceWins() {
        assertTrue(RestrictedApps.selected(prefs(mapOf("restricted_Camera" to true)), "com.example.camera", "Camera"))
        assertFalse(RestrictedApps.selected(prefs(mapOf("restricted_Camera" to true,
            RestrictedApps.key("com.example.camera") to false)), "com.example.camera", "Camera"))
    }
    @Test fun duplicateLabelsDoNotShareNewChoices() {
        val stored = prefs(mapOf(RestrictedApps.key("com.example.one") to true))
        assertTrue(RestrictedApps.selected(stored, "com.example.one", "Same label"))
        assertFalse(RestrictedApps.selected(stored, "com.example.two", "Same label"))
    }
}
