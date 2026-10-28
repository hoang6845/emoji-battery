package tpt.dev.monetization.ads.remote

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ResumeAdRemoteConfigTest {
    private fun config(json: String) = Gson().fromJson(json, RemoteAdsConfig::class.java)

    @Test fun oldConfigMissingFlagKeepsAppOpen() {
        assertFalse(config("""{"configs":{"enable":true}}""").configs!!.useInterSplashOnResume)
        assertNull(config("""{}""").configs)
    }

    @Test fun flagTrueSelectsInterAndFalseSelectsAppOpen() {
        assertTrue(config("""{"configs":{"use_inter_splash_on_resume":true}}""").configs!!.useInterSplashOnResume)
        assertFalse(config("""{"configs":{"use_inter_splash_on_resume":false}}""").configs!!.useInterSplashOnResume)
    }

    @Test fun switchDoesNotAlterCommonInterPlacementOrUnits() {
        val config = config("""
            {
              "configs":{"use_inter_splash_on_resume":true},
              "placements":{"ads_interstitial_id":{"ordered_priority_ads":"interid"}},
              "ad_items":{"interid":{"unit":"interid"}},
              "ad_units":{"interid":[{"network":"admob","unit_id":"inter-unit"}]}
            }
        """.trimIndent())
        assertEquals("inter-unit", config.resolveAdRequests("ads_interstitial_id").single().unitId)
        config.configs!!.useInterSplashOnResume = false
        assertEquals("inter-unit", config.resolveAdRequests("ads_interstitial_id").single().unitId)
    }
}
