package com.hd.emojibattery.runtime

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** ICU initialization and formatting must never block screen creation or clicks. */
object StatusDateFormatter {
    suspend fun format(
        pattern: String,
        locale: Locale = Locale.getDefault(),
        timestamp: Long = System.currentTimeMillis(),
        timeZone: TimeZone? = null
    ): String = withContext(Dispatchers.Default) {
        // Each request owns its formatter: SimpleDateFormat is not thread-safe.
        runCatching {
            SimpleDateFormat(pattern, locale).apply {
                if (timeZone != null) this.timeZone = timeZone
            }.format(Date(timestamp))
        }.getOrDefault("")
    }
}
