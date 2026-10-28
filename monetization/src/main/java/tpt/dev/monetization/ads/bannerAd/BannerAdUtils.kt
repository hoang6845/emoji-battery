package tpt.dev.monetization.ads.bannerAd

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import tpt.dev.monetization.ads.revenue.AdRevenueReporter
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.util.UUID
import tpt.dev.monetization.ads.base.BaseAdUtils
import tpt.dev.monetization.ads.AdsManager
import tpt.dev.monetization.ads.debug.AdMediationDebugLogger
import tpt.dev.monetization.ads.ump.GoogleMobileAdsConsentManager
import tpt.dev.monetization.common.premium.IPremiumManager

class BannerAdUtils internal constructor(
    private val premiumManager: IPremiumManager,
    private val googleMobileAdsConsentManager: GoogleMobileAdsConsentManager
) : BaseAdUtils(premiumManager) {
    fun loadAdaptiveBanner(
        adViewContainer: FrameLayout,
        activity: Activity,
        adId: String,
        onAdLoaded: (() -> Unit)? = null,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        val adView = AdView(activity)
        adViewContainer.removeAllViews()
        adViewContainer.addView(adView)
        val adSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getAdSizeAndroid11(adViewContainer, activity)
        } else {
            getAdSize(adViewContainer, activity)
        }

        val adRequest = BannerAdRequest.Builder(adId, adSize).build()
        loadBannerAdView(adView, adRequest, onAdLoaded, onAdFailedToLoad)
    }

    fun loadCollapsibleBanner(
        adViewContainer: FrameLayout,
        adPlacement: BannerPlacement,
        requestId: String?,
        activity: Activity,
        adId: String,
        onAdLoaded: (() -> Unit)? = null,
        onAdFailedToLoad: (() -> Unit)? = null
    ): String {
        val adView = AdView(activity)
        adViewContainer.removeAllViews()
        adViewContainer.addView(adView)
        val adSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getAdSizeAndroid11(adViewContainer, activity)
        } else {
            getAdSize(adViewContainer, activity)
        }

        val nonNullRequestId = requestId ?: UUID.randomUUID().toString()

        val extras = Bundle()
        extras.putString("collapsible", adPlacement.value)
        extras.putString("collapsible_request_id", nonNullRequestId)

        val adRequest = BannerAdRequest
            .Builder(adId, adSize)
            .setGoogleExtrasBundle(extras)
            .build()

        loadBannerAdView(adView, adRequest, onAdLoaded, onAdFailedToLoad)

        return nonNullRequestId
    }

    fun loadBannerWithSize(
        adViewContainer: FrameLayout,
        adSize: AdSize,
        context: Context,
        adId: String,
        onAdLoaded: (() -> Unit)? = null,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        val adView = AdView(context)
        adViewContainer.removeAllViews()
        adViewContainer.addView(adView)

        loadBannerAdView(
            adView,
            BannerAdRequest.Builder(adId, adSize).build(),
            onAdLoaded,
            onAdFailedToLoad
        )
    }

    private fun loadBannerAdView(
        adView: AdView,
        adRequest: BannerAdRequest,
        onAdLoaded: (() -> Unit)? = null,
        onAdFailedToLoad: (() -> Unit)? = null
    ) {
        if (!AdsManager.getInstance().canRequestAds) {
            onAdFailedToLoad?.invoke()
            return
        }

        adView.loadAd(
            adRequest,
            object : AdLoadCallback<BannerAd> {
            override fun onAdLoaded(ad: BannerAd) {
                AdMediationDebugLogger.logLoadSuccess(
                    format = "BANNER",
                    adUnitId = adRequest.adUnitId,
                    ad = ad
                )
                ad.adEventCallback = object : BannerAdEventCallback {
                    override fun onAdPaid(adValue: AdValue) {
                        AdRevenueReporter.report(
                            adValue = adValue,
                            format = "BANNER",
                            adUnitId = adRequest.adUnitId,
                            ad = ad,
                            placement = null
                        )
                    }

                    override fun onAdImpression() {
                        AdMediationDebugLogger.logAdEvent(
                            event = "IMPRESSION",
                            format = "BANNER",
                            adUnitId = adRequest.adUnitId,
                            ad = ad
                        )
                    }

                    override fun onAdClicked() {
                        AdMediationDebugLogger.logAdEvent(
                            event = "CLICK",
                            format = "BANNER",
                            adUnitId = adRequest.adUnitId,
                            ad = ad
                        )
                    }
                }
                adView.post {
                    onAdLoaded?.invoke()
                }
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                AdMediationDebugLogger.logLoadFailure(
                    format = "BANNER",
                    adUnitId = adRequest.adUnitId,
                    error = adError
                )
                adView.post {
                    onAdFailedToLoad?.invoke()
                }
            }
        })
    }

    /**
     * Get adaptive banner AdSize for Android 11 and above
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun getAdSizeAndroid11(adViewContainer: FrameLayout, activity: Activity): AdSize {
        val windowMetrics = activity.windowManager.currentWindowMetrics
        val bounds = windowMetrics.bounds

        var adWidthPixels = adViewContainer.width.toFloat()

        if (adWidthPixels == 0f) {
            adWidthPixels = bounds.width().toFloat()
        }

        val density = activity.resources.displayMetrics.density
        val adWidth = (adWidthPixels / density).toInt()

        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }

    /**
     * Get adaptive banner AdSize for Android 10 and below
     */
    @Suppress("DEPRECATION")
    private fun getAdSize(adViewContainer: FrameLayout, activity: Activity): AdSize {
        val display = activity.windowManager.defaultDisplay
        val outMetrics = DisplayMetrics()
        display.getMetrics(outMetrics)
        val density = outMetrics.density
        var adWidthPixels = adViewContainer.width.toFloat()

        if (adWidthPixels == 0f) {
            adWidthPixels = outMetrics.widthPixels.toFloat()
        }

        val adWidth = (adWidthPixels / density).toInt()
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
    }
}
