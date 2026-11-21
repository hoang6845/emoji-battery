package com.hd.emojibattery.ads

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.nativeAd.view.ViewNativeAd

/** Mirrors the reference's bottom sheet: video option, Premium option and native slot. */
class UnlockItemSheet(activity: MainActivity, onWatch: () -> Unit, onPremium: () -> Unit) : Dialog(activity) {
    private val ads = AdsManager.getInstance()
    private var ownedAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? = null
    init {
        setContentView(R.layout.dialog_battery_unlock)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        findViewById<View>(R.id.unlock_watch).setOnClickListener { dismiss(); onWatch() }
        findViewById<TextView>(R.id.unlock_premium).setOnClickListener { dismiss(); onPremium() }
        findViewById<View>(R.id.unlock_close).setOnClickListener { dismiss() }
    }
    override fun show() {
        super.show()
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
        // Prepared at screen entry. No new native request when the sheet opens.
        val ad = ads.preloadNativeManagement.getNativeAd("emoji_native_pop_up_unlock", true)
        if (ad != null) {
            ownedAd = ad
            findViewById<ViewNativeAd>(R.id.unlock_native).apply {
                setLayoutNativeAd(tpt.dev.monetization.R.layout.layout_small_native_ads)
                populate(ad)
                visibility = View.VISIBLE
            }
        }
    }
    override fun dismiss() {
        super.dismiss()
        ownedAd?.destroy()
        ownedAd = null
    }
}
