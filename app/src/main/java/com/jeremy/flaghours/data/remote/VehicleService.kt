package com.jeremy.flaghours.data.remote

import com.jeremy.flaghours.data.db.LaborRefCache
import com.jeremy.flaghours.data.db.LaborRefDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

sealed interface LookupResult<out T> {
    data class Found<T>(val value: T, val cached: Boolean) : LookupResult<T>
    data object NotFound : LookupResult<Nothing>
    data class Failed(val message: String) : LookupResult<Nothing>
}

/**
 * Network lookups. Everything here is optional: the app works fully offline and without an API key.
 * Labor references are cache-first and respect the Open Labor Project free tier (10 lookups per day).
 */
class VehicleService(
    private val laborRefs: LaborRefDao,
    private val apiKey: () -> String?,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun decodeVin(vin: String): LookupResult<VehicleInfo> = withContext(Dispatchers.IO) {
        val url = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/".toHttpUrl().newBuilder()
            .addPathSegment(vin.trim().uppercase())
            .addQueryParameter("format", "json")
            .build()
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext LookupResult.Failed("VIN lookup failed (${resp.code}).")
                val info = VehicleApiParsers.parseVpic(resp.body.string())
                if (info == null) LookupResult.NotFound else LookupResult.Found(info, cached = false)
            }
        } catch (e: IOException) {
            LookupResult.Failed("No connection. VIN lookup needs internet.")
        } catch (e: Exception) {
            LookupResult.Failed("Couldn't read the VIN lookup response.")
        }
    }

    fun hasApiKey(): Boolean = !apiKey().isNullOrBlank()

    suspend fun lookupsToday(): Int = laborRefs.fetchedSince(startOfToday())

    suspend fun laborReference(make: String, model: String, year: Int, engine: String?, job: String): LookupResult<LaborReference> {
        val slug = jobSlug(job)
        val eng = engine?.trim().orEmpty()
        laborRefs.get(make.lowercase(), model.lowercase(), year, eng.lowercase(), slug)?.let { c ->
            return if (c.lowHundredths == null && c.highHundredths == null && c.hoursHundredths == null) {
                LookupResult.NotFound
            } else {
                LookupResult.Found(LaborReference(c.job, c.hoursHundredths, c.lowHundredths, c.highHundredths, c.confidence), cached = true)
            }
        }
        val key = apiKey()?.takeIf { it.isNotBlank() } ?: return LookupResult.Failed("Add your Open Labor Project API key in Settings.")
        if (lookupsToday() >= DAILY_LIMIT) return LookupResult.Failed("Daily limit of $DAILY_LIMIT lookups reached. Try again tomorrow.")

        return withContext(Dispatchers.IO) {
            val url = "https://openlaborproject.com/api/v1/labor-times".toHttpUrl().newBuilder()
                .addQueryParameter("make", make)
                .addQueryParameter("model", model)
                .addQueryParameter("year", year.toString())
                .apply { if (eng.isNotEmpty()) addQueryParameter("engine", eng) }
                .addQueryParameter("job", job)
                .build()
            try {
                http.newCall(Request.Builder().url(url).header("x-api-key", key).build()).execute().use { resp ->
                    when (resp.code) {
                        401, 403 -> return@withContext LookupResult.Failed("The API key was rejected. Check it in Settings.")
                        429 -> return@withContext LookupResult.Failed("Open Labor Project daily limit reached.")
                        404 -> {
                            cache(make, model, year, eng, slug, job, null)
                            return@withContext LookupResult.NotFound
                        }
                    }
                    if (!resp.isSuccessful) return@withContext LookupResult.Failed("Lookup failed (${resp.code}).")
                    val ref = VehicleApiParsers.parseOlp(resp.body.string(), job)
                    cache(make, model, year, eng, slug, job, ref)
                    if (ref == null) LookupResult.NotFound else LookupResult.Found(ref, cached = false)
                }
            } catch (e: IOException) {
                LookupResult.Failed("No connection. Labor lookups need internet.")
            } catch (e: Exception) {
                LookupResult.Failed("Couldn't read the lookup response.")
            }
        }
    }

    private suspend fun cache(make: String, model: String, year: Int, engine: String, slug: String, job: String, ref: LaborReference?) {
        laborRefs.upsert(
            LaborRefCache(
                make = make.lowercase(), model = model.lowercase(), year = year, engine = engine.lowercase(), jobSlug = slug,
                job = ref?.job ?: job, hoursHundredths = ref?.hoursHundredths, lowHundredths = ref?.lowHundredths,
                highHundredths = ref?.highHundredths, confidence = ref?.confidence, fetchedAt = clock(),
            ),
        )
    }

    private fun startOfToday(): Long = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    companion object {
        const val DAILY_LIMIT = 10
        const val ATTRIBUTION = "Labor reference data: Open Labor Project"
    }
}
