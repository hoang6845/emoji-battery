package com.hd.emojibattery.runtime

import android.content.Context
import android.content.SharedPreferences
import android.view.View
import java.util.WeakHashMap

/** Package-based preferences; labels are used only to migrate the old settings. */
object RestrictedApps {
    fun key(packageName: String) = "restricted_package_$packageName"
    fun bankDefault(packageName: String, label: String): Boolean {
        val text = "$packageName $label".lowercase(java.util.Locale.ROOT)
        return listOf("bank", "ng?n h?ng", "ngan hang", "vietcom", "vietin", "bidv", "agribank",
            "techcom", "mbbank", "tpbank", "vpbank", "sacombank", "hdbank", "vib.myvib",
            "acb", "ocb", "seabank", "hsbc", "standardchartered", "wellsfargo", "chase",
            "revolut", "timo", "tnex").any { text.contains(it) }
    }
    fun selected(prefs: SharedPreferences, packageName: String, label: String): Boolean =
        if (prefs.contains(key(packageName))) prefs.getBoolean(key(packageName), false)
        else bankDefault(packageName, label) || prefs.getBoolean("restricted_$label", false)

    private val views = WeakHashMap<View, Unit>()
    private var foregroundPackage: String? = null
    private var connected = false
    // Unknown foreground is hidden until Accessibility reports an application window.
    private var blocked = true
    fun watch(view: View) {
        views[view] = Unit
        view.visibility = if (blocked) View.GONE else View.VISIBLE
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                views.remove(v)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }
    fun connection(context: Context, available: Boolean) {
        connected = available
        foregroundPackage = null
        refresh(context)
    }
    fun foreground(context: Context, packageName: String) {
        // System dialogs / the notification shade must not reveal overlays above a blocked app.
        if (packageName == "com.android.systemui" || packageName == "android") return
        foregroundPackage = packageName
        refresh(context)
    }
    fun refresh(context: Context) {
        val pkg = foregroundPackage
        blocked = !connected || pkg == null || if (pkg == context.packageName) false else {
            val label = runCatching { context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
            selected(AppliedContent.prefs(context), pkg, label)
        }
        views.keys.toList().forEach { it.visibility = if (blocked) View.GONE else View.VISIBLE }
    }
}
