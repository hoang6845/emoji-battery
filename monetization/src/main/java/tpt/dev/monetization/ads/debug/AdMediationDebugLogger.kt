package tpt.dev.monetization.ads.debug

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.MediationAdError
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo

private const val TAG = "AdMediationDebug"
private const val GMA_NEXT_GEN_DOMAIN = "com.google.android.libraries.ads.mobile.sdk"

fun ResponseInfo?.isPangle(): Boolean {
    if (this == null) return false
    return adapterClassName.isPangleAdapterName() ||
        loadedAdSourceResponseInfo.isPangle() ||
        adSourceResponses.any { it.isPangle() }
}

object AdMediationDebugLogger {
    fun logLoadSuccess(
        format: String,
        adUnitId: String,
        ad: Ad,
        sourceLabel: String? = null
    ) {
        logResponseInfo(
            title = "LOAD_SUCCESS",
            format = format,
            adUnitId = adUnitId,
            responseInfo = ad.getResponseInfo(),
            sourceLabel = sourceLabel
        )
    }

    fun logLoadFailure(
        format: String,
        adUnitId: String,
        error: LoadAdError,
        sourceLabel: String? = null
    ) {
        val responseInfo = error.responseInfo
        val message = buildString {
            appendHeader("LOAD_FAIL", format, adUnitId, sourceLabel)
            appendLine()
            appendLine("FINAL_ERROR:")
            appendLine("code=${error.code}")
            appendLine("domain=$GMA_NEXT_GEN_DOMAIN")
            appendLine("message=${error.message.cleanForLog()}")
            appendResponseInfo(responseInfo)
        }
        Log.e(TAG, message)
    }

    fun logShowFailure(
        format: String,
        adUnitId: String,
        error: FullScreenContentError,
        sourceLabel: String? = null
    ) {
        val mediationError = error.mediationAdError
        val message = buildString {
            appendHeader("SHOW_FAIL", format, adUnitId, sourceLabel)
            appendLine()
            appendLine("FINAL_ERROR:")
            appendLine("code=${error.code}")
            appendLine("domain=${mediationError?.domain ?: GMA_NEXT_GEN_DOMAIN}")
            appendLine("message=${error.message.cleanForLog()}")
            if (mediationError != null) {
                appendLine()
                appendLine("MEDIATION_ERROR:")
                appendMediationError(mediationError)
            }
        }
        Log.e(TAG, message)
    }

    fun logAdEvent(
        event: String,
        format: String,
        adUnitId: String,
        ad: Ad,
        sourceLabel: String? = null
    ) {
        logResponseInfo(
            title = event,
            format = format,
            adUnitId = adUnitId,
            responseInfo = ad.getResponseInfo(),
            sourceLabel = sourceLabel
        )
    }

    fun logAdInspectorClosed(errorMessage: String?) {
        if (errorMessage == null) {
            Log.d(TAG, "AD_INSPECTOR_CLOSED success")
        } else {
            Log.e(TAG, "AD_INSPECTOR_CLOSED error=$errorMessage")
        }
    }

    private fun logResponseInfo(
        title: String,
        format: String,
        adUnitId: String,
        responseInfo: ResponseInfo?,
        sourceLabel: String?
    ) {
        val message = buildString {
            appendHeader(title, format, adUnitId, sourceLabel)
            appendResponseInfo(responseInfo)
        }
        Log.d(TAG, message)
    }
}

private fun StringBuilder.appendHeader(
    title: String,
    format: String,
    adUnitId: String,
    sourceLabel: String?
) {
    appendLine(title)
    appendLine("FORMAT=$format")
    appendLine("AD_UNIT=$adUnitId")
    if (!sourceLabel.isNullOrBlank()) {
        appendLine("SOURCE=$sourceLabel")
    }
}

private fun StringBuilder.appendResponseInfo(responseInfo: ResponseInfo?) {
    appendLine()
    appendLine("RESPONSE_INFO:")
    if (responseInfo == null) {
        appendLine("null")
        return
    }

    appendLine("responseId=${responseInfo.responseId}")
    appendLine("adapterClassName=${responseInfo.adapterClassName}")
    appendLine("isPangle=${responseInfo.isPangle()}")
    appendLine("loadedAdSource=${responseInfo.loadedAdSourceResponseInfo.shortName()}")
    appendLine()
    appendLine("ADAPTERS:")
    if (responseInfo.adSourceResponses.isEmpty()) {
        appendLine("none")
        return
    }

    responseInfo.adSourceResponses.forEach { adSource ->
        appendLine("${adSource.networkDebugName()}:")
        appendLine("adapterClassName=${adSource.adapterClassName}")
        appendLine("adSourceName=${adSource.name}")
        appendLine("adSourceId=${adSource.id}")
        appendLine("instanceName=${adSource.instanceName}")
        appendLine("instanceId=${adSource.instanceId}")
        appendLine("latency=${adSource.latencyMillis}ms")
        val adError = adSource.adError
        if (adError == null) {
            appendLine("error=null")
        } else {
            appendLine("error:")
            appendMediationError(adError)
        }
    }
}

private fun StringBuilder.appendMediationError(error: MediationAdError) {
    appendLine("code=${error.code}")
    appendLine("domain=${error.domain}")
    appendLine("message=${error.message.cleanForLog()}")
}

private fun AdSourceResponseInfo?.shortName(): String {
    if (this == null) return "none"
    return "${networkDebugName()}(${adapterClassName})"
}

private fun AdSourceResponseInfo.networkDebugName(): String {
    val sourceName = name.orEmpty()
    return when {
        isPangle() -> "Pangle"
        adapterClassName.contains("facebook", ignoreCase = true) ||
            sourceName.contains("Meta Audience Network", ignoreCase = true) -> "Meta"
        adapterClassName.contains("admob", ignoreCase = true) ||
            adapterClassName.contains("google", ignoreCase = true) ||
            sourceName.contains("admob", ignoreCase = true) ||
            sourceName.contains("google", ignoreCase = true) -> "Google"
        sourceName.isNotBlank() -> sourceName
        else -> "Unknown"
    }
}

private fun AdSourceResponseInfo?.isPangle(): Boolean {
    if (this == null) return false
    return adapterClassName.isPangleAdapterName() ||
        name.isPangleAdapterName() ||
        id.isPangleAdapterName() ||
        instanceName.isPangleAdapterName() ||
        instanceId.isPangleAdapterName() ||
        adError?.domain?.isPangleAdapterName() == true
}

private fun String?.isPangleAdapterName(): Boolean {
    if (this.isNullOrBlank()) return false
    return contains("pangle", ignoreCase = true) ||
        contains("bytedance", ignoreCase = true)
}

private fun String.cleanForLog(): String = replace('\n', ' ').replace('\r', ' ')
