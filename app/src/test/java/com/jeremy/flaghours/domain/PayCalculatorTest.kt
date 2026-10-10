package com.jeremy.flaghours.domain

import com.jeremy.flaghours.domain.Fixtures.d
import com.jeremy.flaghours.domain.Fixtures.job
import com.jeremy.flaghours.domain.Fixtures.lastWeek
import com.jeremy.flaghours.domain.Fixtures.now
import com.jeremy.flaghours.domain.Fixtures.plan
import com.jeremy.flaghours.domain.Fixtures.shift
import com.jeremy.flaghours.domain.Fixtures.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayCalculatorTest {

    private fun sum(
        jobs: List<JobLine> = emptyList(),
        shifts: List<ShiftSpan> = emptyList(),
        hourly: List<HourlyWork> = emptyList(),
        p: PayPlan = plan,
        period: PayPeriod = week,
    ) = PayCalculator.summarize(period, jobs, shifts, hourly, p, now)

    @Test fun emptyPeriod() {
        val s = sum()
        assertEquals(0, s.flaggedWorked)
        assertEquals(0, s.flaggedPaid)
        assertEquals(0, s.stillOwed)
        assertEquals(0L, s.grossCents)
        assertNull(s.efficiencyPermille)
    }

    @Test fun workedPaidOwedBuckets() {
        val jobs = listOf(
            job(160, work = "2026-10-06", status = JobStatus.PAID, paid = "2026-10-05"), // worked + paid this week
            job(200, work = "2026-10-07", status = JobStatus.CLOSED), // worked, owed
            job(100, work = "2026-10-01", status = JobStatus.PAID, paid = "2026-10-05"), // carryover paid this week
            job(50, work = "2026-10-02", status = JobStatus.OPEN), // last week, still owed
            job(300, work = "2026-10-13", status = JobStatus.OPEN), // next week, not owed yet
        )
        val s = sum(jobs)
        assertEquals(360, s.flaggedWorked)
        assertEquals(260, s.flaggedPaid)
        assertEquals(250, s.stillOwed)
        assertEquals(7800L, s.flagPayCents) // 2.6 h × $30
        assertEquals(7800L, s.grossCents)
    }

    // Rule 1
    @Test fun partialFlagSplit() {
        val j = job(250, split = 50, status = JobStatus.PAID, paid = "2026-10-05")
        assertEquals(125, PayCalculator.myShare(j, plan, emptyMap()))
        val odd = job(155, split = 33)
        assertEquals(51, PayCalculator.myShare(odd, plan, emptyMap())) // 51.15 -> 51
        assertEquals(3750L, sum(listOf(j)).grossCents)
    }

    // Rule 2 + 3: status drives owed; paid period separate from work date
    @Test fun carryoverWorkedLastWeekPaidThisWeek() {
        val j = job(400, work = "2026-10-02", status = JobStatus.PAID, paid = "2026-10-05")
        val thisWeek = sum(listOf(j))
        val prev = sum(listOf(j), period = lastWeek)
        assertEquals(0, thisWeek.flaggedWorked)
        assertEquals(400, thisWeek.flaggedPaid)
        assertEquals(400, prev.flaggedWorked)
        assertEquals(0, prev.flaggedPaid)
        assertEquals(0, prev.stillOwed) // it is PAID now
    }

    // Rule 4
    @Test fun warrantyAdjustmentOverridesFlag() {
        val j = job(300, type = LaborType.WARRANTY, adjusted = 210, status = JobStatus.PAID, paid = "2026-10-05")
        assertEquals(90, j.adjustmentDiff)
        val s = sum(listOf(j), p = plan.copy(warrantyRateCents = 2500))
        assertEquals(210, s.flaggedPaid)
        assertEquals(5250L, s.grossCents) // 2.1 × $25
    }

    // Rule 5
    @Test fun ratesPerLaborType() {
        val p = plan.copy(warrantyRateCents = 2500, internalRateCents = 2000, diagRateCents = null)
        val jobs = listOf(
            job(100, type = LaborType.CUSTOMER, paid = "2026-10-05", status = JobStatus.PAID),
            job(100, type = LaborType.WARRANTY, paid = "2026-10-05", status = JobStatus.PAID),
            job(100, type = LaborType.INTERNAL, paid = "2026-10-05", status = JobStatus.PAID),
            job(100, type = LaborType.DIAG, paid = "2026-10-05", status = JobStatus.PAID),
        )
        val s = sum(jobs, p = p)
        assertEquals(3000L + 2500 + 2000 + 3000, s.flagPayCents)
        assertEquals(100, s.paidByType[LaborType.DIAG])
        assertEquals(4, s.math.count { it.contains(" h × ") })
    }

    // Rule 6
    @Test fun comebackRules() {
        val original = job(200, work = "2026-09-29", status = JobStatus.PAID, paid = "2026-09-28", type = LaborType.WARRANTY)
        val comeback = job(100, work = "2026-10-06", status = JobStatus.PAID, paid = "2026-10-05", comebackOf = original.id)
        val jobs = listOf(original, comeback)
        val byId = jobs.associateBy { it.id }
        val p = plan.copy(warrantyRateCents = 2500)

        assertEquals(100, PayCalculator.myShare(comeback, p.copy(comebackRule = ComebackRule.PAID), byId))
        assertEquals(0, PayCalculator.myShare(comeback, p.copy(comebackRule = ComebackRule.ZERO_PAY), byId))
        assertEquals(-200, PayCalculator.myShare(comeback, p.copy(comebackRule = ComebackRule.CLAWBACK), byId))
        assertEquals(50, PayCalculator.myShare(comeback, p.copy(comebackRule = ComebackRule.SPLIT, comebackSplitPct = 50), byId))
        // Original is never affected by its own rule.
        assertEquals(200, PayCalculator.myShare(original, p.copy(comebackRule = ComebackRule.CLAWBACK), byId))

        val claw = sum(jobs, p = p.copy(comebackRule = ComebackRule.CLAWBACK))
        assertEquals(-200, claw.flaggedPaid)
        assertEquals(-200, claw.paidByType[LaborType.WARRANTY]) // charged at the original's rate
        assertEquals(-5000L, claw.grossCents)
    }

    @Test fun clawbackOfMissingOriginalIsZero() {
        val cb = job(100, comebackOf = 9999)
        assertEquals(0, PayCalculator.myShare(cb, plan.copy(comebackRule = ComebackRule.CLAWBACK), emptyMap()))
    }

    // Rule 7
    @Test fun flatGuaranteeTopsUp() {
        val p = plan.copy(guarantee = Guarantee.Flat(4000))
        val s = sum(listOf(job(3000, status = JobStatus.PAID, paid = "2026-10-05")), p = p)
        assertEquals(1000, s.guaranteeTopUpHundredths)
        assertEquals(30000L, s.topUpCents)
        assertEquals(120000L, s.grossCents)
        val over = sum(listOf(job(4500, status = JobStatus.PAID, paid = "2026-10-05")), p = p)
        assertEquals(0, over.guaranteeTopUpHundredths)
    }

    @Test fun ifClockedGuarantee() {
        val p = plan.copy(guarantee = Guarantee.IfClocked(hoursHundredths = 4000, minClockedHundredths = 4000))
        val jobs = listOf(job(2000, status = JobStatus.PAID, paid = "2026-10-05"))
        val shifts = (5..9).map { shift("2026-10-%02dT07:00".format(it), "2026-10-%02dT15:00".format(it)) } // 40 h
        val met = sum(jobs, shifts, p = p)
        assertEquals(2400L, met.clockedMinutes)
        assertEquals(2000, met.guaranteeTopUpHundredths)
        val notMet = sum(jobs, shifts.dropLast(1), p = p)
        assertEquals(0, notMet.guaranteeTopUpHundredths)
        assertTrue(notMet.math.any { it.startsWith("Guarantee not met") })
    }

    // Rule 8
    @Test fun hourlyEntries() {
        val hourly = listOf(
            HourlyWork(1, d("2026-10-06"), 90, "Training"),
            HourlyWork(2, d("2026-10-07"), 30, "Shop"),
            HourlyWork(3, d("2026-10-01"), 600, "Last week"),
        )
        val s = sum(hourly = hourly)
        assertEquals(120L, s.hourlyMinutes)
        assertEquals(200, s.hourlyHundredths)
        assertEquals(4000L, s.hourlyPayCents)
        // 20 minutes at $20/h is $6.67 (computed on minutes, not rounded hours).
        assertEquals(667L, sum(hourly = listOf(HourlyWork(4, d("2026-10-06"), 20, "x"))).hourlyPayCents)
    }

    // Rule 9
    @Test fun efficiencyTiers() {
        val p = plan.copy(efficiencyTiers = listOf(EfficiencyTier(100, 105), EfficiencyTier(130, 110)))
        val shifts = listOf(shift("2026-10-06T08:00", "2026-10-06T18:00")) // 10 h
        val jobs = listOf(job(1400, status = JobStatus.PAID, paid = "2026-10-05")) // 14 h = 140%
        val s = sum(jobs, shifts, p = p)
        assertEquals(1400, s.efficiencyPermille)
        assertEquals(110, s.tierMultiplierPct)
        assertEquals(4200L, s.tierBonusCents) // $420 × 10%
        assertEquals(46200L, s.grossCents)
        val low = sum(listOf(job(900, status = JobStatus.PAID, paid = "2026-10-05")), shifts, p = p)
        assertEquals(100, low.tierMultiplierPct)
        assertEquals(0L, low.tierBonusCents)
        assertNull(sum(jobs, p = p).efficiencyPermille)
    }

    // Rule 10
    @Test fun overtimeRegularRate() {
        val p = plan.copy(overtime = OvertimeRule(OvertimeMode.REGULAR_RATE, 4000, 150))
        // 50 h clocked, 50 flag h paid at $30 = $1500 straight. Regular rate $30/h; 10 OT h × $15 = $150.
        val shifts = (5..9).map { shift("2026-10-%02dT07:00".format(it), "2026-10-%02dT17:00".format(it)) }
        val s = sum(listOf(job(5000, status = JobStatus.PAID, paid = "2026-10-05")), shifts, p = p)
        assertEquals(15000L, s.overtimeCents)
        assertEquals(165000L, s.grossCents)
        assertTrue(s.math.any { it.startsWith("Overtime: regular rate") })
    }

    @Test fun overtimeFlagHours() {
        val p = plan.copy(overtime = OvertimeRule(OvertimeMode.FLAG_HOURS, 4000, 150))
        val s = sum(listOf(job(4500, status = JobStatus.PAID, paid = "2026-10-05")), p = p)
        assertEquals(7500L, s.overtimeCents) // 5 h × $30 × 50%
    }

    @Test fun overtimeClockedHourly() {
        val p = plan.copy(overtime = OvertimeRule(OvertimeMode.CLOCKED_HOURLY, 4000, 150))
        val shifts = (5..9).map { shift("2026-10-%02dT07:00".format(it), "2026-10-%02dT16:00".format(it)) } // 45 h
        val s = sum(shifts = shifts, p = p)
        assertEquals(5000L, s.overtimeCents) // 5 h × $20 × 50%
    }

    @Test fun noOvertimeUnderThreshold() {
        val p = plan.copy(overtime = OvertimeRule())
        val s = sum(listOf(job(1000, status = JobStatus.PAID, paid = "2026-10-05")), p = p)
        assertEquals(0L, s.overtimeCents)
    }

    @Test fun clockedCrossesMidnightAndPeriodBoundary() {
        val shifts = listOf(
            shift("2026-10-04T22:00", "2026-10-05T02:00"), // 2 h in this week
            shift("2026-10-11T22:00", "2026-10-12T01:00"), // 2 h in this week
            shift("2026-10-08T20:00", "2026-10-09T04:30"), // 8.5 h
        )
        assertEquals(750L, PayCalculator.clockedMinutes(shifts, week, now))
    }

    @Test fun openShiftCountsToNow() {
        val shifts = listOf(shift("2026-10-11T20:00", null))
        // now = 2026-10-12T08:00; clipped to period end (midnight) = 4 h
        assertEquals(240L, PayCalculator.clockedMinutes(shifts, week, now))
        val nextWeek = PayPeriod(d("2026-10-12"), d("2026-10-18"))
        assertEquals(480L, PayCalculator.clockedMinutes(shifts, nextWeek, now))
    }

    @Test fun efficiency() {
        val shifts = listOf(shift("2026-10-06T08:00", "2026-10-06T09:36")) // 1.6 h
        val s = sum(listOf(job(160)), shifts)
        assertEquals(1000, s.efficiencyPermille)
    }

    @Test fun everythingTogether() {
        val p = plan.copy(
            warrantyRateCents = 2500,
            guarantee = Guarantee.Flat(1000),
            efficiencyTiers = listOf(EfficiencyTier(100, 110)),
        )
        val jobs = listOf(
            job(400, type = LaborType.WARRANTY, adjusted = 300, status = JobStatus.PAID, paid = "2026-10-05"),
            job(400, split = 50, status = JobStatus.PAID, paid = "2026-10-05"),
        )
        val shifts = listOf(shift("2026-10-06T08:00", "2026-10-06T12:00")) // 4 h, worked 5 h -> 125%
        val hourly = listOf(HourlyWork(1, d("2026-10-07"), 60, "Training"))
        val s = sum(jobs, shifts, hourly, p)
        assertEquals(500, s.flaggedPaid)
        assertEquals(7500L + 6000, s.flagPayCents)
        assertEquals(500, s.guaranteeTopUpHundredths)
        assertEquals(15000L, s.topUpCents)
        assertEquals(1250, s.efficiencyPermille)
        assertEquals(2850L, s.tierBonusCents) // (13500 + 15000) × 10%
        assertEquals(2000L, s.hourlyPayCents)
        assertEquals(13500L + 15000 + 2850 + 2000, s.grossCents)
        assertTrue(s.math.last().startsWith("Estimated gross"))
    }
}
