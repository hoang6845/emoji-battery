package tpt.dev.monetization.ads.nativeAd

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesView
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView
import tpt.dev.monetization.R

object NativeAdCalculator {
    private val initialVisibility = java.util.WeakHashMap<View, Int>()

    fun populateNativeAdView(
        nativeAd: NativeAd,
        nativeAdView: NativeAdView
    ) {
        val mediaView: MediaView? = nativeAdView.findViewById(R.id.ad_media)
        nativeAdView.headlineView = nativeAdView.findViewById(R.id.ad_headline)
        nativeAdView.bodyView = nativeAdView.findViewById(R.id.ad_body)
        nativeAdView.callToActionView = nativeAdView.findViewById(R.id.ad_call_to_action)
        nativeAdView.iconView = nativeAdView.findViewById(R.id.ad_app_icon)
        nativeAdView.starRatingView = nativeAdView.findViewById(R.id.ad_stars)
        nativeAdView.advertiserView = nativeAdView.findViewById(R.id.ad_advertiser)
        // Remember the layout's visibility before binding, including when a view is rebound.
        val hiddenAssets = listOfNotNull(
            nativeAdView.bodyView, nativeAdView.callToActionView, nativeAdView.iconView,
            nativeAdView.starRatingView, nativeAdView.advertiserView
        ).filter { initialVisibility.getOrPut(it) { it.visibility } == View.GONE }
        val adChoicesView: AdChoicesView? = nativeAdView.findViewById(R.id.ad_choices)
        if (adChoicesView != null) {
            nativeAdView.adChoicesView = adChoicesView
        }
        (nativeAdView.headlineView as? TextView)?.text = nativeAd.headline
        if (nativeAd.body == null) {
            nativeAdView.bodyView?.visibility = View.INVISIBLE
        } else {
            nativeAdView.bodyView?.visibility = View.VISIBLE
            (nativeAdView.bodyView as? TextView)?.text = nativeAd.body
        }
        if (nativeAd.callToAction == null) {
            nativeAdView.callToActionView?.visibility = View.INVISIBLE
        } else {
            nativeAdView.callToActionView?.visibility = View.VISIBLE
            (nativeAdView.callToActionView as? TextView)?.text =
                nativeAd.callToAction
            (nativeAdView.callToActionView as? Button)?.text =
                nativeAd.callToAction
        }
        if (nativeAd.icon == null) {
            nativeAdView.iconView?.visibility = View.INVISIBLE
        } else {
            (nativeAdView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
            nativeAdView.iconView?.visibility = View.VISIBLE
        }
        if (nativeAd.starRating == null) {
            nativeAdView.starRatingView?.visibility = View.INVISIBLE
        } else {
            (nativeAdView.starRatingView as? RatingBar)?.rating =
                nativeAd.starRating?.toFloat() ?: 0F
            nativeAdView.starRatingView?.visibility = View.VISIBLE
        }
        if (nativeAd.advertiser == null) {
            nativeAdView.advertiserView?.visibility = View.GONE
        } else {
            (nativeAdView.advertiserView as? TextView)?.text = nativeAd.advertiser
            nativeAdView.advertiserView?.visibility = View.VISIBLE
        }
        hiddenAssets.forEach { it.visibility = View.GONE }
        nativeAdView.registerNativeAd(nativeAd, mediaView)
    }
}
