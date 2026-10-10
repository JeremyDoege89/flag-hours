package com.jeremy.flaghours.data

import androidx.room.withTransaction
import com.jeremy.flaghours.data.db.FlagHoursDatabase
import com.jeremy.flaghours.data.db.HourlyEntry
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.db.JobTemplate
import com.jeremy.flaghours.data.db.PayStub
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.data.db.Shift
import com.jeremy.flaghours.data.settings.PayPlanStore
import com.jeremy.flaghours.domain.FlagsPayWhen
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.PaidPeriodPolicy
import com.jeremy.flaghours.domain.PayCalculator
import com.jeremy.flaghours.domain.PayPeriod
import com.jeremy.flaghours.domain.PayPlan
import com.jeremy.flaghours.domain.PaySummary
import com.jeremy.flaghours.domain.HourlyWork
import com.jeremy.flaghours.domain.JobLine
import com.jeremy.flaghours.domain.ShiftSpan
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/** Single entry point for reads and writes. Totals are always computed from these rows, never stored. */
class FlagHoursRepository(
    private val db: FlagHoursDatabase,
    private val planStore: PayPlanStore,
    private val clock: () -> LocalDateTime = { LocalDateTime.now().withNano(0) },
) {
    val plan: Flow<PayPlan> = planStore.plan
    val jobs = db.jobs().observeAllWithRo()
    val repairOrders = db.repairOrders().observeAll()
    val shifts = db.shifts().observeAll()
    val openShift = db.shifts().observeOpen()
    val hourly = db.hourly().observeAll()

    fun now(): LocalDateTime = clock()
    fun today(): LocalDate = clock().toLocalDate()

    suspend fun savePlan(plan: PayPlan) = planStore.save(plan)
    suspend fun currentPlan(): PayPlan = planStore.current()

    // --- Repair orders and jobs ---

    fun observeRo(id: Long) = db.repairOrders().observe(id)
    suspend fun getRo(id: Long) = db.repairOrders().get(id)
    fun observeJobsForRo(roId: Long) = db.jobs().observeForRo(roId)
    suspend fun findRoByNumber(number: String) = db.repairOrders().findByNumber(number.trim())

    suspend fun saveRo(ro: RepairOrder): Long =
        if (ro.id == 0L) db.repairOrders().insert(ro) else ro.id.also { db.repairOrders().update(ro) }

    suspend fun deleteRo(ro: RepairOrder) = db.withTransaction {
        db.jobs().forRo(ro.id).forEach { db.jobs().clearComebackRefs(it.id) }
        db.repairOrders().delete(ro)
    }

    /** Creates an RO with its jobs in one transaction (used by manual entry and scan). */
    suspend fun createRoWithJobs(ro: RepairOrder, jobs: List<Job>): Long = db.withTransaction {
        val roId = db.repairOrders().insert(ro)
        jobs.forEach { saveJob(it.copy(roId = roId), ro) }
        roId
    }

    /**
     * Inserts or updates a job. New jobs get their paid period from the plan; status changes go through
     * [PaidPeriodPolicy]. The description/hours are remembered as an autocomplete template.
     */
    suspend fun saveJob(job: Job, ro: RepairOrder? = null): Long {
        val plan = planStore.current()
        val today = today()
        val vehicle = ro ?: db.repairOrders().get(job.roId)
        db.templates().recordUse(job.description, vehicle?.make, vehicle?.model, job.flaggedHundredths)
        return if (job.id == 0L) {
            val line = PaidPeriodPolicy.onCreate(job.toLine(), today, plan)
            db.jobs().insert(job.withLine(line))
        } else {
            val old = db.jobs().get(job.id)
            val updated = if (old != null && old.status != job.status) {
                job.withLine(PaidPeriodPolicy.onStatusChange(old.toLine().copy(paidPeriodStart = job.paidPeriodStart), job.status, today, plan))
            } else if (plan.flagsPayWhen == FlagsPayWhen.WORK_DATE && old != null && old.workDate != job.workDate) {
                job.withLine(PaidPeriodPolicy.onCreate(job.toLine().copy(paidPeriodStart = null), today, plan))
            } else {
                job
            }
            db.jobs().update(updated)
            job.id
        }
    }

    suspend fun setJobStatus(jobId: Long, status: JobStatus) {
        val job = db.jobs().get(jobId) ?: return
        val line = PaidPeriodPolicy.onStatusChange(job.toLine(), status, today(), planStore.current())
        db.jobs().update(job.withLine(line))
    }

    /** Marks a job paid in a specific period (used from the paycheck screen). */
    suspend fun markPaidInPeriod(jobId: Long, periodStart: LocalDate) {
        val job = db.jobs().get(jobId) ?: return
        db.jobs().update(job.copy(status = JobStatus.PAID, paidPeriodStart = periodStart))
    }

    /** Moves a job's paid period without changing its status. */
    suspend fun movePaidPeriod(jobId: Long, periodStart: LocalDate) {
        val job = db.jobs().get(jobId) ?: return
        db.jobs().update(job.copy(paidPeriodStart = periodStart))
    }

    /** Writes a job exactly as given (used by undo). */
    suspend fun restoreJob(job: Job) = db.jobs().update(job)

    suspend fun getJob(id: Long): Job? = db.jobs().get(id)

    suspend fun deleteJob(job: Job) = db.withTransaction {
        db.jobs().clearComebackRefs(job.id)
        db.jobs().delete(job)
    }

    suspend fun searchTemplates(q: String, make: String?, model: String?): List<JobTemplate> =
        if (q.isBlank()) emptyList() else db.templates().search(q.trim(), make, model)

    // --- Time ---

    suspend fun clockIn() {
        if (db.shifts().open() == null) db.shifts().insert(Shift(start = clock()))
    }

    suspend fun clockOut() {
        val open = db.shifts().open() ?: return
        db.shifts().update(open.copy(end = clock()))
    }

    suspend fun saveShift(shift: Shift) {
        if (shift.id == 0L) db.shifts().insert(shift) else db.shifts().update(shift)
    }

    suspend fun deleteShift(shift: Shift) = db.shifts().delete(shift)

    suspend fun saveHourly(entry: HourlyEntry) {
        if (entry.id == 0L) db.hourly().insert(entry) else db.hourly().update(entry)
    }

    suspend fun deleteHourly(entry: HourlyEntry) = db.hourly().delete(entry)

    // --- Calculation inputs ---

    /** Current rows as pay-engine inputs. */
    suspend fun snapshot(): Snapshot = Snapshot(
        jobs = db.jobs().allWithRo().map { it.toLine() },
        shifts = db.shifts().all().map { it.toSpan() },
        hourly = db.hourly().all().map { it.toWork() },
    )

    fun summarize(snapshot: Snapshot, period: PayPeriod, plan: PayPlan): PaySummary =
        PayCalculator.summarize(period, snapshot.jobs, snapshot.shifts, snapshot.hourly, plan, now())

    // --- Pay stubs ---

    fun observeStub(periodStart: LocalDate): Flow<PayStub?> = db.stubs().observe(periodStart)
    suspend fun saveStub(stub: PayStub) = db.stubs().upsert(stub)
    suspend fun deleteStub(stub: PayStub) = db.stubs().delete(stub)
}

data class Snapshot(val jobs: List<JobLine>, val shifts: List<ShiftSpan>, val hourly: List<HourlyWork>)
