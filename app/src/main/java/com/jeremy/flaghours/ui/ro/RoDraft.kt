package com.jeremy.flaghours.ui.ro

import com.jeremy.flaghours.data.db.Job
import com.jeremy.flaghours.data.db.RepairOrder
import com.jeremy.flaghours.domain.LaborType
import java.time.LocalDate

data class JobDraft(
    val key: Long,
    val description: String = "",
    val laborType: LaborType = LaborType.CUSTOMER,
    val hours: Int? = null,
)

/** Editable RO before it is saved (manual entry or scan result). */
data class RoDraft(
    val roNumber: String = "",
    val openedDate: LocalDate = LocalDate.now(),
    val vin: String = "",
    val year: String = "",
    val make: String = "",
    val model: String = "",
    val engine: String = "",
    val notes: String = "",
    val photoUri: String? = null,
    val jobs: List<JobDraft> = listOf(JobDraft(key = 1)),
) {
    fun toRepairOrder(id: Long = 0) = RepairOrder(
        id = id,
        roNumber = roNumber.trim(),
        openedDate = openedDate,
        vin = vin.trim().uppercase().ifEmpty { null },
        year = year.trim().toIntOrNull(),
        make = make.trim().ifEmpty { null },
        model = model.trim().ifEmpty { null },
        engine = engine.trim().ifEmpty { null },
        photoUri = photoUri,
        notes = notes.trim().ifEmpty { null },
    )

    /** Lines with hours become jobs; blank lines are dropped. */
    fun toJobs(workDate: LocalDate): List<Job> = jobs
        .filter { it.hours != null && (it.description.isNotBlank() || it.hours > 0) }
        .map {
            Job(
                roId = 0,
                description = it.description.trim().ifEmpty { "Labor" },
                laborType = it.laborType,
                flaggedHundredths = it.hours!!,
                workDate = workDate,
            )
        }

    companion object {
        fun from(ro: RepairOrder) = RoDraft(
            roNumber = ro.roNumber,
            openedDate = ro.openedDate,
            vin = ro.vin ?: "",
            year = ro.year?.toString() ?: "",
            make = ro.make ?: "",
            model = ro.model ?: "",
            engine = ro.engine ?: "",
            notes = ro.notes ?: "",
            photoUri = ro.photoUri,
            jobs = emptyList(),
        )
    }
}
