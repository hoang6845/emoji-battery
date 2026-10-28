package tpt.dev.monetization.ads.appOpenAd

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import tpt.dev.monetization.ads.preload.AdRetryPolicy
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager

class AppOpenAdUtils internal constructor(
    private val isAllowShowOpenAd: Boolean,
    private val appOpenAdId: String,
    private val application: Application,
    private val adsManager: AdsManager,
    private val premiumManager: IPremiumManager,
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager,
) : BaseAdUtils(premiumManager),
    DefaultLifecycleObserver {
    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd = false
    private var countLoadAppOpenAds = 0
    private var isPendingShowAd = false
    private var pendingShowActivity: AppCompatActivity? = null
    private var pendingShowAdCompleteListener: OnShowAdCompleteListener? = null
    private var loadedSource: ResolvedAdRequest? = null
    private var loadedAtMillis = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preloadListeners = mutableListOf<(Boolean) -> Unit>()
    internal var isShowingAd = false

    /** Shares the same cache and in-flight request with foreground show requests. */
    fun preload(onComplete: ((Boolean) -> Unit)? = null) {
        runOnMain {
            onComplete?.let { preloadListeners.add(it) }
            loadAd()
        }
    }

    private fun canLoadAd(): Boolean = !adsManager.usesInterSplashOnResume() && isAllowShowOpenAd && appAllowShowAd &&
        adsManager.canRequestAds && adsManager.isAppOpenAdEnabled() &&
        adsManager.canUsePlacement(appOpenAdId)

    private fun loadAd() {
        // A displayed ad is already consumed; refill once its terminal callback arrives.
        if (isShowingAd) return

        if (!canLoadAd()) {
            clearCachedAd()
            notifyPreloadComplete(false)
            completePendingShowAd()
            return
        }

        if (isAdAvailable()) {
            Log.d("AppLifecycle", "App Open cache hit, pendingShow=$isPendingShowAd")
            notifyPreloadComplete(true)
            showPendingAdIfAvailable()
            return
        }

        if (isLoadingAd) {
            Log.d("AppLifecycle", "App Open joining existing load, pendingShow=$isPendingShowAd")
            return
        }

        ++countLoadAppOpenAds
        isLoadingAd = true

        val adRequests = adsManager.resolveAdRequests(appOpenAdId)
        if (adRequests.isEmpty()) {
            isLoadingAd = false
            countLoadAppOpenAds = 0
            notifyPreloadComplete(false)
            completePendingShowAd()
            return
        }

        loadResolvedAd(adRequests, 0)
    }

    private fun loadResolvedAd(adRequests: List<ResolvedAdRequest>, index: Int) {
        if (!canLoadAd()) {
            isLoadingAd = false
            countLoadAppOpenAds = 0
            notifyPreloadComplete(false)
            completePendingShowAd()
            return
        }

        val source = adRequests.getOrNull(index)
        if (source == null) {
            val wait = AdRetryPolicy.delayMillis(countLoadAppOpenAds - 1)
            if (wait == null) {
                isLoadingAd = false
                countLoadAppOpenAds = 0
                notifyPreloadComplete(false)
                completePendingShowAd()
            } else {
                mainHandler.postDelayed({
                    isLoadingAd = false
                    if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !canLoadAd()) {
                        countLoadAppOpenAds = 0
                        notifyPreloadComplete(false)
                        completePendingShowAd()
                    } else {
                        loadAd()
                    }
                }, wait)
            }
            return
        }

        if (!adsManager.isResolvedAdStillEnabled(source)) {
            loadResolvedAd(adRequests, index + 1)
            return
        }

        AppOpenAd.load(
            buildAdRequest(source.unitId),
            object : AdLoadCallback<AppOpenAd> {
                override fun onAdLoaded(ad: AppOpenAd) {
                    runOnMain {
                        if (!canLoadAd()) {
                            isLoadingAd = false
                            countLoadAppOpenAds = 0
                            notifyPreloadComplete(false)
                            completePendingShowAd()
                            return@runOnMain
                        }
                        if (!adsManager.isResolvedAdStillEnabled(source)) {
                            loadResolvedAd(adRequests, index + 1)
                            return@runOnMain
                        }

                        appOpenAd = ad
                        loadedSource = source
                        loadedAtMillis = SystemClock.elapsedRealtime()
                        isLoadingAd = false
                        countLoadAppOpenAds = 0
                        AdMediationDebugLogger.logLoadSuccess(
                            format = "APP_OPEN",
                            adUnitId = source.unitId,
                            ad = ad,
                            sourceLabel = source.debugLabel()
                        )
                        notifyPreloadComplete(true)
                        showPendingAdIfAvailable()
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    runOnMain {
                        AdMediationDebugLogger.logLoadFailure(
                            format = "APP_OPEN",
                            adUnitId = source.unitId,
                            error = adError,
                            sourceLabel = source.debugLabel()
                        )
                        loadResolvedAd(adRequests, index + 1)
                    }
                }
            }
        )
    }

    /** Check if ad exists and can be shown. */
    fun isAdAvailable(): Boolean {
        if (appOpenAd != null && (!canLoadAd() ||
                !adsManager.isResolvedAdStillEnabled(loadedSource) ||
                SystemClock.elapsedRealtime() - loadedAtMillis >= MAX_AD_AGE_MS)) {
            clearCachedAd()
        }
        return !isShowingAd && appOpenAd != null
    }

    /**
     * Use the cached ad first; join an existing preload when it is still loading.
     */
    fun showAdIfAvailable(
        activity: AppCompatActivity,
        onShowAdCompleteListener: OnShowAdCompleteListener
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showAdIfAvailable(activity, onShowAdCompleteListener) }
            return
        }
        Log.d("AppLifecycle", "showAdIfAvailable: start")
        Log.d("AppLifecycle", "isShowingAd = $isShowingAd")
        Log.d("AppLifecycle", "isPendingShowAd = $isPendingShowAd")
        Log.d("AppLifecycle", "canShowOpenAdNow = ${canShowOpenAdNow()}")

        if (isShowingAd) {
            Log.d("AppLifecycle", "showAdIfAvailable: skipped → already showing ad")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }

        if (isPendingShowAd) {
            Log.d("AppLifecycle", "showAdIfAvailable: skipped → already pending show")
            return
        }

        if (!canShowOpenAdNow()) {
            Log.d("AppLifecycle", "showAdIfAvailable: skipped → canShowOpenAdNow = false")
            onShowAdCompleteListener.onShowAdComplete()
            return
        }

        Log.d("AppLifecycle", "showAdIfAvailable: proceed → set pending state")

        isPendingShowAd = true
        pendingShowActivity = activity
        pendingShowAdCompleteListener = onShowAdCompleteListener

        loadAd()
    }

    internal fun canShowOpenAdNow(): Boolean {
        if (!canLoadAd()) {
            return false
        }
        if (adsManager.checkIsShowingFullScreenAd()) {
            return false
        }
        if ((System.currentTimeMillis() - adsManager.getLastTimeShowInterstitialAd() < adsManager.getTimeIntervalShowInterVsOpen().inWholeMilliseconds) ||
            (System.currentTimeMillis() - adsManager.getLastTimeShowOpenAd() < adsManager.getTimeIntervalShowInterVsOpen().inWholeMilliseconds)
        ) {
            return false
        }
        return true
    }

    private fun showAdInternal(
        activity: AppCompatActivity,
        onShowAdCompleteListener: OnShowAdCompleteListener
    ) {
        if (!isAdAvailable() || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.d("AppLifecycle", "App Open show skipped: available=${isAdAvailable()}, activityState=${activity.lifecycle.currentState}")
            completePendingShowAd()
            return
        }

        val shownSource = loadedSource
        val shownAd = appOpenAd ?: return
        var finished = false
        val finishShow = {
            if (!finished) {
                finished = true
                clearCachedAd()
                isShowingAd = false
                adsManager.updateIsShowingFullScreenAd(false)
                onShowAdCompleteListener.onShowAdComplete()
                preload()
            }
        }
        shownAd.adEventCallback = object : AppOpenAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                runOnMain {
                    if (finished) return@runOnMain
                    adsManager.updateLastTimeShowOpenAd(System.currentTimeMillis())
                    finishShow()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                runOnMain {
                    AdMediationDebugLogger.logShowFailure(
                        format = "APP_OPEN",
                        adUnitId = shownSource?.unitId ?: appOpenAdId,
                        error = adError,
                        sourceLabel = shownSource?.debugLabel()
                    )
                    finishShow()
                }
            }

            override fun onAdShowedFullScreenContent() {
                runOnMain {
                    if (finished) return@runOnMain
                    isShowingAd = true
                    clearPendingShowAd()
                    adsManager.updateIsShowingFullScreenAd(true)
                    onShowAdCompleteListener.onAdShown()
                    shownAd.let {
                        AdMediationDebugLogger.logAdEvent(
                            event = "SHOW_SUCCESS",
                            format = "APP_OPEN",
                            adUnitId = shownSource?.unitId ?: appOpenAdId,
                            ad = it,
                            sourceLabel = shownSource?.debugLabel()
                        )
                    }
                }
            }

            override fun onAdPaid(adValue: AdValue) {
                AdRevenueReporter.report(
                    adValue = adValue,
                    format = "APP_OPEN",
                    adUnitId = shownSource?.unitId ?: appOpenAdId,
                    ad = shownAd,
                    placement = shownSource?.debugLabel()
                )
            }

            override fun onAdImpression() {
                shownAd.let {
                    AdMediationDebugLogger.logAdEvent(
                        event = "IMPRESSION",
                        format = "APP_OPEN",
                        adUnitId = shownSource?.unitId ?: appOpenAdId,
                        ad = it,
                        sourceLabel = shownSource?.debugLabel()
                    )
                }
            }
        }

        // Reserve fullscreen ownership before calling the SDK, whose callbacks are asynchronous.
        isShowingAd = true
        clearPendingShowAd()
        adsManager.updateIsShowingFullScreenAd(true)
        try {
            shownAd.show(activity)
        } catch (error: RuntimeException) {
            Log.e("AppLifecycle", "App Open show failed", error)
            finishShow()
        }
    }

    private fun showPendingAdIfAvailable() {
        if (!isPendingShowAd) {
            return
        }

        val activity = pendingShowActivity
        val listener = pendingShowAdCompleteListener

        if (activity == null || listener == null) {
            clearPendingShowAd()
            return
        }

        if (activity.isDestroyed || activity.isFinishing) {
            completePendingShowAd()
            return
        }

        if (!canShowOpenAdNow()) {
            completePendingShowAd()
            return
        }

        if (!isAdAvailable()) {
            return
        }

        showAdInternal(activity, listener)
    }

    private fun completePendingShowAd() {
        val listener = pendingShowAdCompleteListener
        clearPendingShowAd()
        listener?.onShowAdComplete()
    }

    fun setPendingShowAd(isPending: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { setPendingShowAd(isPending) }
            return
        }
        isPendingShowAd = isPending
        if (!isPending) {
            // Cancel only the show opportunity; an unused or late-loaded ad stays cached.
            pendingShowActivity = null
            pendingShowAdCompleteListener = null
        }
    }

    private fun clearPendingShowAd() {
        setPendingShowAd(false)
    }

    companion object {
        private const val MAX_AD_AGE_MS = 4 * 60 * 60 * 1000L
    }

    private fun clearCachedAd() {
        appOpenAd = null
        loadedSource = null
        loadedAtMillis = 0L
    }

    private fun notifyPreloadComplete(loaded: Boolean) {
        val listeners = preloadListeners.toList()
        preloadListeners.clear()
        listeners.forEach { it(loaded) }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post { action() }
    }

    private fun ResolvedAdRequest.debugLabel(): String {
        return "$placementKey/$adItemKey/$unitKey"
    }
}
