package com.jeremy.flaghours.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jeremy.flaghours.domain.HourlyWork
import com.jeremy.flaghours.domain.JobLine
import com.jeremy.flaghours.domain.JobStatus
import com.jeremy.flaghours.domain.LaborType
import com.jeremy.flaghours.domain.ShiftSpan
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "repair_orders", indices = [Index("roNumber")])
data class RepairOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roNumber: String,
    val openedDate: LocalDate,
    val vin: String? = null,
    val year: Int? = null,
    val make: String? = null,
    val model: String? = null,
    val engine: String? = null,
    val photoUri: String? = null,
    val notes: String? = null,
) {
    val vehicleLabel: String
        get() = listOfNotNull(year?.toString(), make, model).joinToString(" ")
}

@Entity(
    tableName = "jobs",
    foreignKeys = [
        ForeignKey(entity = RepairOrder::class, parentColumns = ["id"], childColumns = ["roId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("roId"), Index("workDate"), Index("paidPeriodStart"), Index("status"), Index("comebackOfJobId")],
)
data class Job(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roId: Long,
    val description: String,
    val laborType: LaborType = LaborType.CUSTOMER,
    val flaggedHundredths: Int,
    val adjustedHundredths: Int? = null,
    val mySplitPct: Int = 100,
    val workDate: LocalDate,
    val status: JobStatus = JobStatus.OPEN,
    val paidPeriodStart: LocalDate? = null,
    val comebackOfJobId: Long? = null,
    val refLowHundredths: Int? = null,
    val refHighHundredths: Int? = null,
    val refConfidence: String? = null,
) {
    fun toLine(roNumber: String = "") = JobLine(
        id = id,
        roId = roId,
        description = description,
        laborType = laborType,
        flaggedHundredths = flaggedHundredths,
        adjustedHundredths = adjustedHundredths,
        mySplitPct = mySplitPct,
        workDate = workDate,
        status = status,
        paidPeriodStart = paidPeriodStart,
        comebackOfJobId = comebackOfJobId,
        roNumber = roNumber,
    )

    /** Copy the fields the pay engine may change back onto this entity. */
    fun withLine(line: JobLine) = copy(status = line.status, paidPeriodStart = line.paidPeriodStart)
}

/** A job joined with its RO number and vehicle, for lists. */
data class JobWithRo(
    @Embedded val job: Job,
    @ColumnInfo(name = "roNumber") val roNumber: String,
    @ColumnInfo(name = "roMake") val make: String?,
    @ColumnInfo(name = "roModel") val model: String?,
    @ColumnInfo(name = "roYear") val year: Int?,
) {
    fun toLine() = job.toLine(roNumber)
}

@Entity(tableName = "shifts", indices = [Index("start")])
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val start: LocalDateTime,
    val end: LocalDateTime? = null,
) {
    fun toSpan() = ShiftSpan(id, start, end)
}

@Entity(tableName = "hourly_entries", indices = [Index("date")])
data class HourlyEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val minutes: Int,
    val reason: String,
) {
    fun toWork() = HourlyWork(id, date, minutes, reason)
}

@Entity(tableName = "job_templates", indices = [Index(value = ["description", "make", "model"], unique = true)])
data class JobTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val make: String? = null,
    val model: String? = null,
    val flaggedHundredths: Int,
    val timesUsed: Int = 1,
)

@Entity(tableName = "pay_stubs")
data class PayStub(
    @PrimaryKey val periodStart: LocalDate,
    val hoursHundredths: Int,
    val grossCents: Long? = null,
)

@Entity(tableName = "labor_ref_cache", primaryKeys = ["make", "model", "year", "engine", "jobSlug"])
data class LaborRefCache(
    val make: String,
    val model: String,
    val year: Int,
    val engine: String,
    val jobSlug: String,
    val job: String,
    val hoursHundredths: Int?,
    val lowHundredths: Int?,
    val highHundredths: Int?,
    val confidence: String?,
    val fetchedAt: Long,
)
