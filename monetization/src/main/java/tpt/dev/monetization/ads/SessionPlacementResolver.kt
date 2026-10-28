package tpt.dev.monetization.ads

/** Captured at app launch so reaching Home does not change ads within the first session. */
class SessionPlacementResolver(val isFirstSession: Boolean) {
    fun resolve(placementKey: String, availablePlacements: Set<String>): String {
        val key = placementKey.trim()
        // Navigation inters must never consume first-session ads on returning launches.
        if (!isFirstSession && key == AdsManager.AdPlacement.ADS_INTERSTITIAL_ID) {
            return AdsManager.AdPlacement.ADS_INTERSTITIAL_ID_AFTER_SESSION
        }
        val afterSessionKey = "${key}_after_session"
        return if (!isFirstSession && key in placements && afterSessionKey in availablePlacements) {
            afterSessionKey
        } else {
            key
        }
    }

    private val placements = setOf(
        AdsManager.AdPlacement.ADS_INTER_SPLASH,
        AdsManager.AdPlacement.FULL_BACK,
        AdsManager.AdPlacement.INTER_CHANGE_TAB,
        AdsManager.AdPlacement.ADS_NATIVE_LANGUAGE_ID,
        AdsManager.AdPlacement.ADS_NATIVE_LANGUAGE_CLICK
    )
}
