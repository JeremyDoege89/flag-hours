package com.jeremy.flaghours.data.remote

import com.jeremy.flaghours.domain.LaborRow
import com.jeremy.flaghours.domain.Units
import com.jeremy.flaghours.domain.VehicleOption
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A failed Open Labor Project call, already translated for the user. */
sealed class OlpError(val message: String) {
    data object NoKey : OlpError("Add your Open Labor Project API key in Settings to look up labor times.")
    data object BadKey : OlpError("Open Labor Project rejected the API key. Check it in Settings.")
    data class DailyLimit(val resetAt: Instant) : OlpError("Daily lookup limit reached. It resets at midnight UTC.")
    data class Burst(val retryAfterSeconds: Long?) : OlpError("Too many lookups at once. Wait ${retryAfterSeconds ?: 60} seconds and try again.")
    data object Payment : OlpError("Your Open Labor Project account needs payment.")
    data object TierLocked : OlpError("That lookup isn't available on your Open Labor Project plan.")
    data object Cloudflare : OlpError("The request was blocked by Open Labor Project's firewall (Cloudflare). Try again later or on another network.")
    data class NotFound(val tried: String) : OlpError("Open Labor Project has no data for $tried.")
    data class BadRequest(val detail: String) : OlpError("Vehicle details incomplete: $detail")
    data object Server : OlpError("Open Labor Project had a server error. Try again later.")
    data object Offline : OlpError("No connection. Labor lookups need internet.")
    data class Unexpected(val detail: String) : OlpError("Unexpected response from Open Labor Project ($detail).")
}

sealed interface OlpResult<out T> {
    data class Ok<T>(val value: T) : OlpResult<T>
    data class Err(val error: OlpError) : OlpResult<Nothing>
}

/** Pure parsing of OLP responses (envelope `{data, meta, error}`); see docs/olp-api.md. */
object OlpParsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Returns the `data` element or the translated error. [headers] are lower-cased names.
     * [tried] describes the request for "not found" messages.
     */
    fun envelope(status: Int, body: String, headers: Map<String, String>, tried: String, now: Instant = Instant.now()): OlpResult<JsonElement> {
        val trimmed = body.trimStart()
        if (status == 403 && (trimmed.startsWith("<") || trimmed.isEmpty())) return OlpResult.Err(OlpError.Cloudflare)
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val obj = root as? JsonObject
        val err = obj?.get("error")?.takeUnless { it is JsonNull }
        val code = when (err) {
            is JsonObject -> (err["code"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> err.contentOrNull
            else -> null
        }?.uppercase()
        val detail = (err as? JsonObject)?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull } ?: code ?: "HTTP $status"

        if (code != null || status !in 200..299) {
            val e = when {
                code == "MISSING_API_KEY" -> OlpError.NoKey
                code == "INVALID_API_KEY" || status == 401 -> OlpError.BadKey
                code == "RATE_LIMIT_EXCEEDED" || (status == 429 && code != "BURST_LIMIT_EXCEEDED") -> OlpError.DailyLimit(resetTime(headers, now))
                code == "BURST_LIMIT_EXCEEDED" -> OlpError.Burst(headers["retry-after"]?.trim()?.toLongOrNull())
                code == "PAYMENT_REQUIRED" || status == 402 -> OlpError.Payment
                code == "TIER_LOCKED" || status == 403 -> OlpError.TierLocked
                code == "NOT_FOUND" || status == 404 -> OlpError.NotFound(tried)
                code == "MISSING_PARAMS" || code == "INVALID_YEAR" || status == 400 -> OlpError.BadRequest(detail)
                status >= 500 -> OlpError.Server
                else -> OlpError.Unexpected(detail)
            }
            return OlpResult.Err(e)
        }
        if (root == null) return OlpResult.Err(OlpError.Unexpected("not JSON"))
        val data = obj?.get("data") ?: root
        return OlpResult.Ok(data)
    }

    /** X-RateLimit-Reset-Daily as epoch seconds/millis or ISO time; otherwise next midnight UTC. */
    fun resetTime(headers: Map<String, String>, now: Instant = Instant.now()): Instant {
        val v = headers["x-ratelimit-reset-daily"]?.trim()
        v?.toLongOrNull()?.let { n ->
            return when {
                n > 100_000_000_000L -> Instant.ofEpochMilli(n)
                n > 1_000_000_000L -> Instant.ofEpochSecond(n)
                else -> now.plusSeconds(n) // seconds until reset
            }
        }
        v?.let { runCatching { return Instant.parse(it) } }
        return LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
    }

    fun remainingDaily(headers: Map<String, String>): Int? = headers["x-ratelimit-remaining-daily"]?.trim()?.toIntOrNull()

    /** /vehicles: strings or objects, possibly wrapped under makes/models/variants/items. */
    fun vehicleOptions(data: JsonElement): List<VehicleOption> {
        val arr = listArray(data, "makes", "models", "variants", "items", "results", "vehicles") ?: return emptyList()
        return arr.mapNotNull { e ->
            when (e) {
                is JsonPrimitive -> e.contentOrNull?.let { VehicleOption(slugify(it), it) }
                is JsonObject -> {
                    val name = str(e, "name", "label", "display", "displayName", "model", "make", "title")
                    val slug = str(e, "slug", "id", "value", "key", "modelSlug", "makeSlug") ?: name?.let(::slugify)
                    if (slug == null) null else VehicleOption(
                        slug = slug,
                        name = name ?: slug,
                        yearFrom = int(e, "yearStart", "year_start", "startYear", "minYear", "fromYear", "yearFrom") ?: yearsArray(e)?.minOrNull(),
                        yearTo = int(e, "yearEnd", "year_end", "endYear", "maxYear", "toYear", "yearTo") ?: yearsArray(e)?.maxOrNull(),
                        engines = enginesOf(e),
                    )
                }
                else -> null
            }
        }.distinctBy { it.slug }
    }

    /** /labor-times rows. */
    fun laborRows(data: JsonElement): List<LaborRow> {
        val arr = listArray(data, "laborTimes", "labor_times", "rows", "jobs", "items", "results") ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val job = str(o, "job", "jobName", "name") ?: return@mapNotNull null
            LaborRow(
                job = job,
                jobSlug = str(o, "jobSlug", "job_slug", "slug") ?: slugify(job),
                category = str(o, "category", "categoryName"),
                hoursHundredths = hours(o, "hours", "laborHours", "time"),
                lowHundredths = hours(o, "lowRange", "low_range", "low"),
                highHundredths = hours(o, "highRange", "high_range", "high"),
                confidence = str(o, "confidence"),
                engine = engineOf(o) ?: "",
            )
        }
    }

    private fun listArray(data: JsonElement, vararg keys: String): JsonArray? = when (data) {
        is JsonArray -> data
        is JsonObject -> keys.firstNotNullOfOrNull { data[it] as? JsonArray } ?: data.values.firstOrNull { it is JsonArray } as? JsonArray
        else -> null
    }

    private fun str(o: JsonObject, vararg keys: String): String? =
        keys.firstNotNullOfOrNull { k -> (o[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } }

    private fun int(o: JsonObject, vararg keys: String): Int? = str(o, *keys)?.toIntOrNull()

    private fun hours(o: JsonObject, vararg keys: String): Int? = str(o, *keys)?.let { Units.parseHours(it) }?.takeIf { it > 0 }

    private fun yearsArray(o: JsonObject): List<Int>? =
        (o["years"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }?.takeIf { it.isNotEmpty() }

    private fun enginesOf(o: JsonObject): List<String> = when (val e = o["engines"] ?: o["engine"]) {
        is JsonArray -> e.mapNotNull { x ->
            when (x) {
                is JsonPrimitive -> x.contentOrNull
                is JsonObject -> str(x, "slug", "id", "value", "engineSlug") ?: str(x, "name", "label")?.let(::slugify)
                else -> null
            }
        }
        is JsonPrimitive -> listOfNotNull(e.contentOrNull)
        is JsonObject -> listOfNotNull(str(e, "slug", "id") ?: str(e, "name")?.let(::slugify))
        else -> emptyList()
    }

    private fun engineOf(o: JsonObject): String? =
        str(o, "engineSlug", "engine_slug") ?: when (val e = o["engine"]) {
            is JsonPrimitive -> e.contentOrNull
            is JsonObject -> str(e, "slug", "id") ?: str(e, "name")?.let(::slugify)
            else -> null
        }

    fun slugify(s: String): String = s.lowercase().replace(Regex("[^a-z0-9.]+"), "-").trim('-')
}
