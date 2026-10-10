package com.jeremy.flaghours.domain

import java.time.LocalDate
import java.time.LocalDateTime

object Fixtures {
    fun d(s: String): LocalDate = LocalDate.parse(s)
    fun t(s: String): LocalDateTime = LocalDateTime.parse(s)

    /** Weekly, Monday start, $30 flag, $20 hourly. Week of 2026-10-05..11. */
    val plan = PayPlan(flagRateCents = 3000, hourlyRateCents = 2000, period = PeriodType.WEEKLY, periodStartDay = 1)
    val week = PayPeriod(d("2026-10-05"), d("2026-10-11"))
    val lastWeek = PayPeriod(d("2026-09-28"), d("2026-10-04"))
    val now: LocalDateTime = t("2026-10-12T08:00")

    private var nextId = 1L

    fun job(
        hours: Int,
        work: String = "2026-10-06",
        status: JobStatus = JobStatus.OPEN,
        paid: String? = null,
        type: LaborType = LaborType.CUSTOMER,
        split: Int = 100,
        adjusted: Int? = null,
        comebackOf: Long? = null,
        id: Long = nextId++,
        ro: String = "",
        desc: String = "Job $id",
    ) = JobLine(
        id = id,
        roId = 1,
        description = desc,
        laborType = type,
        flaggedHundredths = hours,
        adjustedHundredths = adjusted,
        mySplitPct = split,
        workDate = d(work),
        status = status,
        paidPeriodStart = paid?.let { d(it) },
        comebackOfJobId = comebackOf,
        roNumber = ro,
    )

    fun shift(start: String, end: String?, id: Long = nextId++) = ShiftSpan(id, t(start), end?.let { t(it) })
}
