package com.jeremy.flaghours.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoTextParserTest {

    private val dealerRo = """
        SMITH FORD - SERVICE DEPARTMENT
        REPAIR ORDER # 448812
        DATE 10/06/26   ADVISOR: JONES
        2019 FORD F-150 XLT 5.0L
        VIN: 1FTEW1E50KFA12345
        MILEAGE IN 84,233
        LINE A  CUSTOMER STATES BRAKES SQUEAL
        A REPLACE FRONT PADS AND ROTORS     1.6
        PARTS  ${'$'}212.40
        LINE B  WARRANTY  OP 1234A
        B REPROGRAM PCM  HRS: 0.8
        C DIAG CHECK ENGINE LIGHT 1.0 HRS
        LABOR TOTAL  ${'$'}540.00
        TAX 12.50
    """.trimIndent()

    @Test fun dealerRoFields() {
        val r = RoTextParser.parse(dealerRo)
        assertEquals("448812", r.roNumber)
        assertEquals(2019, r.year)
        assertEquals("Ford", r.make)
        assertEquals("F-150 XLT", r.model)
        assertEquals(3, r.lines.size)
        assertEquals(ParsedLine("Replace front pads and rotors", 160, LaborType.CUSTOMER), r.lines[0])
        assertEquals(80, r.lines[1].hoursHundredths)
        assertEquals(LaborType.WARRANTY, r.lines[1].laborType)
        assertEquals("Reprogram pcm", r.lines[1].description)
        assertEquals(100, r.lines[2].hoursHundredths)
        assertEquals(LaborType.DIAG, r.lines[2].laborType)
    }

    @Test fun vinWithCheckDigit() {
        // 1HGCM82633A004352 is a well-known valid VIN.
        assertTrue(RoTextParser.vinCheckDigitOk("1HGCM82633A004352"))
        assertFalse(RoTextParser.vinCheckDigitOk("1HGCM82643A004352"))
        assertEquals("1HGCM82633A004352", RoTextParser.findVin("CUSTOMER 1HGCM82633A004352 SEDAN"))
    }

    @Test fun vinOcrFixes() {
        // OCR read the zeros as letter O; VINs never contain O/I/Q.
        assertEquals("1HGCM82633A004352", RoTextParser.findVin("VIN 1HGCM82633AOO4352"))
        assertEquals("1HGCM82633A004352", RoTextParser.findVin("VIN: 1HGC M826 33A0 04352"))
    }

    @Test fun prefersValidVinAmongCandidates() {
        val text = "PART 12345ABCDE678901 ... 1HGCM82633A004352"
        assertEquals("1HGCM82633A004352", RoTextParser.findVin(text))
    }

    @Test fun roNumberVariants() {
        assertEquals("123456", RoTextParser.findRoNumber(listOf("RO# 123456")))
        assertEquals("98765", RoTextParser.findRoNumber(listOf("R.O. NO. 98765")))
        assertEquals("55501", RoTextParser.findRoNumber(listOf("Invoice: 55501")))
        assertEquals("W12345", RoTextParser.findRoNumber(listOf("Work Order W12345")))
        assertNull(RoTextParser.findRoNumber(listOf("Phone 555-1234")))
    }

    @Test fun ignoresMoneyAndTotals() {
        val lines = RoTextParser.findLaborLines(
            listOf("LABOR 125.00", "TOTAL 1.6", "${'$'}45.50", "Oil change 0.3 hrs", "MILEAGE 12.3"),
        )
        assertEquals(listOf(ParsedLine("Oil change", 30, LaborType.CUSTOMER)), lines)
    }

    @Test fun hoursOnlyLineUsesPreviousLineAsDescription() {
        val lines = RoTextParser.findLaborLines(listOf("Replace water pump", "2.4 HRS"))
        assertEquals(ParsedLine("Replace water pump", 240, LaborType.CUSTOMER), lines.single())
    }

    @Test fun sectionLaborTypeCarriesForward() {
        val lines = RoTextParser.findLaborLines(listOf("PAY TYPE: W", "1) Replace turbo 3.2", "2) Coolant flush 0.5 hr"))
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.laborType == LaborType.WARRANTY })
        assertEquals("Replace turbo", lines[0].description)
    }

    @Test fun internalAndLabels() {
        val lines = RoTextParser.findLaborLines(listOf("INTERNAL used car recon FLAG 2.5"))
        assertEquals(ParsedLine("Used car recon", 250, LaborType.INTERNAL), lines.single())
    }

    @Test fun rejectsImplausibleHours() {
        assertTrue(RoTextParser.findLaborLines(listOf("A Something 45.0", "B Other 0.0")).isEmpty())
    }

    @Test fun emptyText() {
        val r = RoTextParser.parse("")
        assertNull(r.roNumber)
        assertNull(r.vin)
        assertTrue(r.lines.isEmpty())
    }

    @Test fun chevyAlias() {
        val r = RoTextParser.parse("2021 CHEVY SILVERADO 1500\nRO 22222")
        assertEquals("Chevrolet", r.make)
        assertEquals("SILVERADO 1500", r.model)
    }
}
