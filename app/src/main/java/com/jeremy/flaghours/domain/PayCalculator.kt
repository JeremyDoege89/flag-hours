package com.jeremy.flaghours.domain

import java.time.Duration
import java.time.LocalDateTime

data class PaySummary(
    val period: PayPeriod,
    /** Σ my share of jobs whose work date is in the period. */
    val flaggedWorked: Int,
    /** Σ my share of jobs paid in the period. */
    val flaggedPaid: Int,
    /** Σ my share of jobs worked on or before period end that aren't PAID yet. */
    val stillOwed: Int,
    val clockedMinutes: Long,
    /** flaggedWorked ÷ clocked, in tenths of a percent (1234 = 123.4%). Null when nothing clocked. */
    val efficiencyPermille: Int?,
    val paidByType: Map<LaborType, Int>,
    val flagPayCents: Long,
    val hourlyMinutes: Long,
    val hourlyPayCents: Long,
    val guaranteeTopUpHundredths: Int,
    val topUpCents: Long,
    val tierMultiplierPct: Int,
    val tierBonusCents: Long,
    val overtimeCents: Long,
    val grossCents: Long,
    /** Human-readable lines showing exactly how the gross was calculated. */
    val math: List<String>,
) {
    val clockedHundredths: Int get() = Units.minutesToHundredths(clockedMinutes)
    val hourlyHundredths: Int get() = Units.minutesToHundredths(hourlyMinutes)
}

object PayCalculator {

    /**
     * My share of one job in hundredths: (adjusted ?: flagged) × split ÷ 100, with the comeback rule applied.
     * CLAWBACK returns the negative of the original job's share.
     */
    fun myShare(job: JobLine, plan: PayPlan, jobsById: Map<Long, JobLine>): Int {
        val base = baseShare(job)
        if (job.comebackOfJobId == null) return base
        return when (plan.comebackRule) {
            ComebackRule.PAID -> base
            ComebackRule.ZERO_PAY -> 0
            ComebackRule.CLAWBACK -> -(jobsById[job.comebackOfJobId]?.let { baseShare(it) } ?: 0)
            ComebackRule.SPLIT -> Units.mulDivRound(base.toLong(), plan.comebackSplitPct.toLong(), 100).toInt()
        }
    }

    /** Labor type the share is paid at; a clawback is charged at the original job's rate. */
    fun shareType(job: JobLine, plan: PayPlan, jobsById: Map<Long, JobLine>): LaborType {
        if (job.comebackOfJobId != null && plan.comebackRule == ComebackRule.CLAWBACK) {
            return jobsById[job.comebackOfJobId]?.laborType ?: job.laborType
        }
        return job.laborType
    }

    private fun baseShare(job: JobLine): Int =
        Units.mulDivRound(job.effectiveHundredths.toLong(), job.mySplitPct.toLong(), 100).toInt()

    /** Minutes of [shifts] that fall inside [period]; an open shift counts up to [now]. */
    fun clockedMinutes(shifts: List<ShiftSpan>, period: PayPeriod, now: LocalDateTime): Long =
        shifts.sumOf { s ->
            val end = s.end ?: now
            val from = maxOf(s.start, period.startTime)
            val to = minOf(end, period.endTimeExclusive)
            if (to.isAfter(from)) Duration.between(from, to).toMinutes() else 0L
        }

    fun summarize(
        period: PayPeriod,
        jobs: List<JobLine>,
        shifts: List<ShiftSpan>,
        hourly: List<HourlyWork>,
        plan: PayPlan,
        now: LocalDateTime,
    ): PaySummary {
        val byId = jobs.associateBy { it.id }
        fun h(x: Int) = Units.formatHours(x, 2)
        fun m(x: Long) = Units.formatMoney(x)

        var worked = 0
        var paid = 0
        var owed = 0
        val paidByType = LaborType.entries.associateWith { 0 }.toMutableMap()
        for (job in jobs) {
            val share = myShare(job, plan, byId)
            if (job.workDate in period) worked += share
            if (job.paidPeriodStart != null && job.paidPeriodStart == period.start) {
                paid += share
                val t = shareType(job, plan, byId)
                paidByType[t] = paidByType.getValue(t) + share
            }
            if (!job.workDate.isAfter(period.end) && job.status != JobStatus.PAID) owed += share
        }

        val clockedMin = clockedMinutes(shifts, period, now)
        val clockedH = Units.minutesToHundredths(clockedMin)
        val efficiency = if (clockedMin > 0) Units.mulDivRound(worked.toLong(), 600, clockedMin).toInt() else null

        val math = mutableListOf<String>()
        var flagPay = 0L
        for (t in LaborType.entries) {
            val hrs = paidByType.getValue(t)
            if (hrs == 0) continue
            val rate = plan.rateFor(t)
            val pay = Units.payCents(hrs, rate)
            flagPay += pay
            math += "${t.label}: ${h(hrs)} h × ${m(rate)} = ${m(pay)}"
        }

        val hourlyMin = hourly.filter { it.date in period }.sumOf { it.minutes.toLong() }
        val hourlyPay = Units.mulDivRound(hourlyMin, plan.hourlyRateCents, 60)
        if (hourlyMin > 0) {
            math += "Hourly: ${h(Units.minutesToHundredths(hourlyMin))} h ($hourlyMin min) × ${m(plan.hourlyRateCents)} = ${m(hourlyPay)}"
        }

        val topUp = when (val g = plan.guarantee) {
            Guarantee.None -> 0
            is Guarantee.Flat -> maxOf(0, g.hoursHundredths - paid)
            is Guarantee.IfClocked -> if (clockedH >= g.minClockedHundredths) maxOf(0, g.hoursHundredths - paid) else 0
        }
        val topUpPay = Units.payCents(topUp, plan.flagRateCents)
        when (val g = plan.guarantee) {
            Guarantee.None -> Unit
            is Guarantee.Flat ->
                math += "Guarantee ${h(g.hoursHundredths)} h − paid ${h(paid)} h = top-up ${h(topUp)} h × ${m(plan.flagRateCents)} = ${m(topUpPay)}"
            is Guarantee.IfClocked ->
                math += if (clockedH >= g.minClockedHundredths) {
                    "Guarantee ${h(g.hoursHundredths)} h (clocked ${h(clockedH)} ≥ ${h(g.minClockedHundredths)}) − paid ${h(paid)} h = top-up ${h(topUp)} h × ${m(plan.flagRateCents)} = ${m(topUpPay)}"
                } else {
                    "Guarantee not met: clocked ${h(clockedH)} h < ${h(g.minClockedHundredths)} h required"
                }
        }

        val tier = if (efficiency == null) null else
            plan.efficiencyTiers.filter { efficiency >= it.minEfficiencyPct * 10 }.maxByOrNull { it.minEfficiencyPct }
        val tierPct = tier?.multiplierPct ?: 100
        val tierBonus = if (tier != null) Units.mulDivRound(flagPay + topUpPay, (tierPct - 100).toLong(), 100) else 0L
        if (tier != null) {
            math += "Efficiency ${Units.formatPermille(efficiency!!)} ≥ ${tier.minEfficiencyPct}%: flag pay ${m(flagPay + topUpPay)} × ${tierPct - 100}% = ${m(tierBonus)}"
        }

        val straight = flagPay + hourlyPay + topUpPay + tierBonus
        val ot = plan.overtime
        var otPay = 0L
        if (ot != null) {
            val prem = (ot.multiplierPct - 100).toLong()
            when (ot.mode) {
                OvertimeMode.REGULAR_RATE -> {
                    val over = clockedH - ot.thresholdHundredths
                    if (over > 0 && clockedH > 0) {
                        otPay = Units.mulDivRound(straight * over, prem, clockedH.toLong() * 100)
                        val regRate = Units.mulDivRound(straight, 100, clockedH.toLong())
                        math += "Overtime: regular rate ${m(straight)} ÷ ${h(clockedH)} h = ${m(regRate)}/h; " +
                            "${h(over)} h over ${h(ot.thresholdHundredths)} × $prem% = ${m(otPay)}"
                    } else {
                        math += "Overtime: clocked ${h(clockedH)} h ≤ ${h(ot.thresholdHundredths)} h, none"
                    }
                }
                OvertimeMode.FLAG_HOURS -> {
                    val over = paid - ot.thresholdHundredths
                    if (over > 0) {
                        otPay = Units.mulDivRound(Units.payCents(over, plan.flagRateCents), prem, 100)
                        math += "Overtime: ${h(over)} flag h over ${h(ot.thresholdHundredths)} × ${m(plan.flagRateCents)} × $prem% = ${m(otPay)}"
                    } else {
                        math += "Overtime: paid ${h(paid)} flag h ≤ ${h(ot.thresholdHundredths)} h, none"
                    }
                }
                OvertimeMode.CLOCKED_HOURLY -> {
                    val over = clockedH - ot.thresholdHundredths
                    if (over > 0) {
                        otPay = Units.mulDivRound(Units.payCents(over, plan.hourlyRateCents), prem, 100)
                        math += "Overtime: ${h(over)} clocked h over ${h(ot.thresholdHundredths)} × ${m(plan.hourlyRateCents)} × $prem% = ${m(otPay)}"
                    } else {
                        math += "Overtime: clocked ${h(clockedH)} h ≤ ${h(ot.thresholdHundredths)} h, none"
                    }
                }
            }
        }

        val gross = straight + otPay
        math += "Estimated gross: ${m(gross)}"

        return PaySummary(
            period = period,
            flaggedWorked = worked,
            flaggedPaid = paid,
            stillOwed = owed,
            clockedMinutes = clockedMin,
            efficiencyPermille = efficiency,
            paidByType = paidByType.toMap(),
            flagPayCents = flagPay,
            hourlyMinutes = hourlyMin,
            hourlyPayCents = hourlyPay,
            guaranteeTopUpHundredths = topUp,
            topUpCents = topUpPay,
            tierMultiplierPct = tierPct,
            tierBonusCents = tierBonus,
            overtimeCents = otPay,
            grossCents = gross,
            math = math,
        )
    }
}
