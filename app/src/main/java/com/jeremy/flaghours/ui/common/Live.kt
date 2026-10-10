package com.jeremy.flaghours.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import com.jeremy.flaghours.data.FlagHoursRepository
import com.jeremy.flaghours.data.db.Shift
import com.jeremy.flaghours.domain.PayCalculator
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.domain.PaySummary
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Current time, refreshed every [periodMs] so open shifts tick. */
@Composable
fun rememberNow(repo: FlagHoursRepository, periodMs: Long = 30_000): State<LocalDateTime> =
    produceState(repo.now()) {
        while (true) {
            value = repo.now()
            delay(periodMs)
        }
    }

/** Live inputs for the pay engine. */
class LiveData(
    val plan: PayPlan,
    val jobs: List<com.jeremy.flaghours.data.db.JobWithRo>,
    val shifts: List<Shift>,
    val hourly: List<com.jeremy.flaghours.data.db.HourlyEntry>,
    val now: LocalDateTime,
    val loaded: Boolean,
) {
    fun summary(period: PayPeriod): PaySummary = PayCalculator.summarize(
        period,
        jobs.map { it.toLine() },
        shifts.map { it.toSpan() },
        hourly.map { it.toWork() },
        plan,
        now,
    )
}

@Composable
fun rememberLiveData(repo: FlagHoursRepository): LiveData {
    val plan by remember { repo.plan }.collectAsState(initial = null)
    val jobs by remember { repo.jobs }.collectAsState(initial = emptyList())
    val shifts by remember { repo.shifts }.collectAsState(initial = emptyList())
    val hourly by remember { repo.hourly }.collectAsState(initial = emptyList())
    val now by rememberNow(repo)
    return LiveData(plan ?: PayPlan(), jobs, shifts, hourly, now, plan != null)
}

/** An open shift running longer than this is probably a forgotten clock-out. */
const val FORGOTTEN_SHIFT_HOURS = 14L
