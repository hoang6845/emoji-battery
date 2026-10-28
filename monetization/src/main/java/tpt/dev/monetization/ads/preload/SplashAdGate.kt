package tpt.dev.monetization.ads.preload

/**
 * One Splash opportunity, accessed on the main thread with a monotonic clock.
 * First launch can require native terminal results alongside the Splash interstitial.
 */
class SplashAdGate(
    startedAtMillis: Long,
    timeoutMillis: Long,
    requiredNativeKeys: Set<String> = emptySet()
) {
    private val deadlineMillis = startedAtMillis + timeoutMillis.coerceAtLeast(0L)
    private val pendingNativeKeys = requiredNativeKeys.toMutableSet()
    private var interFinished = false
    private var interLoaded = false
    private var completed = false
    private var ready = false
    private var closed = false

    /** True only when this result completes the whole required group. */
    fun onInterComplete(loaded: Boolean, nowMillis: Long): Boolean {
        if (closed || completed || interFinished) return false
        interFinished = true
        interLoaded = loaded
        return finishIfReady(nowMillis)
    }

    /** Native failure/disabled is terminal too; duplicate or background keys cannot finish a slot. */
    fun onNativeComplete(key: String, nowMillis: Long): Boolean {
        if (closed || completed || !pendingNativeKeys.remove(key)) return false
        return finishIfReady(nowMillis)
    }

    fun timeout(nowMillis: Long): Boolean {
        if (closed || completed || nowMillis < deadlineMillis) return false
        return finish(allowShow = false)
    }

    private fun finishIfReady(nowMillis: Long): Boolean {
        if (nowMillis >= deadlineMillis) return finish(allowShow = false)
        if (!interFinished || pendingNativeKeys.isNotEmpty()) return false
        return finish(allowShow = interLoaded)
    }

    private fun finish(allowShow: Boolean): Boolean {
        completed = true
        ready = allowShow
        return true
    }

    /** Recheck immediately before the SDK show call, including after background/resume. */
    fun canShow(nowMillis: Long): Boolean =
        completed && ready && !closed && nowMillis < deadlineMillis

    fun close() {
        closed = true
    }
}
