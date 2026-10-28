package tpt.dev.monetization.ads.revenue

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import java.util.UUID

data class AdRevenueEvent(
    val eventId: String,
    val valueMicros: Long,
    val currencyCode: String,
    val adFormat: String,
    val adUnitId: String,
    val placement: String?,
    val adSourceName: String?,
    val adSourceId: String?,
    val adapterClassName: String?,
    val responseId: String?,
    val precision: Int,
    val adSourceInstanceName: String? = null,
    val adSourceInstanceId: String? = null,
    val mediationGroupName: String? = null,
    val mediationAbTestName: String? = null,
    val mediationAbTestVariant: String? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val value: Double get() = valueMicros / 1_000_000.0
}

/** One event per SDK paid callback, including separate impressions from banner refreshes. */
object AdRevenueReporter {
    @Volatile
    var listener: ((AdRevenueEvent) -> Unit)? = null

    @Volatile
    var debugLogging: Boolean = false

    fun report(
        adValue: AdValue,
        format: String,
        adUnitId: String,
        ad: Ad,
        placement: String? = null
    ) {
        // Revenue telemetry must never interrupt ad callbacks or navigation.
        runCatching {
            if (debugLogging) Log.d("AdMobPaidEvent", "callback=true format=$format " +
                "revenue=${adValue.valueMicros / 1_000_000.0} currency=${adValue.currencyCode} " +
                "precision=${adValue.precisionType}")
            val response = ad.getResponseInfo()
            val source = response.loadedAdSourceResponseInfo
            val extras = response.responseExtras
            val event = AdRevenueEvent(
                eventId = UUID.randomUUID().toString(),
                valueMicros = adValue.valueMicros,
                currencyCode = adValue.currencyCode,
                adFormat = format,
                adUnitId = adUnitId,
                placement = placement,
                adSourceName = source?.name,
                adSourceId = source?.id,
                adapterClassName = source?.adapterClassName ?: response.adapterClassName,
                responseId = response.responseId,
                precision = when (adValue.precisionType.toString()) {
                    "ESTIMATED" -> 1
                    "PUBLISHER_PROVIDED" -> 2
                    "PRECISE" -> 3
                    else -> 0
                },
                adSourceInstanceName = source?.instanceName,
                adSourceInstanceId = source?.instanceId,
                mediationGroupName = extras.getString("mediation_group_name"),
                mediationAbTestName = extras.getString("mediation_ab_test_name"),
                mediationAbTestVariant = extras.getString("mediation_ab_test_variant")
            )
            val destination = listener
            if (destination == null) {
                Log.w("AdMobPaidEvent", "TRACKER_NOT_REGISTERED format=$format")
            } else destination(event)
        }.onFailure { Log.e("AdMobPaidEvent", "REPORT_FAILED format=$format error=${it.javaClass.simpleName}") }
    }
}
