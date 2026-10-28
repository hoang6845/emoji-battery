package tpt.dev.monetization.ads.remote

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class RemoteAdsConfigTwoFloorTest {
    private fun config() = Gson().fromJson("""
        {
          "configs": {"enable": true},
          "placements": {
            "splash": {"ordered_priority_ads": "splash_high;splash_medium;splash_all"},
            "intro": {"ordered_priority_ads": "splash_high;splash_medium;splash_all"}
          },
          "ad_items": {
            "splash_high": {"unit": "high"},
            "splash_medium": {"unit": "medium"},
            "splash_all": {"unit": "all"}
          },
          "ad_units": {
            "high": [{"network": "admob", "unit_id": "high-unit"}],
            "medium": [{"network": "admob", "unit_id": "medium-unit"}],
            "all": [{"network": "admob", "unit_id": "all-unit"}]
          }
        }
    """.trimIndent(), RemoteAdsConfig::class.java)

    @Test fun defaultKeepsThreeFloorsAndOptInOnlyChangesSelectedPlacement() {
        val config = config()
        assertEquals(listOf("high-unit", "medium-unit", "all-unit"), config.resolveAdRequests("splash").map { it.unitId })
        config.configs!!.twoFloorPlacements = listOf("splash")
        assertEquals(listOf("high-unit", "all-unit"), config.resolveAdRequests("splash").map { it.unitId })
        assertEquals(3, config.resolveAdRequests("intro").size)
    }
    @Test fun disablingHighDoesNotRemoveUsableMediumFallback() {
        val config = config()
        config.configs!!.twoFloorPlacements = listOf("splash")
        config.adItems!!["splash_high"]!!.enable = false
        assertEquals(listOf("medium-unit", "all-unit"), config.resolveAdRequests("splash").map { it.unitId })
    }
    @Test fun experimentChangeInvalidatesCachedMediumAndRollbackAcceptsItAgain() {
        val config = config()
        val medium = config.resolveAdRequests("splash")[1]
        assertTrue(config.isResolvedSourceStillEnabled(medium))
        config.configs!!.twoFloorPlacements = listOf("splash")
        assertFalse(config.isResolvedSourceStillEnabled(medium))
        config.configs!!.twoFloorPlacements = emptyList()
        assertTrue(config.isResolvedSourceStillEnabled(medium))
    }
    @Test fun legacyAliasUsesCanonicalExperimentKey() {
        val config = config().withLegacyAliases(mapOf("old-unit" to "splash"))
        config.configs!!.twoFloorPlacements = listOf("splash")
        assertEquals(2, config.resolveAdRequests("old-unit").size)
    }
}
