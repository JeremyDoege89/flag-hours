package com.jeremy.flaghours.domain

import java.time.LocalDate
import java.time.LocalDateTime

data class BackupRo(
    val id: Long,
    val roNumber: String,
    val openedDate: LocalDate,
    val vin: String?,
    val year: Int?,
    val make: String?,
    val model: String?,
    val engine: String?,
    val notes: String?,
)

data class BackupJob(
    val line: JobLine,
    val refLowHundredths: Int? = null,
    val refHighHundredths: Int? = null,
    val refConfidence: String? = null,
)

data class BackupStub(val periodStart: LocalDate, val hoursHundredths: Int, val grossCents: Long?)

data class Backup(
    val ros: List<BackupRo>,
    val jobs: List<BackupJob>,
    val shifts: List<ShiftSpan>,
    val hourly: List<HourlyWork>,
    val stubs: List<BackupStub>,
    /** PayPlan as JSON (kept opaque here so domain stays free of the serializer setup). */
    val planJson: String?,
)

class CsvImportException(message: String) : Exception(message)

/**
 * One-file backup in CSV. Every row starts with a record type; each type has its own header row, so the file
 * opens cleanly in a spreadsheet and round-trips exactly (hours as decimal hours, money as dollars, both parsed
 * back to integers without floating point).
 */
object CsvExporter {
    const val MAGIC = "flag-hours-backup"
    const val VERSION = "1"

    private val RO_HEADER = listOf("type", "id", "ro_number", "opened_date", "vin", "year", "make", "model", "engine", "notes")
    private val JOB_HEADER = listOf(
        "type", "id", "ro_id", "ro_number", "description", "labor_type", "flagged_hours", "adjusted_hours", "my_split_pct",
        "work_date", "status", "paid_period_start", "comeback_of_job_id", "ref_low_hours", "ref_high_hours", "ref_confidence",
    )
    private val SHIFT_HEADER = listOf("type", "id", "start", "end")
    private val HOURLY_HEADER = listOf("type", "id", "date", "minutes", "reason")
    private val STUB_HEADER = listOf("type", "period_start", "stub_hours", "gross")
    private val PLAN_HEADER = listOf("type", "json")

    fun export(b: Backup): String {
        val out = StringBuilder()
        fun row(vararg f: String?) = out.append(Csv.line(f.map { it ?: "" })).append("\r\n")
        fun header(h: List<String>) = out.append(Csv.line(h)).append("\r\n")
        fun h(x: Int?) = x?.let { Units.formatHours(it, 2) }
        fun m(x: Long?) = x?.let { Units.formatMoney(it).replace("$", "").replace(",", "") }

        row(MAGIC, VERSION)
        header(RO_HEADER)
        b.ros.forEach { row("RO", it.id.toString(), it.roNumber, it.openedDate.toString(), it.vin, it.year?.toString(), it.make, it.model, it.engine, it.notes) }
        header(JOB_HEADER)
        b.jobs.forEach { j ->
            val l = j.line
            row(
                "JOB", l.id.toString(), l.roId.toString(), l.roNumber, l.description, l.laborType.name, h(l.flaggedHundredths),
                h(l.adjustedHundredths), l.mySplitPct.toString(), l.workDate.toString(), l.status.name, l.paidPeriodStart?.toString(),
                l.comebackOfJobId?.toString(), h(j.refLowHundredths), h(j.refHighHundredths), j.refConfidence,
            )
        }
        header(SHIFT_HEADER)
        b.shifts.forEach { row("SHIFT", it.id.toString(), it.start.toString(), it.end?.toString()) }
        header(HOURLY_HEADER)
        b.hourly.forEach { row("HOURLY", it.id.toString(), it.date.toString(), it.minutes.toString(), it.reason) }
        header(STUB_HEADER)
        b.stubs.forEach { row("STUB", it.periodStart.toString(), h(it.hoursHundredths), m(it.grossCents)) }
        if (b.planJson != null) {
            header(PLAN_HEADER)
            row("PLAN", b.planJson)
        }
        return out.toString()
    }

    fun import(text: String): Backup {
        val rows = Csv.parse(text)
        if (rows.isEmpty() || rows[0].getOrNull(0) != MAGIC) throw CsvImportException("Not a Flag Hours backup file.")
        if (rows[0].getOrNull(1) != VERSION) throw CsvImportException("Unsupported backup version ${rows[0].getOrNull(1)}.")

        val ros = mutableListOf<BackupRo>()
        val jobs = mutableListOf<BackupJob>()
        val shifts = mutableListOf<ShiftSpan>()
        val hourly = mutableListOf<HourlyWork>()
        val stubs = mutableListOf<BackupStub>()
        var plan: String? = null

        rows.drop(1).forEachIndexed { idx, r ->
            val lineNo = idx + 2
            fun f(i: Int): String? = r.getOrNull(i)?.takeIf { it.isNotEmpty() }
            fun req(i: Int, name: String): String = f(i) ?: throw CsvImportException("Line $lineNo: missing $name.")
            fun <T> parse(name: String, block: () -> T): T = try {
                block()
            } catch (e: CsvImportException) {
                throw e
            } catch (e: Exception) {
                throw CsvImportException("Line $lineNo: bad $name.")
            }
            fun hours(i: Int, name: String): Int? = f(i)?.let { Units.parseHours(it) ?: throw CsvImportException("Line $lineNo: bad $name.") }

            when (r.getOrNull(0)) {
                "type", null, "" -> Unit
                "RO" -> ros += parse("RO") {
                    BackupRo(
                        id = req(1, "id").toLong(),
                        roNumber = req(2, "RO number"),
                        openedDate = LocalDate.parse(req(3, "date")),
                        vin = f(4), year = f(5)?.toInt(), make = f(6), model = f(7), engine = f(8), notes = f(9),
                    )
                }
                "JOB" -> jobs += parse("job") {
                    BackupJob(
                        JobLine(
                            id = req(1, "id").toLong(),
                            roId = req(2, "RO id").toLong(),
                            roNumber = f(3) ?: "",
                            description = req(4, "description"),
                            laborType = LaborType.valueOf(req(5, "labor type")),
                            flaggedHundredths = hours(6, "flagged hours") ?: throw CsvImportException("Line $lineNo: missing flagged hours."),
                            adjustedHundredths = hours(7, "adjusted hours"),
                            mySplitPct = f(8)?.toInt() ?: 100,
                            workDate = LocalDate.parse(req(9, "work date")),
                            status = JobStatus.valueOf(req(10, "status")),
                            paidPeriodStart = f(11)?.let { LocalDate.parse(it) },
                            comebackOfJobId = f(12)?.toLong(),
                        ),
                        refLowHundredths = hours(13, "reference low"),
                        refHighHundredths = hours(14, "reference high"),
                        refConfidence = f(15),
                    )
                }
                "SHIFT" -> shifts += parse("shift") {
                    ShiftSpan(req(1, "id").toLong(), LocalDateTime.parse(req(2, "start")), f(3)?.let { LocalDateTime.parse(it) })
                }
                "HOURLY" -> hourly += parse("hourly entry") {
                    HourlyWork(req(1, "id").toLong(), LocalDate.parse(req(2, "date")), req(3, "minutes").toInt(), f(4) ?: "")
                }
                "STUB" -> stubs += parse("pay stub") {
                    BackupStub(
                        LocalDate.parse(req(1, "period start")),
                        hours(2, "stub hours") ?: throw CsvImportException("Line $lineNo: missing stub hours."),
                        f(3)?.let { Units.parseMoney(it) ?: throw CsvImportException("Line $lineNo: bad gross.") },
                    )
                }
                "PLAN" -> plan = f(1)
                else -> throw CsvImportException("Line $lineNo: unknown record type '${r[0]}'.")
            }
        }

        val roIds = ros.map { it.id }.toSet()
        jobs.firstOrNull { it.line.roId !in roIds }?.let { throw CsvImportException("Job ${it.line.id} points to a missing RO.") }
        if (ros.map { it.id }.toSet().size != ros.size) throw CsvImportException("Duplicate RO ids.")
        if (jobs.map { it.line.id }.toSet().size != jobs.size) throw CsvImportException("Duplicate job ids.")
        return Backup(ros, jobs, shifts, hourly, stubs, plan)
    }
}
