package tpt.dev.monetization.ads.interstitlaAd

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.PrepareLoadingAdsDialog
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager

class InterstitialAdUtils internal constructor(
    private val interstitialAdId: String,
    private val context: Context,
    private val adsManager: AdsManager,
    private val premiumManager: IPremiumManager,
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager,
    private val type: InterstitialType,
) : BaseAdUtils(premiumManager) {
    private var mInterstitialAd: InterstitialAd? = null
    private var mAdIsLoading: Boolean = false
    private var isStartShowingAd = false
    private var isLoadFailed: Boolean = false
    private var prepareLoadingAdsDialog: PrepareLoadingAdsDialog? = null
    private var loadedSource: ResolvedAdRequest? = null
    private var loadCallCount = 0
    private var loadRequestCount = 0
    private var loadSuccessCount = 0
    private var loadFailCount = 0
    private var showAttemptCount = 0
    private var showSuccessCount = 0
    private var impressionCount = 0
    private var dismissCount = 0
    private var showFailCount = 0

    companion object {
        private const val TAG = "CommonInterAdDebug"
        private const val SHOW_DELAY_MS = 500L
        private const val LOAD_AND_SHOW_TIMEOUT_MS = 2500L
        private const val LOAD_POLL_INTERVAL_MS = 100L
    }

    fun loadAd() {
        loadCallCount++
        Log.d(TAG, "LOAD_CALL count=$loadCallCount ${buildDebugState()}")

        if (!adsManager.isGlobalAdsEnabled()) {
            Log.d(TAG, "LOAD_SKIP_GLOBAL_DISABLED ${buildDebugState()}")
            return
        }
        if (!appAllowShowAd) {
            Log.d(TAG, "LOAD_SKIP_APP_NOT_ALLOW ${buildDebugState()}")
            return
        }
        if (!adsManager.canRequestAds) {
            Log.d(TAG, "LOAD_SKIP_CONSENT ${buildDebugState()}")
            return
        }
        if (!adsManager.canUsePlacement(interstitialAdId)) {
            mInterstitialAd = null
            loadedSource = null
            isLoadFailed = true
            Log.d(TAG, "LOAD_SKIP_PLACEMENT_DISABLED ${buildDebugState()}")
            return
        }
        if (mInterstitialAd != null) {
            Log.d(TAG, "LOAD_SKIP_ALREADY_LOADED ${buildDebugState()}")
            return
        }
        if (mAdIsLoading) {
            Log.d(TAG, "LOAD_SKIP_ALREADY_LOADING ${buildDebugState()}")
            return
        }

        val adRequests = adsManager.resolveAdRequests(interstitialAdId)
        if (adRequests.isEmpty()) {
            mInterstitialAd = null
            loadedSource = null
            isLoadFailed = true
            Log.d(TAG, "LOAD_SKIP_NO_ENABLED_UNIT ${buildDebugState()}")
            return
        }

        mAdIsLoading = true
        isLoadFailed = false
        loadRequestCount++
        Log.d(TAG, "LOAD_REQUEST count=$loadRequestCount ${buildDebugState()}")

        loadResolvedAd(adRequests, 0)
    }

    private fun loadResolvedAd(adRequests: List<ResolvedAdRequest>, index: Int) {
        val source = adRequests.getOrNull(index)
        if (source == null) {
            mInterstitialAd = null
            loadedSource = null
            mAdIsLoading = false
            isLoadFailed = true
            return
        }

        InterstitialAd.load(
            buildAdRequest(source.unitId),
            object : AdLoadCallback<InterstitialAd> {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loadFailCount++
                    Log.d(
                        TAG,
                        "LOAD_FAIL count=$loadFailCount source=${source.adItemKey} code=${adError.code} " +
                            "message=${adError.message} ${buildDebugState()}"
                    )
                    AdMediationDebugLogger.logLoadFailure(
                        format = "INTERSTITIAL",
                        adUnitId = source.unitId,
                        error = adError,
                        sourceLabel = source.debugLabel()
                    )
                    loadResolvedAd(adRequests, index + 1)
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    if (!adsManager.isResolvedAdStillEnabled(source)) {
                        loadResolvedAd(adRequests, index + 1)
                        return
                    }

                    mInterstitialAd = interstitialAd
                    loadedSource = source
                    mInterstitialAd?.setImmersiveMode(true)
                    mAdIsLoading = false
                    isLoadFailed = false
                    loadSuccessCount++
                    Log.d(TAG, "LOAD_SUCCESS count=$loadSuccessCount source=${source.adItemKey} ${buildDebugState()}")
                    AdMediationDebugLogger.logLoadSuccess(
                        format = "INTERSTITIAL",
                        adUnitId = source.unitId,
                        ad = interstitialAd,
                        sourceLabel = source.debugLabel()
                    )
                }
            }
        )
    }

    fun isAdAvailable(): Boolean {
        if (mInterstitialAd != null && !adsManager.isResolvedAdStillEnabled(loadedSource)) {
            mInterstitialAd = null
            loadedSource = null
        }
        return mInterstitialAd != null
    }

    fun isLoadFail(): Boolean = isLoadFailed

    fun getDebugState(): String = buildDebugState()

    private fun Activity.hideAdStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun Activity.restoreAdStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, window.decorView).show(
            WindowInsetsCompat.Type.systemBars()
        )
    }

    fun showAdOpenAds(
        activity: Activity,
        onAdsShowed: () -> Unit,
        onAdsClosed: () -> Unit
    ) {
        showAttemptCount++
        Log.d(TAG, "SHOW_OPEN_ATTEMPT count=$showAttemptCount activity=${activity.javaClass.simpleName} ${buildDebugState()}")

        if (isStartShowingAd || adsManager.checkIsShowingFullScreenAd()) {
            Log.d(TAG, "SHOW_OPEN_BLOCKED_ALREADY_SHOWING ${buildDebugState()}")
            onAdsClosed()
            return
        }
        if (!adsManager.isGlobalAdsEnabled() || !appAllowShowAd) {
            Log.d(TAG, "SHOW_OPEN_BLOCKED_APP_NOT_ALLOW ${buildDebugState()}")
            onAdsClosed()
            return
        }
        if (!adsManager.canRequestAds) {
            Log.d(TAG, "SHOW_OPEN_BLOCKED_CONSENT ${buildDebugState()}")
            onAdsClosed()
            return
        }
        if (isShowIntervalBlocked()) {
            Log.d(TAG, "SHOW_OPEN_BLOCKED_INTERVAL ${buildDebugState()}")
            onAdsClosed()
            return
        }
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            Log.d(TAG, "SHOW_OPEN_BLOCKED_LIFECYCLE ${buildDebugState()}")
            onAdsClosed()
            return
        }
        if (!adsManager.canUsePlacement(interstitialAdId)) {
            mInterstitialAd = null
            loadedSource = null
            onAdsClosed()
            return
        }
        if (!adsManager.isResolvedAdStillEnabled(loadedSource)) {
            mInterstitialAd = null
            loadedSource = null
            loadAd()
            onAdsClosed()
            return
        }

        val interstitialAd = mInterstitialAd
        if (interstitialAd == null) {
            Log.d(TAG, "SHOW_OPEN_NO_AD_LOAD_THEN_CONTINUE ${buildDebugState()}")
            loadAd()
            onAdsClosed()
            return
        }
        val shownSource = loadedSource

        interstitialAd.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                mInterstitialAd = null
                loadedSource = null
                isStartShowingAd = false
                adsManager.updateIsShowingFullScreenAd(false)
                adsManager.updateLastTimeShowInterstitialAd(System.currentTimeMillis())
                dismissLoadingDialog()
                dismissCount++
                Log.d(TAG, "DISMISSED count=$dismissCount source=open ${buildDebugState()}")
                onAdsClosed()
                activity.restoreAdStatusBar()
            }

            override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                mInterstitialAd = null
                loadedSource = null
                isStartShowingAd = false
                adsManager.updateIsShowingFullScreenAd(false)
                dismissLoadingDialog()
                showFailCount++
                Log.d(
                    TAG,
                    "SHOW_FAIL count=$showFailCount source=open code=${adError.code} " +
                        "message=${adError.message} ${buildDebugState()}"
                )
                AdMediationDebugLogger.logShowFailure(
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    error = adError,
                    sourceLabel = shownSource?.debugLabel()
                )
                onAdsClosed()
                activity.restoreAdStatusBar()
            }

            override fun onAdShowedFullScreenContent() {
                mInterstitialAd = null
                dismissLoadingDialog()
                showSuccessCount++
                Log.d(TAG, "SHOW_SUCCESS count=$showSuccessCount source=open ${buildDebugState()}")
                AdMediationDebugLogger.logAdEvent(
                    event = "SHOW_SUCCESS",
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    ad = interstitialAd,
                    sourceLabel = shownSource?.debugLabel()
                )
                onAdsShowed.invoke()
            }

            override fun onAdClicked() = Unit

            override fun onAdPaid(adValue: AdValue) {
                AdRevenueReporter.report(
                    adValue = adValue,
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    ad = interstitialAd,
                    placement = shownSource?.debugLabel()
                )
            }

            override fun onAdImpression() {
                impressionCount++
                Log.d(TAG, "IMPRESSION count=$impressionCount source=open ${buildDebugState()}")
                AdMediationDebugLogger.logAdEvent(
                    event = "IMPRESSION",
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    ad = interstitialAd,
                    sourceLabel = shownSource?.debugLabel()
                )
            }
        }

        isStartShowingAd = true
        adsManager.updateIsShowingFullScreenAd(true)
        showLoadingDialog(activity)

        Handler(Looper.getMainLooper()).postDelayed({
            if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                Log.d(TAG, "SHOW_CALL source=open ${buildDebugState()}")
                activity.hideAdStatusBar()
                interstitialAd.show(activity)
            } else {
                isStartShowingAd = false
                dismissLoadingDialog()
                adsManager.updateIsShowingFullScreenAd(false)
                Log.d(TAG, "SHOW_ABORT_BEFORE_CALL source=open ${buildDebugState()}")
                onAdsClosed()
            }
        }, SHOW_DELAY_MS)
    }

    fun showAd(
        activity: AppCompatActivity,
        onAdsShowed: (() -> Unit)? = null,
        onAdsClosed: (Boolean) -> Unit
    ) {
        showAttemptCount++
        Log.d(TAG, "SHOW_ATTEMPT count=$showAttemptCount activity=${activity.javaClass.simpleName} ${buildDebugState(activity)}")

        if (isStartShowingAd || adsManager.checkIsShowingFullScreenAd()) {
            Log.d(TAG, "SHOW_BLOCKED_ALREADY_SHOWING ${buildDebugState(activity)}")
            onAdsClosed(false)
            return
        }
        if (!adsManager.isGlobalAdsEnabled() || !appAllowShowAd) {
            Log.d(TAG, "SHOW_BLOCKED_APP_NOT_ALLOW ${buildDebugState(activity)}")
            onAdsClosed(false)
            return
        }
        if (!adsManager.canRequestAds) {
            Log.d(TAG, "SHOW_BLOCKED_CONSENT ${buildDebugState(activity)}")
            onAdsClosed(false)
            return
        }
        if (!adsManager.canUsePlacement(interstitialAdId)) {
            mInterstitialAd = null
            loadedSource = null
            Log.d(TAG, "SHOW_BLOCKED_PLACEMENT_DISABLED ${buildDebugState(activity)}")
            onAdsClosed(false)
            return
        }
        if (!adsManager.isResolvedAdStillEnabled(loadedSource)) {
            mInterstitialAd = null
            loadedSource = null
            Log.d(TAG, "SHOW_BLOCKED_CACHED_SOURCE_DISABLED ${buildDebugState(activity)}")
            loadAd()
            onAdsClosed(false)
            return
        }
        if (isShowIntervalBlocked()) {
            Log.d(TAG, "SHOW_BLOCKED_INTERVAL ${buildDebugState(activity)}")
            onAdsClosed(false)
            return
        }
        if (!isActivityReady(activity)) {
            Log.d(TAG, "SHOW_BLOCKED_LIFECYCLE ${buildDebugState(activity)}")
            onAdsClosed(false)
            return
        }

        val interstitialAd = mInterstitialAd
        if (interstitialAd != null) {
            showLoadedAd(activity, interstitialAd, onAdsShowed, onAdsClosed)
            return
        }

        isStartShowingAd = true
        adsManager.updateIsShowingFullScreenAd(true)
        showLoadingDialog(activity)

        if (!mAdIsLoading) {
            Log.d(TAG, "SHOW_NO_AD_START_LOAD_AND_WAIT ${buildDebugState(activity)}")
            loadAd()
        } else {
            Log.d(TAG, "SHOW_WAIT_EXISTING_LOAD ${buildDebugState(activity)}")
        }

        waitForAdThenShow(activity, onAdsShowed, onAdsClosed)
    }

    private fun showLoadedAd(
        activity: AppCompatActivity,
        interstitialAd: InterstitialAd,
        onAdsShowed: (() -> Unit)?,
        onAdsClosed: (Boolean) -> Unit
    ) {
        val shownSource = loadedSource
        interstitialAd.adEventCallback = object : InterstitialAdEventCallback {
            override fun onAdDismissedFullScreenContent() {
                mInterstitialAd = null
                loadedSource = null
                isStartShowingAd = false
                adsManager.updateIsShowingFullScreenAd(false)
                adsManager.updateLastTimeShowInterstitialAd(System.currentTimeMillis())
                dismissLoadingDialog()
                dismissCount++
                Log.d(TAG, "DISMISSED count=$dismissCount ${buildDebugState(activity)}")
                if (type != InterstitialType.OPEN) loadAd()
                onAdsClosed(true)
                activity.restoreAdStatusBar()
            }

            override fun onAdFailedToShowFullScreenContent(adError: FullScreenContentError) {
                mInterstitialAd = null
                loadedSource = null
                isStartShowingAd = false
                adsManager.updateIsShowingFullScreenAd(false)
                dismissLoadingDialog()
                showFailCount++
                Log.d(
                    TAG,
                    "SHOW_FAIL count=$showFailCount code=${adError.code} " +
                        "message=${adError.message} ${buildDebugState(activity)}"
                )
                AdMediationDebugLogger.logShowFailure(
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    error = adError,
                    sourceLabel = shownSource?.debugLabel()
                )
                if (type != InterstitialType.OPEN) loadAd()
                onAdsClosed(false)
                activity.restoreAdStatusBar()
            }

            override fun onAdShowedFullScreenContent() {
                mInterstitialAd = null
                dismissLoadingDialog()
                showSuccessCount++
                Log.d(TAG, "SHOW_SUCCESS count=$showSuccessCount ${buildDebugState(activity)}")
                AdMediationDebugLogger.logAdEvent(
                    event = "SHOW_SUCCESS",
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    ad = interstitialAd,
                    sourceLabel = shownSource?.debugLabel()
                )
                onAdsShowed?.invoke()
            }

            override fun onAdClicked() = Unit

            override fun onAdPaid(adValue: AdValue) {
                AdRevenueReporter.report(
                    adValue = adValue,
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    ad = interstitialAd,
                    placement = shownSource?.debugLabel()
                )
            }

            override fun onAdImpression() {
                impressionCount++
                Log.d(TAG, "IMPRESSION count=$impressionCount ${buildDebugState(activity)}")
                AdMediationDebugLogger.logAdEvent(
                    event = "IMPRESSION",
                    format = "INTERSTITIAL",
                    adUnitId = shownSource?.unitId ?: interstitialAdId,
                    ad = interstitialAd,
                    sourceLabel = shownSource?.debugLabel()
                )
            }
        }

        isStartShowingAd = true
        adsManager.updateIsShowingFullScreenAd(true)
        showLoadingDialog(activity)
        Log.d(TAG, "SHOW_READY_DELAY activity=${activity.javaClass.simpleName} ${buildDebugState(activity)}")

        Handler(Looper.getMainLooper()).postDelayed({
            if (isActivityReady(activity)) {
                Log.d(TAG, "SHOW_CALL activity=${activity.javaClass.simpleName} ${buildDebugState(activity)}")
                activity.hideAdStatusBar()
                interstitialAd.show(activity)
            } else {
                isStartShowingAd = false
                adsManager.updateIsShowingFullScreenAd(false)
                dismissLoadingDialog()
                Log.d(TAG, "SHOW_ABORT_BEFORE_CALL ${buildDebugState(activity)}")
                onAdsClosed(false)
            }
        }, SHOW_DELAY_MS)
    }

    private fun waitForAdThenShow(
        activity: AppCompatActivity,
        onAdsShowed: (() -> Unit)?,
        onAdsClosed: (Boolean) -> Unit,
        waitedMs: Long = 0L
    ) {
        val interstitialAd = mInterstitialAd
        if (interstitialAd != null) {
            Log.d(TAG, "WAIT_AD_LOADED waitedMs=$waitedMs activity=${activity.javaClass.simpleName} ${buildDebugState(activity)}")
            showLoadedAd(activity, interstitialAd, onAdsShowed, onAdsClosed)
            return
        }

        if (!mAdIsLoading || waitedMs >= LOAD_AND_SHOW_TIMEOUT_MS || !isActivityReady(activity)) {
            isStartShowingAd = false
            adsManager.updateIsShowingFullScreenAd(false)
            dismissLoadingDialog()
            Log.d(
                TAG,
                "WAIT_ABORT waitedMs=$waitedMs timeoutMs=$LOAD_AND_SHOW_TIMEOUT_MS " +
                    "activityReady=${isActivityReady(activity)} ${buildDebugState(activity)}"
            )
            onAdsClosed(false)
            return
        }

        Handler(Looper.getMainLooper()).postDelayed({
            waitForAdThenShow(activity, onAdsShowed, onAdsClosed, waitedMs + LOAD_POLL_INTERVAL_MS)
        }, LOAD_POLL_INTERVAL_MS)
    }

    private fun isShowIntervalBlocked(): Boolean {
        return System.currentTimeMillis() - adsManager.getLastTimeShowInterstitialAd() <
            adsManager.getTimeIntervalShowInterstitialAd().inWholeMilliseconds
    }

    private fun isActivityReady(activity: AppCompatActivity): Boolean {
        return !activity.isDestroyed &&
            !activity.isFinishing &&
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    }

    private fun showLoadingDialog(activity: Activity) {
        try {
            dismissLoadingDialog()
            prepareLoadingAdsDialog = PrepareLoadingAdsDialog(activity)
            prepareLoadingAdsDialog?.show()
        } catch (e: Exception) {
            prepareLoadingAdsDialog = null
            Log.d(TAG, "showLoadingDialog error: ${e.message}")
        }
    }

    private fun dismissLoadingDialog() {
        try {
            if (prepareLoadingAdsDialog?.isShowing == true) {
                prepareLoadingAdsDialog?.dismiss()
            }
        } catch (e: Exception) {
            Log.d(TAG, "dismissLoadingDialog error: ${e.message}")
        } finally {
            prepareLoadingAdsDialog = null
        }
    }

    private fun buildDebugState(activity: AppCompatActivity? = null): String {
        val now = System.currentTimeMillis()
        val lastShowTime = adsManager.getLastTimeShowInterstitialAd()
        val interval = adsManager.getTimeIntervalShowInterstitialAd().inWholeMilliseconds
        val elapsed = if (lastShowTime == 0L) -1L else now - lastShowTime
        val remaining = if (lastShowTime == 0L) 0L else (interval - elapsed).coerceAtLeast(0L)
        val activityState = activity?.lifecycle?.currentState?.name ?: "none"
        val processState = ProcessLifecycleOwner.get().lifecycle.currentState.name

        return "state{" +
            "adKey=$interstitialAdId, " +
            "type=$type, " +
            "source=${loadedSource?.adItemKey ?: "none"}, " +
            "loaded=${mInterstitialAd != null}, " +
            "loading=$mAdIsLoading, " +
            "isStartShowing=$isStartShowingAd, " +
            "isLoadFailed=$isLoadFailed, " +
            "globalAdsEnabled=${adsManager.isGlobalAdsEnabled()}, " +
            "appAllowShowAd=$appAllowShowAd, " +
            "canRequestAds=${adsManager.canRequestAds}, " +
            "globalFullscreenShowing=${adsManager.checkIsShowingFullScreenAd()}, " +
            "activityState=$activityState, " +
            "processState=$processState, " +
            "lastShowMs=$lastShowTime, " +
            "intervalMs=$interval, " +
            "elapsedMs=$elapsed, " +
            "remainingMs=$remaining, " +
            "loadCalls=$loadCallCount, " +
            "loadRequests=$loadRequestCount, " +
            "loadSuccess=$loadSuccessCount, " +
            "loadFails=$loadFailCount, " +
            "showAttempts=$showAttemptCount, " +
            "showSuccess=$showSuccessCount, " +
            "impressions=$impressionCount, " +
            "dismisses=$dismissCount, " +
            "showFails=$showFailCount" +
            "}"
    }

    private fun ResolvedAdRequest.debugLabel(): String {
        return "$placementKey/$adItemKey/$unitKey"
    }
}
