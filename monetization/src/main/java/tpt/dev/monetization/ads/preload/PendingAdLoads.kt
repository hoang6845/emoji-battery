package tpt.dev.monetization.ads.preload

/** Main-thread registry. A token prevents cleared requests from repopulating a cache. */
internal class PendingAdLoads<T> {
    private data class Flight<T>(val token: Long, val listeners: MutableList<(T?) -> Unit>)
    private val flights = mutableMapOf<String, Flight<T>>()
    private var sequence = 0L

    fun contains(key: String) = key in flights
    val size: Int get() = flights.size
    fun begin(key: String): Long {
        check(key !in flights)
        val token = ++sequence
        flights[key] = Flight(token, mutableListOf())
        return token
    }
    fun isCurrent(key: String, token: Long) = flights[key]?.token == token
    fun subscribe(key: String, listener: (T?) -> Unit): () -> Unit {
        val flight = checkNotNull(flights[key])
        flight.listeners.add(listener)
        return { flight.listeners.remove(listener); Unit }
    }
    fun unsubscribe(key: String, listener: (T?) -> Unit) {
        flights[key]?.listeners?.remove(listener)
    }
    fun complete(key: String, token: Long, value: T?) {
        if (!isCurrent(key, token)) return
        val listeners = flights.remove(key)!!.listeners.toList()
        listeners.forEach { it(value) }
    }
    fun cancel(key: String) {
        val flight = flights.remove(key) ?: return
        flight.listeners.toList().forEach { it(null) }
    }
    fun clear() = flights.keys.toList().forEach(::cancel)
}

internal object AdRetryPolicy {
    /** Initial attempt plus two retries; never an unbounded no-fill loop. */
    fun delayMillis(retriesCompleted: Int): Long? = when (retriesCompleted) {
        0 -> 2_000L
        1 -> 5_000L
        else -> null
    }
}
