package com.jeremy.flaghours.domain

/** One labor-time row from the reference catalog for a vehicle. */
data class LaborRow(
    val job: String,
    val jobSlug: String,
    val category: String?,
    val hoursHundredths: Int?,
    val lowHundredths: Int?,
    val highHundredths: Int?,
    val confidence: String?,
    /** Engine slug the row applies to, or "" when the catalog doesn't say. */
    val engine: String = "",
)

data class JobMatch(val row: LaborRow, val score: Int)

/**
 * Matches a technician's job description ("R&R radiator", "frt pads") to catalog job names, which are named after the
 * part with no verb ("Radiator", "Brake Pads - Front"). Pure Kotlin, no network: it runs against cached rows.
 * Scores are 0..100.
 */
object JobMatcher {

    private val PHRASES = listOf(
        Regex("""\bremove\s*(and|&)\s*replace\b""") to " ",
        Regex("""\bremove\s*(and|&)\s*install\b""") to " ",
        Regex("""\br\s*(&|/|and)\s*r\b""") to " ",
        Regex("""\br\s*(&|/)\s*i\b""") to " ",
        Regex("""\bt-?stat\b""") to " thermostat ",
        Regex("""\ba/c\b""") to " ac ",
        Regex("""\bp/s\b""") to " power steering ",
        Regex("""\bo2\b""") to " oxygen sensor ",
        Regex("""\bw/""") to " ",
    )

    private val FILLER = setOf(
        "replace", "replacement", "replaced", "replacing", "install", "installed", "installation", "new", "w", "with",
        "and", "the", "a", "an", "of", "for", "to", "on", "in", "rr", "customer", "cust", "states", "perform", "performed",
        "job", "labor", "part", "parts", "assembly", "assy", "complete", "unit", "inspect", "check",
    )

    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "rad" to listOf("radiator"),
        "tstat" to listOf("thermostat"),
        "wp" to listOf("water", "pump"),
        "alt" to listOf("alternator"),
        "pads" to listOf("brake", "pad"),
        "pad" to listOf("brake", "pad"),
        "rotors" to listOf("brake", "rotor"),
        "rotor" to listOf("brake", "rotor"),
        "brakes" to listOf("brake", "pad"),
        "brks" to listOf("brake"),
        "brk" to listOf("brake"),
        "frt" to listOf("front"),
        "fr" to listOf("front"),
        "frnt" to listOf("front"),
        "fnt" to listOf("front"),
        "rear" to listOf("rear"),
        "rr" to listOf("rear"),
        "lh" to listOf("left"),
        "rh" to listOf("right"),
        "lf" to listOf("left", "front"),
        "rf" to listOf("right", "front"),
        "lr" to listOf("left", "rear"),
        "drvr" to listOf("left"),
        "pass" to listOf("right"),
        "plugs" to listOf("spark", "plug"),
        "trans" to listOf("transmission"),
        "xmsn" to listOf("transmission"),
        "cond" to listOf("condenser"),
        "comp" to listOf("compressor"),
        "serp" to listOf("serpentine"),
        "blt" to listOf("belt"),
        "eng" to listOf("engine"),
        "mtr" to listOf("motor"),
        "hdlamp" to listOf("headlamp"),
        "headlight" to listOf("headlamp"),
        "brg" to listOf("bearing"),
        "hub" to listOf("hub"),
        "cat" to listOf("catalytic", "converter"),
        "batt" to listOf("battery"),
        "strt" to listOf("starter"),
        "ac" to listOf("ac"),
        "diag" to listOf("diagnosis"),
        "diagnostic" to listOf("diagnosis"),
        "diagnose" to listOf("diagnosis"),
        "ps" to listOf("power", "steering"),
        "fuel" to listOf("fuel"),
        "ctrl" to listOf("control"),
        "susp" to listOf("suspension"),
        "shks" to listOf("shock"),
        "lwr" to listOf("lower"),
        "upr" to listOf("upper"),
    )

    /** Query tokens: phrases rewritten, punctuation split, filler dropped, abbreviations expanded, plurals stemmed. */
    fun queryTokens(text: String): List<String> {
        val raw = baseTokens(text)
        // "rr" means rear only when it isn't the R&R verb (already removed above); keep it as rear.
        return raw.flatMap { t -> SYNONYMS[t] ?: listOf(t) }
            .filter { it !in FILLER || it == "rear" }
            .map(::stem)
            .distinct()
    }

    /** Candidate tokens from a catalog job name or slug (no filler removal beyond trivial words). */
    fun candidateTokens(text: String): List<String> =
        baseTokens(text).filter { it !in setOf("and", "the", "of", "w", "with", "a") }.map(::stem).distinct()

    private fun baseTokens(text: String): List<String> {
        var s = " " + text.lowercase() + " "
        for ((re, rep) in PHRASES) s = s.replace(re, rep)
        return s.split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
    }

    private fun stem(t: String): String = when {
        t.length > 4 && t.endsWith("ies") -> t.dropLast(3) + "y"
        t.length > 3 && t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
        else -> t
    }

    fun score(query: List<String>, row: LaborRow): Int {
        if (query.isEmpty()) return 0
        val cand = (candidateTokens(row.job) + candidateTokens(row.jobSlug.replace('-', ' '))).distinct()
        if (cand.isEmpty()) return 0
        var recall = 0.0
        val used = mutableSetOf<String>()
        for (q in query) {
            var best = 0.0
            var bestTok: String? = null
            for (c in cand) {
                val s = tokenSimilarity(q, c)
                if (s > best) { best = s; bestTok = c }
            }
            recall += best
            if (best >= 0.7 && bestTok != null) used += bestTok
        }
        recall /= query.size
        val nameTokens = candidateTokens(row.job).ifEmpty { cand }
        val precision = nameTokens.count { it in used }.toDouble() / nameTokens.size
        return ((0.7 * recall + 0.3 * precision) * 100).toInt()
    }

    private fun tokenSimilarity(q: String, c: String): Double {
        if (q == c) return 1.0
        if (q.length >= 3 && c.startsWith(q)) return 0.8
        if (c.length >= 3 && q.startsWith(c) && c.length >= q.length - 2) return 0.75
        val d = editDistance(q, c)
        return when {
            q.length >= 4 && d <= 1 -> 0.7
            q.length >= 7 && d <= 2 -> 0.5
            else -> 0.0
        }
    }

    fun editDistance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** Best matches for [description], highest first; ties go to the shorter (more general) job name. */
    fun match(description: String, rows: List<LaborRow>, limit: Int = 5, minScore: Int = 35): List<JobMatch> {
        val q = queryTokens(description)
        if (q.isEmpty()) return emptyList()
        return rows.asSequence()
            .map { JobMatch(it, score(q, it)) }
            .filter { it.score >= minScore }
            .sortedWith(compareByDescending<JobMatch> { it.score }.thenBy { it.row.job.length }.thenBy { it.row.job })
            .distinctBy { it.row.jobSlug + "|" + it.row.engine }
            .take(limit)
            .toList()
    }

    /** True when the top match is strong and clearly ahead, so it can be applied without asking. */
    fun isConfident(matches: List<JobMatch>): Boolean {
        val top = matches.firstOrNull() ?: return false
        val second = matches.getOrNull(1)?.score ?: 0
        return top.score >= 85 && top.score - second >= 8
    }
}

/** Picks an engine slug like "3.5l-v6-ecoboost" from displacement + cylinders. */
object EngineMatcher {
    private val DISP = Regex("""(\d{1,2}\.\d)\s*l""")
    private val CYL = Regex("""\b(?:v|i|l|h|w|f)(\d{1,2})\b|(\d{1,2})\s*-?\s*cyl""")

    fun displacement(text: String): String? = DISP.find(text.lowercase())?.groupValues?.get(1)

    fun cylinders(text: String): Int? = CYL.find(text.lowercase().replace('-', ' '))?.let { m ->
        (m.groupValues[1].ifEmpty { m.groupValues[2] }).toIntOrNull()
    }

    /** Engines consistent with the vehicle; empty when nothing fits (then the engine filter is skipped). */
    fun candidates(engines: List<String>, displacementL: String?, cylinders: Int?): List<String> {
        if (displacementL == null && cylinders == null) return engines
        val withDisp = if (displacementL != null) engines.filter { displacement(it) == displacementL } else engines
        val withBoth = if (cylinders != null) withDisp.filter { cylinders(it) == null || cylinders(it) == cylinders } else withDisp
        return withBoth.ifEmpty { withDisp }
    }
}

/** A make, model or variant from the catalog's /vehicles endpoint. */
data class VehicleOption(
    val slug: String,
    val name: String,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val engines: List<String> = emptyList(),
) {
    fun coversYear(year: Int): Boolean = (yearFrom == null || year >= yearFrom) && (yearTo == null || year <= yearTo)
}

/** Maps NHTSA-style names ("CHEVROLET", "F-150", "Silverado 1500") to catalog slugs. */
object VehicleMatcher {
    private val MAKE_ALIASES = mapOf(
        "chevy" to "chevrolet", "vw" to "volkswagen", "mercedes" to "mercedes-benz", "mercedes benz" to "mercedes-benz",
        "land rover" to "land-rover", "alfa romeo" to "alfa-romeo", "mini cooper" to "mini",
    )

    fun norm(s: String): String = s.lowercase().replace(Regex("""[^a-z0-9]+"""), " ").trim()
    private fun compact(s: String): String = norm(s).replace(" ", "")

    fun matchMake(make: String, makes: List<VehicleOption>): VehicleOption? {
        val n = norm(make)
        val target = MAKE_ALIASES[n] ?: n
        return makes.firstOrNull { compact(it.slug) == compact(target) || compact(it.name) == compact(target) }
            ?: makes.firstOrNull { compact(it.slug).startsWith(compact(target)) || compact(it.name).startsWith(compact(target)) }
    }

    /** Models ranked by similarity to [model]; only those covering [year] when years are known. */
    fun rankModels(model: String, year: Int?, models: List<VehicleOption>): List<Pair<VehicleOption, Int>> {
        val q = compact(model)
        val qTokens = norm(model).split(" ").filter { it.isNotEmpty() }
        if (q.isEmpty()) return emptyList()
        return models
            .filter { year == null || it.coversYear(year) }
            .map { m ->
                val names = listOf(compact(m.slug), compact(m.name))
                val tokens = (norm(m.slug).split(" ") + norm(m.name).split(" ")).filter { it.isNotEmpty() }.toSet()
                val s = when {
                    names.any { it == q } -> 100
                    names.any { it.startsWith(q) || q.startsWith(it) } -> 85
                    else -> {
                        val overlap = qTokens.count { it in tokens }
                        if (overlap == 0) 0 else 40 + 40 * overlap / maxOf(qTokens.size, tokens.size / 2)
                    }
                }
                m to s.coerceAtMost(100)
            }
            .filter { it.second >= 40 }
            .sortedWith(compareByDescending<Pair<VehicleOption, Int>> { it.second }.thenBy { it.first.name.length })
    }

    /** The single clear model, or null if the user should pick. */
    fun confidentModel(ranked: List<Pair<VehicleOption, Int>>): VehicleOption? {
        val top = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)?.second ?: 0
        return if (top.second >= 85 && top.second > second) top.first else null
    }
}
