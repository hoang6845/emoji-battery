package com.hd.emojibattery.ads

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.hd.emojibattery.BuildConfig
import com.hd.emojibattery.MainActivity
import com.hd.emojibattery.R
import com.hd.emojibattery.ui.navigation.ScreenIds as S
import kotlinx.coroutines.launch
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.PrepareLoadingAdsDialog
import tpt.dev.monetization.ads.nativeAd.view.ViewNativeAd
import tpt.dev.monetization.common.premium.PremiumManager

/** One activity owns navigation opportunities. Managers continue to own ad caches. */
class BatteryAds(private val activity: MainActivity) {
    private val ads get() = AdsManager.getInstance()
    private val inter get() = ads.preloadInterstitialManagement
    private val handler = Handler(Looper.getMainLooper())
    private val prepared = mutableSetOf<String>()
    private val oneShotConsumed = mutableSetOf<String>()
    private var initialized = false
    private var bindingGeneration = 0
    private var unbind: (() -> Unit)? = null
    private var cancelOpportunity: (() -> Unit)? = null
    private var activeDialog: android.app.Dialog? = null
    private var suppressInterUntil = 0L
    var busy = false
        private set

    private fun enabled() = !BuildConfig.UI_TEST_MODE && ads.canRequestAds &&
        ads.isGlobalAdsEnabled() && !PremiumManager.INSTANCE.isSubscribed()

    fun initialize(onReady: () -> Unit) {
        if (BuildConfig.UI_TEST_MODE || initialized) { onReady(); return }
        initialized = true
        // Use only the bundled demo units until production IDs are supplied.
        ads.gatherConsent(activity, object : AdsManager.OnGatherConsentListener {
            override fun onCompletion(error: String?) {
                val current = activity.supportFragmentManager.findFragmentById(R.id.main_container)
                val screen = current?.arguments?.getString("screen_id")
                if (current != null && current.view != null && screen != null) onScreen(current, screen)
                onReady()
            }
        })
    }

    fun prepare(screen: String) {
        if (!enabled()) return
        BatteryAdPlacements.native(screen)?.let { key ->
            if (key !in oneShotConsumed && prepared.add(key)) {
                ads.preloadNativeManagement.load(activity, key)
            }
        }
        BatteryAdPlacements.banner(screen)?.let { key ->
            if (prepared.add(key)) ads.preloadBannerManagement.loadAdaptiveBanner(activity, key)
        }
    }

    fun preloadInter(key: String = BatteryAdPlacements.ADMOB_INTERSTITIAL_ID) {
        if (enabled() && prepared.add(key)) inter.load(activity, key)
    }

    fun transition(source: Fragment?, target: String, next: () -> Unit) {
        if (busy) return // Ignore double taps while the first route owns the opportunity.
        prepare(target) // Native request starts BEFORE inter / destination navigation.
        if (!BatteryAdPlacements.transitionNeedsInter(target) ||
            SystemClock.elapsedRealtime() < suppressInterUntil || source == null) {
            next(); return
        }
        showInterstitial(source, BatteryAdPlacements.ADMOB_INTERSTITIAL_ID, next = next)
    }

    /** Total load + prepare budget is 3s. No retries or refill after failure. */
    fun showInterstitial(source: Fragment, key: String, waitForHigh: Boolean = false, next: () -> Unit) {
        if (!enabled()) { next(); return }
        if (busy) return
        val owner = source.viewLifecycleOwnerLiveData.value ?: run { next(); return }
        val deadline = SystemClock.elapsedRealtime() + 3000L
        busy = true
        var done = false
        var presented = false
        val loading = PrepareLoadingAdsDialog(activity)
        activeDialog = loading
        loading.show()
        lateinit var timeout: Runnable
        lateinit var observer: DefaultLifecycleObserver
        fun alive() = !done && !activity.isFinishing && !activity.isDestroyed &&
            source.isAdded && source.viewLifecycleOwnerLiveData.value === owner &&
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        fun finish(continueRoute: Boolean) {
            if (done) return
            done = true
            handler.removeCallbacks(timeout)
            owner.lifecycle.removeObserver(observer)
            loading.dismiss()
            activeDialog = null
            cancelOpportunity = null
            busy = false
            // Invalidate callbacks from timed-out/abandoned loads, including a late high.
            if (!presented) inter.remove(key) else prepared.remove(key)
            if (continueRoute) owner.lifecycleScope.launch {
                owner.lifecycle.withResumed {
                    if (source.isAdded && source.viewLifecycleOwnerLiveData.value === owner) next()
                }
            }
        }
        timeout = Runnable { if (!presented) finish(true) }
        observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { if (!presented) finish(true) }
            override fun onDestroy(owner: LifecycleOwner) { finish(false) }
        }
        cancelOpportunity = { finish(false) }
        owner.lifecycle.addObserver(observer)
        handler.postDelayed(timeout, 3000L)
        fun showReady() {
            // Leave room for the manager's 500ms preparation; never extend the loading budget.
            if (!alive() || deadline - SystemClock.elapsedRealtime() <= 550L) { finish(true); return }
            inter.showGuarded(activity, key,
                shouldShow = { alive() && enabled() && SystemClock.elapsedRealtime() < deadline },
                onAdClosed = { finish(true) },
                onAdPresenting = { presented = true; handler.removeCallbacks(timeout); loading.dismiss() },
                onAdShowed = {
                    presented = true
                    handler.removeCallbacks(timeout)
                    loading.dismiss()
                })
        }
        fun fallback() {
            if (!alive() || SystemClock.elapsedRealtime() >= deadline) { finish(true); return }
            // Cancel high still in flight. Demand mode starts at medium (or all for 2 floor).
            inter.remove(key)
            inter.loadFallback(activity, key) {
                if (done) return@loadFallback
                if (inter.isLoaded(key)) showReady() else finish(true)
            }
        }
        if (inter.isLoaded(key)) showReady()
        else if (waitForHigh) {
            prepared.add(key)
            inter.load(activity, key) {
                if (!done) { if (inter.isLoaded(key)) showReady() else fallback() }
            }
        } else fallback()
    }

    fun onScreen(fragment: Fragment, screen: String) {
        unbind?.invoke()
        unbind = null
        val generation = ++bindingGeneration
        val activityHost = activity.findViewById<FrameLayout>(R.id.battery_ad_host)
        activityHost.removeAllViews()
        activityHost.visibility = View.GONE
        val host = if (screen == S.INTRO_2 || screen == S.INTRO_3) {
            fragment.requireView().findViewById<FrameLayout>(R.id.intro_ad_space)
        } else activityHost
        host.removeAllViews()
        host.visibility = View.GONE
        ads.setAppOpenAdEnabled(fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>)
        if (!enabled()) return
        prepare(screen) // Also covers restored fragments and deep links.
        BatteryAdPlacements.nextNativeScreens(screen).forEach(::prepare)
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>) preloadInter()
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>) {
            ads.markSplashCompleted()
            ads.preloadResumeAd(activity)
        }
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>) {
            ads.preloadRewardedManagement.load(activity, BatteryAdPlacements.REWARD_ID)
            if (prepared.add("emoji_native_pop_up_unlock")) ads.preloadNativeManagement.load(activity, "emoji_native_pop_up_unlock")
        }
        val owner = fragment.viewLifecycleOwnerLiveData.value ?: return
        val banner = BatteryAdPlacements.banner(screen)
        val nativeKey = if (banner == null) BatteryAdPlacements.native(screen) else null
        var ownedAd: NativeAd? = null
        var callback: ((NativeAd?) -> Unit)? = null
        var cleaned = false
        val cleanup = cleanup@{
            if (cleaned) return@cleanup
            cleaned = true
            callback?.let { cb -> nativeKey?.let { ads.preloadNativeManagement.cancelLoadCallback(it, cb) } }
            nativeKey?.let { ads.preloadNativeManagement.markHidden(it) }
            ownedAd?.destroy()
            ownedAd = null
            if (banner != null) {
                ads.preloadBannerManagement.destroy(banner)
                prepared.remove(banner)
            }
            if (bindingGeneration == generation) {
                host.removeAllViews()
                host.visibility = View.GONE
            }
        }
        unbind = cleanup
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                cleanup()
            }
        })
        if (banner != null) {
            ads.preloadBannerManagement.loadAdaptiveBanner(activity, banner) {
                if (bindingGeneration == generation && enabled() && owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    host.visibility = if (ads.preloadBannerManagement.showBanner(banner, host)) View.VISIBLE else View.GONE
                }
            }
        } else if (nativeKey != null && nativeKey !in oneShotConsumed) {
            callback = {
                if (bindingGeneration == generation && enabled() && ownedAd == null &&
                    owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    val ad = ads.preloadNativeManagement.getNativeAd(nativeKey, removeAfterGet = true)
                    if (ad != null) {
                        ownedAd = ad
                        ads.preloadNativeManagement.markShowing(nativeKey)
                        val view = ViewNativeAd(activity)
                        val layout = ads.resolveNativeLayoutRes(activity, nativeKey)
                        if (layout != 0) view.setLayoutNativeAd(layout)
                        view.populate(ad)
                        host.addView(view)
                        host.visibility = View.VISIBLE
                        if (ads.shouldReloadNativePlacement(nativeKey, false)) {
                            ads.preloadNativeManagement.loadForOwner(activity, nativeKey, owner)
                        } else oneShotConsumed.add(nativeKey)
                    }
                }
            }
            ads.preloadNativeManagement.loadForOwner(activity, nativeKey, owner, callback)
        }
    }

    fun unlock(fragment: Fragment, itemId: String, onUnlocked: () -> Unit) {
        if (SessionUnlocks.contains(itemId) || PremiumManager.INSTANCE.isSubscribed() || BuildConfig.UI_TEST_MODE) {
            onUnlocked(); return
        }
        if (busy) return
        val sheet = UnlockItemSheet(activity, onWatch = {
            activeDialog = null
            showReward(fragment, itemId, onUnlocked)
        }, onPremium = { activity.openScreen(S.PREMIUM, true) })
        activeDialog = sheet
        sheet.setOnDismissListener {
            if (activeDialog === sheet) activeDialog = null
            if (enabled()) ads.preloadNativeManagement.load(activity, "emoji_native_pop_up_unlock")
        }
        sheet.show()
    }

    private fun showReward(fragment: Fragment, itemId: String, onUnlocked: () -> Unit) {
        if (!enabled()) { unavailable(); return }
        val owner = fragment.viewLifecycleOwnerLiveData.value ?: return
        busy = true
        var finished = false
        var earned = false
        var presented = false
        val deadline = SystemClock.elapsedRealtime() + 3000L
        val loading = PrepareLoadingAdsDialog(activity)
        activeDialog = loading
        loading.show()
        lateinit var timeout: Runnable
        lateinit var rewardObserver: DefaultLifecycleObserver
        fun finish() {
            if (finished) return
            finished = true
            handler.removeCallbacks(timeout)
            owner.lifecycle.removeObserver(rewardObserver)
            loading.dismiss()
            activeDialog = null
            busy = false
            cancelOpportunity = null
            if (earned) {
                SessionUnlocks.add(itemId)
                suppressInterUntil = SystemClock.elapsedRealtime() + 2000L
                owner.lifecycleScope.launch { owner.lifecycle.withResumed { onUnlocked() } }
            } else if (!presented) unavailable()
        }
        timeout = Runnable { if (!presented) finish() }
        rewardObserver = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) { if (!presented) finish() }
        }
        owner.lifecycle.addObserver(rewardObserver)
        cancelOpportunity = {
            finished = true
            owner.lifecycle.removeObserver(rewardObserver)
            loading.dismiss()
            busy = false
            handler.removeCallbacks(timeout)
        }
        handler.postDelayed(timeout, 3000L)
        ads.preloadRewardedManagement.load(activity, BatteryAdPlacements.REWARD_ID) {
            if (finished) return@load
            val eligible = { !finished && enabled() && fragment.isAdded &&
                owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            if (!eligible() || deadline - SystemClock.elapsedRealtime() <= 550L) { finish(); return@load }
            ads.preloadRewardedManagement.show(activity, BatteryAdPlacements.REWARD_ID,
                reload = false, shouldShow = eligible,
                onAdPresenting = { presented = true; handler.removeCallbacks(timeout); loading.dismiss() },
                onAdShowed = { presented = true; handler.removeCallbacks(timeout); loading.dismiss() },
                onRewarded = { earned = true },
                onAdClosed = { finish() }, onLoadFailed = { finish() })
        }
    }

    private fun unavailable() = Toast.makeText(activity, R.string.battery_ad_unavailable, Toast.LENGTH_SHORT).show()
    fun canResumeAd() = !busy && activeDialog?.isShowing != true
    fun close() {
        cancelOpportunity?.invoke()
        unbind?.invoke()
        activeDialog?.dismiss()
        activeDialog = null
        handler.removeCallbacksAndMessages(null)
    }
}

/** Memory only: earned items reset with a new process/session. Never overwrites purchases. */
object SessionUnlocks {
    private val ids = mutableSetOf<String>()
    fun contains(id: String) = id in ids
    fun add(id: String) { ids.add(id) }
    fun isLocked(context: android.content.Context, id: String): Boolean {
        if (contains(id)) return false
        val data = com.hd.emojibattery.data.AppContentRepository.get(context).snapshot()
        for (key in data.keys()) {
            val list = data.optJSONArray(key) ?: continue
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                if (item.optString("id") == id) return !item.optBoolean("free", true)
            }
        }
        return false
    }
}
