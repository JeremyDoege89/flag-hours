package com.jeremy.flaghours.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeremy.flaghours.data.db.FlagHoursDatabase
import com.jeremy.flaghours.data.db.HourlyEntry
import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.db.PayStub
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.data.db.Shift
import com.jeremy.flaghours.data.settings.PayPlanStore
import com.jeremy.flaghours.domain.FlagsPayWhen
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.LaborType
import com.jeremy.flaghours.domain.PayPlan
import androidx.datastore.core.DataStoreFactory
import com.jeremy.flaghours.data.settings.PayPlanSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {
    private lateinit var db: FlagHoursDatabase
    private lateinit var repo: FlagHoursRepository
    private lateinit var scope: CoroutineScope
    private var now = LocalDateTime.parse("2026-10-07T09:00")

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FlagHoursDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File.createTempFile("plan", ".json").also { it.delete() }
        val store = PayPlanStore(DataStoreFactory.create(PayPlanSerializer, scope = scope, produceFile = { file }))
        repo = FlagHoursRepository(db, store) { now }
        runBlocking { repo.savePlan(PayPlan(flagRateCents = 3000, flagsPayWhen = FlagsPayWhen.MARKED_PAID)) }
    }

    @After fun tearDown() {
        db.close()
        scope.cancel()
    }

    @Test fun createRoWithJobsAndMarkPaid() = runBlocking {
        val ro = RepairOrder(roNumber = "4471", openedDate = LocalDate.parse("2026-10-06"), make = "Ford", model = "F-150")
        val roId = repo.createRoWithJobs(
            ro,
            listOf(
                Job(roId = 0, description = "Front brakes", flaggedHundredths = 160, workDate = LocalDate.parse("2026-10-06")),
                Job(roId = 0, description = "Oil change", flaggedHundredths = 30, workDate = LocalDate.parse("2026-10-06"), laborType = LaborType.WARRANTY),
            ),
        )
        val jobs = repo.jobs.first()
        assertEquals(2, jobs.size)
        assertEquals("4471", jobs.first().roNumber)
        assertNull(jobs.first().job.paidPeriodStart)

        val id = jobs.first { it.job.description == "Front brakes" }.job.id
        repo.setJobStatus(id, JobStatus.PAID)
        val paid = db.jobs().get(id)!!
        assertEquals(JobStatus.PAID, paid.status)
        assertEquals(LocalDate.parse("2026-10-05"), paid.paidPeriodStart)

        // Templates remember description + hours per vehicle.
        val t = repo.searchTemplates("brak", "Ford", "F-150")
        assertEquals(160, t.single().flaggedHundredths)

        // Deleting the RO cascades to jobs.
        repo.deleteRo(db.repairOrders().get(roId)!!)
        assertEquals(0, repo.jobs.first().size)
    }

    @Test fun templateUsageCounts() = runBlocking {
        val roId = repo.saveRo(RepairOrder(roNumber = "1", openedDate = LocalDate.parse("2026-10-06")))
        repeat(3) { repo.saveJob(Job(roId = roId, description = "Alignment", flaggedHundredths = 100 + it, workDate = LocalDate.parse("2026-10-06"))) }
        val t = repo.searchTemplates("align", null, null).single()
        assertEquals(3, t.timesUsed)
        assertEquals(102, t.flaggedHundredths)
    }

    @Test fun workDatePlanAssignsPaidPeriodOnCreate() = runBlocking {
        repo.savePlan(repo.currentPlan().copy(flagsPayWhen = FlagsPayWhen.WORK_DATE))
        val roId = repo.saveRo(RepairOrder(roNumber = "2", openedDate = LocalDate.parse("2026-10-01")))
        val id = repo.saveJob(Job(roId = roId, description = "Diag", flaggedHundredths = 100, workDate = LocalDate.parse("2026-10-01")))
        assertEquals(LocalDate.parse("2026-09-28"), db.jobs().get(id)!!.paidPeriodStart)
    }

    @Test fun clockInOut() = runBlocking {
        repo.clockIn()
        repo.clockIn() // no double shift
        assertNotNull(db.shifts().open())
        now = now.plusHours(8)
        repo.clockOut()
        val s = db.shifts().all().single()
        assertEquals(LocalDateTime.parse("2026-10-07T17:00"), s.end)
        assertNull(db.shifts().open())
    }

    @Test fun shiftsHourlyStubsRoundTrip() = runBlocking {
        repo.saveShift(Shift(start = LocalDateTime.parse("2026-10-06T22:00"), end = LocalDateTime.parse("2026-10-07T02:30")))
        repo.saveHourly(HourlyEntry(date = LocalDate.parse("2026-10-06"), minutes = 90, reason = "Training"))
        repo.saveStub(PayStub(LocalDate.parse("2026-10-05"), 4210, 126300))
        repo.saveStub(PayStub(LocalDate.parse("2026-10-05"), 4300, null))
        assertEquals(LocalDateTime.parse("2026-10-07T02:30"), repo.shifts.first().single().end)
        assertEquals(90, repo.hourly.first().single().minutes)
        assertEquals(4300, repo.observeStub(LocalDate.parse("2026-10-05")).first()!!.hoursHundredths)
    }

    @Test fun planPersists() = runBlocking {
        repo.savePlan(PayPlan(flagRateCents = 4150, decimals = 2, setupComplete = true))
        val p = repo.plan.first()
        assertEquals(4150L, p.flagRateCents)
        assertEquals(true, p.setupComplete)
    }
}
