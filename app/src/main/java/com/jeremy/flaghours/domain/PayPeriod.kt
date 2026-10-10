package com.jeremy.flaghours.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** An inclusive date range [start]..[end]. */
data class PayPeriod(val start: LocalDate, val end: LocalDate) {
    init {
        require(!end.isBefore(start)) { "end before start" }
    }

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)

    val startTime: LocalDateTime get() = start.atStartOfDay()

    /** Exclusive end instant (midnight after [end]). */
    val endTimeExclusive: LocalDateTime get() = end.plusDays(1).atStartOfDay()

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    companion object {

        fun forDate(date: LocalDate, plan: PayPlan): PayPeriod = when (plan.period) {
            PeriodType.WEEKLY -> {
                val dow = DayOfWeek.of(plan.periodStartDay.coerceIn(1, 7))
                val start = date.with(TemporalAdjusters.previousOrSame(dow))
                PayPeriod(start, start.plusDays(6))
            }
            PeriodType.BIWEEKLY -> {
                val anchor = plan.biweeklyAnchorEpochDay?.let { LocalDate.ofEpochDay(it) }
                    ?: defaultBiweeklyAnchor(plan.periodStartDay)
                val diff = ChronoUnit.DAYS.between(anchor, date)
                val offset = Math.floorMod(diff, 14L)
                val start = date.minusDays(offset)
                PayPeriod(start, start.plusDays(13))
            }
            PeriodType.SEMI_MONTHLY -> {
                if (date.dayOfMonth <= 15) {
                    PayPeriod(date.withDayOfMonth(1), date.withDayOfMonth(15))
                } else {
                    PayPeriod(date.withDayOfMonth(16), date.with(TemporalAdjusters.lastDayOfMonth()))
                }
            }
        }

        /** The period starting on [start] (or containing it, if [start] isn't a boundary). */
        fun startingOn(start: LocalDate, plan: PayPlan): PayPeriod = forDate(start, plan)

        fun previous(period: PayPeriod, plan: PayPlan): PayPeriod = forDate(period.start.minusDays(1), plan)

        fun next(period: PayPeriod, plan: PayPlan): PayPeriod = forDate(period.end.plusDays(1), plan)

        /** Monday 2024-01-01 shifted to the configured start weekday; any fixed date works as an anchor. */
        private fun defaultBiweeklyAnchor(startDay: Int): LocalDate =
            LocalDate.of(2024, 1, 1).plusDays((startDay.coerceIn(1, 7) - 1).toLong())
    }
}
