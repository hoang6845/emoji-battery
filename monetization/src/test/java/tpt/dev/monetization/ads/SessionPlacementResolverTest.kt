package tpt.dev.monetization.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionPlacementResolverTest {
    private val sessionKeys = setOf(
        "ads_interstitial_id", "ads_inter_splash", "full_back", "inter_change_tab",
        "ads_native_language_id", "ads_native_language_click"
    )
    private val available = sessionKeys + sessionKeys.map { "${it}_after_session" }

    @Test
    fun firstSessionKeepsNormalPlacements() {
        val resolver = SessionPlacementResolver(isFirstSession = true)
        sessionKeys.forEach { assertEquals(it, resolver.resolve(it, available)) }
    }

    @Test
    fun laterSessionUsesOnlySupportedAndConfiguredPlacements() {
        val resolver = SessionPlacementResolver(isFirstSession = false)
        sessionKeys.forEach {
            val resolved = resolver.resolve(it, available)
            assertEquals("${it}_after_session", resolved)
            assertEquals(resolved, resolver.resolve(resolved, available))
        }
        listOf("ads_open_id", "reward", "ads_native_home",
            "ads_native_intro1", "ads_native_intro2", "ads_native_intro_full_id",
            "ads_native_import_link", "ads_native_upload_file",
            "ads_banner_add_bottom_sheet", "ads_collapse_channel", "unknown"
        ).forEach {
            // Old remote configs may still contain variants removed from the sheet.
            assertEquals(it, resolver.resolve(it, available + "${it}_after_session"))
        }
    }

    @Test
    fun explicitResumeAfterSessionKeyNeverFallsBackToFirstSession() {
        val key = AdsManager.AdPlacement.ADS_INTERSTITIAL_ID_AFTER_SESSION
        listOf(true, false).forEach { isFirstSession ->
            val resolver = SessionPlacementResolver(isFirstSession)
            assertEquals(key, resolver.resolve(key, available))
            assertEquals(key, resolver.resolve(key, sessionKeys))
            assertEquals(key, resolver.resolve(key, emptySet()))
        }
    }

    @Test
    fun navigationInterAlwaysUsesAfterSessionKeyOnReturningLaunches() {
        val original = AdsManager.AdPlacement.ADS_INTERSTITIAL_ID
        val after = AdsManager.AdPlacement.ADS_INTERSTITIAL_ID_AFTER_SESSION
        listOf(available, sessionKeys, emptySet()).forEach { configuredKeys ->
            assertEquals(original, SessionPlacementResolver(true).resolve(original, configuredKeys))
            val returning = SessionPlacementResolver(false)
            assertEquals(after, returning.resolve(" $original ", configuredKeys))
            assertEquals(after, returning.resolve(after, configuredKeys))
        }
    }

    @Test
    fun missingAfterSessionPlacementFallsBackToNormalKey() {
        val resolver = SessionPlacementResolver(isFirstSession = false)
        (sessionKeys - AdsManager.AdPlacement.ADS_INTERSTITIAL_ID).forEach {
            assertEquals(it, resolver.resolve(" $it ", sessionKeys))
            assertEquals(it, resolver.resolve(it, emptySet()))
        }
        assertEquals("", resolver.resolve("  ", available))
        assertEquals("ca-app-pub-123/456", resolver.resolve("ca-app-pub-123/456", available))
    }
}
