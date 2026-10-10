package com.jeremy.flaghours.domain

import kotlin.math.abs

/**
 * Integer helpers for hours (Int hundredths: 1.6 h = 160) and money (Long cents).
 * Nothing here touches floating point.
 */
object Units {

    /** a * b / d rounded half away from zero. */
    fun mulDivRound(a: Long, b: Long, d: Long): Long {
        require(d != 0L) { "divide by zero" }
        val n = a * b
        val q = n / d
        val r = n % d
        return if (abs(r) * 2 >= abs(d)) q + (if ((n < 0) xor (d < 0)) -1 else 1) else q
    }

    /** Pay for [hundredths] hours at [rateCents] per hour. */
    fun payCents(hundredths: Int, rateCents: Long): Long = mulDivRound(hundredths.toLong(), rateCents, 100)

    fun minutesToHundredths(minutes: Long): Int = mulDivRound(minutes, 100, 60).toInt()

    fun hundredthsToMinutes(hundredths: Int): Long = mulDivRound(hundredths.toLong(), 60, 100)

    /** Format hundredths with [decimals] (0..2) places, e.g. 160 -> "1.6". Rounds half away from zero. */
    fun formatHours(hundredths: Int, decimals: Int = 1): String {
        val d = decimals.coerceIn(0, 2)
        val scale = when (d) { 0 -> 100L; 1 -> 10L; else -> 1L }
        val scaled = mulDivRound(hundredths.toLong(), 1, scale)
        return formatScaled(scaled, d)
    }

    /** Format cents as dollars, e.g. 123456 -> "$1,234.56", -50 -> "-$0.50". */
    fun formatMoney(cents: Long): String {
        val neg = cents < 0
        val a = abs(cents)
        val dollars = a / 100
        val c = a % 100
        val grouped = dollars.toString().reversed().chunked(3).joinToString(",").reversed()
        return (if (neg) "-" else "") + "$" + grouped + "." + c.toString().padStart(2, '0')
    }

    /** Format a permille value (1234 -> "123.4%"). */
    fun formatPermille(permille: Int): String = formatScaled(permille.toLong(), 1) + "%"

    private fun formatScaled(scaled: Long, decimals: Int): String {
        if (decimals == 0) return scaled.toString()
        val neg = scaled < 0
        val a = abs(scaled)
        var div = 1L
        repeat(decimals) { div *= 10 }
        val whole = a / div
        val frac = (a % div).toString().padStart(decimals, '0')
        return (if (neg) "-" else "") + whole + "." + frac
    }

    /** Parse "1.6", "1", ".25", "2.35" into hundredths. Extra decimals are rounded. Null if invalid. */
    fun parseHours(text: String): Int? = parseFixed(text, 2)?.toInt()

    /** Parse "$32.50", "32.5", "1,200" into cents. Null if invalid. */
    fun parseMoney(text: String): Long? = parseFixed(text.replace("$", "").replace(",", ""), 2)

    private fun parseFixed(raw: String, places: Int): Long? {
        val t = raw.trim()
        if (t.isEmpty()) return null
        val neg = t.startsWith("-")
        val body = if (neg) t.substring(1) else t
        if (!Regex("""\d*(\.\d*)?""").matches(body) || body == "." || body.isEmpty()) return null
        val parts = body.split(".")
        val whole = parts[0].ifEmpty { "0" }
        val frac = parts.getOrElse(1) { "" }
        if (whole.length > 12) return null
        var value = whole.toLong()
        repeat(places) { i -> value = value * 10 + (frac.getOrNull(i)?.digitToInt() ?: 0) }
        val next = frac.getOrNull(places)?.digitToInt() ?: 0
        if (next >= 5) value += 1
        return if (neg) -value else value
    }
}
