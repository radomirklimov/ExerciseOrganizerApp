package com.organizer.sync

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** ISO-8601 UTC timestamps compatible with backend java.time.Instant JSON. */
object SyncTimestamps {
    private val outputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val withMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    private val withoutMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    fun format(epochMillis: Long): String = synchronized(outputFormat) {
        outputFormat.format(Date(epochMillis))
    }

    fun parse(value: String): Long? {
        val normalized = REGEX_FRACTION.replace(value) { match ->
            val fraction = match.groupValues[1]
            ".${fraction.take(3)}Z"
        }
        return synchronized(withMillis) { runCatching { withMillis.parse(normalized)?.time }.getOrNull() }
            ?: synchronized(withoutMillis) { runCatching { withoutMillis.parse(normalized)?.time }.getOrNull() }
    }

    private val REGEX_FRACTION = Regex("\\.(\\d+)Z$")
}
