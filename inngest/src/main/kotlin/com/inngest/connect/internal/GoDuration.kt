package com.inngest.connect.internal

/**
 * Parses Go-style duration strings as sent by the gateway in
 * GatewayConnectionReadyData (e.g. "10s", "500ms", "1m30s").
 */
internal object GoDuration {
    private val unitMillis =
        linkedMapOf(
            // Order matters: longer suffixes are matched first.
            "ms" to 1.0,
            "ns" to 0.000001,
            "us" to 0.001,
            "µs" to 0.001,
            "h" to 3_600_000.0,
            "m" to 60_000.0,
            "s" to 1_000.0,
        )

    /**
     * Returns the duration in milliseconds, or null when [value] is null,
     * blank, or not a valid Go duration. "0" (no unit) parses as 0 like Go.
     */
    fun toMillisOrNull(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        var rest = value.trim()
        if (rest == "0") return 0
        var totalMillis = 0.0
        while (rest.isNotEmpty()) {
            val numberEnd =
                rest
                    .indexOfFirst { !it.isDigit() && it != '.' }
                    .let { if (it == -1) rest.length else it }
            if (numberEnd == 0) return null
            val number = rest.substring(0, numberEnd).toDoubleOrNull() ?: return null
            rest = rest.substring(numberEnd)
            val unit = unitMillis.keys.firstOrNull { rest.startsWith(it) } ?: return null
            totalMillis += number * unitMillis.getValue(unit)
            rest = rest.substring(unit.length)
        }
        return totalMillis.toLong()
    }

    /** Like [toMillisOrNull], falling back to [defaultMillis] for absent/invalid values. */
    fun toMillis(
        value: String?,
        defaultMillis: Long,
    ): Long = toMillisOrNull(value) ?: defaultMillis
}
