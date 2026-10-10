package com.jeremy.flaghours.domain

import com.jeremy.flaghours.domain.Fixtures.d
import com.jeremy.flaghours.domain.Fixtures.job
import com.jeremy.flaghours.domain.Fixtures.plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaidPeriodPolicyTest {
    private val today = d("2026-10-14") // week of 10-12

    @Test fun workDatePaysInWorkWeek() {
        val p = plan.copy(flagsPayWhen = FlagsPayWhen.WORK_DATE)
        val j = PaidPeriodPolicy.onCreate(job(100, work = "2026-10-01"), today, p)
        assertEquals(d("2026-09-28"), j.paidPeriodStart)
        val closed = PaidPeriodPolicy.onStatusChange(j, JobStatus.PAID, today, p)
        assertEquals(d("2026-09-28"), closed.paidPeriodStart)
        assertEquals(JobStatus.PAID, closed.status)
    }

    @Test fun roClosedPaysInCloseWeek() {
        val p = plan.copy(flagsPayWhen = FlagsPayWhen.RO_CLOSED)
        val j = PaidPeriodPolicy.onCreate(job(100, work = "2026-10-01"), today, p)
        assertNull(j.paidPeriodStart)
        val closed = PaidPeriodPolicy.onStatusChange(j, JobStatus.CLOSED, today, p)
        assertEquals(d("2026-10-12"), closed.paidPeriodStart)
        // Marking paid later keeps the close week.
        val paid = PaidPeriodPolicy.onStatusChange(closed, JobStatus.PAID, d("2026-10-20"), p)
        assertEquals(d("2026-10-12"), paid.paidPeriodStart)
        // Reopening clears it.
        assertNull(PaidPeriodPolicy.onStatusChange(paid, JobStatus.OPEN, today, p).paidPeriodStart)
    }

    @Test fun markedPaidPaysInMarkWeek() {
        val p = plan.copy(flagsPayWhen = FlagsPayWhen.MARKED_PAID)
        val j = job(100, work = "2026-10-01")
        val closed = PaidPeriodPolicy.onStatusChange(j, JobStatus.CLOSED, today, p)
        assertNull(closed.paidPeriodStart)
        val paid = PaidPeriodPolicy.onStatusChange(closed, JobStatus.PAID, today, p)
        assertEquals(d("2026-10-12"), paid.paidPeriodStart)
        assertNull(PaidPeriodPolicy.onStatusChange(paid, JobStatus.CLOSED, today, p).paidPeriodStart)
    }

    @Test fun createdAlreadyPaid() {
        val p = plan.copy(flagsPayWhen = FlagsPayWhen.MARKED_PAID)
        val j = PaidPeriodPolicy.onCreate(job(100, status = JobStatus.PAID), today, p)
        assertEquals(d("2026-10-12"), j.paidPeriodStart)
    }
}
