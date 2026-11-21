package tpt.dev.monetization.ads.preload

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
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

class PreloadRewardedManagement(
    val context: Context,
    val adsManager: AdsManager,
    val premiumManager: IPremiumManager,
    val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {

    private val adsPool: MutableMap<String, RewardedAd> = HashMap()
    private val adSources: MutableMap<String, ResolvedAdRequest> = HashMap()
    private val loadingPools: MutableSet<String> = mutableSetOf()
    private val pending = PendingAdLoads<Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var backupAdKey: String = ""

    var isShowingAds = false
        private set

    private var prepareLoadingAdsDialog: PrepareLoadingAdsDialog? = null

    companion object {
        private const val TAG = "PreloadRewarded"
        private const val DEFAULT_DELAY_WATERFALL = 300L
    }

    fun load(activity: Activity, adKey: String, callback: (() -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { load(activity, adKey, callback) }
            return
        }
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        Log.d(TAG, "load: $adKey -> $poolKey")

        if (!appAllowShowAd) {
            Log.d(TAG, "load: $adKey - app not allow show ad")
            callback?.invoke()
            return
        }

        if (!adsManager.canRequestAds) {
            Log.d(TAG, "load: $adKey - cannot request ads")
            callback?.invoke()
            return
        }

        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(TAG, "load: $adKey - placement disabled or missing")
            remove(adKey)
            callback?.invoke()
            return
        }

        clearInvalidCachedAd(poolKey)

        if (adsPool.containsKey(poolKey)) {
            Log.d(TAG, "load: $adKey - already loaded")
            callback?.invoke()
            return
        }

        if (loadingPools.contains(poolKey)) {
            Log.d(TAG, "load: $adKey - already loading")
            callback?.let { listener -> pending.subscribe(poolKey) { listener() } }
            return
        }

        val adRequests = adsManager.resolveAdRequests(adKey)
        if (adRequests.isEmpty()) {
            Log.d(TAG, "load: $adKey - no enabled remote ad unit")
            callback?.invoke()
            return
        }

        loadingPools.add(poolKey)
        val token = pending.begin(poolKey)
        callback?.let { listener -> pending.subscribe(poolKey) { listener() } }
        loadResolvedAd(poolKey, adRequests, 0, token)
    }

    private fun loadResolvedAd(
        poolKey: String,
        adRequests: List<ResolvedAdRequest>,
        index: Int,
        token: Long
    ) {
        if (!pending.isCurrent(poolKey, token)) return
        if (!appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(poolKey)) {
            loadingPools.remove(poolKey)
            pending.complete(poolKey, token, Unit)
            return
        }
        val source = adRequests.getOrNull(index)
        if (source == null) {
            loadingPools.remove(poolKey)
            pending.complete(poolKey, token, Unit)
            return
        }

        RewardedAd.load(
            buildAdRequest(source.unitId),
            object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    handler.post {
                        if (!pending.isCurrent(poolKey, token)) return@post
                        if (!appAllowShowAd || !adsManager.canRequestAds || !adsManager.canUsePlacement(poolKey)) {
                            loadingPools.remove(poolKey)
                            pending.complete(poolKey, token, Unit)
                            return@post
                        }
                        Log.d(TAG, "onAdLoaded: ${source.placementKey}/${source.adItemKey}")
                        if (!adsManager.isResolvedAdStillEnabled(source)) {
                            loadResolvedAd(poolKey, adRequests, index + 1, token)
                            return@post
                        }

                        adsPool[poolKey] = rewardedAd
                        adSources[poolKey] = source
                        rewardedAd.setImmersiveMode(true)
                        loadingPools.remove(poolKey)
                        AdMediationDebugLogger.logLoadSuccess(
                            format = "REWARDED",
                            adUnitId = source.unitId,
                            ad = rewardedAd,
                            sourceLabel = source.debugLabel()
                        )
                        pending.complete(poolKey, token, Unit)
                    }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    handler.post {
                        Log.e(
                            TAG,
                            "onAdFailedToLoad: ${source.placementKey}/${source.adItemKey} - ${loadAdError.message}"
                        )
                        AdMediationDebugLogger.logLoadFailure(
                            format = "REWARDED",
                            adUnitId = source.unitId,
                            error = loadAdError,
                            sourceLabel = source.debugLabel()
                        )
                        loadResolvedAd(poolKey, adRequests, index + 1, token)
                    }
                }
            }
        )
    }

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
                val randomDelay = delayMs + Random.nextInt(0, 500)
                delay(index * randomDelay)

                load(activity, adKey) {
                    loadedCount++
                    if (loadedCount == totalCount) {
                        callback?.invoke()
                    }
                }
            }
        }
    }

    fun enableBackup(activity: Activity, backupKey: String) {
        this.backupAdKey = backupKey
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

    fun show(
        activity: Activity,
        adKey: String,
        reload: Boolean = true,
        onAdShowed: (() -> Unit)? = null,
        onAdClosed: (() -> Unit)? = null,
        onRewarded: ((RewardItem) -> Unit)? = null,
        onLoadFailed: (() -> Unit)? = null,
        shouldShow: (() -> Boolean)? = null,
        onAdPresenting: (() -> Unit)? = null
    ) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        Log.d(TAG, "show: $adKey -> $poolKey")

        if (shouldShow?.invoke() == false || !adsManager.canRequestAds || adsManager.checkIsShowingFullScreenAd() || isShowingAds) {
            Log.d(TAG, "show: $adKey - already showing ads")
            onLoadFailed?.invoke()
            return
        }

        if (!appAllowShowAd) {
            Log.d(TAG, "show: $adKey - app not allow show ad")
            onLoadFailed?.invoke()
            return
        }

        if (!adsManager.canUsePlacement(adKey)) {
            Log.d(TAG, "show: $adKey - placement disabled or missing")
            remove(adKey)
            onLoadFailed?.invoke()
            return
        }

        if (!adsManager.isResolvedAdStillEnabled(adSources[poolKey])) {
            Log.d(TAG, "show: $adKey - cached source disabled")
            remove(adKey)
            if (adsManager.shouldReloadPlacement(adKey, reload)) {
                load(activity, adKey)
            }
            onLoadFailed?.invoke()
            return
        }

        val ad = adsPool[poolKey]
        if (ad == null) {
            Log.d(TAG, "show: $adKey - ad not loaded, try backup")
            showBackupIfAvailable(activity, onAdShowed, onAdClosed, onRewarded, onLoadFailed)
            return
        }
        val shownSource = adSources[poolKey]

        isShowingAds = true
        adsManager.updateIsShowingFullScreenAd(true)
        showLoadingDialog(activity)

        ad.adEventCallback = object : RewardedAdEventCallback {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "onAdShowedFullScreenContent: $adKey")
                AdMediationDebugLogger.logAdEvent(
                    event = "SHOW_SUCCESS",
                    format = "REWARDED",
                    adUnitId = shownSource?.unitId ?: adKey,
                    ad = ad,
                    sourceLabel = shownSource?.debugLabel()
                )
                isShowingAds = true
                adsPool.remove(poolKey)
                adSources.remove(poolKey)
                dismissLoadingDialog()
                onAdShowed?.invoke()
                adsManager.updateIsShowingFullScreenAd(true)

                if (adsManager.shouldReloadPlacement(adKey, reload)) {
                    load(activity, adKey)
                }
            }

            override fun onAdPaid(adValue: AdValue) {
                AdRevenueReporter.report(
                    adValue = adValue,
                    format = "REWARDED",
                    adUnitId = shownSource?.unitId ?: adKey,
                    ad = ad,
                    placement = shownSource?.debugLabel()
                )
            }

            override fun onAdImpression() {
                AdMediationDebugLogger.logAdEvent(
                    event = "IMPRESSION",
                    format = "REWARDED",
                    adUnitId = shownSource?.unitId ?: adKey,
                    ad = ad,
                    sourceLabel = shownSource?.debugLabel()
                )
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "onAdDismissedFullScreenContent: $adKey")
                isShowingAds = false
                adsManager.updateIsShowingFullScreenAd(false)
                dismissLoadingDialog()
                onAdClosed?.invoke()
            }

            override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                Log.e(TAG, "onAdFailedToShowFullScreenContent: $adKey - ${adError.message}")
                AdMediationDebugLogger.logShowFailure(
                    format = "REWARDED",
                    adUnitId = shownSource?.unitId ?: adKey,
                    error = adError,
                    sourceLabel = shownSource?.debugLabel()
                )
                isShowingAds = false
                adsPool.remove(poolKey)
                adSources.remove(poolKey)
                dismissLoadingDialog()
                adsManager.updateIsShowingFullScreenAd(false)

                showBackupIfAvailable(activity, onAdShowed, onAdClosed, onRewarded, onLoadFailed)

                if (adsManager.shouldReloadPlacement(adKey, reload)) {
                    load(activity, adKey)
                }
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (!activity.isDestroyed && !activity.isFinishing && appAllowShowAd && adsManager.canRequestAds &&
                adsManager.canUsePlacement(adKey) && adsManager.isResolvedAdStillEnabled(shownSource) &&
                (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
                    ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) != false && shouldShow?.invoke() != false) {
                dismissLoadingDialog()
                onAdPresenting?.invoke()
                ad.show(activity) { rewardItem ->
                    Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                    onRewarded?.invoke(rewardItem)
                }
            } else {
                isShowingAds = false
                adsManager.updateIsShowingFullScreenAd(false)
                dismissLoadingDialog()
                onLoadFailed?.invoke()
            }
        }, 500)
    }

    private fun showBackupIfAvailable(
        activity: Activity,
        onAdShowed: (() -> Unit)?,
        onAdClosed: (() -> Unit)?,
        onRewarded: ((RewardItem) -> Unit)?,
        onLoadFailed: (() -> Unit)?
    ) {
        if (backupAdKey.isEmpty() || !isLoaded(backupAdKey)) {
            Log.d(TAG, "showBackupIfAvailable: no backup available")
            onLoadFailed?.invoke()
            return
        }

        Log.d(TAG, "showBackupIfAvailable: showing backup $backupAdKey")
        show(activity, backupAdKey, true, onAdShowed, onAdClosed, onRewarded, onLoadFailed)
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

    fun clearAll() {
        adsPool.clear()
        adSources.clear()
        loadingPools.clear()
        pending.clear()
    }

    fun remove(adKey: String) {
        val poolKey = adsManager.resolveAdPoolKey(adKey)
        adsPool.remove(poolKey)
        adSources.remove(poolKey)
        loadingPools.remove(poolKey)
        pending.cancel(poolKey)
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
