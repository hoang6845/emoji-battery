package com.hd.emojibattery.ads

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.hd.emojibattery.MainActivity
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.nativeAd.view.ViewNativeAd
import tpt.dev.monetization.common.premium.PremiumManager
import kotlin.math.roundToInt

/** A visible visit owns one creative; leaving the viewport releases that ownership. */
class CatalogNativeSlot(context: android.content.Context) : FrameLayout(context), DefaultLifecycleObserver {
    private val manager get() = AdsManager.getInstance()
    private val key = "emoji_native_list_data_custom"
    private var ad: NativeAd? = null
    private var callback: ((NativeAd?) -> Unit)? = null
    private var owner: LifecycleOwner? = null
    private var visibleVisit = false
    private var generation = 0
    private val rect = Rect()
    private val scroll = ViewTreeObserver.OnScrollChangedListener { checkVisibility() }
    private val layout = ViewTreeObserver.OnGlobalLayoutListener { checkVisibility() }
    private fun allowed() = !com.hd.emojibattery.BuildConfig.UI_TEST_MODE &&
        manager.canRequestAds && manager.isGlobalAdsEnabled() && manager.isConfiguredPlacementEnabled(key) &&
        !PremiumManager.INSTANCE.isSubscribed()

    init {
        layoutParams = androidx.recyclerview.widget.RecyclerView.LayoutParams(-1, heightPx())
        skeleton()
    }

    private fun heightPx() = (212 * resources.displayMetrics.density).roundToInt()
    private fun skeleton() {
        removeAllViews()
        addView(ViewNativeAd(context).apply {
            setLayoutNativeAd(tpt.dev.monetization.R.layout.battery_native_list)
        }, LayoutParams(-1, -1))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        owner = findViewTreeLifecycleOwner()
        owner?.lifecycle?.addObserver(this)
        viewTreeObserver.addOnScrollChangedListener(scroll)
        viewTreeObserver.addOnGlobalLayoutListener(layout)
        post { checkVisibility() }
    }

    private fun checkVisibility() {
        val reservedHeight = if (allowed()) heightPx() else 0
        if (layoutParams.height != reservedHeight) layoutParams = layoutParams.apply { height = reservedHeight }

        val active = isShown && getGlobalVisibleRect(rect) && rect.height() > 0 &&
            owner?.lifecycle?.currentState?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) == true
        if (!active || !allowed()) {
            if (visibleVisit) release()
            return
        }
        if (visibleVisit) return
        visibleVisit = true
        skeleton()
        val host = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<MainActivity>().firstOrNull() ?: return
        val token = ++generation
        val result: (NativeAd?) -> Unit = result@{
            if (token != generation || !visibleVisit || !allowed()) return@result
            val creative = manager.preloadNativeManagement.getNativeAd(key, removeAfterGet = true) ?: return@result
            ad = creative
            (getChildAt(0) as ViewNativeAd).populate(creative)
        }
        callback = result
        // No previous creative/cache is reused for a new visible visit.
        manager.preloadNativeManagement.remove(key)
        owner?.let { manager.preloadNativeManagement.loadForOwner(host, key, it, result) }
    }

    private fun release() {
        visibleVisit = false
        generation++
        callback?.let { manager.preloadNativeManagement.cancelLoadCallback(key, it) }
        callback = null
        ad?.destroy()
        ad = null
        skeleton()
    }

    override fun onResume(owner: LifecycleOwner) { checkVisibility() }
    override fun onPause(owner: LifecycleOwner) { release() }
    override fun onDestroy(owner: LifecycleOwner) { release() }
    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnScrollChangedListener(scroll)
        viewTreeObserver.removeOnGlobalLayoutListener(layout)
        owner?.lifecycle?.removeObserver(this)
        owner = null
        release()
        super.onDetachedFromWindow()
    }
}
