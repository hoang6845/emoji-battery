package tpt.dev.monetization.ads

import org.junit.Assert.*
import org.junit.Test

class ResumeAdOpportunityTest {
    @Test fun coldStartDoesNotHaveResumeOpportunity() {
        assertNull(ResumeAdOpportunity().consume())
    }

    @Test fun normalWarmResumeCanOnlyBeConsumedOnce() {
        val gate = ResumeAdOpportunity()
        gate.onBackground(splashCompleted = true, fullscreenShowing = false)
        val token = gate.consume()!!
        assertTrue(gate.isCurrent(token))
        assertNull(gate.consume())
        assertFalse(gate.isCurrent(token))
    }

    @Test fun returningFromAdClickDoesNotCreateAnotherAdOpportunity() {
        val gate = ResumeAdOpportunity()
        gate.onBackground(splashCompleted = true, fullscreenShowing = true)
        assertNull(gate.consume())
    }

    @Test fun backgroundBeforeSplashCompletesCannotShowResumeAd() {
        val gate = ResumeAdOpportunity()
        gate.onBackground(splashCompleted = false, fullscreenShowing = false)
        assertNull(gate.consume())
    }

    @Test fun pauseInvalidatesPreparedShowAndNextResumeUsesNewToken() {
        val gate = ResumeAdOpportunity()
        gate.onBackground(true, false)
        val oldToken = gate.consume()!!
        gate.invalidate()
        assertFalse(gate.isCurrent(oldToken))
        gate.onBackground(true, false)
        val nextToken = gate.consume()!!
        assertFalse(gate.isCurrent(oldToken))
        assertTrue(gate.isCurrent(nextToken))
    }
}
