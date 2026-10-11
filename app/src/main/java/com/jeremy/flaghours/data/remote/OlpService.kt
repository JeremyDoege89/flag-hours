package com.jeremy.flaghours.data.remote

import com.jeremy.flaghours.data.db.LaborRowEntity
import com.jeremy.flaghours.data.db.OlpCatalog
import com.jeremy.flaghours.data.db.OlpDao
import com.jeremy.flaghours.data.db.OlpVehicle
import com.jeremy.flaghours.data.db.OlpVehicleAlias
import com.jeremy.flaghours.domain.EngineMatcher
import com.jeremy.flaghours.domain.LaborRow
import com.jeremy.flaghours.domain.VehicleMatcher
import com.jeremy.flaghours.domain.VehicleOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** The RO's vehicle as entered or decoded from the VIN. */
data class VehicleQuery(
    val year: Int,
    val make: String,
    val model: String,
    val displacementL: String? = null,
    val cylinders: Int? = null,
) {
    val aliasKey: String get() = "$year|${VehicleMatcher.norm(make)}|${VehicleMatcher.norm(model)}"
    val label: String get() = "$year $make $model"
}

sealed interface VehicleLoad {
    /** Rows for the vehicle (already filtered to the chosen engine, if any). */
    data class Ready(val vehicleKey: String, val rows: List<LaborRow>, val engine: String?, val engines: List<String>, val fromCache: Boolean) : VehicleLoad
    data class ChooseMake(val options: List<VehicleOption>, val tried: String) : VehicleLoad
    data class ChooseModel(val makeSlug: String, val options: List<VehicleOption>, val tried: String) : VehicleLoad
    data class ChooseEngine(val vehicleKey: String, val options: List<String>) : VehicleLoad
    data class Failed(val error: OlpError) : VehicleLoad
}

data class Quota(val remaining: Int?, val resetAt: Instant?)

/**
 * Open Labor Project lookups designed for the 10/day free tier: the makes list and models per make are cached for a
 * long time, and each vehicle costs one /labor-times call (no `job`, no `engine`). Job matching then happens locally.
 * See docs/olp-api.md.
 */
class OlpService(
    private val dao: OlpDao,
    private val apiKey: () -> String?,
    private val log: OlpDebugLog?,
    private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build(),
    private val clock: () -> Instant = Instant::now,
    private val baseUrl: String = "https://openlaborproject.com/api/v1/",
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _quota = MutableStateFlow(Quota(null, null))
    val quota: StateFlow<Quota> = _quota

    /** Number of network calls made by this instance (for tests and the debug screen). */
    var callCount = 0
        private set

    fun hasKey(): Boolean = !apiKey().isNullOrBlank()

    /** Cached rows only; never touches the network. Null when the vehicle hasn't been loaded yet. */
    suspend fun cached(q: VehicleQuery): VehicleLoad? {
        val alias = dao.alias(q.aliasKey) ?: return null
        val vehicle = dao.vehicle(alias.vehicleKey) ?: return null
        return fromCache(vehicle.vehicleKey, alias, q)
    }

    /** Resolve the vehicle and fetch its rows (at most: makes + models + labor-times = 3 calls, usually 1 or 0). */
    suspend fun load(q: VehicleQuery): VehicleLoad {
        cached(q)?.let { return it }
        val makes = when (val r = makes()) {
            is OlpResult.Ok -> r.value
            is OlpResult.Err -> return VehicleLoad.Failed(r.error)
        }
        val make = VehicleMatcher.matchMake(q.make, makes)
            ?: return VehicleLoad.ChooseMake(makes, "make \"${OlpParsers.slugify(q.make)}\"")
        return loadForMake(q, make.slug)
    }

    /** Continue after the user picked a make. */
    suspend fun loadForMake(q: VehicleQuery, makeSlug: String): VehicleLoad {
        val models = when (val r = models(makeSlug)) {
            is OlpResult.Ok -> r.value
            is OlpResult.Err -> return VehicleLoad.Failed(r.error)
        }
        val ranked = VehicleMatcher.rankModels(q.model, q.year, models)
        val model = VehicleMatcher.confidentModel(ranked)
        val tried = "$makeSlug / \"${OlpParsers.slugify(q.model)}\" / ${q.year}"
        return when {
            model != null -> loadVehicle(q, makeSlug, model.slug)
            ranked.isNotEmpty() -> VehicleLoad.ChooseModel(makeSlug, ranked.map { it.first }, tried)
            models.isNotEmpty() -> VehicleLoad.ChooseModel(makeSlug, models.filter { it.coversYear(q.year) }.ifEmpty { models }, tried)
            else -> loadVehicle(q, makeSlug, OlpParsers.slugify(q.model))
        }
    }

    /** Fetch rows for exact slugs (after a model pick, or from the manual picker). */
    suspend fun loadVehicle(q: VehicleQuery, makeSlug: String, modelSlug: String): VehicleLoad {
        val key = "$makeSlug|$modelSlug|${q.year}"
        dao.vehicle(key)?.let { v ->
            val alias = dao.alias(q.aliasKey)?.takeIf { it.vehicleKey == key } ?: OlpVehicleAlias(q.aliasKey, key, null).also { dao.upsertAlias(it) }
            return fromCache(v.vehicleKey, alias, q)
        }
        val tried = "$makeSlug / $modelSlug / ${q.year}"
        val data = when (val r = get("labor-times", listOf("make" to makeSlug, "model" to modelSlug, "year" to q.year.toString()), tried)) {
            is OlpResult.Ok -> r.value
            is OlpResult.Err -> return VehicleLoad.Failed(r.error)
        }
        val rows = OlpParsers.laborRows(data)
        if (rows.isEmpty()) return VehicleLoad.Failed(OlpError.NotFound(tried))
        dao.replaceVehicle(
            OlpVehicle(key, makeSlug, modelSlug, q.year, rows.size, clock().toEpochMilli()),
            rows.map { LaborRowEntity(key, it.engine, it.jobSlug, it.job, it.category, it.hoursHundredths, it.lowHundredths, it.highHundredths, it.confidence) }
                .distinctBy { it.engine + "|" + it.jobSlug },
        )
        val alias = OlpVehicleAlias(q.aliasKey, key, null)
        dao.upsertAlias(alias)
        return fromCache(key, alias, q, fresh = true)
    }

    suspend fun chooseEngine(q: VehicleQuery, vehicleKey: String, engine: String): VehicleLoad {
        val alias = OlpVehicleAlias(q.aliasKey, vehicleKey, engine)
        dao.upsertAlias(alias)
        return fromCache(vehicleKey, alias, q)
    }

    /** Forget which catalog vehicle/engine this RO vehicle maps to (rows stay cached). */
    suspend fun forget(q: VehicleQuery) = dao.deleteAlias(q.aliasKey)

    private suspend fun fromCache(vehicleKey: String, alias: OlpVehicleAlias, q: VehicleQuery, fresh: Boolean = false): VehicleLoad {
        val all = dao.rows(vehicleKey).map { it.toRow() }
        val engines = all.map { it.engine }.filter { it.isNotEmpty() }.distinct().sorted()
        var engine = alias.engineSlug
        if (engine == null) {
            val candidates = EngineMatcher.candidates(engines, q.displacementL, q.cylinders)
            engine = when {
                engines.size <= 1 -> ""
                candidates.size == 1 -> candidates.single()
                else -> return VehicleLoad.ChooseEngine(vehicleKey, candidates.ifEmpty { engines })
            }
            dao.upsertAlias(alias.copy(engineSlug = engine))
        }
        val rows = if (engine.isEmpty()) all else all.filter { it.engine == engine || it.engine.isEmpty() }
        return VehicleLoad.Ready(vehicleKey, rows, engine.ifEmpty { null }, engines, fromCache = !fresh)
    }

    // --- /vehicles (cached) ---

    suspend fun makes(): OlpResult<List<VehicleOption>> = catalog("makes", MAKES_MAX_AGE, emptyList(), "the makes list")

    suspend fun models(makeSlug: String): OlpResult<List<VehicleOption>> =
        catalog("models:$makeSlug", MODELS_MAX_AGE, listOf("make" to makeSlug), "make \"$makeSlug\"")

    private suspend fun catalog(key: String, maxAge: Duration, params: List<Pair<String, String>>, tried: String): OlpResult<List<VehicleOption>> {
        val cached = dao.catalog(key)
        if (cached != null && clock().toEpochMilli() - cached.fetchedAt < maxAge.toMillis()) {
            runCatching { OlpParsers.vehicleOptions(json.parseToJsonElement(cached.json)) }.getOrNull()?.let { return OlpResult.Ok(it) }
        }
        return when (val r = get("vehicles", params, tried)) {
            is OlpResult.Ok -> {
                val options = OlpParsers.vehicleOptions(r.value)
                if (options.isNotEmpty()) dao.upsertCatalog(OlpCatalog(key, r.value.toString(), clock().toEpochMilli()))
                OlpResult.Ok(options)
            }
            is OlpResult.Err -> {
                // A stale cache beats nothing.
                cached?.let { c -> runCatching { OlpParsers.vehicleOptions(json.parseToJsonElement(c.json)) }.getOrNull()?.takeIf { it.isNotEmpty() } }
                    ?.let { OlpResult.Ok(it) } ?: r
            }
        }
    }

    // --- HTTP ---

    private suspend fun get(path: String, params: List<Pair<String, String>>, tried: String): OlpResult<JsonElement> {
        val key = apiKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return OlpResult.Err(OlpError.NoKey)
        val q = _quota.value
        val now = clock()
        if (q.remaining == 0 && q.resetAt != null && q.resetAt.isAfter(now)) return OlpResult.Err(OlpError.DailyLimit(q.resetAt))
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return withContext(Dispatchers.IO) {
            try {
                callCount++
                http.newCall(Request.Builder().url(url).header("x-api-key", key).header("Accept", "application/json").build()).execute().use { resp ->
                    val body = resp.body.string()
                    val headers = resp.headers.associate { (n, v) -> n.lowercase() to v }
                    log?.let { runCatching { it.record(url.toString(), resp.code, headers, body) } }
                    val remaining = OlpParsers.remainingDaily(headers)
                    val result = OlpParsers.envelope(resp.code, body, headers, tried, now)
                    _quota.value = when {
                        result is OlpResult.Err && result.error is OlpError.DailyLimit -> Quota(0, (result.error as OlpError.DailyLimit).resetAt)
                        remaining != null -> Quota(remaining, OlpParsers.resetTime(headers, now))
                        else -> _quota.value
                    }
                    result
                }
            } catch (e: IOException) {
                OlpResult.Err(OlpError.Offline)
            }
        }
    }

    companion object {
        val MAKES_MAX_AGE: Duration = Duration.ofDays(180)
        val MODELS_MAX_AGE: Duration = Duration.ofDays(30)
        const val ATTRIBUTION = "Labor reference data: Open Labor Project"
    }
}
