package tpt.dev.monetization.ads.nativeAd

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.remote.ResolvedAdRequest
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager


class SingleNativeAdUtils internal constructor(
    private val adsManager: AdsManager,
    private val premiumManager: IPremiumManager,
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {
    private companion object {
        const val TAG = "SingleNative"
    }

    fun loadAd(
        activity: Activity,
        adId: String,
        numberOfAdsToLoad: Int = 1,
        onLoadFailed: (stringFail: String?) -> Unit,
        onAdLoaded: (NativeAd) -> Unit
    ) {
        Log.d(TAG, "NATIVE_SINGLE_LOAD_CALL adKey=$adId activity=${activity.javaClass.simpleName}")
        if (!appAllowShowAd) {
            Log.d(TAG, "NATIVE_SINGLE_LOAD_SKIP_APP_NOT_ALLOW adKey=$adId")
            onLoadFailed("app not allow show ad")
            return
        }
        if (!adsManager.canRequestAds) {
            Log.d(TAG, "NATIVE_SINGLE_LOAD_SKIP_CONSENT adKey=$adId")
            onLoadFailed("can not request ads")
            return
        }

        if (!adsManager.canUsePlacement(adId)) {
            Log.d(TAG, "NATIVE_SINGLE_LOAD_SKIP_PLACEMENT_DISABLED adKey=$adId")
            onLoadFailed("placement disabled or missing")
            return
        }

        val adRequests = adsManager.resolveAdRequests(adId)
        if (adRequests.isEmpty()) {
            Log.d(TAG, "NATIVE_SINGLE_LOAD_SKIP_NO_ENABLED_UNIT adKey=$adId")
            onLoadFailed("no enabled remote ad unit")
            return
        }

        Log.d(TAG, "NATIVE_SINGLE_LOAD_START adKey=$adId candidates=${adRequests.size}")
        loadResolvedAd(
            activity = activity,
            adRequests = adRequests,
            index = 0,
            numberOfAdsToLoad = numberOfAdsToLoad,
            onLoadFailed = onLoadFailed,
            onAdLoaded = onAdLoaded
        )
    }

    private fun loadResolvedAd(
        activity: Activity,
        adRequests: List<ResolvedAdRequest>,
        index: Int,
        numberOfAdsToLoad: Int,
        onLoadFailed: (stringFail: String?) -> Unit,
        onAdLoaded: (NativeAd) -> Unit
    ) {
        val source = adRequests.getOrNull(index)
        if (source == null) {
            Log.d(TAG, "NATIVE_SINGLE_LOAD_EXHAUSTED")
            onLoadFailed("all remote ad units failed")
            return
        }

        Log.d(TAG, "NATIVE_SINGLE_LOAD_SOURCE_ATTEMPT index=$index source=${source.debugLabel()}")

        val videoOptions = VideoOptions
            .Builder()
            .setStartMuted(true)
            .build()

        val adRequest = NativeAdRequest
            .Builder(source.unitId, listOf(NativeAd.NativeAdType.NATIVE))
            .setVideoOptions(videoOptions)
            .build()

        val adCallback = object : NativeAdLoaderCallback {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(
                    TAG,
                    "NATIVE_SINGLE_LOAD_FAIL source=${source.debugLabel()} " +
                        "code=${adError.code} message=${adError.message}"
                )
                AdMediationDebugLogger.logLoadFailure(
                    format = "NATIVE",
                    adUnitId = source.unitId,
                    error = adError,
                    sourceLabel = source.debugLabel()
                )
                loadResolvedAd(
                    activity = activity,
                    adRequests = adRequests,
                    index = index + 1,
                    numberOfAdsToLoad = numberOfAdsToLoad,
                    onLoadFailed = onLoadFailed,
                    onAdLoaded = onAdLoaded
                )
            }

            override fun onNativeAdLoaded(ad: NativeAd) {
                // If this callback occurs after the activity is destroyed, you must call
                // destroy and return or you may get a memory leak.
                if (activity.isDestroyed || activity.isFinishing || activity.isChangingConfigurations) {
                    ad.destroy()
                    return
                }

                if (!adsManager.isResolvedAdStillEnabled(source)) {
                    Log.d(TAG, "NATIVE_SINGLE_SOURCE_DISABLED source=${source.debugLabel()}")
                    ad.destroy()
                    loadResolvedAd(
                        activity = activity,
                        adRequests = adRequests,
                        index = index + 1,
                        numberOfAdsToLoad = numberOfAdsToLoad,
                        onLoadFailed = onLoadFailed,
                        onAdLoaded = onAdLoaded
                    )
                    return
                }

                Log.d(TAG, "NATIVE_SINGLE_LOAD_SUCCESS source=${source.debugLabel()}")
                AdMediationDebugLogger.logLoadSuccess(
                    format = "NATIVE",
                    adUnitId = source.unitId,
                    ad = ad,
                    sourceLabel = source.debugLabel()
                )
                ad.adEventCallback = object : NativeAdEventCallback {
                    override fun onAdClicked() {
                        Log.d(TAG, "NATIVE_SINGLE_CLICK source=${source.debugLabel()}")
                        AdMediationDebugLogger.logAdEvent(
                            event = "CLICK",
                            format = "NATIVE",
                            adUnitId = source.unitId,
                            ad = ad,
                            sourceLabel = source.debugLabel()
                        )
                    }

                    override fun onAdPaid(adValue: AdValue) {
                        AdRevenueReporter.report(
                            adValue = adValue,
                            format = "NATIVE",
                            adUnitId = source.unitId,
                            ad = ad,
                            placement = source.debugLabel()
                        )
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "NATIVE_SINGLE_IMPRESSION source=${source.debugLabel()}")
                        AdMediationDebugLogger.logAdEvent(
                            event = "IMPRESSION",
                            format = "NATIVE",
                            adUnitId = source.unitId,
                            ad = ad,
                            sourceLabel = source.debugLabel()
                        )
                    }
                }
                onAdLoaded(ad)
            }
        }

        if (numberOfAdsToLoad > 1) {
            NativeAdLoader.load(adRequest, numberOfAdsToLoad, adCallback)
        } else {
            NativeAdLoader.load(adRequest, adCallback)
        }
    }

    private fun ResolvedAdRequest.debugLabel(): String {
        return "$placementKey/$adItemKey/$unitKey"
    }
}
