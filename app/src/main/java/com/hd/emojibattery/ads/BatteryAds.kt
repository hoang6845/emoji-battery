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
class BatteryAds(private val activity: androidx.appcompat.app.AppCompatActivity) {
    private val ads get() = AdsManager.getInstance()
    private val inter get() = ads.preloadInterstitialManagement
    private val handler = Handler(Looper.getMainLooper())
    private val prepared = mutableSetOf<String>()
    private val oneShotConsumed = mutableSetOf<String>()
    private val visitedIntroAds = mutableSetOf<String>()
    private var initialized = false
    private var initializing = false
    private val readyCallbacks = mutableListOf<() -> Unit>()
    private var bindingGeneration = 0
    private var unbind: (() -> Unit)? = null
    private var cancelOpportunity: (() -> Unit)? = null
    private var activeDialog: android.app.Dialog? = null
    private var suppressInterUntil = 0L
    private var lastCollapseExpansion = Long.MIN_VALUE
    private var languageReloadSelected = false
    private val languageAdComplete = kotlinx.coroutines.flow.MutableStateFlow(false)
    val languageAdCompletion: kotlinx.coroutines.flow.StateFlow<Boolean> = languageAdComplete
    private val nativeDialogs = mutableSetOf<android.app.Dialog>()
    var busy = false
        private set

    private fun enabled() = initialized && !BuildConfig.UI_TEST_MODE && ads.canRequestAds &&
        ads.isGlobalAdsEnabled() && !PremiumManager.INSTANCE.isSubscribed()

    fun initialize(onReady: () -> Unit) {
        if (BuildConfig.UI_TEST_MODE || initialized) { onReady(); return }
        readyCallbacks.add(onReady)
        if (initializing) return
        initializing = true
        ads.gatherConsent(activity, object : AdsManager.OnGatherConsentListener {
            override fun onCompletion(error: String?) {
                BatteryAdsConfig.fetch(activity) { config ->
                    ads.updateRemoteAdsConfig(config)
                    ads.setGlobalAdsEnabled(!BuildConfig.UI_TEST_MODE)
                    initialized = true
                    initializing = false
                    val callbacks = readyCallbacks.toList()
                    readyCallbacks.clear()
                    if (activity.isFinishing || activity.isDestroyed) return@fetch
                    val current = activity.supportFragmentManager.findFragmentById(R.id.main_container)
                    val screen = current?.arguments?.getString("screen_id")
                    if (current != null && current.view != null && screen != null) onScreen(current, screen)
                    callbacks.forEach { it() }
                }
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

    /** Splash waits for its interstitial; Language natives continue loading independently. */
    fun awaitSplash(source: Fragment, returning: Boolean, next: () -> Unit) {
        if (!enabled() || !ads.isInterSplashEnabled()) { next(); return }
        val owner = source.viewLifecycleOwner
        val keys = if (returning) emptySet() else setOf("emoji_native_language", "emoji_native_language_reload")
        val gate = tpt.dev.monetization.ads.preload.SplashAdGate(
            SystemClock.elapsedRealtime(), BatteryAdsConfig.splashLoadingTimeMs)
        val key = BatteryAdPlacements.ADMOB_SPLASH_ID
        var finished = false
        var presented = false
        var showRequested = false
        lateinit var timeout: Runnable
        fun current() = source.isAdded && source.viewLifecycleOwnerLiveData.value === owner
        fun navigate() {
            if (finished) return
            finished = true
            gate.close()
            handler.removeCallbacks(timeout)
            owner.lifecycleScope.launch {
                owner.lifecycle.withResumed { if (current()) next() }
            }
        }
        fun ready() {
            owner.lifecycleScope.launch {
                owner.lifecycle.withResumed {
                    if (finished || !current()) return@withResumed
                    if (!gate.canShow(SystemClock.elapsedRealtime()) || !enabled()) { navigate(); return@withResumed }
                    if (showRequested) return@withResumed
                    showRequested = true
                    inter.setCustomShowInterval(key, 0L)
                    inter.showGuarded(activity, key,
                        shouldShow = { !finished && current() && enabled() &&
                            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                            gate.canShow(SystemClock.elapsedRealtime()) },
                        onAdPresenting = { presented = true; handler.removeCallbacks(timeout) },
                        onAdClosed = { navigate() })
                }
            }
        }
        timeout = Runnable {
            if (!presented && !finished) {
                gate.timeout(SystemClock.elapsedRealtime())
                android.util.Log.d("BatterySplash", "Interstitial deadline reached; continue without late show")
                navigate()
            }
        }
        handler.postDelayed(timeout, BatteryAdsConfig.splashLoadingTimeMs)
        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                finished = true
                gate.close()
                handler.removeCallbacks(timeout)
            }
        })
        keys.forEach { nativeKey ->
            prepared.add(nativeKey)
            ads.preloadNativeManagement.load(activity, nativeKey)
        }
        inter.loadForOwner(activity, key, owner) {
            if (!finished && current()) {
                val loaded = inter.isLoaded(key)
                android.util.Log.d("BatterySplash", "Interstitial load completed: loaded=$loaded")
                if (gate.onInterComplete(loaded, SystemClock.elapsedRealtime())) ready()
            }
        }
    }

    fun transition(source: Fragment?, target: String, next: () -> Unit) {
        if (busy) return // Ignore double taps while the first route owns the opportunity.
        prepare(target) // Native request starts BEFORE inter / destination navigation.
        if (source !is com.hd.emojibattery.ui.common.BaseFeatureFragment<*> ||
            target in setOf(S.LANGUAGE, S.INTRO_1, S.INTRO_2, S.INTRO_3, S.INTRO_5, S.SPLASH, S.PAYWALL, S.PREMIUM) ||
            SystemClock.elapsedRealtime() < suppressInterUntil) {
            next(); return
        }
        showInterstitial(source, BatteryAdPlacements.ADMOB_INTERSTITIAL_ID, next = next)
    }

    /** The navigation opportunity owns a finite 3s load + prepare deadline. */
    fun showInterstitial(source: Fragment, key: String, waitForHigh: Boolean = false, next: () -> Unit) {
        if (!enabled()) { next(); return }
        if (busy) return
        val owner = source.viewLifecycleOwnerLiveData.value ?: run { next(); return }
        val deadline = SystemClock.elapsedRealtime() + 3000L
        inter.setCustomShowInterval(key, 0L)
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
                // Reserve presentation before the host pauses, but keep loading covered
                // until the SDK confirms that its fullscreen content is visible.
                onAdPresenting = { presented = true; handler.removeCallbacks(timeout) },
                onAdShowed = {
                    presented = true
                    handler.removeCallbacks(timeout)
                    loading.dismiss()
                })
        }
        fun fallback() {
            if (!alive() || SystemClock.elapsedRealtime() >= deadline) { finish(true); return }
            // Single production unit: join an existing preload rather than requesting it twice.
            inter.load(activity, key) {
                if (done) return@load
                if (inter.isLoaded(key)) showReady() else finish(true)
            }
        }
        if (inter.isLoaded(key)) showReady()
        else if (waitForHigh) {
            prepared.add(key)
            inter.load(activity, key) {
                if (!done) { if (inter.isLoaded(key)) showReady() else finish(true) }
            }
        } else fallback()
    }

    fun onScreen(fragment: Fragment, screen: String) {
        unbind?.invoke()
        unbind = null
        val generation = ++bindingGeneration
        val miniHost = activity.findViewById<FrameLayout>(R.id.battery_home_mini_host)
        miniHost.removeAllViews()
        miniHost.visibility = View.GONE
        val activityHost = activity.findViewById<FrameLayout>(R.id.battery_ad_host)
        activityHost.removeAllViews()
        activityHost.visibility = View.GONE
        val actionHost = activity.findViewById<FrameLayout>(R.id.battery_action_host)
        actionHost.removeAllViews()
        val fullNative = screen == S.INTRO_3
        val dock = activity.findViewById<android.widget.LinearLayout>(R.id.battery_bottom_dock)
        dock.layoutParams = (dock.layoutParams as FrameLayout.LayoutParams).apply {
            height = -2
            bottomMargin = 0
        }
        activityHost.layoutParams = android.widget.LinearLayout.LayoutParams(-1, if (fullNative) 0 else -2).apply {
            weight = if (fullNative) 1f else 0f
        }
        // Intro ads belong to the fragment: inline inside its panel, or on their own page.
        val host = fragment.view?.findViewById<FrameLayout>(R.id.intro_ad_host) ?: activityHost
        host.removeAllViews()
        host.visibility = View.GONE
        ads.setAppOpenAdEnabled(fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>)
        val footer = fragment.view?.findViewById<View>(R.id.feature_footer)
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*> &&
            !com.hd.emojibattery.ui.navigation.ScreenRouter.isSticker(screen) &&
            !com.hd.emojibattery.ui.navigation.ScreenRouter.isMainTab(screen)) {
            host.post {
                if (bindingGeneration == generation && footer != null && footer.visibility == View.VISIBLE) {
                    (footer.parent as? android.view.ViewGroup)?.removeView(footer)
                    actionHost.addView(footer)
                }
            }
        }
        if (!enabled()) {
            if (screen == S.LANGUAGE) languageAdComplete.value = true
            return
        }
        if (screen in setOf(S.INTRO_2, S.INTRO_3, S.INTRO_5)) {
            BatteryAdPlacements.native(screen)?.let { key ->
                // Re-entering an intro always starts a new opportunity, even when
                // its remote configuration disables automatic background refill.
                if (!visitedIntroAds.add(key)) {
                    ads.preloadNativeManagement.remove(key)
                    prepared.remove(key)
                }
                oneShotConsumed.remove(key)
            }
        }
        if (screen == S.LANGUAGE && prepared.add("emoji_native_language_reload")) {
            ads.preloadNativeManagement.load(activity, "emoji_native_language_reload")
        }
        if (screen == S.INTRO_1) {
            BatteryAdsConfig.nativePlacements.filter {
                it.contains("native") && it != "emoji_native_list_data_custom" &&
                    it != "emoji_native_small_all_home" && !it.startsWith("emoji_native_language")
            }.forEach {
                if (prepared.add(it)) ads.preloadNativeManagement.load(activity, it)
            }
        }
        prepare(screen) // Also covers restored fragments and deep links.
        BatteryAdPlacements.nextNativeScreens(screen).forEach(::prepare)
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>) preloadInter()
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>) {
            ads.markSplashCompleted()
            ads.preloadResumeAd(activity)
        }
        if (fragment is com.hd.emojibattery.ui.common.BaseFeatureFragment<*>) {
            ads.preloadRewardedManagement.load(activity, BatteryAdPlacements.REWARD_ID)
            listOf("emoji_native_pop_up", "emoji_native_pop_up_unlock").forEach {
                if (prepared.add(it)) ads.preloadNativeManagement.load(activity, it)
            }
        }
        val owner = fragment.viewLifecycleOwnerLiveData.value ?: return
        val cleanupMini = bindHomeMini(owner, screen, generation, miniHost)
        val banner = BatteryAdPlacements.banner(screen)
        val nativeKey = if (screen == S.LANGUAGE && languageReloadSelected) "emoji_native_language_reload"
            else if (banner == null) BatteryAdPlacements.native(screen) else null
        val collapsible = nativeKey?.startsWith("emoji_native_collab_") == true ||
            (screen == S.STATUS_BAR && nativeKey != null)
        var ownedAd: NativeAd? = null
        var callback: ((NativeAd?) -> Unit)? = null
        var cleaned = false
        val cleanup = cleanup@{
            if (cleaned) return@cleanup
            cleaned = true
            cleanupMini()
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
            // Inflate the actual ad layout before loading: skeleton and creative share
            // the same measured space, including the inline intro placements.
            val introNative = screen == S.INTRO_2 || fullNative || screen == S.INTRO_5
            val loadingIntroView = if (introNative && ads.isConfiguredPlacementEnabled(nativeKey)) ViewNativeAd(activity).apply {
                val layout = ads.resolveNativeLayoutRes(activity, nativeKey)
                setLayoutNativeAd(if (layout != 0) layout else if (fullNative)
                    tpt.dev.monetization.R.layout.battery_native_full
                    else tpt.dev.monetization.R.layout.battery_native_onboard)
                if (fullNative) onCollapseRequested = { (activity as MainActivity).openScreen(S.INTRO_5, false) }
                host.addView(this, FrameLayout.LayoutParams(-1, if (fullNative) -1 else -2))
                host.visibility = View.VISIBLE
            } else null
            callback = {
                if (bindingGeneration == generation && enabled() && ownedAd == null &&
                    owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                    val ad = ads.preloadNativeManagement.getNativeAd(nativeKey, removeAfterGet = true)
                    if (ad == null && fullNative) {
                        // No fill leaves the standalone page navigation available.
                        host.removeAllViews()
                        host.visibility = View.GONE
                    } else if (ad != null) {
                        ownedAd = ad
                        ads.preloadNativeManagement.markShowing(nativeKey)
                        val view = loadingIntroView ?: ViewNativeAd(activity)
                        val layout = ads.resolveNativeLayoutRes(activity, nativeKey)
                        if (collapsible) {
                            val expanded = com.hd.emojibattery.ui.navigation.ScreenRouter.isPet(screen) ||
                                lastCollapseExpansion == Long.MIN_VALUE ||
                                SystemClock.elapsedRealtime() - lastCollapseExpansion >= BatteryAdsConfig.collapseIntervalMs
                            fun positionDock(isExpanded: Boolean) {
                                dock.layoutParams = (dock.layoutParams as FrameLayout.LayoutParams).apply {
                                    bottomMargin = if (!isExpanded && com.hd.emojibattery.ui.navigation.ScreenRouter.isMainTab(screen))
                                        activity.findViewById<View>(R.id.app_bottom_nav).height +
                                            (if (miniHost.visibility == View.VISIBLE) miniHost.layoutParams.height else 0) else 0
                                }
                            }
                            positionDock(expanded)
                            view.setLayoutNativeAd(if (expanded) tpt.dev.monetization.R.layout.battery_native_collapse else tpt.dev.monetization.R.layout.layout_small_native_ads)
                            if (expanded) lastCollapseExpansion = SystemClock.elapsedRealtime()
                            view.onCollapseRequested = {
                                host.removeAllViews()
                                host.visibility = View.GONE
                                ads.preloadNativeManagement.markHidden(nativeKey)
                                ownedAd?.destroy()
                                ownedAd = null
                                positionDock(false)
                            }
                        } else if (loadingIntroView == null && layout != 0) view.setLayoutNativeAd(layout)
                        if (fullNative) view.onCollapseRequested = { (activity as MainActivity).openScreen(S.INTRO_5, false) }
                        view.populate(ad)
                        if (view.parent == null) host.addView(view, FrameLayout.LayoutParams(-1, if (fullNative) -1 else -2))
                        host.visibility = View.VISIBLE
                        if (ads.shouldReloadNativePlacement(nativeKey, false)) {
                            ads.preloadNativeManagement.loadForOwner(activity, nativeKey, owner)
                        } else oneShotConsumed.add(nativeKey)
                    }
                    if (screen == S.LANGUAGE) languageAdComplete.value = true
                }
            }
            ads.preloadNativeManagement.loadForOwner(activity, nativeKey, owner, callback)
        }
    }

    /** Independent bottom slot: reserve its height even while loading or after no fill. */
    private fun bindHomeMini(owner: LifecycleOwner, screen: String, generation: Int, host: FrameLayout): () -> Unit {
        val key = "emoji_native_small_all_home"
        if (!com.hd.emojibattery.ui.navigation.ScreenRouter.isMainTab(screen) ||
            !ads.isConfiguredPlacementEnabled(key)) return {}
        val manager = ads.preloadNativeManagement
        val view = ViewNativeAd(activity).apply {
            setLayoutNativeAd(tpt.dev.monetization.R.layout.battery_native_mini)
        }
        host.addView(view, FrameLayout.LayoutParams(-1, -1))
        host.visibility = View.VISIBLE
        var ownedAd: NativeAd? = null
        var closed = false
        val callback: (NativeAd?) -> Unit = {
            if (!closed && bindingGeneration == generation && enabled() &&
                ads.isConfiguredPlacementEnabled(key) && ownedAd == null &&
                owner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                manager.getNativeAd(key, removeAfterGet = true)?.let { ad ->
                    ownedAd = ad
                    manager.markShowing(key)
                    view.populate(ad)
                }
            }
        }
        manager.loadForOwner(activity, key, owner, callback)
        return {
            closed = true
            manager.cancelLoadCallback(key, callback)
            manager.markHidden(key)
            ownedAd?.destroy()
            ownedAd = null
            manager.remove(key)
            if (bindingGeneration == generation) {
                host.removeAllViews()
                host.visibility = View.GONE
            }
        }
    }

    fun beginLanguage() {
        languageReloadSelected = false
        languageAdComplete.value = !enabled() || ads.preloadNativeManagement.isLoaded("emoji_native_language")
        listOf("emoji_native_language", "emoji_native_language_reload").forEach {
            oneShotConsumed.remove(it)
            prepared.remove(it)
        }
    }

    fun showLanguageReload(fragment: Fragment) {
        if (languageReloadSelected) return
        languageReloadSelected = true
        languageAdComplete.value = !enabled()
        onScreen(fragment, S.LANGUAGE)
    }

    /** Cached compact native, owned only for the lifetime of this dialog window. */
    fun bindPopup(dialog: android.app.Dialog, includeNative: Boolean = true, reserveNativeSpace: Boolean = false) {
        val decor = dialog.window?.decorView ?: return
        var ad: NativeAd? = null
        var attached = false
        val key = "emoji_native_pop_up"
        var callback: ((NativeAd?) -> Unit)? = null
        // Prepare the Adjustment skeleton before the sheet's first measurement.
        val reservedHost = if (reserveNativeSpace) dialog.findViewById<FrameLayout>(R.id.discard_native_host) else null
        val preparedView = if (reservedHost != null && includeNative && enabled()) {
            ViewNativeAd(activity).apply {
                setLayoutNativeAd(tpt.dev.monetization.R.layout.battery_native_list)
                reservedHost.layoutParams = reservedHost.layoutParams.apply {
                    height = (212 * activity.resources.displayMetrics.density).toInt()
                }
                reservedHost.visibility = View.VISIBLE
                reservedHost.addView(this, FrameLayout.LayoutParams(-1, -1))
            }
        } else null
        fun attach() {
            if (attached || !dialog.isShowing) return
            attached = true
            nativeDialogs.add(dialog)
            dialog.window?.let { window ->
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
                    hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                }
            }
            if (!includeNative || !enabled()) {
                reservedHost?.visibility = View.GONE
                return
            }
            val root = dialog.findViewById<android.view.ViewGroup>(android.R.id.content) ?: return
            val cardHost = dialog.findViewById<FrameLayout>(R.id.discard_native_host)
            val adView = preparedView ?: ViewNativeAd(activity).apply {
                setLayoutNativeAd(tpt.dev.monetization.R.layout.battery_native_list)
            }
            if (cardHost != null) {
                cardHost.visibility = View.VISIBLE
                if (adView.parent == null) cardHost.addView(adView, FrameLayout.LayoutParams(-1, -2))
            } else {
                val wrapper = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL }
                while (root.childCount > 0) {
                    val child = root.getChildAt(0)
                    root.removeView(child)
                    wrapper.addView(child, android.widget.LinearLayout.LayoutParams(-1, -2))
                }
                wrapper.addView(adView)
                root.addView(wrapper)
            }
            fun populateCached(): Boolean {
                if (!dialog.isShowing || !attached || !enabled()) return false
                val native = ads.preloadNativeManagement.getNativeAd(key, removeAfterGet = true) ?: return false
                ad = native
                adView.populate(native)
                return true
            }
            if (!populateCached()) {
                val result: (NativeAd?) -> Unit = {
                    if (dialog.isShowing && attached && !populateCached()) {
                        adView.visibility = View.GONE
                        // Keep the reserved sheet height stable even on no fill.
                        cardHost?.visibility = if (reserveNativeSpace) View.INVISIBLE else View.GONE
                    }
                }
                callback = result
                ads.preloadNativeManagement.load(activity, key, callback = result)
            }
            // Refill after consuming a cached creative.
            if (ad != null) ads.preloadNativeManagement.load(activity, key)
        }
        decor.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { v.post { attach() } }
            override fun onViewDetachedFromWindow(v: View) {
                attached = false
                callback?.let { ads.preloadNativeManagement.cancelLoadCallback(key, it) }
                callback = null
                ad?.destroy(); ad = null
                nativeDialogs.remove(dialog)
                v.removeOnAttachStateChangeListener(this)
            }
        })
        if (dialog.isShowing) decor.post { attach() }
    }

    fun unlock(fragment: Fragment, itemId: String, onUnlocked: () -> Unit) {
        if (SessionUnlocks.contains(itemId) || PremiumManager.INSTANCE.isSubscribed() || BuildConfig.UI_TEST_MODE) {
            onUnlocked(); return
        }
        if (busy) return
        val sheet = UnlockItemSheet(activity, onWatch = {
            activeDialog = null
            showReward(fragment, itemId, onUnlocked)
        }, onPremium = { (activity as MainActivity).openScreen(S.PREMIUM, true) })
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
    fun canResumeAd() = !busy && activeDialog?.isShowing != true && nativeDialogs.none { it.isShowing }
    fun close() {
        cancelOpportunity?.invoke()
        unbind?.invoke()
        activeDialog?.dismiss()
        activeDialog = null
        nativeDialogs.toList().forEach { it.dismiss() }
        nativeDialogs.clear()
        handler.removeCallbacksAndMessages(null)
    }
}

/** Memory only: earned items reset with a new process/session. Never overwrites purchases. */
object SessionUnlocks {
    private val ids = mutableSetOf<String>()
    fun contains(id: String) = id in ids
    fun add(id: String) { ids.add(id) }
    fun isLocked(context: android.content.Context, id: String): Boolean {
        if (PremiumManager.INSTANCE.isSubscribed() || contains(id)) return false
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
