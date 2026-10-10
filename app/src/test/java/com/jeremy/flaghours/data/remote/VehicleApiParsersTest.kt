package com.jeremy.flaghours.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleApiParsersTest {
    @Test fun vpic() {
        val body = """
            {"Count":1,"Message":"Results returned successfully","SearchCriteria":"VIN:1FTEW1E50KFA12345",
             "Results":[{"ModelYear":"2019","Make":"FORD","Model":"F-150","DisplacementL":"5.0","EngineCylinders":"8",
             "FuelTypePrimary":"Gasoline","ErrorCode":"0"}]}
        """.trimIndent()
        assertEquals(VehicleInfo(2019, "Ford", "F-150", "5.0L 8-cyl"), VehicleApiParsers.parseVpic(body))
    }

    @Test fun vpicShortMakeAndEv() {
        val body = """{"Results":[{"ModelYear":"2023","Make":"BMW","Model":"i4","DisplacementL":"","EngineCylinders":"","FuelTypePrimary":"Electric"}]}"""
        assertEquals(VehicleInfo(2023, "BMW", "i4", "EV"), VehicleApiParsers.parseVpic(body))
    }

    @Test fun vpicUndecodable() {
        assertNull(VehicleApiParsers.parseVpic("""{"Results":[{"ModelYear":"","Make":"","Model":""}]}"""))
        assertNull(VehicleApiParsers.parseVpic("""{"Results":[]}"""))
    }

    @Test fun olpFlat() {
        val body = """{"job":"Front brake pads","hours":1.5,"low":1.2,"high":1.8,"confidence":"high"}"""
        assertEquals(LaborReference("Front brake pads", 150, 120, 180, "high"), VehicleApiParsers.parseOlp(body, "x"))
    }

    @Test fun olpWrappedArrayAndRangeObject() {
        val body = """{"data":[{"name":"Water pump","labor_hours":"2.4","range":{"min":2.0,"max":3.1},"confidence_level":"medium"}]}"""
        assertEquals(LaborReference("Water pump", 240, 200, 310, "medium"), VehicleApiParsers.parseOlp(body, "x"))
    }

    @Test fun olpHoursOnly() {
        assertEquals(LaborReference("Alignment", 100, 100, 100, null), VehicleApiParsers.parseOlp("""{"results":[{"time":1.0}]}""", "Alignment"))
    }

    @Test fun olpEmpty() {
        assertNull(VehicleApiParsers.parseOlp("""{"data":[]}""", "x"))
        assertNull(VehicleApiParsers.parseOlp("""{"message":"no match"}""", "x"))
    }

    @Test fun slug() {
        assertEquals("front-brake-pads-rotors", jobSlug("Front brake pads & rotors!"))
    }
}
