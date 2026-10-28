package tpt.dev.monetization.ads

/** Joins concurrent initialization requests without reporting readiness before completion. */
internal class MobileAdsInitializationGate {
    @Volatile
    var isReady: Boolean = false
        private set

    private var isInitializing = false
    private val listeners = mutableListOf<(String?) -> Unit>()

    fun initialize(start: ((String?) -> Unit) -> Unit, onComplete: (String?) -> Unit) {
        val alreadyReady = synchronized(this) {
            if (isReady) {
                true
            } else {
                listeners.add(onComplete)
                if (isInitializing) return
                isInitializing = true
                false
            }
        }
        if (alreadyReady) {
            onComplete(null)
            return
        }
        var completed = false
        start { error ->
            val pending = synchronized(this) {
                if (completed) return@start
                completed = true
                isReady = error == null
                isInitializing = false
                listeners.toList().also { listeners.clear() }
            }
            pending.forEach { it(error) }
        }
    }
}
