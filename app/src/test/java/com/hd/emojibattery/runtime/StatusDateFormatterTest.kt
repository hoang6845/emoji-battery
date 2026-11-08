package com.hd.emojibattery.runtime

import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StatusDateFormatterTest {
    @Test fun preservesDateTimePatternsAndTimeZones() = runBlocking {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals("Thu, Jan 01", StatusDateFormatter.format("EEE, MMM dd", Locale.ENGLISH, 0L, utc))
        assertEquals("12:00 AM", StatusDateFormatter.format("h:mm a", Locale.ENGLISH, 0L, utc))
        assertEquals("7:00 AM", StatusDateFormatter.format("h:mm a", Locale.ENGLISH, 0L, TimeZone.getTimeZone("GMT+07:00")))
    }

    @Test fun concurrentRequestsKeepTheirOwnFormatAndLocale() = runBlocking {
        val values = (0 until 40).map { index ->
            async {
                StatusDateFormatter.format("MMMM", if (index % 2 == 0) Locale.ENGLISH else Locale.FRENCH,
                    0L, TimeZone.getTimeZone("UTC"))
            }
        }.awaitAll()
        values.forEachIndexed { index, value -> assertEquals(if (index % 2 == 0) "January" else "janvier", value) }
    }

    @Test fun malformedSavedPatternDoesNotCrashPreview() = runBlocking {
        assertEquals("", StatusDateFormatter.format("invalid pattern [", Locale.ENGLISH))
    }
}
