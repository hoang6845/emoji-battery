package tpt.dev.monetization.ads.preload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SplashAdGateTest {
    @Test fun returningLaunchOnlyWaitsForInter() {
        val gate = SplashAdGate(1000L, 15000L)
        assertTrue(gate.onInterComplete(true, 1200L))
        assertTrue(gate.canShow(1700L))
    }

    @Test fun failedInterCompletesWithoutAnExtraWait() {
        val gate = SplashAdGate(1000L, 15000L)
        assertTrue(gate.onInterComplete(false, 1200L))
        assertFalse(gate.canShow(1200L))
        assertFalse(gate.onInterComplete(true, 1300L))
    }

    @Test fun lateSuccessCannotReopenTimedOutOpportunity() {
        val gate = SplashAdGate(1000L, 15000L)
        assertTrue(gate.timeout(16000L))
        assertFalse(gate.onInterComplete(true, 16001L))
        assertFalse(gate.canShow(16001L))
    }

    @Test fun deadlineStillAppliesWhenTimeoutCoroutineHasNotRun() {
        val gate = SplashAdGate(1000L, 15000L)
        assertTrue(gate.onInterComplete(true, 16000L))
        assertFalse(gate.canShow(16000L))
    }

    @Test fun preparedAdCannotShowAfterDeadlineOrLongBackgroundPause() {
        val gate = SplashAdGate(1000L, 15000L)
        assertTrue(gate.onInterComplete(true, 15900L))
        assertTrue(gate.canShow(15999L))
        assertFalse(gate.canShow(16400L))
    }

    @Test fun navigationOrViewDestructionClosesPreparedOpportunity() {
        val gate = SplashAdGate(1000L, 15000L)
        assertTrue(gate.onInterComplete(true, 1200L))
        gate.close()
        assertFalse(gate.canShow(1700L))
        assertFalse(gate.onInterComplete(true, 1800L))
    }

    @Test fun closedPendingOpportunityIgnoresCallbacks() {
        val gate = SplashAdGate(1000L, 15000L)
        gate.close()
        assertFalse(gate.onInterComplete(true, 1200L))
        assertFalse(gate.onInterComplete(false, 16000L))
    }

    @Test fun firstLaunchWaitsForInterAndBothLanguageNatives() {
        val gate = SplashAdGate(1000L, 15000L, setOf("language", "language_click"))
        assertFalse(gate.onInterComplete(true, 1200L))
        assertFalse(gate.canShow(1200L))
        assertFalse(gate.onNativeComplete("language", 1300L))
        assertFalse(gate.onNativeComplete("language", 1400L))
        assertFalse(gate.onNativeComplete("home", 1400L))
        assertTrue(gate.onNativeComplete("language_click", 1500L))
        assertTrue(gate.canShow(2000L))
        assertFalse(gate.timeout(16000L))
    }

    @Test fun nativesMayFinishBeforeInterWithoutOpeningGateEarly() {
        val gate = SplashAdGate(1000L, 15000L, setOf("language", "language_click"))
        assertFalse(gate.onNativeComplete("language_click", 1100L))
        assertFalse(gate.onNativeComplete("language", 1200L))
        assertFalse(gate.canShow(1200L))
        assertTrue(gate.onInterComplete(true, 1500L))
        assertTrue(gate.canShow(2000L))
    }

    @Test fun missingNativeDoesNotExtendSharedDeadlineOrAllowLateShow() {
        val gate = SplashAdGate(1000L, 15000L, setOf("language", "language_click"))
        assertFalse(gate.onInterComplete(true, 1200L))
        assertFalse(gate.onNativeComplete("language", 1300L))
        assertTrue(gate.timeout(16000L))
        assertFalse(gate.onNativeComplete("language_click", 16001L))
        assertFalse(gate.canShow(16001L))
    }

    @Test fun failedInterStillWaitsForBothNativeTerminalResults() {
        val gate = SplashAdGate(1000L, 15000L, setOf("language", "language_click"))
        assertFalse(gate.onInterComplete(false, 1200L))
        assertFalse(gate.onNativeComplete("language", 1300L))
        assertTrue(gate.onNativeComplete("language_click", 1400L))
        assertFalse(gate.canShow(1400L))
    }
}
