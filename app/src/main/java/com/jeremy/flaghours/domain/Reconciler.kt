package com.jeremy.flaghours.domain

import kotlin.math.abs

enum class GapReason(val label: String) {
    NOT_MARKED_PAID("Not marked paid"),
    SPLIT_MISMATCH("Split may differ"),
    WARRANTY_CUT("Warranty adjustment"),
    PAID_IN_OTHER_PERIOD("Paid in another period"),
    MARKED_PAID_HERE("Marked paid this period"),
}

/**
 * One job that could explain part of the paycheck gap.
 * [deltaHundredths] is how much "my recorded paid hours" would change if this explanation is true,
 * i.e. a positive delta closes a positive gap (stub paid more than recorded).
 */
data class GapExplanation(
    val reason: GapReason,
    val job: JobLine,
    val deltaHundredths: Int,
    val message: String,
    /** This one job alone exactly accounts for the gap. */
    val exactMatch: Boolean,
)

data class Reconciliation(
    val stubHundredths: Int,
    val recordedHundredths: Int,
    /** stub − (flaggedPaid + hourly + guarantee top-up). Positive: the check paid more than recorded. */
    val gapHundredths: Int,
    val explanations: List<GapExplanation>,
    val stubGrossCents: Long?,
    val grossGapCents: Long?,
)

object Reconciler {

    fun reconcile(
        stubHundredths: Int,
        stubGrossCents: Long?,
        summary: PaySummary,
        jobs: List<JobLine>,
        plan: PayPlan,
    ): Reconciliation {
        val period = summary.period
        val byId = jobs.associateBy { it.id }
        val recorded = summary.flaggedPaid + summary.hourlyHundredths + summary.guaranteeTopUpHundredths
        val gap = stubHundredths - recorded
        val d = plan.decimals.coerceAtLeast(1)
        fun h(x: Int) = Units.formatHours(x, d)
        fun ro(j: JobLine) = if (j.roNumber.isNotBlank()) "RO ${j.roNumber} " else ""

        val out = mutableListOf<GapExplanation>()
        if (gap != 0) {
            for (job in jobs) {
                val share = PayCalculator.myShare(job, plan, byId)
                val paidHere = job.paidPeriodStart == period.start
                val workedByEnd = !job.workDate.isAfter(period.end)

                if (gap > 0) {
                    if (workedByEnd && job.paidPeriodStart == null && job.status != JobStatus.PAID && share != 0) {
                        out += GapExplanation(
                            GapReason.NOT_MARKED_PAID, job, share,
                            "${ro(job)}${job.description}: ${h(share)} h not marked paid. If it was on this check, mark it paid in this period.",
                            share == gap,
                        )
                    }
                    if (job.paidPeriodStart != null && !paidHere && job.workDate in period && share != 0) {
                        out += GapExplanation(
                            GapReason.PAID_IN_OTHER_PERIOD, job, share,
                            "${ro(job)}${job.description}: worked this period but recorded as paid in the period starting ${job.paidPeriodStart}.",
                            share == gap,
                        )
                    }
                    if (paidHere && job.mySplitPct < 100) {
                        val full = job.effectiveHundredths
                        val delta = full - share
                        out += GapExplanation(
                            GapReason.SPLIT_MISMATCH, job, delta,
                            "${ro(job)}${job.description}: you recorded a ${job.mySplitPct}% split (${h(share)} h). Paid at 100% would be ${h(full)} h (+${h(delta)}).",
                            delta == gap,
                        )
                    }
                    val cut = job.adjustmentDiff
                    if (paidHere && cut != null && cut > 0) {
                        val delta = Units.mulDivRound(cut.toLong(), job.mySplitPct.toLong(), 100).toInt()
                        out += GapExplanation(
                            GapReason.WARRANTY_CUT, job, delta,
                            "${ro(job)}${job.description}: warranty cut from ${h(job.flaggedHundredths)} to ${h(job.adjustedHundredths!!)} h. If paid as flagged, +${h(delta)} h.",
                            delta == gap,
                        )
                    }
                } else {
                    if (paidHere && share > 0) {
                        out += GapExplanation(
                            GapReason.MARKED_PAID_HERE, job, -share,
                            "${ro(job)}${job.description}: ${h(share)} h marked paid this period. If it wasn't on this check, it may be paid next time.",
                            -share == gap,
                        )
                    }
                    if (paidHere && job.adjustedHundredths == null && job.laborType == LaborType.WARRANTY && share > 0) {
                        out += GapExplanation(
                            GapReason.WARRANTY_CUT, job, 0,
                            "${ro(job)}${job.description}: warranty line with no adjustment recorded. Check whether the manufacturer cut the time.",
                            false,
                        )
                    }
                    if (paidHere && job.mySplitPct == 100 && share > 0) {
                        // Could have been split with another tech on the shop's side.
                        val half = Units.mulDivRound(share.toLong(), 50, 100).toInt()
                        if (half == -gap) {
                            out += GapExplanation(
                                GapReason.SPLIT_MISMATCH, job, -half,
                                "${ro(job)}${job.description}: paid at 50% would be exactly the gap. Was this split with another tech?",
                                true,
                            )
                        }
                    }
                }
            }
        }

        val sorted = out.sortedWith(
            compareByDescending<GapExplanation> { it.exactMatch }
                .thenBy { abs(gap - it.deltaHundredths) }
                .thenBy { it.job.workDate },
        )
        return Reconciliation(
            stubHundredths = stubHundredths,
            recordedHundredths = recorded,
            gapHundredths = gap,
            explanations = sorted,
            stubGrossCents = stubGrossCents,
            grossGapCents = stubGrossCents?.let { it - summary.grossCents },
        )
    }
}
