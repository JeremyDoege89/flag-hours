package com.jeremy.flaghours.domain

import java.time.LocalDate

/** Decides which pay period a job is paid in, driven by [PayPlan.flagsPayWhen]. */
object PaidPeriodPolicy {

    /** paidPeriodStart for a newly created job. */
    fun onCreate(job: JobLine, today: LocalDate, plan: PayPlan): JobLine = when (plan.flagsPayWhen) {
        FlagsPayWhen.WORK_DATE -> job.copy(paidPeriodStart = PayPeriod.forDate(job.workDate, plan).start)
        FlagsPayWhen.RO_CLOSED ->
            if (job.status != JobStatus.OPEN) job.copy(paidPeriodStart = job.paidPeriodStart ?: PayPeriod.forDate(today, plan).start) else job
        FlagsPayWhen.MARKED_PAID ->
            if (job.status == JobStatus.PAID) job.copy(paidPeriodStart = job.paidPeriodStart ?: PayPeriod.forDate(today, plan).start) else job
    }

    /** Apply a status change, filling or clearing paidPeriodStart as the plan dictates. */
    fun onStatusChange(job: JobLine, newStatus: JobStatus, today: LocalDate, plan: PayPlan): JobLine {
        val todayStart = PayPeriod.forDate(today, plan).start
        val paid = when (plan.flagsPayWhen) {
            FlagsPayWhen.WORK_DATE -> job.paidPeriodStart ?: PayPeriod.forDate(job.workDate, plan).start
            FlagsPayWhen.RO_CLOSED -> when (newStatus) {
                JobStatus.OPEN -> null
                JobStatus.CLOSED, JobStatus.PAID -> job.paidPeriodStart ?: todayStart
            }
            FlagsPayWhen.MARKED_PAID -> when (newStatus) {
                JobStatus.OPEN, JobStatus.CLOSED -> null
                JobStatus.PAID -> job.paidPeriodStart ?: todayStart
            }
        }
        return job.copy(status = newStatus, paidPeriodStart = paid)
    }
}
