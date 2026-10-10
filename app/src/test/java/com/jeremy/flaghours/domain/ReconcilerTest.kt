package com.jeremy.flaghours.domain

import com.jeremy.flaghours.domain.Fixtures.job
import com.jeremy.flaghours.domain.Fixtures.now
import com.jeremy.flaghours.domain.Fixtures.plan
import com.jeremy.flaghours.domain.Fixtures.week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilerTest {
    private fun rec(stub: Int, jobs: List<JobLine>, p: PayPlan = plan, gross: Long? = null): Reconciliation {
        val s = PayCalculator.summarize(week, jobs, emptyList(), emptyList(), p, now)
        return Reconciler.reconcile(stub, gross, s, jobs, p)
    }

    @Test fun noGap() {
        val r = rec(160, listOf(job(160, status = JobStatus.PAID, paid = "2026-10-05")))
        assertEquals(0, r.gapHundredths)
        assertTrue(r.explanations.isEmpty())
    }

    @Test fun notMarkedPaidExplainsPositiveGap() {
        val missing = job(120, work = "2026-10-03", status = JobStatus.CLOSED, ro = "4471", desc = "Front brakes")
        val r = rec(280, listOf(job(160, status = JobStatus.PAID, paid = "2026-10-05"), missing))
        assertEquals(120, r.gapHundredths)
        val top = r.explanations.first()
        assertEquals(GapReason.NOT_MARKED_PAID, top.reason)
        assertEquals(missing.id, top.job.id)
        assertTrue(top.exactMatch)
        assertTrue(top.message.startsWith("RO 4471 Front brakes"))
    }

    @Test fun splitMismatch() {
        val split = job(300, split = 50, status = JobStatus.PAID, paid = "2026-10-05")
        val r = rec(300, listOf(split))
        assertEquals(150, r.gapHundredths)
        assertEquals(GapReason.SPLIT_MISMATCH, r.explanations.first().reason)
        assertTrue(r.explanations.first().exactMatch)
    }

    @Test fun warrantyCut() {
        val w = job(300, type = LaborType.WARRANTY, adjusted = 200, status = JobStatus.PAID, paid = "2026-10-05")
        val r = rec(300, listOf(w))
        assertEquals(100, r.gapHundredths)
        assertEquals(GapReason.WARRANTY_CUT, r.explanations.first().reason)
        assertEquals(100, r.explanations.first().deltaHundredths)
    }

    @Test fun paidInOtherPeriod() {
        val other = job(200, work = "2026-10-06", status = JobStatus.PAID, paid = "2026-10-12")
        val r = rec(200, listOf(other))
        assertEquals(200, r.gapHundredths)
        assertEquals(GapReason.PAID_IN_OTHER_PERIOD, r.explanations.first().reason)
    }

    @Test fun negativeGapListsJobsMarkedPaidHere() {
        val a = job(100, status = JobStatus.PAID, paid = "2026-10-05")
        val b = job(250, status = JobStatus.PAID, paid = "2026-10-05")
        val r = rec(100, listOf(a, b))
        assertEquals(-250, r.gapHundredths)
        val top = r.explanations.first()
        assertEquals(b.id, top.job.id)
        assertTrue(top.exactMatch)
    }

    @Test fun includesHourlyAndTopUpInRecorded() {
        val p = plan.copy(guarantee = Guarantee.Flat(1000))
        val r = rec(1000, listOf(job(400, status = JobStatus.PAID, paid = "2026-10-05")), p)
        assertEquals(1000, r.recordedHundredths)
        assertEquals(0, r.gapHundredths)
    }

    @Test fun grossGap() {
        val r = rec(100, listOf(job(100, status = JobStatus.PAID, paid = "2026-10-05")), gross = 3100)
        assertEquals(100L, r.grossGapCents)
    }
}
