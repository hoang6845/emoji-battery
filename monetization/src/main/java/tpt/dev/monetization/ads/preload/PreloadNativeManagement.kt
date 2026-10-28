package tpt.dev.monetization.ads.preload

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import java.lang.ref.WeakReference
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager
import kotlin.random.Random

/**
 * Preload Native Management
 * Quản lý việc preload và hiển thị native ads với cơ chế pool
 */
class PreloadNativeManagement(
    private val context: Context,
    private val adsManager: AdsManager,
    private val premiumManager: IPremiumManager,
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {

    // Pool chứa các native ads đã load
    private val adsPool: MutableMap<String, NativeAd> = HashMap()
    private val adSources: MutableMap<String, ResolvedAdRequest> = HashMap()
    private val showingPools: MutableSet<String> = mutableSetOf()

    // Set chứa các key đang trong quá trình load
    private val loadingPools: MutableSet<String> = mutableSetOf()
    private val pending = PendingAdLoads<NativeAd>()
    private val handler = Handler(Looper.getMainLooper())
    private val retryOwners = mutableMapOf<String, WeakReference<LifecycleOwner>>()

    // Key backup ads
    private var backupAdKey: String = ""

    companion object {
        private const val TAG = "PreloadNative"
        private const val DEFAULT_DELAY_WATERFALL = 300L
    }

    /**
     * Load native ad với ad key
     */
    fun load(
        activity: Activity,
        adKey: String,
        numberOfAds: Int = 1,
        callback: ((NativeAd?) -> Unit)? = null
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { load(activity, adKey, numberOfAds, callback) }
            return
        }
        val poolKey = adsManager.resolveAdPoolKey(adKey)

        val shouldReload = adsManager.shouldReloadNativePlacement(adKey, true)
        Log.d(
            TAG,
            "NATIVE_LOAD_CALL adKey=$adKey poolKey=$poolKey shouldReload=$shouldReload " +
                    buildDebugState(poolKey)
        )
        if (!canShow(adKey, shouldReload)) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_ALREADY_SHOWING adKey=$adKey poolKey=$poolKey " +
                        "shouldReload=$shouldReload ${buildDebugState(poolKey)}"
            )
            callback?.invoke(null)
            return
        }

        if (!appAllowShowAd) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_APP_NOT_ALLOW adKey=$adKey poolKey=$poolKey ${
                    buildDebugState(poolKey)
                }"
            )
            callback?.invoke(null)
            return
        }

        if (!adsManager.canRequestAds) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_CONSENT adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}"
            )
            callback?.invoke(null)
            return
        }

        // Kiểm tra đã load rồi
        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_PLACEMENT_DISABLED adKey=$adKey poolKey=$poolKey ${
                    buildDebugState(poolKey)
                }"
            )
            remove(adKey)
            callback?.invoke(null)
            return
        }

        clearInvalidCachedAd(poolKey)

        if (adsPool.containsKey(poolKey)) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_ALREADY_LOADED adKey=$adKey poolKey=$poolKey ${
                    buildDebugState(poolKey)
                }"
            )
            callback?.invoke(adsPool[poolKey])
            return
        }

        // Kiểm tra đang load
        if (loadingPools.contains(poolKey)) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_ALREADY_LOADING adKey=$adKey poolKey=$poolKey ${
                    buildDebugState(poolKey)
                }"
            )
            callback?.let { pending.subscribe(poolKey, it) }
            return
        }

        // Thêm vào loading pool
        val adRequests = adsManager.resolveAdRequests(adKey)
        if (adRequests.isEmpty()) {
            Log.d(
                TAG,
                "NATIVE_LOAD_SKIP_NO_ENABLED_UNIT adKey=$adKey poolKey=$poolKey ${
                    buildDebugState(poolKey)
                }"
            )
            callback?.invoke(null)
            return
        }

        loadingPools.add(poolKey)
        val token = pending.begin(poolKey)
        callback?.let { pending.subscribe(poolKey, it) }
        Log.d(
            TAG,
            "NATIVE_LOAD_START adKey=$adKey poolKey=$poolKey candidates=${adRequests.size} " +
                    buildDebugState(poolKey)
        )

        loadResolvedAd(activity, poolKey, adRequests, 0, token, 0)
    }

    fun loadForOwner(activity: Activity, adKey: String, owner: LifecycleOwner, callback: ((NativeAd?) -> Unit)? = null) {
        retryOwners[adsManager.resolveAdPoolKey(adKey)] = WeakReference(owner)
        load(activity, adKey, callback = callback)
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
        adRequests: List<ResolvedAdRequest>,
        index: Int,
        token: Long,
        retries: Int
    ) {
        if (!pending.isCurrent(poolKey, token)) return
        if (!appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(poolKey)) {
            completeLoad(poolKey, token, null)
            return
        }
        val source = adRequests.getOrNull(index)
        if (source == null) {
            val wait = AdRetryPolicy.delayMillis(retries)
            if (wait == null) {
                completeLoad(poolKey, token, null)
            } else {
                Log.d(TAG, "NATIVE_RETRY_SCHEDULED poolKey=$poolKey delayMs=$wait")
                handler.postDelayed({
                    if (!pending.isCurrent(poolKey, token)) return@postDelayed
                    if (!retryOwnerActive(activity, poolKey) ||
                        !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                        !canShow(poolKey, adsManager.shouldReloadNativePlacement(poolKey, true))) {
                        completeLoad(poolKey, token, null)
                    } else {
                        loadResolvedAd(activity, poolKey, adsManager.resolveAdRequests(poolKey), 0, token, retries + 1)
                    }
                }, wait)
            }
            return
        }

        Log.d(
            TAG,
            "NATIVE_LOAD_SOURCE_ATTEMPT poolKey=$poolKey index=$index source=${source.debugLabel()} " +
                    buildDebugState(poolKey)
        )

        val videoOptions = VideoOptions.Builder()
            .setStartMuted(true)
            .build()

        val adRequest = NativeAdRequest.Builder(
            source.unitId,
            listOf(NativeAd.NativeAdType.NATIVE)
        )
            .setVideoOptions(videoOptions)
            .build()

        val adCallback = object : NativeAdLoaderCallback {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                handler.post {
                    if (!pending.isCurrent(poolKey, token)) return@post
                    Log.e(
                        TAG,
                        "NATIVE_LOAD_FAIL poolKey=$poolKey source=${source.debugLabel()} " +
                                "code=${adError.code} message=${adError.message} " +
                                buildDebugState(poolKey)
                    )
                    AdMediationDebugLogger.logLoadFailure(
                        format = "NATIVE",
                        adUnitId = source.unitId,
                        error = adError,
                        sourceLabel = source.debugLabel()
                    )
                    loadResolvedAd(activity, poolKey, adRequests, index + 1, token, retries)
                }
            }

            override fun onNativeAdLoaded(nativeAd: NativeAd) {
                handler.post {
                    if (!pending.isCurrent(poolKey, token) || !appAllowShowAd ||
                        !adsManager.canRequestAds || !adsManager.canUsePlacement(poolKey)) {
                        nativeAd.destroy()
                        completeLoad(poolKey, token, null)
                        return@post
                    }

                    Log.d(
                        TAG,
                        "NATIVE_LOAD_CALLBACK poolKey=$poolKey source=${source.debugLabel()} ${
                            buildDebugState(poolKey)
                        }"
                    )
                    if (!adsManager.isResolvedAdStillEnabled(source)) {
                        Log.d(
                            TAG,
                            "NATIVE_LOAD_SOURCE_DISABLED poolKey=$poolKey source=${source.debugLabel()}"
                        )
                        nativeAd.destroy()
                        loadResolvedAd(activity, poolKey, adRequests, index + 1, token, retries)
                        return@post
                    }

                    adsPool[poolKey]?.destroy()
                    adsPool[poolKey] = nativeAd
                    adSources[poolKey] = source
                    loadingPools.remove(poolKey)
                    nativeAd.adEventCallback = object : NativeAdEventCallback {
                        override fun onAdClicked() {
                            Log.d(
                                TAG,
                                "NATIVE_CLICK poolKey=$poolKey source=${source.debugLabel()} ${
                                    buildDebugState(poolKey)
                                }"
                            )
                            AdMediationDebugLogger.logAdEvent(
                                event = "CLICK",
                                format = "NATIVE",
                                adUnitId = source.unitId,
                                ad = nativeAd,
                                sourceLabel = source.debugLabel()
                            )
                        }

                        override fun onAdPaid(adValue: AdValue) {
                            AdRevenueReporter.report(
                                adValue = adValue,
                                format = "NATIVE",
                                adUnitId = source.unitId,
                                ad = nativeAd,
                                placement = source.debugLabel()
                            )
                        }

                        override fun onAdImpression() {
                            Log.d(
                                TAG,
                                "NATIVE_IMPRESSION poolKey=$poolKey source=${source.debugLabel()} ${
                                    buildDebugState(poolKey)
                                }"
                            )
                            AdMediationDebugLogger.logAdEvent(
                                event = "IMPRESSION",
                                format = "NATIVE",
                                adUnitId = source.unitId,
                                ad = nativeAd,
                                sourceLabel = source.debugLabel()
                            )
                        }
                    }
                    Log.d(
                        TAG,
                        "NATIVE_LOAD_SUCCESS poolKey=$poolKey source=${source.debugLabel()} ${
                            buildDebugState(poolKey)
                        }"
                    )
                    AdMediationDebugLogger.logLoadSuccess(
                        format = "NATIVE",
                        adUnitId = source.unitId,
                        ad = nativeAd,
                        sourceLabel = source.debugLabel()
                    )
                    completeLoad(poolKey, token, nativeAd)
                }
            }
        }

        // A pool slot owns one ad; loading several would overwrite ads.
        try {
            NativeAdLoader.load(adRequest, adCallback)
        } catch (error: RuntimeException) {
            Log.e(TAG, "Native load failed poolKey=$poolKey", error)
            loadResolvedAd(activity, poolKey, adRequests, index + 1, token, retries)
        }
    }

    private fun completeLoad(poolKey: String, token: Long, ad: NativeAd?) {
        if (!pending.isCurrent(poolKey, token)) return
        loadingPools.remove(poolKey)
        pending.complete(poolKey, token, ad)
    }

    fun cancelLoadCallback(adKey: String, callback: (NativeAd?) -> Unit) {
        pending.unsubscribe(adsManager.resolveAdPoolKey(adKey), callback)
    }

    /**
     * Load nhiều native ads với waterfall delay
     */
    fun loadWithWaterfall(
        activity: Activity,
        adKeys: List<String>,
        delayMs: Long = DEFAULT_DELAY_WATERFALL,
        callback: (() -> Unit)? = null
    ) {
        Log.d(
            TAG,
            "NATIVE_WATERFALL_LOAD_CALL activity=${activity.javaClass.simpleName} keys=$adKeys delayMs=$delayMs"
        )
        if (adKeys.isEmpty()) {
            Log.d(TAG, "NATIVE_WATERFALL_LOAD_SKIP_EMPTY")
            callback?.invoke()
            return
        }

        val appCompatActivity = activity as? AppCompatActivity ?: run {
            Log.d(
                TAG,
                "NATIVE_WATERFALL_LOAD_SKIP_NOT_APPCOMPAT activity=${activity.javaClass.name}"
            )
            return
        }
        var loadedCount = 0
        val totalCount = adKeys.size

        adKeys.forEachIndexed { index, adKey ->
            appCompatActivity.lifecycleScope.launch {
                // Delay waterfall với random
                val randomDelay = delayMs + Random.nextInt(0, 500)
                delay(index * randomDelay)

                Log.d(
                    TAG,
                    "NATIVE_WATERFALL_LOAD_ITEM_START index=$index total=$totalCount adKey=$adKey"
                )
                load(activity, adKey) {
                    loadedCount++
                    Log.d(
                        TAG,
                        "NATIVE_WATERFALL_LOAD_ITEM_DONE loadedCount=$loadedCount total=$totalCount adKey=$adKey"
                    )
                    if (loadedCount == totalCount) {
                        Log.d(TAG, "NATIVE_WATERFALL_LOAD_COMPLETE keys=$adKeys")
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
        Log.d(TAG, "NATIVE_BACKUP_ENABLE backupKey=$backupKey")
        load(activity, backupKey)
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

    fun canShow(adKey: String, shouldReload: Boolean): Boolean {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        return shouldReload || !showingPools.contains(poolKey)
    }

    fun markShowing(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        showingPools.add(poolKey)
        Log.d(TAG, "NATIVE_MARK_SHOWING adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
    }

    fun markHidden(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        showingPools.remove(poolKey)
        Log.d(TAG, "NATIVE_MARK_HIDDEN adKey=$adKey poolKey=$poolKey ${buildDebugState(poolKey)}")
    }

    /**
     * Get native ad từ pool
     */
    fun getNativeAd(adKey: String, removeAfterGet: Boolean = false): NativeAd? {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        clearInvalidCachedAd(poolKey)

        val ad = adsPool[poolKey]
        if (removeAfterGet) {
            adsPool.remove(poolKey)
            adSources.remove(poolKey)
        }
        return ad
    }

    /**
     * Get native ad hoặc backup
     */
    fun getNativeAdOrBackup(adKey: String, removeAfterGet: Boolean = false): NativeAd? {
        var ad = getNativeAd(adKey, removeAfterGet)
        if (ad == null && backupAdKey.isNotEmpty()) {
            Log.d(TAG, "getNativeAdOrBackup: using backup $backupAdKey")
            ad = getNativeAd(backupAdKey, removeAfterGet)
        }
        return ad
    }

    /**
     * Destroy native ad
     */
    fun destroy(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        adsPool[poolKey]?.destroy()
        adsPool.remove(poolKey)
        adSources.remove(poolKey)
        loadingPools.remove(poolKey)
        showingPools.remove(poolKey)
        pending.cancel(poolKey)
        retryOwners.remove(poolKey)
    }

    /**
     * Clear tất cả ads trong pool
     */
    fun clearAll() {
        adsPool.values.forEach { it.destroy() }
        adsPool.clear()
        adSources.clear()
        loadingPools.clear()
        showingPools.clear()
        pending.clear()
        retryOwners.clear()
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
        showingPools.remove(poolKey)
        pending.cancel(poolKey)
        retryOwners.remove(poolKey)
    }

    private fun clearInvalidCachedAd(poolKey: String) {
        if (adsPool.containsKey(poolKey) && !adsManager.isResolvedAdStillEnabled(adSources[poolKey])) {
            adsPool[poolKey]?.destroy()
            adsPool.remove(poolKey)
            adSources.remove(poolKey)
            showingPools.remove(poolKey)
            Log.d(TAG, "NATIVE_CLEAR_INVALID poolKey=$poolKey ${buildDebugState(poolKey)}")
        }
    }

    private fun buildDebugState(poolKey: String): String {
        return "state{" +
                "loaded=${adsPool.containsKey(poolKey)}, " +
                "loading=${loadingPools.contains(poolKey)}, " +
                "showing=${showingPools.contains(poolKey)}, " +
                "poolSize=${adsPool.size}, " +
                "loadingSize=${loadingPools.size}, " +
                "showingSize=${showingPools.size}, " +
                "source=${adSources[poolKey]?.debugLabel() ?: "none"}" +
                "}"
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
