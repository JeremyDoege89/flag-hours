package com.jeremy.flaghours.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {
    @Test fun parseHours() {
        assertEquals(160, Units.parseHours("1.6"))
        assertEquals(100, Units.parseHours("1"))
        assertEquals(25, Units.parseHours(".25"))
        assertEquals(235, Units.parseHours("2.35"))
        assertEquals(236, Units.parseHours("2.355"))
        assertEquals(235, Units.parseHours("2.354"))
        assertEquals(-50, Units.parseHours("-0.5"))
        assertEquals(1000, Units.parseHours(" 10. "))
        assertNull(Units.parseHours(""))
        assertNull(Units.parseHours("."))
        assertNull(Units.parseHours("abc"))
        assertNull(Units.parseHours("1.2.3"))
    }

    @Test fun parseMoney() {
        assertEquals(3250L, Units.parseMoney("32.50"))
        assertEquals(3250L, Units.parseMoney("$32.5"))
        assertEquals(120000L, Units.parseMoney("1,200"))
        assertNull(Units.parseMoney("$"))
    }

    @Test fun formatHours() {
        assertEquals("1.6", Units.formatHours(160))
        assertEquals("1.6", Units.formatHours(155))
        assertEquals("1.5", Units.formatHours(154))
        assertEquals("1.55", Units.formatHours(155, 2))
        assertEquals("2", Units.formatHours(150, 0))
        assertEquals("-0.5", Units.formatHours(-50))
        assertEquals("0.0", Units.formatHours(0))
    }

    @Test fun formatMoney() {
        assertEquals("$1,234.56", Units.formatMoney(123456))
        assertEquals("-$0.50", Units.formatMoney(-50))
        assertEquals("$0.00", Units.formatMoney(0))
        assertEquals("$1,000,000.00", Units.formatMoney(100_000_000))
    }

    @Test fun rounding() {
        assertEquals(3, Units.mulDivRound(5, 1, 2))
        assertEquals(-3, Units.mulDivRound(-5, 1, 2))
        assertEquals(2, Units.mulDivRound(7, 1, 3))
        // 1.6 h × $32.50 = $52.00
        assertEquals(5200L, Units.payCents(160, 3250))
        // 0.33 h × $31.17 = 10.2861 -> $10.29
        assertEquals(1029L, Units.payCents(33, 3117))
        assertEquals(50, Units.minutesToHundredths(30))
        assertEquals(2, Units.minutesToHundredths(1))
        assertEquals(30L, Units.hundredthsToMinutes(50))
    }

    @Test fun permille() {
        assertEquals("123.4%", Units.formatPermille(1234))
    }
}
