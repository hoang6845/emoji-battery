package tpt.dev.monetization.ads.remote

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class RemoteAdsConfig {
    @SerializedName("enable")
    var enable: Boolean? = null

    @SerializedName("configs")
    var configs: RemoteGlobalConfig? = null

    @SerializedName(value = "placements", alternate = ["placement"])
    var placements: Map<String, RemotePlacement>? = null

    @SerializedName(value = "ad_items", alternate = ["ad_item", "adItems"])
    var adItems: Map<String, RemoteAdItem>? = null

    @SerializedName(value = "ad_units", alternate = ["units", "ad_unit", "adUnits"])
    var adUnits: Map<String, List<RemoteAdUnit>>? = null

    @Transient
    var legacyPlacementAliases: Map<String, String> = emptyMap()

    fun hasRemoteData(): Boolean {
        return placements.orEmpty().isNotEmpty() ||
            adItems.orEmpty().isNotEmpty() ||
            adUnits.orEmpty().isNotEmpty()
    }

    fun withLegacyAliases(aliases: Map<String, String>): RemoteAdsConfig {
        legacyPlacementAliases = aliases
            .mapKeys { it.key.trim() }
            .mapValues { it.value.trim() }
            .filter { it.key.isNotEmpty() && it.value.isNotEmpty() }
        return this
    }

    fun isGlobalEnabled(defaultValue: Boolean = true): Boolean {
        return configs?.enable ?: enable ?: defaultValue
    }

    fun preloadLoadingTime(defaultValue: Long): Long {
        return configs?.loadingTime
            ?.takeIf { it > 0L }
            ?: defaultValue
    }

    fun resolvePlacementKey(requestKey: String): String {
        val key = requestKey.trim()
        if (key.isEmpty()) return key
        if (placements.orEmpty().containsKey(key)) return key
        return legacyPlacementAliases[key] ?: key
    }

    fun isPlacementEnabled(requestKey: String): Boolean {
        if (!hasRemoteData() || !isGlobalEnabled()) return false

        val placementKey = resolvePlacementKey(requestKey)
        val placement = placements.orEmpty()[placementKey] ?: return false
        return placement.isEnabled()
    }

    fun shouldReload(requestKey: String, defaultValue: Boolean): Boolean {
        if (!hasRemoteData()) return defaultValue
        if (!isPlacementEnabled(requestKey)) return false

        val placement = placements.orEmpty()[resolvePlacementKey(requestKey)] ?: return false
        return placement.placementConfig?.reload ?: defaultValue
    }

    fun shouldReloadNative(requestKey: String, defaultValue: Boolean): Boolean {
        if (!hasRemoteData()) return defaultValue
        if (!isPlacementEnabled(requestKey)) return false

        return resolveAdRequests(requestKey)
            .firstOrNull()
            ?.nativeConfig
            ?.shouldReload
            ?: defaultValue
    }

    fun resolveAdRequests(requestKey: String): List<ResolvedAdRequest> {
        if (!hasRemoteData() || !isGlobalEnabled()) return emptyList()

        val placementKey = resolvePlacementKey(requestKey)
        val placement = placements.orEmpty()[placementKey] ?: return emptyList()
        if (!placement.isEnabled()) return emptyList()

        val requests = placement.orderedAdItems().flatMap { adItemKey ->
            val adItem = adItems.orEmpty()[adItemKey] ?: return@flatMap emptyList()
            if (!adItem.isEnabled()) return@flatMap emptyList()

            val unitKey = adItem.unit?.trim().takeUnless { it.isNullOrEmpty() } ?: adItemKey
            adUnits.orEmpty()[unitKey].orEmpty()
                .filter { unit -> unit.isEnabled() && unit.isAdMob() && unit.unitId.isNullOrBlank().not() }
                .map { unit ->
                    ResolvedAdRequest(
                        requestKey = requestKey.trim(),
                        placementKey = placementKey,
                        adItemKey = adItemKey,
                        unitKey = unitKey,
                        network = unit.network?.trim().orEmpty().ifBlank { NETWORK_ADMOB },
                        unitId = unit.unitId.orEmpty().trim(),
                        nativeConfig = adItem.nativeConfig
                    )
                }
        }
        // Opt-in per placement. Keep the original order and fall back to the
        // configured waterfall if high/all are not both enabled candidates.
        val twoFloor = placementKey in configs?.twoFloorPlacements.orEmpty()
        return if (twoFloor && requests.any { it.adItemKey.endsWith("_high") } &&
            requests.any { it.adItemKey.endsWith("_all") }) {
            requests.filterNot { it.adItemKey.endsWith("_medium") }
        } else requests
    }

    fun isResolvedSourceStillEnabled(source: ResolvedAdRequest): Boolean {
        if (!hasRemoteData()) return true
        if (!isPlacementEnabled(source.placementKey)) return false

        return resolveAdRequests(source.placementKey).any { candidate ->
            candidate.adItemKey == source.adItemKey && candidate.unitKey == source.unitKey &&
                candidate.network == source.network && candidate.unitId == source.unitId
        }
    }

    fun nativeLayoutNameForRequest(requestKey: String): String? {
        return resolveAdRequests(requestKey)
            .firstOrNull()
            ?.nativeConfig
            ?.layoutRes
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val NETWORK_ADMOB = "admob"
    }
}

class RemoteGlobalConfig {
    /** Preload high only; medium/all are requested explicitly at a show opportunity. */
    @SerializedName("inter_high_preload_only")
    var interHighPreloadOnly: Boolean = false

    @SerializedName("use_inter_splash_on_resume")
    var useInterSplashOnResume: Boolean = false

    @SerializedName("two_floor_placements")
    var twoFloorPlacements: List<String>? = null

    @SerializedName("enable")
    var enable: Boolean? = null

    @SerializedName(
        value = "loading_time",
        alternate = ["loading_time_ms", "preload_loading_time", "preload_timeout_ms"]
    )
    var loadingTime: Long? = null
}

class RemotePlacement {
    @SerializedName("ordered_priority_ads")
    var orderedPriorityAds: String? = null

    @SerializedName("placement_config")
    var placementConfig: RemotePlacementConfig? = null

    fun isEnabled(): Boolean {
        return placementConfig?.enable ?: true
    }

    fun orderedAdItems(): List<String> {
        return orderedPriorityAds
            .orEmpty()
            .split(';', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}

class RemotePlacementConfig {
    @SerializedName("enable")
    var enable: Boolean? = null

    @SerializedName("reload")
    var reload: Boolean? = null
}

class RemoteAdItem {
    @SerializedName("unit")
    var unit: String? = null

    @SerializedName("enable")
    var enable: Boolean? = null

    @SerializedName("native_config")
    var nativeConfig: RemoteNativeConfig? = null

    fun isEnabled(): Boolean {
        return enable ?: true
    }
}

class RemoteNativeConfig {
    @SerializedName("layout_res")
    var layoutRes: String? = null

    @SerializedName("meta_layout_res")
    var metaLayoutRes: String? = null

    @SerializedName("should_reload")
    var shouldReload: Boolean? = null
}

class RemoteAdUnit {
    @SerializedName("network")
    var network: String? = null

    @SerializedName("unit_id")
    var unitId: String? = null

    @SerializedName("enable")
    var enable: Boolean? = null

    fun isEnabled(): Boolean {
        return enable ?: true
    }

    fun isAdMob(): Boolean {
        return network.isNullOrBlank() || network.equals("admob", ignoreCase = true)
    }
}

data class ResolvedAdRequest(
    val requestKey: String,
    val placementKey: String,
    val adItemKey: String,
    val unitKey: String,
    val network: String,
    val unitId: String,
    val nativeConfig: RemoteNativeConfig?
)
