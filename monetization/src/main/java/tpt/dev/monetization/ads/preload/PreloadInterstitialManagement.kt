package tpt.dev.monetization.ads.preload

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.PrepareLoadingAdsDialog
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager
import kotlin.random.Random

class PreloadInterstitialManagement(
    val context: Context,
    val adsManager: AdsManager,
    val premiumManager: IPremiumManager,
    val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {
    private val adsPool: MutableMap<String, InterstitialAd> = HashMap()
    private val adSources: MutableMap<String, ResolvedAdRequest> = HashMap()
    private val loadingPools: MutableSet<String> = mutableSetOf()
    private val pending = PendingAdLoads<Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private val retryOwners = mutableMapOf<String, WeakReference<LifecycleOwner>>()
    private val loadRequestCountByKey: MutableMap<String, Int> = mutableMapOf()
    private val loadSuccessCountByKey: MutableMap<String, Int> = mutableMapOf()
    private val loadFailCountByKey: MutableMap<String, Int> = mutableMapOf()
    private val showAttemptCountByKey: MutableMap<String, Int> = mutableMapOf()
    private val showSuccessCountByKey: MutableMap<String, Int> = mutableMapOf()
    private val impressionCountByKey: MutableMap<String, Int> = mutableMapOf()
    private val dismissCountByKey: MutableMap<String, Int> = mutableMapOf()

    private var lastShowAdsTime: Long = 0
    private val lastShowAdsTimeByKey: MutableMap<String, Long> = mutableMapOf()
    private val customShowIntervals: MutableMap<String, Long> = mutableMapOf()
    private var backupAdKey: String = ""

    var isShowingAds = false
        private set

    private var prepareLoadingAdsDialog: PrepareLoadingAdsDialog? = null

    companion object {
        private const val TAG = "PreloadInterstitial"
        private const val DEFAULT_DELAY_WATERFALL = 300L
    }

    fun load(activity: Activity, adKey: String, callback: (() -> Unit)? = null) {
        loadSelected(activity, adKey, false, callback)
    }

    /** The caller owns the deadline and must remove this opportunity on timeout. No retries. */
    fun loadFallback(activity: Activity, adKey: String, callback: (() -> Unit)? = null) {
        loadSelected(activity, adKey, true, callback)
    }

    private fun loadSelected(activity: Activity, adKey: String, fallback: Boolean, callback: (() -> Unit)?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { loadSelected(activity, adKey, fallback, callback) }
            return
        }
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        Log.d(TAG, "LOAD_CALL adKey=$adKey poolKey=$poolKey activity=${activity.javaClass.simpleName} ${buildDebugState(poolKey)}")

        if (!appAllowShowAd) {
            Log.d(TAG, "LOAD_SKIP_APP_NOT_ALLOW adKey=$adKey ${buildDebugState(poolKey)}")
            callback?.invoke()
            return
        }

        if (!adsManager.canRequestAds) {
            Log.d(TAG, "LOAD_SKIP_CONSENT adKey=$adKey ${buildDebugState(poolKey)}")
            callback?.invoke()
            return
        }

        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(TAG, "LOAD_SKIP_PLACEMENT_DISABLED adKey=$adKey ${buildDebugState(poolKey)}")
            remove(adKey)
            callback?.invoke()
            return
        }

        clearInvalidCachedAd(poolKey)

        if (adsPool.containsKey(poolKey)) {
            Log.d(TAG, "LOAD_SKIP_ALREADY_LOADED adKey=$adKey ${buildDebugState(poolKey)}")
            callback?.invoke()
            return
        }

        if (loadingPools.contains(poolKey)) {
            Log.d(TAG, "LOAD_SKIP_ALREADY_LOADING adKey=$adKey ${buildDebugState(poolKey)}")
            callback?.let { listener -> pending.subscribe(poolKey) { listener() } }
            return
        }

        val staged = adsManager.activeRemoteAdsConfig()?.configs?.interHighPreloadOnly == true
        val adRequests = adsManager.resolveAdRequests(adKey).filter {
            when {
                fallback -> it.adItemKey.endsWith("_medium") || it.adItemKey.endsWith("_all")
                staged -> it.adItemKey.endsWith("_high")
                else -> true
            }
        }.let { if (staged && !fallback) it.take(1) else it }
        if (adRequests.isEmpty()) {
            Log.d(TAG, "LOAD_SKIP_NO_ENABLED_UNIT adKey=$adKey ${buildDebugState(poolKey)}")
            callback?.invoke()
            return
        }

        loadingPools.add(poolKey)
        val token = pending.begin(poolKey)
        callback?.let { listener -> pending.subscribe(poolKey) { listener() } }
        val requestNo = incrementCounter(loadRequestCountByKey, poolKey)
        Log.d(TAG, "LOAD_REQUEST count=$requestNo adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")

        loadResolvedAd(activity, poolKey, adKey, adRequests, 0, token, if (fallback) -1 else 0)
    }

    fun loadForOwner(activity: Activity, adKey: String, owner: LifecycleOwner, callback: (() -> Unit)? = null) {
        retryOwners[adsManager.resolveAdPoolKey(adKey)] = WeakReference(owner)
        load(activity, adKey, callback)
    }

    private fun retryOwnerActive(activity: Activity, key: String): Boolean {
        val reference = retryOwners[key]
        return if (reference != null) {
            reference.get()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        } else {
            !activity.isDestroyed && !activity.isFinishing &&
                (activity as? LifecycleOwner)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true
        }
    }

    private fun loadResolvedAd(
        activity: Activity,
        poolKey: String,
        adKey: String,
        adRequests: List<ResolvedAdRequest>,
        index: Int,
        token: Long,
        retries: Int
    ) {
        if (!pending.isCurrent(poolKey, token)) return
        if (!appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(adKey)) {
            completeLoad(poolKey, token)
            return
        }
        val source = adRequests.getOrNull(index)
        if (source == null) {
            if (retries < 0 || adsManager.activeRemoteAdsConfig()?.configs?.interHighPreloadOnly == true) {
                completeLoad(poolKey, token)
                return
            }
            val wait = AdRetryPolicy.delayMillis(retries)
            if (wait == null) {
                completeLoad(poolKey, token)
            } else {
                Log.d(TAG, "INTER_RETRY_SCHEDULED poolKey=$poolKey delayMs=$wait")
                handler.postDelayed({
                    if (!pending.isCurrent(poolKey, token)) return@postDelayed
                    if (!retryOwnerActive(activity, poolKey) ||
                        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        completeLoad(poolKey, token)
                    } else {
                        loadResolvedAd(activity, poolKey, adKey, adsManager.resolveAdRequests(adKey), 0, token, retries + 1)
                    }
                }, wait)
            }
            return
        }

        InterstitialAd.load(
            buildAdRequest(source.unitId),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    handler.post {
                        if (!pending.isCurrent(poolKey, token)) return@post
                        if (!appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(adKey)) {
                            completeLoad(poolKey, token)
                            return@post
                        }
                        if (!adsManager.isResolvedAdStillEnabled(source)) {
                            loadResolvedAd(activity, poolKey, adKey, adRequests, index + 1, token, retries)
                            return@post
                        }

                        adsPool[poolKey] = interstitialAd
                        adSources[poolKey] = source
                        interstitialAd.setImmersiveMode(true)
                        loadingPools.remove(poolKey)
                        val successNo = incrementCounter(loadSuccessCountByKey, poolKey)
                        Log.d(TAG, "LOAD_SUCCESS count=$successNo adKey=$adKey poolKey=$poolKey source=${source.adItemKey} ${buildDebugState(poolKey)}")
                        AdMediationDebugLogger.logLoadSuccess(
                            format = "INTERSTITIAL",
                            adUnitId = source.unitId,
                            ad = interstitialAd,
                            sourceLabel = source.debugLabel()
                        )
                        completeLoad(poolKey, token)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handler.post {
                        if (!pending.isCurrent(poolKey, token)) return@post
                        val failNo = incrementCounter(loadFailCountByKey, poolKey)
                        Log.e(
                            TAG,
                            "LOAD_FAIL count=$failNo adKey=$adKey poolKey=$poolKey source=${source.adItemKey} " +
                                "code=${loadAdError.code} message=${loadAdError.message} " +
                                buildDebugState(poolKey)
                        )
                        AdMediationDebugLogger.logLoadFailure(
                            format = "INTERSTITIAL",
                            adUnitId = source.unitId,
                            error = loadAdError,
                            sourceLabel = source.debugLabel()
                        )
                        loadResolvedAd(activity, poolKey, adKey, adRequests, index + 1, token, retries)
                    }
                }
            }
        )
    }

    private fun completeLoad(poolKey: String, token: Long) {
        if (!pending.isCurrent(poolKey, token)) return
        loadingPools.remove(poolKey)
        pending.complete(poolKey, token, Unit)
    }

    fun loadWithWaterfall(
        activity: Activity,
        adKeys: List<String>,
        delayMs: Long = DEFAULT_DELAY_WATERFALL,
        callback: (() -> Unit)? = null
    ) {
        Log.d(TAG, "WATERFALL_LOAD_CALL activity=${activity.javaClass.simpleName} keys=$adKeys delayMs=$delayMs")

        if (adKeys.isEmpty()) {
            Log.d(TAG, "WATERFALL_LOAD_SKIP_EMPTY")
            callback?.invoke()
            return
        }

        val appCompatActivity = activity as? AppCompatActivity ?: run {
            Log.d(TAG, "WATERFALL_LOAD_SKIP_NOT_APPCOMPAT activity=${activity.javaClass.name} keys=$adKeys")
            return
        }
        var loadedCount = 0
        val totalCount = adKeys.size

        adKeys.forEachIndexed { index, adKey ->
            appCompatActivity.lifecycleScope.launch {
                val randomDelay = delayMs + Random.nextInt(0, 500)
                delay(index * randomDelay)

                Log.d(TAG, "WATERFALL_LOAD_ITEM index=$index total=$totalCount adKey=$adKey ${getDebugState(adKey)}")
                load(activity, adKey) {
                    loadedCount++
                    Log.d(TAG, "WATERFALL_LOAD_ITEM_DONE loadedCount=$loadedCount total=$totalCount adKey=$adKey ${getDebugState(adKey)}")
                    if (loadedCount == totalCount) {
                        Log.d(TAG, "WATERFALL_LOAD_COMPLETE keys=$adKeys")
                        callback?.invoke()
                    }
                }
            }
        }
    }

    fun enableBackup(activity: Activity, backupKey: String) {
        this.backupAdKey = backupKey
        Log.d(TAG, "BACKUP_ENABLE backupKey=$backupKey ${getDebugState(backupKey)}")
        load(activity, backupKey)
    }

    fun isLoaded(adKey: String): Boolean {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        clearInvalidCachedAd(poolKey)
        return adsPool.containsKey(poolKey)
    }

    fun isLoading(adKey: String): Boolean {
        return loadingPools.contains(adsManager.resolveAdPoolKey(adKey))
    }

    fun getDebugState(adKey: String): String {
        return buildDebugState(adsManager.resolveAdPoolKey(adKey))
    }

    fun setCustomShowInterval(adKey: String, intervalMillis: Long) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        customShowIntervals[poolKey] = intervalMillis.coerceAtLeast(0L)
        Log.d(TAG, "INTERVAL_SET adKey=$adKey poolKey=$poolKey intervalMs=${customShowIntervals[poolKey]} ${buildDebugState(poolKey)}")
    }

    fun show(
        activity: Activity,
        adKey: String,
        reload: Boolean = true,
        onAdShowed: (() -> Unit)? = null,
        onAdClosed: ((Boolean) -> Unit)? = null
    ) = showInternal(activity, adKey, reload, onAdShowed, onAdClosed, null)

    /** A screen-owned opportunity; also checked after the prepare delay. No backup on a miss. */
    fun showGuarded(
        activity: Activity,
        adKey: String,
        shouldShow: () -> Boolean,
        onAdClosed: ((Boolean) -> Unit)? = null,
        onAdShowed: (() -> Unit)? = null,
        onAdPresenting: (() -> Unit)? = null
    ) = showInternal(activity, adKey, false, onAdShowed, onAdClosed, shouldShow, onAdPresenting)

    private fun showInternal(
        activity: Activity,
        adKey: String,
        reload: Boolean,
        onAdShowed: (() -> Unit)?,
        onAdClosed: ((Boolean) -> Unit)?,
        shouldShow: (() -> Boolean)?,
        onAdPresenting: (() -> Unit)? = null
    ) {
        if (shouldShow?.invoke() == false) {
            onAdClosed?.invoke(false)
            return
        }
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        val showAttemptNo = incrementCounter(showAttemptCountByKey, poolKey)
        Log.d(
            TAG,
            "SHOW_ATTEMPT count=$showAttemptNo adKey=$adKey poolKey=$poolKey reload=$reload " +
                "activity=${activity.javaClass.simpleName} ${buildDebugState(poolKey)}"
        )

        if (isShowingAds || adsManager.checkIsShowingFullScreenAd()) {
            Log.d(TAG, "SHOW_BLOCKED_ALREADY_SHOWING count=$showAttemptNo adKey=$adKey ${buildDebugState(poolKey)}")
            onAdClosed?.invoke(false)
            return
        }

        if (!appAllowShowAd) {
            Log.d(TAG, "SHOW_BLOCKED_APP_NOT_ALLOW count=$showAttemptNo adKey=$adKey ${buildDebugState(poolKey)}")
            onAdClosed?.invoke(false)
            return
        }

        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(TAG, "SHOW_BLOCKED_PLACEMENT_DISABLED count=$showAttemptNo adKey=$adKey ${buildDebugState(poolKey)}")
            remove(adKey)
            onAdClosed?.invoke(false)
            return
        }

        if (adsPool.containsKey(poolKey) && !adsManager.isResolvedAdStillEnabled(adSources[poolKey])) {
            Log.d(TAG, "SHOW_BLOCKED_CACHED_SOURCE_DISABLED count=$showAttemptNo adKey=$adKey ${buildDebugState(poolKey)}")
            remove(adKey)
            if (adsManager.shouldReloadPlacement(adKey, reload)) {
                load(activity, adKey)
            }
            onAdClosed?.invoke(false)
            return
        }

        val currentTime = System.currentTimeMillis()
        val customInterval = customShowIntervals[poolKey]
        val interval = customInterval ?: adsManager.getTimeIntervalShowInterstitialAd().inWholeMilliseconds
        val lastShowTime = if (customInterval != null) {
            lastShowAdsTimeByKey[poolKey] ?: 0L
        } else {
            lastShowAdsTime
        }

        if (currentTime - lastShowTime < interval) {
            Log.d(
                TAG,
                "SHOW_BLOCKED_INTERVAL count=$showAttemptNo adKey=$adKey elapsedMs=${currentTime - lastShowTime} " +
                    "intervalMs=$interval ${buildDebugState(poolKey)}"
            )
            onAdClosed?.invoke(false)
            return
        }

        val ad = adsPool[poolKey]
        if (ad == null) {
            Log.d(TAG, "SHOW_BLOCKED_NO_LOADED_AD count=$showAttemptNo adKey=$adKey ${buildDebugState(poolKey)}")
            if (adsManager.shouldReloadPlacement(adKey, reload)) load(activity, adKey)
            if (shouldShow == null) showBackupIfAvailable(activity, onAdShowed, onAdClosed)
            else onAdClosed?.invoke(false)
            return
        }
        val shownSource = adSources[poolKey]

        val appCompatActivity = activity as? AppCompatActivity
        val isProcessResumed =
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val isActivityResumed =
            appCompatActivity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true

        if (!isProcessResumed || !isActivityResumed) {
            Log.d(
                TAG,
                "SHOW_BLOCKED_LIFECYCLE count=$showAttemptNo adKey=$adKey " +
                    "processResumed=$isProcessResumed activityResumed=$isActivityResumed ${buildDebugState(poolKey)}"
            )
            onAdClosed?.invoke(false)
            return
        }

        // Reserve the slot during the prepare delay as well as SDK presentation.
        isShowingAds = true
        adsManager.updateIsShowingFullScreenAd(true)
        showLoadingDialog(activity)

        ad.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                isShowingAds = true
                adsPool.remove(poolKey)
                adSources.remove(poolKey)
                val successNo = incrementCounter(showSuccessCountByKey, poolKey)
                Log.d(TAG, "SHOW_SUCCESS count=$successNo adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
                    AdMediationDebugLogger.logAdEvent(
                        event = "SHOW_SUCCESS",
                        format = "INTERSTITIAL",
                        adUnitId = shownSource?.unitId ?: adKey,
                        ad = ad,
                        sourceLabel = shownSource?.debugLabel()
                    )

                dismissLoadingDialog()
                adsManager.updateIsShowingFullScreenAd(true)
                onAdShowed?.invoke()
            }

            override fun onAdPaid(adValue: AdValue) {
                AdRevenueReporter.report(
                    adValue = adValue,
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: adKey,
                    ad = ad,
                    placement = shownSource?.debugLabel()
                )
            }

            override fun onAdImpression() {
                val impressionNo = incrementCounter(impressionCountByKey, poolKey)
                Log.d(TAG, "IMPRESSION count=$impressionNo adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
                    AdMediationDebugLogger.logAdEvent(
                        event = "IMPRESSION",
                        format = "INTERSTITIAL",
                        adUnitId = shownSource?.unitId ?: adKey,
                        ad = ad,
                        sourceLabel = shownSource?.debugLabel()
                    )
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingAds = false
                adsManager.updateIsShowingFullScreenAd(false)

                val showTime = System.currentTimeMillis()
                if (customInterval != null) {
                    lastShowAdsTimeByKey[poolKey] = showTime
                } else {
                    lastShowAdsTime = showTime
                }
                // Cross-format cooldown includes custom placements such as Splash.
                adsManager.updateLastTimeShowInterstitialAd(showTime)

                dismissLoadingDialog()
                val dismissNo = incrementCounter(dismissCountByKey, poolKey)
                Log.d(
                    TAG,
                    "DISMISSED count=$dismissNo adKey=$adKey poolKey=$poolKey reload=$reload " +
                        "showTimeMs=$showTime ${buildDebugState(poolKey)}"
                )

                if (adsManager.shouldReloadPlacement(adKey, reload)) {
                    Log.d(TAG, "RELOAD_AFTER_DISMISS adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
                    load(activity, adKey)
                }

                invokeOnAdClosedWhenResumed(activity, true, onAdClosed)
            }

            override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                isShowingAds = false
                adsPool.remove(poolKey)
                adSources.remove(poolKey)
                dismissLoadingDialog()
                adsManager.updateIsShowingFullScreenAd(false)
                Log.e(
                    TAG,
                    "SHOW_FAIL count=$showAttemptNo adKey=$adKey poolKey=$poolKey code=${adError.code} " +
                        "message=${adError.message} reload=$reload ${buildDebugState(poolKey)}"
                )
                AdMediationDebugLogger.logShowFailure(
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: adKey,
                    error = adError,
                    sourceLabel = shownSource?.debugLabel()
                )

                if (adsManager.shouldReloadPlacement(adKey, reload)) {
                    Log.d(TAG, "RELOAD_AFTER_SHOW_FAIL adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
                    load(activity, adKey)
                }

                Handler(Looper.getMainLooper()).post {
                    onAdClosed?.invoke(false)
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val latestIsProcessResumed =
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val latestIsActivityResumed =
                appCompatActivity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true

            if (!activity.isDestroyed && !activity.isFinishing && latestIsProcessResumed && latestIsActivityResumed &&
                appAllowShowAd && adsManager.canRequestAds && adsManager.canUsePlacement(adKey) &&
                adsManager.isResolvedAdStillEnabled(shownSource) &&
                shouldShow?.invoke() != false
            ) {
                Log.d(TAG, "SHOW_CALL count=$showAttemptNo adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
                adsManager.updateIsShowingFullScreenAd(true)
                onAdPresenting?.invoke()
                ad.show(activity)
                // Keep the loading window behind the SDK activity throughout its entrance.
                // It is dismissed by onAdShowedFullScreenContent (or a terminal callback).
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(
                    tpt.dev.monetization.R.anim.interstitial_slide_in_right,
                    tpt.dev.monetization.R.anim.interstitial_hold
                )
            } else {
                isShowingAds = false
                dismissLoadingDialog()
                adsManager.updateIsShowingFullScreenAd(false)
                Log.d(
                    TAG,
                    "SHOW_ABORT_BEFORE_CALL count=$showAttemptNo adKey=$adKey poolKey=$poolKey " +
                        "activityDestroyed=${activity.isDestroyed} activityFinishing=${activity.isFinishing} " +
                        "processResumed=$latestIsProcessResumed activityResumed=$latestIsActivityResumed ${buildDebugState(poolKey)}"
                )
                onAdClosed?.invoke(false)
            }
        }, 500)
    }

    private fun invokeOnAdClosedWhenResumed(
        activity: Activity,
        isShowed: Boolean,
        onAdClosed: ((Boolean) -> Unit)?,
        retryCount: Int = 0
    ) {
        val appCompatActivity = activity as? AppCompatActivity
        val isProcessResumed =
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val isActivityResumed =
            appCompatActivity?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true

        if (!activity.isDestroyed && !activity.isFinishing && isProcessResumed && isActivityResumed) {
            Handler(Looper.getMainLooper()).post {
                try {
                    Log.d(TAG, "invoke onAdClosed safely, isShowed=$isShowed")
                    onAdClosed?.invoke(isShowed)
                } catch (e: Exception) {
                    Log.e(TAG, "onAdClosed callback error: ${e.message}", e)
                }
            }
            return
        }

        if (retryCount >= 10) {
            Handler(Looper.getMainLooper()).post {
                try {
                    Log.d(TAG, "invoke onAdClosed fallback, isShowed=$isShowed")
                    onAdClosed?.invoke(isShowed)
                } catch (e: Exception) {
                    Log.e(TAG, "onAdClosed fallback error: ${e.message}", e)
                }
            }
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            invokeOnAdClosedWhenResumed(activity, isShowed, onAdClosed, retryCount + 1)
        }, 150)
    }

    private fun showBackupIfAvailable(
        activity: Activity,
        onAdShowed: (() -> Unit)?,
        onAdClosed: ((Boolean) -> Unit)?
    ) {
        val backupPoolKey = adsManager.resolveAdPoolKey(backupAdKey)
        if (backupAdKey.isEmpty() || !adsPool.containsKey(backupPoolKey)) {
            Log.d(TAG, "showBackupIfAvailable: no backup available")
            onAdClosed?.invoke(false)
            return
        }

        Log.d(TAG, "showBackupIfAvailable: showing backup $backupAdKey")
        show(activity, backupAdKey, true, onAdShowed, onAdClosed)
    }

    private fun showLoadingDialog(context: Context) {
        try {
            dismissLoadingDialog()
            prepareLoadingAdsDialog = PrepareLoadingAdsDialog(context)
            prepareLoadingAdsDialog?.show()
        } catch (e: Exception) {
            Log.e(TAG, "showLoadingDialog error: ${e.message}")
            prepareLoadingAdsDialog = null
        }
    }

    private fun dismissLoadingDialog() {
        try {
            if (prepareLoadingAdsDialog?.isShowing == true) {
                prepareLoadingAdsDialog?.dismiss()
            }
        } catch (e: Exception) {
            Log.e(TAG, "dismissLoadingDialog error: ${e.message}")
        } finally {
            prepareLoadingAdsDialog = null
        }
    }

    private fun incrementCounter(counters: MutableMap<String, Int>, adKey: String): Int {
        val nextValue = (counters[adKey] ?: 0) + 1
        counters[adKey] = nextValue
        return nextValue
    }

    private fun buildDebugState(poolKey: String): String {
        val now = System.currentTimeMillis()
        val customInterval = customShowIntervals[poolKey]
        val interval = customInterval ?: adsManager.getTimeIntervalShowInterstitialAd().inWholeMilliseconds
        val lastShowTime = if (customInterval != null) {
            lastShowAdsTimeByKey[poolKey] ?: 0L
        } else {
            lastShowAdsTime
        }
        val elapsed = if (lastShowTime == 0L) -1L else now - lastShowTime
        val remaining = if (lastShowTime == 0L) 0L else (interval - elapsed).coerceAtLeast(0L)

        return "state{" +
            "poolKey=$poolKey, " +
            "loaded=${adsPool.containsKey(poolKey)}, " +
            "loading=${loadingPools.contains(poolKey)}, " +
            "poolSize=${adsPool.size}, " +
            "loadingSize=${loadingPools.size}, " +
            "isShowingAds=$isShowingAds, " +
            "globalFullscreenShowing=${adsManager.checkIsShowingFullScreenAd()}, " +
            "customInterval=${customInterval != null}, " +
            "intervalMs=$interval, " +
            "lastShowMs=$lastShowTime, " +
            "elapsedMs=$elapsed, " +
            "remainingMs=$remaining, " +
            "backupKey=${backupAdKey.ifEmpty { "none" }}, " +
            "loadRequests=${loadRequestCountByKey[poolKey] ?: 0}, " +
            "loadSuccess=${loadSuccessCountByKey[poolKey] ?: 0}, " +
            "loadFails=${loadFailCountByKey[poolKey] ?: 0}, " +
            "showAttempts=${showAttemptCountByKey[poolKey] ?: 0}, " +
            "showSuccess=${showSuccessCountByKey[poolKey] ?: 0}, " +
            "impressions=${impressionCountByKey[poolKey] ?: 0}, " +
            "dismisses=${dismissCountByKey[poolKey] ?: 0}" +
            "}"
    }

    fun resetAdShowedTime() {
        lastShowAdsTime = 0
        lastShowAdsTimeByKey.clear()
    }

    fun clearAll() {
        adsPool.clear()
        adSources.clear()
        loadingPools.clear()
        pending.clear()
        retryOwners.clear()
    }

    fun remove(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        adsPool.remove(poolKey)
        adSources.remove(poolKey)
        loadingPools.remove(poolKey)
        pending.cancel(poolKey)
        retryOwners.remove(poolKey)
    }

    private fun clearInvalidCachedAd(poolKey: String) {
        if (adsPool.containsKey(poolKey) && !adsManager.isResolvedAdStillEnabled(adSources[poolKey])) {
            adsPool.remove(poolKey)
            adSources.remove(poolKey)
        }
    }

    private fun ResolvedAdRequest.debugLabel(): String {
        return "$placementKey/$adItemKey/$unitKey"
    }
}
