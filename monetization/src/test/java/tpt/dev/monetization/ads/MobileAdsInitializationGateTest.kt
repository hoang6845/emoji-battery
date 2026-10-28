package tpt.dev.monetization.ads

import org.junit.Assert.*
import org.junit.Test

class MobileAdsInitializationGateTest {
    @Test
    fun concurrentRequestsWaitForSdkCompletionAndInitializeOnlyOnce() {
        val gate = MobileAdsInitializationGate()
        var starts = 0
        var callbacks = 0
        lateinit var finish: (String?) -> Unit
        val start: ((String?) -> Unit) -> Unit = { starts++; finish = it }
        repeat(3) {
            gate.initialize(start) { error ->
                assertNull(error)
                assertTrue(gate.isReady)
                callbacks++
            }
        }
        assertEquals(1, starts)
        assertEquals(0, callbacks)
        assertFalse(gate.isReady)
        finish(null)
        assertEquals(3, callbacks)
        finish(null)
        assertEquals(3, callbacks)
        gate.initialize(start) { callbacks++ }
        assertEquals(1, starts)
        assertEquals(4, callbacks)
    }

    @Test
    fun initializationFailureCompletesAllWaitersAndAllowsRetry() {
        val gate = MobileAdsInitializationGate()
        lateinit var finish: (String?) -> Unit
        val errors = mutableListOf<String?>()
        val start: ((String?) -> Unit) -> Unit = { finish = it }
        repeat(2) { gate.initialize(start) { errors.add(it) } }
        val oldFinish = finish
        finish("initialization failed")
        assertFalse(gate.isReady)
        assertEquals(listOf("initialization failed", "initialization failed"), errors)
        gate.initialize(start) { errors.add(it) }
        oldFinish(null)
        assertFalse(gate.isReady)
        finish(null)
        assertTrue(gate.isReady)
        assertEquals(listOf("initialization failed", "initialization failed", null), errors)
    }

    @Test
    fun synchronousSdkCompletionReportsReady() {
        val gate = MobileAdsInitializationGate()
        var completed = false
        gate.initialize(start = { it(null) }) {
            assertTrue(gate.isReady)
            completed = true
        }
        assertTrue(completed)
    }
}
