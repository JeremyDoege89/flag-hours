package com.jeremy.flaghours.data.remote

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jeremy.flaghours.data.db.FlagHoursDatabase
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VehicleServiceTest {
    private lateinit var db: FlagHoursDatabase
    private val requests = mutableListOf<okhttp3.Request>()
    private var code = 200
    private var body = """{"job":"Front brakes","hours":1.5,"low":1.2,"high":1.8,"confidence":"high"}"""
    private var key: String? = "test-key"

    private val fake = Interceptor { chain ->
        requests += chain.request()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private fun service() = VehicleService(db.laborRefs(), { key }, OkHttpClient.Builder().addInterceptor(fake).build())

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), FlagHoursDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun lookupSendsKeyAndCaches() = runBlocking {
        val s = service()
        val first = s.laborReference("Ford", "F-150", 2019, "5.0L", "Front brakes")
        assertEquals(LookupResult.Found(LaborReference("Front brakes", 150, 120, 180, "high"), cached = false), first)
        val req = requests.single()
        assertEquals("test-key", req.header("x-api-key"))
        assertEquals("Ford", req.url.queryParameter("make"))
        assertEquals("2019", req.url.queryParameter("year"))
        assertEquals("5.0L", req.url.queryParameter("engine"))

        val second = s.laborReference("FORD", "f-150", 2019, "5.0l", "front brakes")
        assertTrue((second as LookupResult.Found).cached)
        assertEquals(1, requests.size)
    }

    @Test fun noKeyNoNetwork() = runBlocking {
        key = null
        val r = service().laborReference("Ford", "F-150", 2019, null, "Brakes")
        assertTrue(r is LookupResult.Failed)
        assertTrue(requests.isEmpty())
    }

    @Test fun dailyLimit() = runBlocking {
        val s = service()
        repeat(VehicleService.DAILY_LIMIT) { s.laborReference("Ford", "F-150", 2019, null, "Job $it") }
        assertEquals(VehicleService.DAILY_LIMIT, requests.size)
        val r = s.laborReference("Ford", "F-150", 2019, null, "One more")
        assertTrue(r is LookupResult.Failed && r.message.contains("Daily limit"))
        assertEquals(VehicleService.DAILY_LIMIT, requests.size)
    }

    @Test fun notFoundIsCached() = runBlocking {
        code = 404
        val s = service()
        assertEquals(LookupResult.NotFound, s.laborReference("Ford", "F-150", 2019, null, "Flux capacitor"))
        assertEquals(LookupResult.NotFound, s.laborReference("Ford", "F-150", 2019, null, "Flux capacitor"))
        assertEquals(1, requests.size)
    }

    @Test fun rejectedKey() = runBlocking {
        code = 401
        val r = service().laborReference("Ford", "F-150", 2019, null, "Brakes")
        assertTrue(r is LookupResult.Failed && r.message.contains("rejected"))
    }

    @Test fun decodeVin() = runBlocking {
        body = """{"Results":[{"ModelYear":"2019","Make":"FORD","Model":"F-150","DisplacementL":"5.0","EngineCylinders":"8"}]}"""
        val r = service().decodeVin("1ftew1e50kfa12345")
        assertEquals(LookupResult.Found(VehicleInfo(2019, "Ford", "F-150", "5.0L 8-cyl"), cached = false), r)
        assertTrue(requests.single().url.toString().startsWith("https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/1FTEW1E50KFA12345?format=json"))
    }
}
