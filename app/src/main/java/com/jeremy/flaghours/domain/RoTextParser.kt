package com.jeremy.flaghours.domain

data class ParsedLine(val description: String, val hoursHundredths: Int, val laborType: LaborType)

data class ParsedRo(
    val roNumber: String?,
    val vin: String?,
    val year: Int?,
    val make: String?,
    val model: String?,
    val lines: List<ParsedLine>,
)

/**
 * Heuristics that turn OCR text from a repair order into a draft. Everything it finds is shown for review
 * before saving; it never guesses silently. Pure Kotlin so it can be unit-tested against sample RO text.
 */
object RoTextParser {

    private val RO_LABELED = Regex(
        """(?i)\b(?:R\s?\.?\s?O\.?|REPAIR\s+ORDER|WORK\s+ORDER|INVOICE|TAG)\s*(?:#|NO\.?|NUM(?:BER)?\.?)?\s*[:#.]?\s*([A-Z]{0,2}\d{4,10})\b""",
    )
    private val VIN_LABELED = Regex("""(?i)\bVIN\b\s*[:#.]?\s*([A-Z0-9 \-]{17,23})""")
    private val VIN_TOKEN = Regex("""\b[A-Z0-9]{17}\b""")
    private val YEAR_MAKE_MODEL: Regex
    private val MAKES = listOf(
        "ACURA", "ALFA ROMEO", "AUDI", "BMW", "BUICK", "CADILLAC", "CHEVROLET", "CHEVY", "CHRYSLER", "DODGE", "FIAT", "FORD",
        "GENESIS", "GMC", "HONDA", "HYUNDAI", "INFINITI", "JAGUAR", "JEEP", "KIA", "LAND ROVER", "LEXUS", "LINCOLN", "MAZDA",
        "MERCEDES-BENZ", "MERCEDES", "MINI", "MITSUBISHI", "NISSAN", "POLESTAR", "PORSCHE", "RAM", "RIVIAN", "SUBARU",
        "TESLA", "TOYOTA", "VOLKSWAGEN", "VW", "VOLVO",
    )

    init {
        val makes = MAKES.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it).replace(" ", "\\s+") }
        YEAR_MAKE_MODEL = Regex("""(?i)\b((?:19[89]|20[0-4])\d)\s+($makes)\b\s*([A-Z0-9][A-Z0-9\-]*(?:\s+[A-Z0-9\-]{1,5})?)?""")
    }

    /** Hours with an explicit unit or label: "1.6 HRS", "HRS: 2.3", "HOURS 0.8", "FLAG 1.2", "1.5H". */
    private val HOURS_LABELED = listOf(
        Regex("""(?i)(\d{1,2}(?:\.\d{1,2})?|\.\d{1,2})\s*(?:HRS?|HOURS?|H)\b\.?"""),
        Regex("""(?i)\b(?:HRS?|HOURS?|FLAG(?:GED)?|LABOR\s+TIME|TIME|FRH?)\s*[:=]?\s*(\d{1,2}(?:\.\d{1,2})?|\.\d{1,2})\b"""),
    )

    /** A labor line that starts with a line code ("A", "1)", "LINE B", "J2") and ends in a one-decimal number. */
    private val LINE_CODE_TRAILING = Regex("""(?i)^\s*(?:LINE\s+)?(?:[A-Z]|\d{1,2}|[A-Z]\d)[).:\-]?\s+(.+?)\s+(\d{1,2}\.\d)\s*$""")

    private val MONEY = Regex("""\$\s*\d""")
    private val SKIP_WORDS = Regex("""(?i)\b(TOTAL|SUBTOTAL|TAX|PARTS|SUPPLIES|FEE|DISCOUNT|AMOUNT|BALANCE|MILEAGE|ODOMETER|MILES|PHONE|QTY)\b""")

    fun parse(text: String): ParsedRo {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val upper = text.uppercase()
        return ParsedRo(
            roNumber = findRoNumber(lines),
            vin = findVin(upper),
            year = null,
            make = null,
            model = null,
            lines = findLaborLines(lines),
        ).let { withVehicle(it, upper) }
    }

    fun findRoNumber(lines: List<String>): String? {
        for (l in lines) {
            RO_LABELED.find(l)?.let { return it.groupValues[1].uppercase() }
        }
        return null
    }

    fun findVin(upperText: String): String? {
        VIN_LABELED.find(upperText)?.let { m ->
            val cleaned = normalizeVin(m.groupValues[1].replace(" ", "").replace("-", "").take(17))
            if (cleaned.length == 17 && looksLikeVin(cleaned)) return cleaned
        }
        val candidates = VIN_TOKEN.findAll(upperText).map { normalizeVin(it.value) }.filter { looksLikeVin(it) }.toList()
        return candidates.firstOrNull { vinCheckDigitOk(it) } ?: candidates.firstOrNull()
    }

    /** OCR fixes: VINs never contain I, O or Q. */
    fun normalizeVin(raw: String): String = raw.uppercase().replace('O', '0').replace('Q', '0').replace('I', '1')

    private fun looksLikeVin(v: String): Boolean =
        v.length == 17 && v.all { it.isLetterOrDigit() } && v.count { it.isDigit() } >= 5 && v.count { it.isLetter() } >= 2

    /** North American check digit (position 9). */
    fun vinCheckDigitOk(vin: String): Boolean {
        if (vin.length != 17) return false
        val weights = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)
        var sum = 0
        for (i in 0 until 17) {
            val c = vin[i]
            val v = when (c) {
                in '0'..'9' -> c - '0'
                in "AJ" -> 1; in "BKS" -> 2; in "CLT" -> 3; in "DMU" -> 4; in "ENV" -> 5
                in "FW" -> 6; in "GPX" -> 7; in "HY" -> 8; in "RZ" -> 9
                else -> return false
            }
            sum += v * weights[i]
        }
        val r = sum % 11
        val expected = if (r == 10) 'X' else ('0' + r)
        return vin[8] == expected
    }

    private fun withVehicle(ro: ParsedRo, upper: String): ParsedRo {
        val m = YEAR_MAKE_MODEL.find(upper) ?: return ro
        val make = m.groupValues[2].replace(Regex("\\s+"), " ").let {
            when (it) {
                "CHEVY" -> "CHEVROLET"
                "VW" -> "VOLKSWAGEN"
                else -> it
            }
        }
        val model = m.groupValues.getOrNull(3)?.trim()?.ifEmpty { null }?.takeUnless { it.matches(Regex("""\d{1,2}\.\d+""")) }
        return ro.copy(year = m.groupValues[1].toInt(), make = titleCase(make), model = model)
    }

    fun findLaborLines(lines: List<String>): List<ParsedLine> {
        val out = mutableListOf<ParsedLine>()
        var context: LaborType? = null
        lines.forEachIndexed { i, raw ->
            val line = raw.replace('\t', ' ')
            laborTypeIn(line)?.let { context = it }
            if (MONEY.containsMatchIn(line) || SKIP_WORDS.containsMatchIn(line)) return@forEachIndexed
            if (RO_LABELED.containsMatchIn(line) || line.contains("VIN", ignoreCase = true)) return@forEachIndexed

            var hours: Int? = null
            var desc: String? = null
            for (re in HOURS_LABELED) {
                val m = re.find(line) ?: continue
                val h = Units.parseHours(m.groupValues[1]) ?: continue
                if (h in 10..3000) {
                    hours = h
                    desc = line.removeRange(m.range)
                    break
                }
            }
            if (hours == null) {
                LINE_CODE_TRAILING.find(line)?.let { m ->
                    val h = Units.parseHours(m.groupValues[2])
                    if (h != null && h in 10..3000) {
                        hours = h
                        desc = m.groupValues[1]
                    }
                }
            }
            val h = hours ?: return@forEachIndexed
            var d = cleanDescription(desc ?: "")
            if (d.length < 3 && i > 0) d = cleanDescription(lines[i - 1])
            out += ParsedLine(d.ifEmpty { "Labor" }, h, laborTypeIn(line) ?: context ?: LaborType.CUSTOMER)
        }
        return out
    }

    private fun laborTypeIn(line: String): LaborType? {
        val u = line.uppercase()
        return when {
            Regex("""\bWARR(ANTY)?\b|\bW/?TY\b|\bPAY\s*TYPE\s*[:=]?\s*W\b""").containsMatchIn(u) -> LaborType.WARRANTY
            Regex("""\bINTERNAL\b|\bINT\b|\bPAY\s*TYPE\s*[:=]?\s*I\b""").containsMatchIn(u) -> LaborType.INTERNAL
            Regex("""\bDIAG(NOSTIC|NOSIS)?S?\b""").containsMatchIn(u) -> LaborType.DIAG
            Regex("""\bCUSTOMER\b|\bC/P\b|\bCUST\b|\bPAY\s*TYPE\s*[:=]?\s*C\b""").containsMatchIn(u) -> LaborType.CUSTOMER
            else -> null
        }
    }

    private fun cleanDescription(s: String): String {
        var d = s
            .replace(Regex("""(?i)\b(LABOR|LBR|HRS?|HOURS?|FLAG(GED)?|TIME|FRH?|TECH\s*#?\s*\d*|OP\s*CODE\s*[:#]?\s*\S+)\b[:=]?"""), " ")
            .replace(Regex("""(?i)\b(WARRANTY|WARR|INTERNAL|CUSTOMER|C/P|CUST|PAY\s*TYPE\s*[:=]?\s*[WCI])\b"""), " ")
            .replace(Regex("""^\s*(?:LINE\s+)?(?:[A-Z]|\d{1,2}|[A-Z]\d)[).:\-]\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^\s*[A-Z]\s+(?=[A-Z]{2,})"""), "")
            .replace(Regex("""[\s\-:|*]+$"""), "")
            .replace(Regex("""^[\s\-:|*]+"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        if (d.isNotEmpty() && d == d.uppercase() && d.any { it.isLetter() }) d = sentenceCase(d)
        return d.replaceFirstChar { it.uppercase() }
    }

    private fun sentenceCase(s: String) = s.lowercase().replaceFirstChar { it.uppercase() }

    private fun titleCase(s: String) = s.lowercase().split(" ", "-").joinToString(" ") { w ->
        if (w in setOf("bmw", "gmc", "ram", "mini")) w.uppercase() else w.replaceFirstChar { it.uppercase() }
    }.replace("Mercedes Benz", "Mercedes-Benz")
}

/** VIN from a barcode payload (Code 39/128, Data Matrix, QR). Import labels often prefix an extra "I". */
fun vinFromBarcode(raw: String): String? {
    val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }
    val candidates = buildList {
        if (cleaned.length == 18 && cleaned.startsWith("I")) add(cleaned.drop(1))
        for (i in 0..(cleaned.length - 17).coerceAtLeast(-1)) if (i >= 0) add(cleaned.substring(i, i + 17))
    }.filter { v -> v.length == 17 && v.none { it in "IOQ" } && v.count(Char::isDigit) >= 5 }
    return candidates.firstOrNull { RoTextParser.vinCheckDigitOk(it) } ?: candidates.firstOrNull()?.takeIf { cleaned.length in 17..18 }
}
