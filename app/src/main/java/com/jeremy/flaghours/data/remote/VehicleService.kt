package com.jeremy.flaghours.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface LookupResult<out T> {
    data class Found<T>(val value: T) : LookupResult<T>
    data object NotFound : LookupResult<Nothing>
    data class Failed(val message: String) : LookupResult<Nothing>
}

/** NHTSA vPIC VIN decoding (free, no key). */
class VehicleService(
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
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
                if (info == null) LookupResult.NotFound else LookupResult.Found(info)
            }
        } catch (e: IOException) {
            LookupResult.Failed("No connection. VIN lookup needs internet.")
        } catch (e: Exception) {
            LookupResult.Failed("Couldn't read the VIN lookup response.")
        }
    }
}
