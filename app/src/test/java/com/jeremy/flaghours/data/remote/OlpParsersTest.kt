package com.jeremy.flaghours.data.remote

import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Fixtures in src/test/resources/olp follow the documented shape (no real key was available to capture live ones). */
class OlpParsersTest {
    private fun fixture(name: String) = javaClass.getResource("/olp/$name")!!.readText()
    private val now = Instant.parse("2026-10-11T05:00:00Z")

    private fun ok(status: Int, body: String): JsonElement =
        (OlpParsers.envelope(status, body, emptyMap(), "x", now) as OlpResult.Ok).value

    private fun err(status: Int, body: String, headers: Map<String, String> = emptyMap()): OlpError =
        (OlpParsers.envelope(status, body, headers, "ford / f-150 / 2017", now) as OlpResult.Err).error

    @Test fun makes() {
        val makes = OlpParsers.vehicleOptions(ok(200, fixture("vehicles-makes.json")))
        assertEquals(listOf("chevrolet", "ford", "toyota", "mercedes-benz"), makes.map { it.slug })
    }

    @Test fun modelsWrappedWithYears() {
        val models = OlpParsers.vehicleOptions(ok(200, fixture("vehicles-ford.json")))
        assertEquals(3, models.size)
        assertEquals(1997, models[0].yearFrom)
        assertEquals(2025, models[2].yearTo)
    }

    @Test fun stringOptions() {
        val m = OlpParsers.vehicleOptions(ok(200, """{"data":["Ford","Land Rover"]}"""))
        assertEquals(listOf("ford", "land-rover"), m.map { it.slug })
    }

    @Test fun laborRows() {
        val rows = OlpParsers.laborRows(ok(200, fixture("labor-times-f150-2017.json")))
        assertEquals(9, rows.size)
        val r = rows[0]
        assertEquals("Radiator", r.job)
        assertEquals("radiator", r.jobSlug)
        assertEquals("Cooling System", r.category)
        assertEquals(210, r.hoursHundredths)
        assertEquals(180, r.lowHundredths)
        assertEquals(260, r.highHundredths)
        assertEquals("high", r.confidence)
        assertEquals("3.5l-v6-ecoboost", r.engine)
    }

    @Test fun errors() {
        assertTrue(err(429, fixture("error-rate-limit.json")) is OlpError.DailyLimit)
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), (err(429, fixture("error-rate-limit.json")) as OlpError.DailyLimit).resetAt)
        assertEquals(Instant.ofEpochSecond(1_791_763_200), (err(429, fixture("error-rate-limit.json"), mapOf("x-ratelimit-reset-daily" to "1791763200")) as OlpError.DailyLimit).resetAt)
        val nf = err(404, fixture("error-not-found.json"))
        assertTrue(nf is OlpError.NotFound && nf.message.contains("ford / f-150 / 2017"))
        assertEquals(OlpError.BadKey, err(401, """{"error":{"code":"INVALID_API_KEY"}}"""))
        assertEquals(OlpError.NoKey, err(401, """{"error":{"code":"MISSING_API_KEY"}}"""))
        assertEquals(OlpError.TierLocked, err(403, """{"error":{"code":"TIER_LOCKED"}}"""))
        assertEquals(OlpError.Cloudflare, err(403, "<!DOCTYPE html><html>Just a moment...</html>"))
        assertEquals(OlpError.Burst(12), err(429, """{"error":{"code":"BURST_LIMIT_EXCEEDED"}}""", mapOf("retry-after" to "12")))
        assertEquals(OlpError.Payment, err(402, """{"error":{"code":"PAYMENT_REQUIRED"}}"""))
        assertTrue(err(400, """{"error":{"code":"INVALID_YEAR","message":"year must be YYYY"}}""") is OlpError.BadRequest)
        assertEquals(OlpError.Server, err(500, "oops"))
        // An error code in a 200 envelope is still an error.
        assertTrue(err(200, """{"data":null,"error":{"code":"NOT_FOUND"}}""") is OlpError.NotFound)
    }

    @Test fun remaining() {
        assertEquals(7, OlpParsers.remainingDaily(mapOf("x-ratelimit-remaining-daily" to "7")))
    }
}
