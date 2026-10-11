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
        assertEquals(VehicleInfo(2019, "Ford", "F-150", "5.0L 8-cyl", "5.0", 8), VehicleApiParsers.parseVpic(body))
    }

    @Test fun vpicShortMakeAndEv() {
        val body = """{"Results":[{"ModelYear":"2023","Make":"BMW","Model":"i4","DisplacementL":"","EngineCylinders":"","FuelTypePrimary":"Electric"}]}"""
        assertEquals(VehicleInfo(2023, "BMW", "i4", "EV", null, null), VehicleApiParsers.parseVpic(body))
    }

    @Test fun vpicUndecodable() {
        assertNull(VehicleApiParsers.parseVpic("""{"Results":[{"ModelYear":"","Make":"","Model":""}]}"""))
        assertNull(VehicleApiParsers.parseVpic("""{"Results":[]}"""))
    }

}
