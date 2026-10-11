package com.jeremy.flaghours.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaborMatchingTest {
    private fun row(job: String, cat: String = "Cooling", h: Int = 100) =
        LaborRow(job, job.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-'), cat, h, h - 20, h + 30, "high")

    private val catalog = listOf(
        row("Radiator"), row("Radiator Cap"), row("Radiator Hose - Lower"), row("Radiator Hose - Upper"),
        row("Radiator Hoses - Both"), row("Radiator Removal and Service"), row("Radiator Support"),
        row("Thermostat"), row("Thermostat Housing"), row("Water Pump"), row("Water Pump Pulley"), row("Coolant Flush"),
        row("Brake Pads - Front", "Brakes"), row("Brake Pads - Rear", "Brakes"), row("Brake Rotors - Front Pair", "Brakes"),
        row("Brake Pads and Rotors - Front", "Brakes"), row("Brake Caliper - Front Left", "Brakes"),
        row("Alternator", "Electrical"), row("Starter", "Electrical"), row("Battery", "Electrical"),
        row("Spark Plugs", "Engine"), row("Serpentine Belt", "Engine"), row("Wheel Bearing - Front Left", "Suspension"),
        row("A/C Compressor", "HVAC"), row("A/C Condenser", "HVAC"),
    )

    private fun top(q: String) = JobMatcher.match(q, catalog).firstOrNull()?.row?.job

    @Test fun radiatorPhrasings() {
        assertEquals("Radiator", top("radiator replacement"))
        assertEquals("Radiator", top("R&R radiator"))
        assertEquals("Radiator", top("replace rad"))
        assertEquals("Radiator", top("Remove and replace radiator"))
        assertEquals("Radiator", top("RADIATOR R/R"))
        assertTrue(JobMatcher.match("radiator replacement", catalog).size <= 5)
    }

    @Test fun brakes() {
        assertEquals("Brake Pads - Front", top("front brakes"))
        assertEquals("Brake Pads - Front", top("frt pads"))
        assertEquals("Brake Pads - Rear", top("rear brake pads"))
        assertEquals("Brake Rotors - Front Pair", top("front rotors"))
        assertEquals("Brake Caliper - Front Left", top("LF caliper"))
    }

    @Test fun others() {
        assertEquals("Water Pump", top("water pump replace"))
        assertEquals("Water Pump", top("WP R&R"))
        assertEquals("Thermostat", top("t-stat"))
        assertEquals("Thermostat", top("replace thermostat"))
        assertEquals("Alternator", top("alt replacement"))
        assertEquals("Spark Plugs", top("plugs"))
        assertEquals("A/C Compressor", top("a/c comp"))
        assertEquals("Wheel Bearing - Front Left", top("lf wheel brg"))
        assertEquals("Radiator Hose - Lower", top("lower radiator hose"))
    }

    @Test fun typoTolerance() {
        assertEquals("Alternator", top("altenator"))
        assertEquals("Thermostat", top("thermostst"))
    }

    @Test fun noMatchForNonsense() {
        assertTrue(JobMatcher.match("customer states noise", catalog).isEmpty())
        assertTrue(JobMatcher.match("replace", catalog).isEmpty())
    }

    @Test fun confidence() {
        assertTrue(JobMatcher.isConfident(JobMatcher.match("water pump", catalog)))
        assertFalse(JobMatcher.isConfident(JobMatcher.match("radiator hose", catalog)))
    }

    @Test fun matchesSlugToo() {
        val rows = listOf(LaborRow("Pump, Water (engine driven)", "water-pump", "Cooling", 200, 180, 240, "high"))
        assertEquals("water-pump", JobMatcher.match("wp", rows).single().row.jobSlug)
    }

    @Test fun engineCandidates() {
        val engines = listOf("3.5l-v6-ecoboost", "3.5l-v6", "2.7l-v6-ecoboost", "5.0l-v8", "3.3l-v6")
        assertEquals(listOf("5.0l-v8"), EngineMatcher.candidates(engines, "5.0", 8))
        assertEquals(listOf("3.5l-v6-ecoboost", "3.5l-v6"), EngineMatcher.candidates(engines, "3.5", 6))
        assertEquals(engines, EngineMatcher.candidates(engines, null, null))
        assertEquals("3.5", EngineMatcher.displacement("3.5L 6-cyl"))
        assertEquals(6, EngineMatcher.cylinders("3.5L 6-cyl"))
        assertEquals(8, EngineMatcher.cylinders("5.0l-v8"))
        assertEquals(4, EngineMatcher.cylinders("2.0l-i4-turbo"))
    }

    @Test fun makeMatching() {
        val makes = listOf(VehicleOption("chevrolet", "Chevrolet"), VehicleOption("ford", "Ford"), VehicleOption("mercedes-benz", "Mercedes-Benz"), VehicleOption("ram", "RAM"))
        assertEquals("chevrolet", VehicleMatcher.matchMake("CHEVY", makes)?.slug)
        assertEquals("chevrolet", VehicleMatcher.matchMake("CHEVROLET", makes)?.slug)
        assertEquals("mercedes-benz", VehicleMatcher.matchMake("MERCEDES-BENZ", makes)?.slug)
        assertEquals("ram", VehicleMatcher.matchMake("Ram", makes)?.slug)
        assertNull(VehicleMatcher.matchMake("Yugo", makes))
    }

    @Test fun modelMatching() {
        val models = listOf(
            VehicleOption("f-150", "F-150", 1997, 2026), VehicleOption("f-250-super-duty", "F-250 Super Duty"),
            VehicleOption("silverado-1500", "Silverado 1500"), VehicleOption("silverado-2500hd", "Silverado 2500HD"),
            VehicleOption("ranger", "Ranger", 2019, 2026),
        )
        assertEquals("f-150", VehicleMatcher.confidentModel(VehicleMatcher.rankModels("F-150", 2017, models))?.slug)
        assertEquals("silverado-1500", VehicleMatcher.confidentModel(VehicleMatcher.rankModels("Silverado 1500", 2020, models))?.slug)
        val ambiguous = VehicleMatcher.rankModels("Silverado", 2020, models)
        assertEquals(2, ambiguous.size)
        assertNull(VehicleMatcher.confidentModel(ambiguous))
        assertTrue(VehicleMatcher.rankModels("Ranger", 2005, models).isEmpty()) // year not covered
    }

    @Test fun barcodeVin() {
        assertEquals("1HGCM82633A004352", vinFromBarcode("1HGCM82633A004352"))
        assertEquals("1HGCM82633A004352", vinFromBarcode("I1HGCM82633A004352"))
        assertEquals("1HGCM82633A004352", vinFromBarcode("VIN:1HGCM82633A004352;MY:2003"))
        assertNull(vinFromBarcode("123456"))
    }
}
