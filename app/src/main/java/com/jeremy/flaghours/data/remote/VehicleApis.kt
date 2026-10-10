package com.jeremy.flaghours.data.remote

import com.jeremy.flaghours.domain.Units
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class VehicleInfo(val year: Int?, val make: String?, val model: String?, val engine: String?)

data class LaborReference(
    val job: String,
    val hoursHundredths: Int?,
    val lowHundredths: Int?,
    val highHundredths: Int?,
    val confidence: String?,
)

/** JSON parsing for the two web APIs, kept free of Android/network code so it is unit-tested. */
object VehicleApiParsers {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** NHTSA vPIC DecodeVinValues response → vehicle. Null when vPIC couldn't decode it. */
    fun parseVpic(body: String): VehicleInfo? {
        val root = json.parseToJsonElement(body).jsonObject
        val r = (root["Results"] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        fun s(k: String) = (r[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "Not Applicable" }
        val make = s("Make")?.let(::titleCase)
        val model = s("Model")
        if (make == null && model == null) return null
        val disp = s("DisplacementL")?.let { d -> Units.parseHours(d)?.let { Units.formatHours(it, 1) + "L" } }
        val cyl = s("EngineCylinders")?.let { "$it-cyl" }
        val fuel = s("FuelTypePrimary")?.takeIf { it.contains("Electric", true) }?.let { "EV" }
        val engine = listOfNotNull(disp, cyl, fuel).joinToString(" ").ifEmpty { null }
        return VehicleInfo(s("ModelYear")?.toIntOrNull(), make, model, engine)
    }

    /**
     * Open Labor Project labor-times response → reference range. Accepts the documented shape and a few close
     * variants (top-level object, `data`/`results` wrapper or array; hours/low/high under common key names),
     * so a minor API change degrades to "no reference" instead of a crash.
     */
    fun parseOlp(body: String, job: String): LaborReference? {
        val root = json.parseToJsonElement(body)
        val obj = firstObject(root) ?: return null
        fun num(vararg keys: String): Int? = keys.firstNotNullOfOrNull { k -> findNumber(obj, k) }
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> (obj[k] as? JsonPrimitive)?.contentOrNull }
        val hours = num("hours", "labor_hours", "laborHours", "time", "labor_time", "laborTime", "typical_hours", "average_hours")
        val low = num("low", "min", "low_hours", "lowHours", "min_hours", "minHours", "range_low")
        val high = num("high", "max", "high_hours", "highHours", "max_hours", "maxHours", "range_high")
        if (hours == null && low == null && high == null) return null
        return LaborReference(
            job = str("job", "job_name", "name", "description", "operation") ?: job,
            hoursHundredths = hours,
            lowHundredths = low ?: hours,
            highHundredths = high ?: hours,
            confidence = str("confidence", "confidence_level", "quality"),
        )
    }

    private fun firstObject(e: JsonElement): JsonObject? = when (e) {
        is JsonArray -> e.firstOrNull()?.let { firstObject(it) }
        is JsonObject -> {
            val wrapped = listOf("data", "result", "results", "labor_time", "laborTime", "labor_times").firstNotNullOfOrNull { e[it] }
            if (wrapped != null && wrapped !is JsonPrimitive) firstObject(wrapped) else e
        }
        else -> null
    }

    /** Number at [key] in [obj] or in a nested `range` object, as hundredths of an hour. */
    private fun findNumber(obj: JsonObject, key: String): Int? {
        val v = obj[key] ?: (obj["range"] as? JsonObject)?.get(key) ?: return null
        val text = (v as? JsonPrimitive)?.contentOrNull ?: return null
        return Units.parseHours(text)?.takeIf { it > 0 }
    }

    private fun titleCase(s: String): String =
        if (s.length <= 3) s.uppercase() else s.lowercase().split(" ", "-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

/** "Front brake pads & rotors" → "front-brake-pads-rotors" (cache key and query). */
fun jobSlug(description: String): String =
    description.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(80)
