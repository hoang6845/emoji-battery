package tpt.dev.monetization.ads.base

import android.os.Bundle
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import tpt.dev.monetization.common.premium.IPremiumManager

open class BaseAdUtils internal constructor(
    private val premiumManager: IPremiumManager,
) {
    internal fun buildAdRequest(adUnitId: String): AdRequest =
        AdRequest.Builder(adUnitId).build()

    internal fun buildAdRequest(adUnitId: String, extras: Bundle): AdRequest =
        AdRequest.Builder(adUnitId)
            .setGoogleExtrasBundle(extras)
            .build()

    /**
     * Check if app allows showing ads
     * Returns true if user is NOT premium (free user can see ads)
     * Returns false if user is premium (premium user should not see ads)
     */
    internal val appAllowShowAd: Boolean
        get() = !premiumManager.isSubscribed()

}
