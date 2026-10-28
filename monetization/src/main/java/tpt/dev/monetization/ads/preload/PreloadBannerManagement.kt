package tpt.dev.monetization.ads.preload

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.os.Handler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager
import java.util.UUID
import kotlin.random.Random

/**
 * Preload Banner Management
 * Quản lý việc preload và hiển thị banner ads với cơ chế pool
 */
class PreloadBannerManagement(
    private val context: Context,
    private val adsManager: AdsManager,
    private val premiumManager: IPremiumManager,
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {

    // Pool chứa các banner ads đã load
    private val adsPool: MutableMap<String, AdView> = HashMap()
    private val adSources: MutableMap<String, ResolvedAdRequest> = HashMap()
    
    // Set chứa các key đang trong quá trình load
    private val handler = Handler(Looper.getMainLooper())
    private val loadTokens = mutableMapOf<String, Long>()
    private var loadSequence = 0L
    private val loadingPools: MutableSet<String> = mutableSetOf()
    private val collapsibleWaitingCallbacks: MutableMap<String, MutableList<(AdView?, String) -> Unit>> =
        HashMap()
    
    // Key backup ads
    private var backupAdKey: String = ""

    companion object {
        private const val TAG = "PreloadBanner"
        private const val DEFAULT_DELAY_WATERFALL = 300L
    }

    /**
     * Load adaptive banner ad
     */
    fun loadAdaptiveBanner(
        activity: Activity,
        adKey: String,
        callback: ((AdView?) -> Unit)? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { loadAdaptiveBanner(activity, adKey, callback) }
            return
        }
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        Log.d(TAG, "loadAdaptiveBanner: $adKey -> $poolKey")

        if (!appAllowShowAd) {
            Log.d(TAG, "loadAdaptiveBanner: $adKey - app not allow show ad")
            callback?.invoke(null)
            return
        }

        if (!adsManager.canRequestAds) {
            Log.d(TAG, "loadAdaptiveBanner: $adKey - cannot request ads")
            callback?.invoke(null)
            return
        }

        // Kiểm tra đã load rồi
        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(TAG, "loadAdaptiveBanner: $adKey - placement disabled or missing")
            remove(adKey)
            callback?.invoke(null)
            return
        }

        clearInvalidCachedAd(poolKey)

        if (adsPool.containsKey(poolKey)) {
            Log.d(TAG, "loadAdaptiveBanner: $adKey - already loaded")
            callback?.invoke(adsPool[poolKey])
            return
        }

        // Kiểm tra đang load
        if (loadingPools.contains(poolKey)) {
            Log.d(TAG, "loadAdaptiveBanner: $adKey - already loading")
            callback?.invoke(null)
            return
        }

        // Thêm vào loading pool
        val adRequests = adsManager.resolveAdRequests(adKey)
        if (adRequests.isEmpty()) {
            Log.d(TAG, "loadAdaptiveBanner: $adKey - no enabled remote ad unit")
            callback?.invoke(null)
            return
        }

        loadingPools.add(poolKey)
        val token = ++loadSequence
        loadTokens[poolKey] = token
        loadAdaptiveResolvedBanner(activity, poolKey, adRequests, 0, callback, token, 0)
    }

    private fun loadAdaptiveResolvedBanner(
        activity: Activity,
        poolKey: String,
        adRequests: List<ResolvedAdRequest>,
        index: Int,
        callback: ((AdView?) -> Unit)?,
        token: Long,
        retries: Int
    ) {
        if (loadTokens[poolKey] != token) return
        val source = adRequests.getOrNull(index)
        if (source == null) {
            val wait = AdRetryPolicy.delayMillis(retries)
            if (wait == null) {
                loadingPools.remove(poolKey)
                activity.runOnUiThread { callback?.invoke(null) }
            } else {
                handler.postDelayed({
                    if (loadTokens[poolKey] != token) return@postDelayed
                    if (!canRetry(activity, poolKey)) {
                        loadingPools.remove(poolKey)
                        callback?.invoke(null)
                    } else {
                        loadAdaptiveResolvedBanner(activity, poolKey, adsManager.resolveAdRequests(poolKey), 0, callback, token, retries + 1)
                    }
                }, wait)
            }
            return
        }

        // Create AdView
        val adView = AdView(activity)

        // Get adaptive size
        val adSize = getAdaptiveAdSize(activity)
        val adRequest = BannerAdRequest.Builder(source.unitId, adSize).build()

        // Set ad listener
        adView.loadAd(
            adRequest,
            object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                handler.post {
                    Log.d(TAG, "onAdLoaded: ${source.placementKey}/${source.adItemKey}")
                    AdMediationDebugLogger.logLoadSuccess(
                        format = "BANNER",
                        adUnitId = source.unitId,
                        ad = ad,
                        sourceLabel = source.debugLabel()
                    )
                    if (loadTokens[poolKey] != token || activity.isDestroyed || activity.isFinishing ||
                        !appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(poolKey)) {
                        adView.destroy()
                        if (loadTokens[poolKey] == token) {
                            loadingPools.remove(poolKey)
                            activity.runOnUiThread { callback?.invoke(null) }
                        }
                        return@post
                    }
                    if (!adsManager.isResolvedAdStillEnabled(source)) {
                        adView.destroy()
                        loadAdaptiveResolvedBanner(activity, poolKey, adRequests, index + 1, callback, token, retries)
                        return@post
                    }
                    ad.adEventCallback = buildBannerEventCallback(
                        format = "BANNER",
                        adUnitId = source.unitId,
                        sourceLabel = source.debugLabel(),
                        ad = ad
                    )

                    adsPool[poolKey]?.destroy()
                    adsPool[poolKey] = adView
                    adSources[poolKey] = source
                    loadingPools.remove(poolKey)
                    activity.runOnUiThread {
                        callback?.invoke(adView)
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                handler.post {
                    Log.e(
                        TAG,
                        "onAdFailedToLoad: ${source.placementKey}/${source.adItemKey} - ${adError.message}"
                    )
                    adView.destroy()
                    if (loadTokens[poolKey] != token) return@post
                    AdMediationDebugLogger.logLoadFailure(
                        format = "BANNER",
                        adUnitId = source.unitId,
                        error = adError,
                        sourceLabel = source.debugLabel()
                    )
                    loadAdaptiveResolvedBanner(activity, poolKey, adRequests, index + 1, callback, token, retries)
                }
            }
        })
    }

    /**
     * Load collapsible banner ad
     */
    fun loadCollapsibleBanner(
        activity: Activity,
        adKey: String,
        placement: BannerPlacement = BannerPlacement.BOTTOM,
        requestId: String? = null,
        callback: ((AdView?, String) -> Unit)? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { loadCollapsibleBanner(activity, adKey, placement, requestId, callback) }
            return
        }
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        Log.d(TAG, "loadCollapsibleBanner: $adKey -> $poolKey")

        if (!appAllowShowAd) {
            Log.d(TAG, "loadCollapsibleBanner: $adKey - app not allow show ad")
            activity.runOnUiThread { callback?.invoke(null, "") }
            return
        }

        if (!adsManager.canRequestAds) {
            Log.d(TAG, "loadCollapsibleBanner: $adKey - cannot request ads")
            activity.runOnUiThread { callback?.invoke(null, "") }
            return
        }

        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(TAG, "loadCollapsibleBanner: $adKey - placement disabled or missing")
            remove(adKey)
            activity.runOnUiThread { callback?.invoke(null, "") }
            return
        }

        clearInvalidCachedAd(poolKey)

        adsPool[poolKey]?.let { loadedAd ->
            activity.runOnUiThread {
                callback?.invoke(loadedAd, requestId.orEmpty())
            }
            return
        }

        callback?.let {
            collapsibleWaitingCallbacks
                .getOrPut(poolKey) { mutableListOf() }
                .add(it)
        }

        // Kiểm tra đang load
        if (loadingPools.contains(poolKey)) {
            return
        }

        // Thêm vào loading pool
        val adRequests = adsManager.resolveAdRequests(adKey)
        if (adRequests.isEmpty()) {
            Log.d(TAG, "loadCollapsibleBanner: $adKey - no enabled remote ad unit")
            val callbacks = collapsibleWaitingCallbacks.remove(poolKey).orEmpty()
            activity.runOnUiThread {
                callbacks.forEach { waitingCallback -> waitingCallback.invoke(null, "") }
            }
            return
        }

        loadingPools.add(poolKey)
        val token = ++loadSequence
        loadTokens[poolKey] = token
        loadCollapsibleResolvedBanner(activity, poolKey, adRequests, 0, placement, requestId, token, 0)
    }

    private fun loadCollapsibleResolvedBanner(
        activity: Activity,
        poolKey: String,
        adRequests: List<ResolvedAdRequest>,
        index: Int,
        placement: BannerPlacement,
        requestId: String?,
        token: Long,
        retries: Int
    ) {
        if (loadTokens[poolKey] != token) return
        val source = adRequests.getOrNull(index)
        if (source == null) {
            val wait = AdRetryPolicy.delayMillis(retries)
            if (wait != null) {
                handler.postDelayed({
                    if (loadTokens[poolKey] != token) return@postDelayed
                    if (canRetry(activity, poolKey)) {
                        loadCollapsibleResolvedBanner(activity, poolKey, adsManager.resolveAdRequests(poolKey), 0, placement, requestId, token, retries + 1)
                    } else {
                        finishCollapsibleLoad(activity, poolKey, requestId)
                    }
                }, wait)
            } else {
                finishCollapsibleLoad(activity, poolKey, requestId)
            }
            return
        }

        // Create AdView
        val adView = AdView(activity)

        // Get adaptive size
        val adSize = getAdaptiveAdSize(activity)

        // Generate request ID
        val nonNullRequestId = requestId ?: UUID.randomUUID().toString()

        // Build collapsible extras
        val extras = Bundle()
        extras.putString("collapsible", placement.value)
        extras.putString("collapsible_request_id", nonNullRequestId)

        val adRequest = BannerAdRequest.Builder(source.unitId, adSize)
            .setGoogleExtrasBundle(extras)
            .build()

        // Set ad listener
        adView.loadAd(
            adRequest,
            object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                handler.post {
                    Log.d(TAG, "onAdLoaded: ${source.placementKey}/${source.adItemKey} (collapsible)")
                    AdMediationDebugLogger.logLoadSuccess(
                        format = "BANNER_COLLAPSIBLE",
                        adUnitId = source.unitId,
                        ad = ad,
                        sourceLabel = source.debugLabel()
                    )
                    if (loadTokens[poolKey] != token || activity.isDestroyed || activity.isFinishing ||
                        !appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(poolKey)) {
                        adView.destroy()
                        if (loadTokens[poolKey] == token) {
                            loadingPools.remove(poolKey)
                            finishCollapsibleLoad(activity, poolKey, requestId)
                        }
                        return@post
                    }
                    if (!adsManager.isResolvedAdStillEnabled(source)) {
                        adView.destroy()
                        loadCollapsibleResolvedBanner(
                            activity,
                            poolKey,
                            adRequests,
                            index + 1,
                            placement,
                            nonNullRequestId,
                            token,
                            retries
                        )
                        return@post
                    }
                    ad.adEventCallback = buildBannerEventCallback(
                        format = "BANNER_COLLAPSIBLE",
                        adUnitId = source.unitId,
                        sourceLabel = source.debugLabel(),
                        ad = ad
                    )

                    adsPool[poolKey]?.destroy()
                    adsPool[poolKey] = adView
                    adSources[poolKey] = source
                    loadingPools.remove(poolKey)
                    val callbacks = collapsibleWaitingCallbacks.remove(poolKey).orEmpty()
                    activity.runOnUiThread {
                        callbacks.forEach { waitingCallback ->
                            waitingCallback.invoke(adView, nonNullRequestId)
                        }
                    }
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                handler.post {
                    Log.e(
                        TAG,
                        "onAdFailedToLoad: ${source.placementKey}/${source.adItemKey} - ${adError.message}"
                    )
                    adView.destroy()
                    if (loadTokens[poolKey] != token) return@post
                    AdMediationDebugLogger.logLoadFailure(
                        format = "BANNER_COLLAPSIBLE",
                        adUnitId = source.unitId,
                        error = adError,
                        sourceLabel = source.debugLabel()
                    )
                    loadCollapsibleResolvedBanner(
                        activity,
                        poolKey,
                        adRequests,
                        index + 1,
                        placement,
                        nonNullRequestId,
                        token,
                        retries
                    )
                }
            }
        })
    }

    private fun canRetry(activity: Activity, key: String) = !activity.isDestroyed && !activity.isFinishing &&
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
        appAllowShowAd && adsManager.canRequestAds && adsManager.canUsePlacement(key)

    private fun finishCollapsibleLoad(activity: Activity, key: String, requestId: String?) {
        loadingPools.remove(key)
        val callbacks = collapsibleWaitingCallbacks.remove(key).orEmpty()
        activity.runOnUiThread { callbacks.forEach { it(null, requestId.orEmpty()) } }
    }

    fun showCollapsibleBanner(
        activity: Activity,
        adKey: String,
        container: FrameLayout,
        placement: BannerPlacement = BannerPlacement.BOTTOM,
        reload: Boolean = false,
        canShow: () -> Boolean = { true },
        callback: ((Boolean) -> Unit)? = null
    ) {
        if (!appAllowShowAd) {
            callback?.invoke(false)
            return
        }

        if (!adsManager.canUsePlacement(adKey)) {
            remove(adKey)
            callback?.invoke(false)
            return
        }

        fun showLoadedAd(): Boolean {
            if (!canShow() || !appAllowShowAd || !adsManager.canUsePlacement(adKey)) return false
            val shown = showBanner(
                adKey = adKey,
                container = container,
                removeAfterShow = true
            )
            if (shown && adsManager.shouldReloadPlacement(adKey, reload)) {
                loadCollapsibleBanner(
                    activity,
                    adKey,
                    placement
                )
            }
            return shown
        }

        if (isLoaded(adKey)) {
            activity.runOnUiThread {
                val shown = showLoadedAd()
                callback?.invoke(shown)
            }
            return
        }

        loadCollapsibleBanner(
            activity,
            adKey = adKey,
            placement = placement
        ) { adView, _ ->
            if (adView == null) {
                callback?.invoke(false)
                return@loadCollapsibleBanner
            }

            val shown = showLoadedAd()
            callback?.invoke(shown)
        }
    }

    /**
     * Load nhiều banner ads với waterfall delay
     */
    fun loadWithWaterfall(
        activity: Activity,
        adKeys: List<String>,
        delayMs: Long = DEFAULT_DELAY_WATERFALL,
        callback: (() -> Unit)? = null
    ) {
        if (adKeys.isEmpty()) {
            callback?.invoke()
            return
        }

        val appCompatActivity = activity as? AppCompatActivity ?: return
        var loadedCount = 0
        val totalCount = adKeys.size

        adKeys.forEachIndexed { index, adKey ->
            appCompatActivity.lifecycleScope.launch {
                // Delay waterfall với random
                val randomDelay = delayMs + Random.nextInt(0, 500)
                delay(index * randomDelay)

                loadAdaptiveBanner(activity, adKey) {
                    loadedCount++
                    if (loadedCount == totalCount) {
                        callback?.invoke()
                    }
                }
            }
        }
    }

    /**
     * Enable backup ad
     */
    fun enableBackup(activity: Activity, backupKey: String) {
        this.backupAdKey = backupKey
        loadAdaptiveBanner(activity, backupKey)
    }

    /**
     * Kiểm tra ad đã load chưa
     */
    fun isLoaded(adKey: String): Boolean {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        clearInvalidCachedAd(poolKey)
        return adsPool.containsKey(poolKey)
    }

    /**
     * Kiểm tra ad đang load
     */
    fun isLoading(adKey: String): Boolean {
        return loadingPools.contains(adsManager.resolveAdPoolKey(adKey))
    }

    /**
     * Show banner ad vào container
     */
    fun showBanner(
        adKey: String,
        container: FrameLayout,
        removeAfterShow: Boolean = false
    ): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            container.post {
                showBanner(adKey, container, removeAfterShow)
            }
            return true
        }

        val poolKey = adsManager.resolveAdPoolKey(adKey)
        if (!adsManager.canUsePlacement(adKey)) {
            remove(adKey)
            return false
        }

        clearInvalidCachedAd(poolKey)

        val adView = adsPool[poolKey]
        if (adView == null) {
            Log.d(TAG, "showBanner: $adKey - ad not loaded")
            return false
        }

        // Remove from parent if exists
        (adView.parent as? FrameLayout)?.removeView(adView)

        // Add to container
        container.removeAllViews()
        container.addView(adView)

        if (removeAfterShow) {
            adsPool.remove(poolKey)
            adSources.remove(poolKey)
        }

        Log.d(TAG, "showBanner: $adKey - shown successfully")
        return true
    }

    /**
     * Show banner hoặc backup
     */
    fun showBannerOrBackup(
        adKey: String,
        container: FrameLayout,
        removeAfterShow: Boolean = false
    ): Boolean {
        var success = showBanner(adKey, container, removeAfterShow)
        if (!success && backupAdKey.isNotEmpty()) {
            Log.d(TAG, "showBannerOrBackup: using backup $backupAdKey")
            success = showBanner(backupAdKey, container, removeAfterShow)
        }
        return success
    }

    /**
     * Get adaptive ad size
     */
    private fun getAdaptiveAdSize(activity: Activity): AdSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getAdSizeAndroid11(activity)
        } else {
            getAdSizeBeforeAndroid11(activity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun getAdSizeAndroid11(activity: Activity): AdSize {
        val windowMetrics = activity.windowManager.currentWindowMetrics
        val bounds = windowMetrics.bounds
        val adWidthPixels = bounds.width().toFloat()
        val density = activity.resources.displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    @Suppress("DEPRECATION")
    private fun getAdSizeBeforeAndroid11(activity: Activity): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        val adWidthPixels = outMetrics.widthPixels.toFloat()
        val density = outMetrics.density
        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    /**
     * Destroy banner ad
     */
    fun destroy(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        adsPool[poolKey]?.destroy()
        adsPool.remove(poolKey)
        adSources.remove(poolKey)
        loadingPools.remove(poolKey)
        loadTokens.remove(poolKey)
        collapsibleWaitingCallbacks.remove(poolKey)
    }

    /**
     * Clear tất cả ads trong pool
     */
    fun clearAll() {
        adsPool.values.forEach { it.destroy() }
        adsPool.clear()
        adSources.clear()
        loadingPools.clear()
        loadTokens.clear()
        collapsibleWaitingCallbacks.clear()
    }

    /**
     * Remove ad khỏi pool
     */
    fun remove(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        adsPool[poolKey]?.destroy()
        adsPool.remove(poolKey)
        adSources.remove(poolKey)
        loadingPools.remove(poolKey)
        loadTokens.remove(poolKey)
        collapsibleWaitingCallbacks.remove(poolKey)
    }

    private fun clearInvalidCachedAd(poolKey: String) {
        if (adsPool.containsKey(poolKey) && !adsManager.isResolvedAdStillEnabled(adSources[poolKey])) {
            adsPool[poolKey]?.destroy()
            adsPool.remove(poolKey)
            adSources.remove(poolKey)
        }
    }

    private fun buildBannerEventCallback(
        format: String,
        adUnitId: String,
        sourceLabel: String,
        ad: BannerAd
    ): BannerAdEventCallback {
        return object : BannerAdEventCallback {
            override fun onAdPaid(adValue: AdValue) {
                AdRevenueReporter.report(
                    adValue = adValue,
                    format = format,
                    adUnitId = adUnitId,
                    ad = ad,
                    placement = sourceLabel
                )
            }

            override fun onAdImpression() {
                AdMediationDebugLogger.logAdEvent(
                    event = "IMPRESSION",
                    format = format,
                    adUnitId = adUnitId,
                    ad = ad,
                    sourceLabel = sourceLabel
                )
            }

            override fun onAdClicked() {
                AdMediationDebugLogger.logAdEvent(
                    event = "CLICK",
                    format = format,
                    adUnitId = adUnitId,
                    ad = ad,
                    sourceLabel = sourceLabel
                )
            }
        }
    }

    private fun ResolvedAdRequest.debugLabel(): String {
        return "$placementKey/$adItemKey/$unitKey"
    }

    /**
     * Get số lượng ads đã load
     */
    fun getLoadedCount(): Int {
        return adsPool.size
    }

    /**
     * Get số lượng ads đang load
     */
    fun getLoadingCount(): Int {
        return loadingPools.size
    }
}

/**
 * Banner placement enum
 */
enum class BannerPlacement(val value: String) {
    TOP("top"),
    BOTTOM("bottom")
}
