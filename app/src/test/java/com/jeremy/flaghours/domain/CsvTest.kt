package com.jeremy.flaghours.domain

import com.jeremy.flaghours.domain.Fixtures.d
import com.jeremy.flaghours.domain.Fixtures.t
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test fun parseQuotedFields() {
        val rows = Csv.parse("a,\"b,c\",\"say \"\"hi\"\"\"\r\n\"multi\nline\",,x\n")
        assertEquals(listOf("a", "b,c", "say \"hi\""), rows[0])
        assertEquals(listOf("multi\nline", "", "x"), rows[1])
    }

    @Test fun escapeRoundTrip() {
        val fields = listOf("plain", "comma,here", "quote\"here", "new\nline", "")
        assertEquals(fields, Csv.parse(Csv.line(fields)).single())
    }

    @Test fun ignoresBomAndBlankLines() {
        assertEquals(listOf(listOf("a", "b")), Csv.parse("﻿a,b\n\n"))
    }

    private val backup = Backup(
        ros = listOf(BackupRo(1, "4471", d("2026-10-06"), "1FTFW1E50NFA00001", 2022, "Ford", "F-150", "3.5L", "Customer says \"squeal\", front")),
        jobs = listOf(
            BackupJob(
                JobLine(10, 1, "Front brakes, pads + rotors", LaborType.WARRANTY, 235, 180, 50, d("2026-10-06"), JobStatus.PAID, d("2026-10-05"), null, "4471"),
                refLowHundredths = 150, refHighHundredths = 250, refConfidence = "high",
            ),
            BackupJob(JobLine(11, 1, "Brake comeback", LaborType.CUSTOMER, 50, workDate = d("2026-10-09"), comebackOfJobId = 10, roNumber = "4471")),
        ),
        shifts = listOf(ShiftSpan(1, t("2026-10-06T07:00"), t("2026-10-06T15:30")), ShiftSpan(2, t("2026-10-07T07:00"), null)),
        hourly = listOf(HourlyWork(1, d("2026-10-07"), 90, "Training, EV")),
        stubs = listOf(BackupStub(d("2026-10-05"), 4210, 126355), BackupStub(d("2026-09-28"), 3800, null)),
        planJson = """{"flagRateCents":3250,"guarantee":{"type":"none"}}""",
    )

    @Test fun backupRoundTrip() {
        val text = CsvExporter.export(backup)
        assertTrue(text.startsWith("flag-hours-backup,1"))
        assertEquals(backup, CsvExporter.import(text))
    }

    @Test fun hoursAreHumanReadable() {
        val text = CsvExporter.export(backup)
        assertTrue(text.contains(",2.35,1.80,50,"))
        assertTrue(text.contains("1263.55"))
    }

    @Test fun rejectsForeignFile() {
        assertThrows(CsvImportException::class.java) { CsvExporter.import("name,hours\nfoo,1\n") }
    }

    @Test fun reportsBadLine() {
        val text = CsvExporter.export(backup).replace(",2.35,", ",two,")
        val e = assertThrows(CsvImportException::class.java) { CsvExporter.import(text) }
        assertTrue(e.message!!, e.message!!.contains("flagged hours"))
    }

    @Test fun rejectsOrphanJob() {
        val text = CsvExporter.export(backup.copy(ros = emptyList()))
        assertThrows(CsvImportException::class.java) { CsvExporter.import(text) }
    }
}
