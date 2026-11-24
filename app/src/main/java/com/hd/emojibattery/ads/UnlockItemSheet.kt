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
class UnlockItemSheet(private val activity: androidx.appcompat.app.AppCompatActivity, onWatch: () -> Unit, onPremium: () -> Unit) : Dialog(activity) {
    private val ads = AdsManager.getInstance()
    private var ownedAd: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd? = null
    private val placement = "emoji_native_pop_up_unlock"
    private var loadCallback: ((com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd?) -> Unit)? = null
    private fun canLoadNative() = !com.hd.emojibattery.BuildConfig.UI_TEST_MODE &&
        ads.canRequestAds && ads.isGlobalAdsEnabled() && ads.isConfiguredPlacementEnabled(placement) &&
        !tpt.dev.monetization.common.premium.PremiumManager.INSTANCE.isSubscribed()
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
            androidx.core.view.WindowCompat.getInsetsController(this, decorView).apply {
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            }
        }
        val adView = findViewById<ViewNativeAd>(R.id.unlock_native)
        if (!canLoadNative()) {
            adView.visibility = View.GONE
            return
        }
        // The native layout supplies its existing skeleton until a creative is ready.
        adView.setLayoutNativeAd(tpt.dev.monetization.R.layout.battery_native_list)
        adView.visibility = View.VISIBLE
        fun populateCached(): Boolean {
            if (!isShowing || !canLoadNative()) return false
            val ad = ads.preloadNativeManagement.getNativeAd(placement, true) ?: return false
            ownedAd = ad
            adView.populate(ad)
            return true
        }
        if (!populateCached()) {
            val result: (com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd?) -> Unit = {
                if (isShowing && !populateCached()) adView.visibility = View.GONE
            }
            loadCallback = result
            ads.preloadNativeManagement.load(activity, placement, callback = result)
        }
    }
    override fun onStop() {
        loadCallback?.let { ads.preloadNativeManagement.cancelLoadCallback(placement, it) }
        loadCallback = null
        ownedAd?.destroy()
        ownedAd = null
        super.onStop()
    }
}
