package com.hd.emojibattery.runtime

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.hd.emojibattery.GestureAccessibilityService

/** Always identify this installed app, including applicationId suffixes, in permission links. */
object AppSettings {
    private fun accessibilityComponent(context: Context) =
        ComponentName(context, GestureAccessibilityService::class.java).flattenToString()

    // The details activity can require a system-only permission. Use the public entry point.
    fun accessibilitySettings(context: Context): Intent {
        val component = accessibilityComponent(context)
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(":settings:fragment_args_key", component)
            .putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", component)
            })
    }

    fun accessibilityUnavailable(context: Context) {
        android.widget.Toast.makeText(context,
            "Could not open Accessibility settings. Open Settings > Accessibility and enable Emoji Battery, then return and try again.",
            android.widget.Toast.LENGTH_LONG).show()
    }
    fun openAccessibility(activity: Activity) {
        if (activity is com.hd.emojibattery.MainActivity) {
            activity.requestEmojiEnable(enableBattery = false)
            return
        }
        try {
            activity.startActivity(accessibilitySettings(activity))
        } catch (_: android.content.ActivityNotFoundException) {
            accessibilityUnavailable(activity)
        } catch (_: SecurityException) {
            accessibilityUnavailable(activity)
        }
    }
}
