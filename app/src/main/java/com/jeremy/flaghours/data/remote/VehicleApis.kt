package com.jeremy.flaghours.data.remote

import com.jeremy.flaghours.domain.Units
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

data class VehicleInfo(
    val year: Int?,
    val make: String?,
    val model: String?,
    val engine: String?,
    val displacementL: String? = null,
    val cylinders: Int? = null,
)

/** NHTSA vPIC parsing, kept free of Android/network code so it is unit-tested. */
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
        val dispL = s("DisplacementL")?.let { d -> Units.parseHours(d)?.let { Units.formatHours(it, 1) } }
        return VehicleInfo(s("ModelYear")?.toIntOrNull(), make, model, engine, dispL, s("EngineCylinders")?.toIntOrNull())
    }

    private fun titleCase(s: String): String =
        if (s.length <= 3) s.uppercase() else s.lowercase().split(" ", "-").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
}

