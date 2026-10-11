package com.jeremy.flaghours.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeremy.flaghours.data.db.FlagHoursDatabase
import com.jeremy.flaghours.domain.JobMatcher
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OlpServiceTest {
    private lateinit var db: FlagHoursDatabase
    private val requests = mutableListOf<okhttp3.Request>()
    private var key: String? = "test-key"
    private var override: ((okhttp3.Request) -> Pair<Int, String>)? = null
    private val headers = mutableMapOf("X-RateLimit-Remaining-Daily" to "7")
    private lateinit var logFile: File

    private fun fixture(name: String) = javaClass.getResource("/olp/$name")!!.readText()

    private val fake = Interceptor { chain ->
        val req = chain.request()
        requests += req
        val (code, body) = override?.invoke(req) ?: when {
            req.url.encodedPath.endsWith("/vehicles") && req.url.queryParameter("make") == null -> 200 to fixture("vehicles-makes.json")
            req.url.encodedPath.endsWith("/vehicles") && req.url.queryParameter("make") == "ford" -> 200 to fixture("vehicles-ford.json")
            req.url.encodedPath.endsWith("/labor-times") && req.url.queryParameter("model") == "f-150" -> 200 to fixture("labor-times-f150-2017.json")
            else -> 404 to fixture("error-not-found.json")
        }
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private fun service() = OlpService(
        db.olp(), { key }, OlpDebugLog(logFile), OkHttpClient.Builder().addInterceptor(fake).build(),
        clock = { Instant.parse("2026-10-11T05:00:00Z") },
    )

    private val f150 = VehicleQuery(2017, "FORD", "F-150", "3.5", 6)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FlagHoursDatabase::class.java)
            .allowMainThreadQueries().build()
        logFile = File.createTempFile("olp", ".log")
    }

    @After fun tearDown() = db.close()

    @Test fun firstVehicleCostsThreeCallsThenZero() = runBlocking {
        val s = service()
        val r = s.load(f150) as VehicleLoad.Ready
        assertEquals(3, requests.size)
        assertEquals("test-key", requests.last().header("x-api-key"))
        assertEquals(null, requests.last().url.queryParameter("job"))
        assertEquals(null, requests.last().url.queryParameter("engine"))
        assertEquals("2017", requests.last().url.queryParameter("year"))
        assertEquals("3.5l-v6-ecoboost", r.engine)
        assertEquals(8, r.rows.size) // the 5.0 V8 radiator row is filtered out
        assertEquals("Radiator", JobMatcher.match("radiator replacement", r.rows).first().row.job)
        assertEquals(7, s.quota.value.remaining)

        // Same vehicle again, or typed differently: no calls.
        assertTrue(s.load(VehicleQuery(2017, "Ford", "f150", "3.5", 6)) is VehicleLoad.Ready)
        s.load(f150)
        assertEquals(3, requests.size)
        assertTrue(s.cached(f150) is VehicleLoad.Ready)
        assertTrue(File(logFile.path).readText().contains("/labor-times"))
        assertTrue(!File(logFile.path).readText().contains("test-key"))
    }

    @Test fun otherModelSameMakeCostsOneCall() = runBlocking {
        val s = service()
        s.load(f150)
        requests.clear()
        val r = s.load(VehicleQuery(2021, "Ford", "Ranger"))
        assertEquals(1, requests.size) // makes + models cached
        assertTrue(r is VehicleLoad.Failed && r.error is OlpError.NotFound)
        assertTrue((r as VehicleLoad.Failed).error.message.contains("ford / ranger / 2021"))
    }

    @Test fun ambiguousEngineAsks() = runBlocking {
        val s = service()
        val r = s.load(VehicleQuery(2017, "Ford", "F-150"))
        assertTrue(r is VehicleLoad.ChooseEngine)
        val picked = s.chooseEngine(VehicleQuery(2017, "Ford", "F-150"), (r as VehicleLoad.ChooseEngine).vehicleKey, "5.0l-v8") as VehicleLoad.Ready
        assertEquals("5.0l-v8", picked.engine)
        assertEquals(190, picked.rows.single { it.jobSlug == "radiator" }.hoursHundredths)
    }

    @Test fun unknownMakeOffersPicker() = runBlocking {
        val r = service().load(VehicleQuery(2017, "Yugo", "GV"))
        assertTrue(r is VehicleLoad.ChooseMake && r.options.isNotEmpty())
    }

    @Test fun noKey() = runBlocking {
        key = null
        val r = service().load(f150)
        assertEquals(VehicleLoad.Failed(OlpError.NoKey), r)
        assertTrue(requests.isEmpty())
    }

    @Test fun dailyLimitStopsFurtherCalls() = runBlocking {
        override = { 429 to fixture("error-rate-limit.json") }
        val s = service()
        val r = s.load(f150)
        assertTrue(r is VehicleLoad.Failed && r.error is OlpError.DailyLimit)
        assertEquals(Instant.parse("2026-10-12T00:00:00Z"), ((r as VehicleLoad.Failed).error as OlpError.DailyLimit).resetAt)
        val before = requests.size
        s.load(VehicleQuery(2020, "Toyota", "Camry"))
        assertEquals(before, requests.size)
    }

    @Test fun cloudflare() = runBlocking {
        override = { 403 to "<html>challenge</html>" }
        assertEquals(VehicleLoad.Failed(OlpError.Cloudflare), service().load(f150))
    }
}
