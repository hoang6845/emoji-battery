package tpt.dev.monetization.ads

/** Main-thread warm-resume opportunities. Leaving the foreground invalidates delayed shows. */
internal class ResumeAdOpportunity {
    private var pending = false
    private var generation = 0L

    fun onBackground(splashCompleted: Boolean, fullscreenShowing: Boolean) {
        generation++
        pending = splashCompleted && !fullscreenShowing
    }

    fun consume(): Long? {
        generation++
        if (!pending) return null
        pending = false
        return generation
    }

    fun invalidate() {
        generation++
    }

    fun isCurrent(token: Long): Boolean = token == generation
}
