package com.jeremy.flaghours.domain

import java.time.LocalDate
import java.time.LocalDateTime

enum class LaborType(val label: String) {
    CUSTOMER("Customer pay"),
    WARRANTY("Warranty"),
    INTERNAL("Internal"),
    DIAG("Diag"),
}

enum class JobStatus(val label: String) {
    OPEN("Open"),
    CLOSED("Closed"),
    PAID("Paid"),
}

/** One labor line as flagged on an RO. Pure-Kotlin mirror of the Room entity. */
data class JobLine(
    val id: Long,
    val roId: Long,
    val description: String,
    val laborType: LaborType,
    val flaggedHundredths: Int,
    val adjustedHundredths: Int? = null,
    val mySplitPct: Int = 100,
    val workDate: LocalDate,
    val status: JobStatus = JobStatus.OPEN,
    val paidPeriodStart: LocalDate? = null,
    val comebackOfJobId: Long? = null,
    val roNumber: String = "",
) {
    /** Hours the shop actually pays for the line (warranty adjustment overrides the flag). */
    val effectiveHundredths: Int get() = adjustedHundredths ?: flaggedHundredths

    /** Positive when the adjustment cut time, negative when it added time, null when not adjusted. */
    val adjustmentDiff: Int? get() = adjustedHundredths?.let { flaggedHundredths - it }
}

data class ShiftSpan(val id: Long, val start: LocalDateTime, val end: LocalDateTime?)

data class HourlyWork(val id: Long, val date: LocalDate, val minutes: Int, val reason: String)
