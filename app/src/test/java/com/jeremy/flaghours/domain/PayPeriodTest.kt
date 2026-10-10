package com.jeremy.flaghours.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PayPeriodTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun weeklyMonday() {
        val plan = PayPlan(period = PeriodType.WEEKLY, periodStartDay = 1)
        // 2026-10-10 is a Saturday.
        val p = PayPeriod.forDate(d("2026-10-10"), plan)
        assertEquals(d("2026-10-05"), p.start)
        assertEquals(d("2026-10-11"), p.end)
        assertEquals(p, PayPeriod.forDate(d("2026-10-05"), plan))
        assertEquals(7L, p.days)
    }

    @Test fun weeklyThursdayStart() {
        val plan = PayPlan(period = PeriodType.WEEKLY, periodStartDay = 4)
        val p = PayPeriod.forDate(d("2026-10-10"), plan)
        assertEquals(d("2026-10-08"), p.start)
        assertEquals(d("2026-10-14"), p.end)
        assertEquals(d("2026-10-01"), PayPeriod.previous(p, plan).start)
        assertEquals(d("2026-10-15"), PayPeriod.next(p, plan).start)
    }

    @Test fun biweeklyAnchored() {
        val anchor = d("2026-09-28") // a Monday
        val plan = PayPlan(period = PeriodType.BIWEEKLY, biweeklyAnchorEpochDay = anchor.toEpochDay())
        val p = PayPeriod.forDate(d("2026-10-10"), plan)
        assertEquals(d("2026-09-28"), p.start)
        assertEquals(d("2026-10-11"), p.end)
        assertEquals(d("2026-10-12"), PayPeriod.forDate(d("2026-10-12"), plan).start)
        // Dates before the anchor still land on the 14-day grid.
        assertEquals(d("2026-09-14"), PayPeriod.forDate(d("2026-09-20"), plan).start)
        assertEquals(14L, p.days)
    }

    @Test fun semiMonthly() {
        val plan = PayPlan(period = PeriodType.SEMI_MONTHLY)
        assertEquals(PayPeriod(d("2026-10-01"), d("2026-10-15")), PayPeriod.forDate(d("2026-10-15"), plan))
        assertEquals(PayPeriod(d("2026-10-16"), d("2026-10-31")), PayPeriod.forDate(d("2026-10-16"), plan))
        assertEquals(PayPeriod(d("2026-02-16"), d("2026-02-28")), PayPeriod.forDate(d("2026-02-20"), plan))
        assertEquals(PayPeriod(d("2028-02-16"), d("2028-02-29")), PayPeriod.forDate(d("2028-02-29"), plan))
        val p = PayPeriod.forDate(d("2026-10-03"), plan)
        assertEquals(d("2026-09-16"), PayPeriod.previous(p, plan).start)
    }

    @Test fun contains() {
        val p = PayPeriod(d("2026-10-05"), d("2026-10-11"))
        assertTrue(d("2026-10-05") in p)
        assertTrue(d("2026-10-11") in p)
        assertFalse(d("2026-10-12") in p)
        assertFalse(d("2026-10-04") in p)
    }
}
