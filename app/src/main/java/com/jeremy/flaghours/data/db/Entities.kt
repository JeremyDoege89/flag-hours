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
    /** Reference catalog job this line was matched to, e.g. "Radiator". */
    val refJob: String? = null,
    val refHoursHundredths: Int? = null,
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
    /** Reference catalog job slug the user picked for this description (auto-applied next time). */
    val refJobSlug: String? = null,
)

@Entity(tableName = "pay_stubs")
data class PayStub(
    @PrimaryKey val periodStart: LocalDate,
    val hoursHundredths: Int,
    val grossCents: Long? = null,
)

/** A vehicle whose reference labor times have been fetched (one call per vehicle). */
@Entity(tableName = "olp_vehicles")
data class OlpVehicle(
    @PrimaryKey val vehicleKey: String,
    val makeSlug: String,
    val modelSlug: String,
    val year: Int,
    val rowCount: Int,
    val fetchedAt: Long,
)

/** Maps an RO's year/make/model text to a resolved catalog vehicle and the chosen engine. */
@Entity(tableName = "olp_vehicle_aliases")
data class OlpVehicleAlias(
    @PrimaryKey val aliasKey: String,
    val vehicleKey: String,
    /** Chosen engine slug; null = not chosen yet, "" = use all rows. */
    val engineSlug: String?,
)

/** Cached labor-time rows for a vehicle (all engines). */
@Entity(tableName = "labor_rows", primaryKeys = ["vehicleKey", "engine", "jobSlug"], indices = [Index("vehicleKey")])
data class LaborRowEntity(
    val vehicleKey: String,
    val engine: String,
    val jobSlug: String,
    val job: String,
    val category: String?,
    val hoursHundredths: Int?,
    val lowHundredths: Int?,
    val highHundredths: Int?,
    val confidence: String?,
) {
    fun toRow() = com.jeremy.flaghours.domain.LaborRow(job, jobSlug, category, hoursHundredths, lowHundredths, highHundredths, confidence, engine)
}

/** Cached /vehicles responses (makes list, models per make) as raw JSON. */
@Entity(tableName = "olp_catalog")
data class OlpCatalog(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAt: Long,
)
