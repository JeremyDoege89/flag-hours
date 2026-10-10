package com.jeremy.flaghours.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

private const val JOB_WITH_RO =
    "SELECT jobs.*, r.roNumber AS roNumber, r.make AS roMake, r.model AS roModel, r.year AS roYear " +
        "FROM jobs JOIN repair_orders r ON r.id = jobs.roId"

@Dao
interface RepairOrderDao {
    @Insert suspend fun insert(ro: RepairOrder): Long
    @Update suspend fun update(ro: RepairOrder)
    @Delete suspend fun delete(ro: RepairOrder)

    @Query("SELECT * FROM repair_orders WHERE id = :id") fun observe(id: Long): Flow<RepairOrder?>
    @Query("SELECT * FROM repair_orders WHERE id = :id") suspend fun get(id: Long): RepairOrder?
    @Query("SELECT * FROM repair_orders WHERE roNumber = :roNumber LIMIT 1") suspend fun findByNumber(roNumber: String): RepairOrder?
    @Query("SELECT * FROM repair_orders ORDER BY openedDate DESC, id DESC") fun observeAll(): Flow<List<RepairOrder>>
    @Query("SELECT * FROM repair_orders ORDER BY id") suspend fun all(): List<RepairOrder>
    @Query("DELETE FROM repair_orders") suspend fun deleteAll()
}

@Dao
interface JobDao {
    @Insert suspend fun insert(job: Job): Long
    @Update suspend fun update(job: Job)
    @Update suspend fun updateAll(jobs: List<Job>)
    @Delete suspend fun delete(job: Job)

    @Query("SELECT * FROM jobs WHERE id = :id") suspend fun get(id: Long): Job?
    @Query("SELECT * FROM jobs WHERE roId = :roId ORDER BY id") fun observeForRo(roId: Long): Flow<List<Job>>
    @Query("SELECT * FROM jobs WHERE roId = :roId ORDER BY id") suspend fun forRo(roId: Long): List<Job>
    @Query("$JOB_WITH_RO ORDER BY jobs.workDate DESC, jobs.id DESC") fun observeAllWithRo(): Flow<List<JobWithRo>>
    @Query("$JOB_WITH_RO ORDER BY jobs.workDate DESC, jobs.id DESC") suspend fun allWithRo(): List<JobWithRo>
    @Query("SELECT * FROM jobs ORDER BY id") suspend fun all(): List<Job>
    @Query("UPDATE jobs SET comebackOfJobId = NULL WHERE comebackOfJobId = :id") suspend fun clearComebackRefs(id: Long)
    @Query("DELETE FROM jobs") suspend fun deleteAll()
}

@Dao
interface ShiftDao {
    @Insert suspend fun insert(shift: Shift): Long
    @Update suspend fun update(shift: Shift)
    @Delete suspend fun delete(shift: Shift)
    @Query("SELECT * FROM shifts ORDER BY start DESC") fun observeAll(): Flow<List<Shift>>
    @Query("SELECT * FROM shifts ORDER BY start") suspend fun all(): List<Shift>
    @Query("SELECT * FROM shifts WHERE `end` IS NULL ORDER BY start DESC LIMIT 1") suspend fun open(): Shift?
    @Query("SELECT * FROM shifts WHERE `end` IS NULL ORDER BY start DESC LIMIT 1") fun observeOpen(): Flow<Shift?>
    @Query("DELETE FROM shifts") suspend fun deleteAll()
}

@Dao
interface HourlyDao {
    @Insert suspend fun insert(entry: HourlyEntry): Long
    @Update suspend fun update(entry: HourlyEntry)
    @Delete suspend fun delete(entry: HourlyEntry)
    @Query("SELECT * FROM hourly_entries ORDER BY date DESC, id DESC") fun observeAll(): Flow<List<HourlyEntry>>
    @Query("SELECT * FROM hourly_entries ORDER BY id") suspend fun all(): List<HourlyEntry>
    @Query("DELETE FROM hourly_entries") suspend fun deleteAll()
}

@Dao
interface JobTemplateDao {
    @Query(
        "SELECT * FROM job_templates WHERE description LIKE '%' || :q || '%' " +
            "ORDER BY (CASE WHEN make = :make AND model = :model THEN 0 WHEN make = :make THEN 1 ELSE 2 END), timesUsed DESC LIMIT :limit",
    )
    suspend fun search(q: String, make: String?, model: String?, limit: Int = 8): List<JobTemplate>

    @Query("SELECT * FROM job_templates WHERE description = :description AND make IS :make AND model IS :model LIMIT 1")
    suspend fun find(description: String, make: String?, model: String?): JobTemplate?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: JobTemplate): Long
    @Update suspend fun update(t: JobTemplate)

    @Transaction
    suspend fun recordUse(description: String, make: String?, model: String?, hundredths: Int) {
        val d = description.trim()
        if (d.isEmpty()) return
        val existing = find(d, make, model)
        if (existing == null) {
            insert(JobTemplate(description = d, make = make, model = model, flaggedHundredths = hundredths))
        } else {
            update(existing.copy(flaggedHundredths = hundredths, timesUsed = existing.timesUsed + 1))
        }
    }
}

@Dao
interface PayStubDao {
    @Upsert suspend fun upsert(stub: PayStub)
    @Delete suspend fun delete(stub: PayStub)
    @Query("SELECT * FROM pay_stubs WHERE periodStart = :start") fun observe(start: LocalDate): Flow<PayStub?>
    @Query("SELECT * FROM pay_stubs ORDER BY periodStart") suspend fun all(): List<PayStub>
    @Query("DELETE FROM pay_stubs") suspend fun deleteAll()
}

@Dao
interface LaborRefDao {
    @Query("SELECT * FROM labor_ref_cache WHERE make = :make AND model = :model AND year = :year AND engine = :engine AND jobSlug = :jobSlug")
    suspend fun get(make: String, model: String, year: Int, engine: String, jobSlug: String): LaborRefCache?

    @Upsert suspend fun upsert(row: LaborRefCache)

    @Query("SELECT COUNT(*) FROM labor_ref_cache WHERE fetchedAt >= :since") suspend fun fetchedSince(since: Long): Int
}
