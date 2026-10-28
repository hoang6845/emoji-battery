package tpt.dev.monetization.ads.preload

import org.junit.Assert.*
import org.junit.Test

class PendingAdLoadsTest {
    @Test fun subscribersWaitForOneTerminalResult() {
        val loads = PendingAdLoads<String>()
        val results = mutableListOf<String?>()
        val token = loads.begin("home")
        loads.subscribe("home") { results.add(it) }
        loads.subscribe("home") { results.add(it) }
        assertTrue(loads.contains("home"))
        assertTrue(results.isEmpty())
        loads.complete("home", token, "ad")
        loads.complete("home", token, "duplicate")
        assertEquals(listOf("ad", "ad"), results)
        assertFalse(loads.contains("home"))
    }
    @Test fun departedViewDoesNotConsumeAnotherViewsResult() {
        val loads = PendingAdLoads<String>()
        val results = mutableListOf<String?>()
        val token = loads.begin("home")
        val detach = loads.subscribe("home") { fail("Departed view was notified") }
        loads.subscribe("home") { results.add(it) }
        detach()
        loads.complete("home", token, "ad")
        assertEquals(listOf("ad"), results)
    }
    @Test fun oldCallbackCannotCompleteReplacementRequestAfterClear() {
        val loads = PendingAdLoads<String>()
        val results = mutableListOf<String?>()
        val old = loads.begin("home")
        loads.subscribe("home") { results.add(it) }
        loads.clear()
        val replacement = loads.begin("home")
        loads.subscribe("home") { results.add(it) }
        loads.complete("home", old, "stale")
        assertEquals(listOf<String?>(null), results)
        assertTrue(loads.isCurrent("home", replacement))
        loads.complete("home", replacement, "fresh")
        assertEquals(listOf(null, "fresh"), results)
    }
    @Test fun listenerMayStartNextRequestWithoutOldCompletionRemovingIt() {
        val loads = PendingAdLoads<String>()
        val old = loads.begin("home")
        var next = 0L
        loads.subscribe("home") { next = loads.begin("home") }
        loads.complete("home", old, "ad")
        assertTrue(loads.isCurrent("home", next))
    }
    @Test fun retriesStopAfterTwoDelays() {
        assertEquals(2000L, AdRetryPolicy.delayMillis(0))
        assertEquals(5000L, AdRetryPolicy.delayMillis(1))
        assertNull(AdRetryPolicy.delayMillis(2))
        assertNull(AdRetryPolicy.delayMillis(20))
    }
}
