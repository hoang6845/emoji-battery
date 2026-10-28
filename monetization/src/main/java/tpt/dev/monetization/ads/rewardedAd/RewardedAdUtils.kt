package tpt.dev.monetization.ads.rewardedAd

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference
import tpt.dev.monetization.ads.preload.AdRetryPolicy
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.PrepareLoadingAdsDialog
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager

class RewardedAdUtils internal constructor(
    private val rewardedAdId: String,
    private val adsManager: AdsManager,
    premiumManager: IPremiumManager,
    googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {
    private data class CachedAd(val ad: RewardedAd, val source: ResolvedAdRequest,
        val loadedAt: Long = SystemClock.elapsedRealtime())

    private val handler = Handler(Looper.getMainLooper())
    private var cachedAd: CachedAd? = null
    private var loading = false
    private var loadOwner: WeakReference<LifecycleOwner>? = null
    private var showing = false
    private val loadCallbacks = mutableListOf<(CachedAd?) -> Unit>()

    companion object {
        private const val CACHE_TTL_MS = 55 * 60 * 1000L
        private const val SHOW_WAIT_TIMEOUT_MS = 15_000L
        private const val TAG = "RewardedAdUtils"
    }

    /** Silent preload; displaying an ad still requires an explicit watch action. */
    fun preloadRewardedAd(owner: LifecycleOwner? = null) {
        handler.post {
            if (owner != null) loadOwner = WeakReference(owner)
            loadRewardedAd()
        }
    }

    fun showRewardedAd(
        activity: AppCompatActivity,
        onAdShowed: () -> Unit,
        onAdDismissed: () -> Unit,
        onLoadFailed: () -> Unit,
        onRewarded: (RewardItem) -> Unit
    ) {
        if (showing || !canLoad() || activity.isFinishing || activity.isDestroyed ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            onLoadFailed()
            return
        }
        loadOwner = WeakReference(activity)
        showing = true
        adsManager.updateIsShowingFullScreenAd(true)
        val loadingDialog = runCatching { PrepareLoadingAdsDialog(activity).also { it.show() } }.getOrNull()
        var waiting = true
        lateinit var timeout: Runnable
        lateinit var observer: DefaultLifecycleObserver
        lateinit var callback: (CachedAd?) -> Unit

        fun finishWaiting() {
            waiting = false
            handler.removeCallbacks(timeout)
            loadCallbacks.remove(callback)
            activity.lifecycle.removeObserver(observer)
            runCatching { loadingDialog?.dismiss() }
        }
        fun failWaiting() {
            if (!waiting) return
            finishWaiting()
            showing = false
            adsManager.updateIsShowingFullScreenAd(false)
            onLoadFailed()
        }
        timeout = Runnable { failWaiting() }
        observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = failWaiting()
            override fun onDestroy(owner: LifecycleOwner) = failWaiting()
        }
        callback = callback@{ loaded ->
            if (!waiting) return@callback
            if (loaded == null || !canLoad() || !adsManager.isResolvedAdStillEnabled(loaded.source) ||
                activity.isFinishing || activity.isDestroyed ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                failWaiting()
                return@callback
            }
            finishWaiting()
            // Consume before show: even an ad that fails to show cannot be reused.
            cachedAd = null
            val ad = loaded.ad
            val source = loaded.source
            var completed = false
            fun finishShow(failed: Boolean) {
                if (completed) return
                completed = true
                showing = false
                adsManager.updateIsShowingFullScreenAd(false)
                if (failed) onLoadFailed() else onAdDismissed()
            }
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdPaid(adValue: AdValue) {
                    AdRevenueReporter.report(adValue, "REWARDED", source.unitId, ad, source.debugLabel())
                }

                override fun onAdShowedFullScreenContent() {
                    handler.post {
                        AdMediationDebugLogger.logAdEvent("SHOW_SUCCESS", "REWARDED", source.unitId, ad, source.debugLabel())
                        onAdShowed()
                    }
                }
                override fun onAdImpression() {
                    AdMediationDebugLogger.logAdEvent("IMPRESSION", "REWARDED", source.unitId, ad, source.debugLabel())
                }
                override fun onAdDismissedFullScreenContent() {
                    handler.post { finishShow(false) }
                }
                override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                    AdMediationDebugLogger.logShowFailure("REWARDED", source.unitId, adError, source.debugLabel())
                    handler.post { finishShow(true) }
                }
            }
            var rewarded = false
            try {
                ad.show(activity) {
                    handler.post {
                        if (!rewarded) {
                            rewarded = true
                            onRewarded(it)
                        }
                    }
                }
            } catch (error: RuntimeException) {
                Log.e(TAG, "Rewarded show failed", error)
                finishShow(true)
            }
        }
        activity.lifecycle.addObserver(observer)
        handler.postDelayed(timeout, SHOW_WAIT_TIMEOUT_MS)
        loadRewardedAd(callback)
    }

    private fun canLoad() = appAllowShowAd && adsManager.canRequestAds && adsManager.canUsePlacement(rewardedAdId)

    private fun loadRewardedAd(callback: ((CachedAd?) -> Unit)? = null) {
        if (!canLoad()) {
            cachedAd = null
            callback?.invoke(null)
            return
        }
        cachedAd = cachedAd?.takeIf {
            SystemClock.elapsedRealtime() - it.loadedAt < CACHE_TTL_MS && adsManager.isResolvedAdStillEnabled(it.source)
        }
        cachedAd?.let {
            callback?.invoke(it)
            return
        }
        callback?.let { loadCallbacks.add(it) }
        // Join a preload already in flight instead of starting another request.
        if (loading) return
        loading = true
        loadResolvedRewardedAd(adsManager.resolveAdRequests(rewardedAdId), 0)
    }

    private fun completeLoad(ad: CachedAd?) {
        cachedAd = ad
        loading = false
        val callbacks = loadCallbacks.toList()
        loadCallbacks.clear()
        callbacks.forEach { it(ad) }
    }

    private fun loadResolvedRewardedAd(adRequests: List<ResolvedAdRequest>, index: Int, retries: Int = 0) {
        val source = adRequests.getOrNull(index)
        if (!canLoad()) { completeLoad(null); return }
        if (source == null) {
            val wait = AdRetryPolicy.delayMillis(retries)
            if (wait == null) { completeLoad(null); return }
            handler.postDelayed({
                val ownerActive = loadOwner?.get()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) ?: false
                if (!ownerActive || !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || !canLoad()) {
                    completeLoad(null)
                } else {
                    loadResolvedRewardedAd(adsManager.resolveAdRequests(rewardedAdId), 0, retries + 1)
                }
            }, wait)
            return
        }
        if (!adsManager.isResolvedAdStillEnabled(source)) {
            loadResolvedRewardedAd(adRequests, index + 1, retries)
            return
        }
        try {
            RewardedAd.load(buildAdRequest(source.unitId), object : AdLoadCallback<RewardedAd> {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    handler.post {
                        AdMediationDebugLogger.logLoadFailure("REWARDED", source.unitId, adError, source.debugLabel())
                        loadResolvedRewardedAd(adRequests, index + 1, retries)
                    }
                }
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    handler.post {
                        if (!canLoad()) completeLoad(null)
                        else if (!adsManager.isResolvedAdStillEnabled(source)) loadResolvedRewardedAd(adRequests, index + 1, retries)
                        else {
                            AdMediationDebugLogger.logLoadSuccess("REWARDED", source.unitId, rewardedAd, source.debugLabel())
                            completeLoad(CachedAd(rewardedAd, source))
                        }
                    }
                }
            })
        } catch (error: RuntimeException) {
            Log.e(TAG, "Rewarded load failed: ${source.debugLabel()}", error)
            loadResolvedRewardedAd(adRequests, index + 1, retries)
        }
    }

    private fun ResolvedAdRequest.debugLabel() = "$placementKey/$adItemKey/$unitKey"
}
