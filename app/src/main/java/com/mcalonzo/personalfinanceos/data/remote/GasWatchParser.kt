package com.mcalonzo.personalfinanceos.data.remote

import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONException
import org.json.JSONObject

enum class GasFuelType(val jsonKey: String, val displayName: String) {
    UNLEADED("unleaded", "Unleaded"),
    PREMIUM_95("premium95", "Premium 95"),
    PREMIUM_97("premium97", "Premium 97"),
    DIESEL("diesel", "Diesel"),
    PREMIUM_DIESEL("premiumDiesel", "Premium Diesel"),
    EGASOLINE("egasoline", "E-Gasoline"),
    KEROSENE("kerosene", "Kerosene");

    companion object {
        fun fromKeyOrLabel(value: String): GasFuelType {
            val normalized = value.trim()
            return entries.firstOrNull {
                it.jsonKey.equals(normalized, ignoreCase = true) ||
                    it.displayName.equals(normalized, ignoreCase = true)
            } ?: PREMIUM_95
        }
    }
}

data class GasLocationOption(
    val slug: String,
    val displayName: String
)

object GasWatchLocations {
    val ALL: List<GasLocationOption> = listOf(
        GasLocationOption("quezon-city", "Quezon City"),
        GasLocationOption("manila", "Manila"),
        GasLocationOption("makati", "Makati"),
        GasLocationOption("pasig", "Pasig"),
        GasLocationOption("taguig", "Taguig"),
        GasLocationOption("mandaluyong", "Mandaluyong"),
        GasLocationOption("san-juan", "San Juan"),
        GasLocationOption("marikina", "Marikina"),
        GasLocationOption("caloocan", "Caloocan"),
        GasLocationOption("paranaque", "Parañaque"),
        GasLocationOption("las-pinas", "Las Piñas"),
        GasLocationOption("muntinlupa", "Muntinlupa"),
        GasLocationOption("valenzuela", "Valenzuela"),
        GasLocationOption("malabon", "Malabon"),
        GasLocationOption("navotas", "Navotas"),
        GasLocationOption("pateros", "Pateros"),
        GasLocationOption("antipolo", "Antipolo"),
        GasLocationOption("cavite-city", "Cavite City")
    )
}

data class ParsedGasStation(
    val stationId: Int,
    val brand: String,
    val stationName: String,
    val pricesCentavos: Map<GasFuelType, Long>
)

data class ParsedGasPrice(
    val stationId: Int,
    val stationName: String,
    val premium95Centavos: Long
)

object GasWatchAllowlist {
    val ALLOWED_BRANDS: Set<String> = setOf("petron", "shell", "caltex", "seaoil", "phoenix")
    val ALLOWED_FUEL_TYPES: List<GasFuelType> = listOf(GasFuelType.UNLEADED, GasFuelType.PREMIUM_95)

    fun isAllowedBrand(brand: String): Boolean = brand.trim().lowercase() in ALLOWED_BRANDS

    fun isAllowedFuel(fuel: GasFuelType): Boolean = fuel in ALLOWED_FUEL_TYPES

    fun coerceFuelType(fuel: GasFuelType): GasFuelType =
        if (isAllowedFuel(fuel)) fuel else GasFuelType.PREMIUM_95

    fun filterStations(stations: List<ParsedGasStation>): List<ParsedGasStation> {
        return stations.mapNotNull { station ->
            if (!isAllowedBrand(station.brand)) return@mapNotNull null
            val allowedPrices = station.pricesCentavos.filterKeys { isAllowedFuel(it) }
            if (allowedPrices.isEmpty()) return@mapNotNull null
            station.copy(pricesCentavos = allowedPrices)
        }
    }
}

/**
 * Extracts station fuel prices from standard inline `<script>` JSON or Next.js escaped
 * script payloads on GasWatchPH.
 */
object GasWatchParser {

    private const val DEFAULT_STATION_ID = 874
    private val CENTAVOS_MULTIPLIER = BigDecimal(100)
    private val SCRIPT_BLOCK_REGEX = Regex("<script\\b[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    private val SCRIPT_TAG_PRESENT_REGEX = Regex("<script\\b", RegexOption.IGNORE_CASE)
    private val PRICES_ANCHOR_REGEX = Regex(""""prices"\s*:\s*\{""")

    fun parseStationPremium95(
        html: String,
        targetStationId: Int = DEFAULT_STATION_ID
    ): ParsedGasPrice? = parseStationPrice(html, targetStationId, GasFuelType.PREMIUM_95)

    fun parseStationPrice(
        html: String,
        targetStationId: Int = DEFAULT_STATION_ID,
        fuelType: GasFuelType = GasFuelType.PREMIUM_95
    ): ParsedGasPrice? {
        if (html.isBlank()) return null

        return try {
            val candidateBlocks = extractCandidateBlocks(html) ?: return null
            val idPattern = Regex(""""id"\s*:\s*${Regex.escape(targetStationId.toString())}(?![0-9])""")
            val idToken = targetStationId.toString()

            for (block in candidateBlocks) {
                if (!block.contains(idToken)) continue

                parseFromText(block, idPattern, targetStationId, fuelType)?.let { return it }

                if (block.contains("\\\"")) {
                    val unescapedLayer1 = unescapeJsonStringLayer(block)
                    parseFromText(unescapedLayer1, idPattern, targetStationId, fuelType)?.let { return it }

                    if (unescapedLayer1.contains("\\\"")) {
                        val unescapedLayer2 = unescapeJsonStringLayer(unescapedLayer1)
                        parseFromText(unescapedLayer2, idPattern, targetStationId, fuelType)?.let { return it }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun parseStations(html: String): List<ParsedGasStation> {
        if (html.isBlank()) return emptyList()

        return try {
            val candidateBlocks = extractCandidateBlocks(html) ?: return emptyList()
            val byId = LinkedHashMap<Int, ParsedGasStation>()

            for (block in candidateBlocks) {
                if (!block.contains("prices")) continue

                collectStationsFromText(block, byId)

                if (block.contains("\\\"")) {
                    val unescapedLayer1 = unescapeJsonStringLayer(block)
                    collectStationsFromText(unescapedLayer1, byId)

                    if (unescapedLayer1.contains("\\\"")) {
                        val unescapedLayer2 = unescapeJsonStringLayer(unescapedLayer1)
                        collectStationsFromText(unescapedLayer2, byId)
                    }
                }
            }

            byId.values.sortedWith(compareBy<ParsedGasStation> { it.brand.lowercase() }.thenBy { it.stationName.lowercase() })
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractCandidateBlocks(html: String): List<String>? {
        val scriptBlocks = SCRIPT_BLOCK_REGEX.findAll(html)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()

        return when {
            scriptBlocks.isNotEmpty() -> scriptBlocks
            !SCRIPT_TAG_PRESENT_REGEX.containsMatchIn(html) -> listOf(html)
            else -> null
        }
    }

    private fun collectStationsFromText(
        text: String,
        out: MutableMap<Int, ParsedGasStation>
    ) {
        for (match in PRICES_ANCHOR_REGEX.findAll(text)) {
            val objectStart = findEnclosingObjectStart(text, match.range.first) ?: continue
            val objectEnd = findMatchingObjectEnd(text, objectStart) ?: continue
            val jsonCandidate = text.substring(objectStart, objectEnd + 1)
            val parsed = parseFullStationJson(jsonCandidate) ?: continue
            out.putIfAbsent(parsed.stationId, parsed)
        }
    }

    private fun parseFromText(
        text: String,
        idPattern: Regex,
        targetStationId: Int,
        fuelType: GasFuelType
    ): ParsedGasPrice? {
        for (match in idPattern.findAll(text)) {
            val objectStart = findEnclosingObjectStart(text, match.range.first) ?: continue
            val objectEnd = findMatchingObjectEnd(text, objectStart) ?: continue
            val jsonCandidate = text.substring(objectStart, objectEnd + 1)
            parseStationJson(jsonCandidate, targetStationId, fuelType)?.let { return it }
        }
        return null
    }

    private fun parseFullStationJson(jsonStr: String): ParsedGasStation? {
        val stationObject = try {
            JSONObject(jsonStr)
        } catch (_: JSONException) {
            return null
        }

        val idValue = stationObject.opt("id") as? Number ?: return null
        val idDouble = idValue.toDouble()
        if (!idDouble.isFinite()) return null
        val stationId = idValue.toInt()
        if (stationId.toDouble() != idDouble) return null

        if (stationObject.isNull("name")) return null
        val stationName = (stationObject.opt("name") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val brand = (stationObject.opt("brand") as? String)?.trim().orEmpty()
        val pricesObject = stationObject.optJSONObject("prices") ?: return null

        val prices = LinkedHashMap<GasFuelType, Long>()
        for (fuel in GasFuelType.entries) {
            if (pricesObject.isNull(fuel.jsonKey)) continue
            val rawPrice = pricesObject.opt(fuel.jsonKey) as? Number ?: continue
            val centavos = toCentavosOrNull(rawPrice) ?: continue
            prices[fuel] = centavos
        }
        if (prices.isEmpty()) return null

        return ParsedGasStation(
            stationId = stationId,
            brand = brand,
            stationName = stationName,
            pricesCentavos = prices
        )
    }

    private fun parseStationJson(
        jsonStr: String,
        targetStationId: Int,
        fuelType: GasFuelType
    ): ParsedGasPrice? {
        val stationObject = try {
            JSONObject(jsonStr)
        } catch (_: JSONException) {
            return null
        }

        val idValue = stationObject.opt("id") as? Number ?: return null
        val idDouble = idValue.toDouble()
        if (!idDouble.isFinite() || idDouble != targetStationId.toDouble()) {
            return null
        }

        if (stationObject.isNull("name")) return null
        val stationName = (stationObject.opt("name") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        val pricesObject = stationObject.optJSONObject("prices") ?: return null
        if (pricesObject.isNull(fuelType.jsonKey)) return null
        val rawPrice = pricesObject.opt(fuelType.jsonKey) as? Number ?: return null
        val centavos = toCentavosOrNull(rawPrice) ?: return null

        return ParsedGasPrice(
            stationId = targetStationId,
            stationName = stationName,
            premium95Centavos = centavos
        )
    }

    private fun toCentavosOrNull(rawPrice: Number): Long? {
        val decimal = when (rawPrice) {
            is BigDecimal -> rawPrice
            is Int, is Long -> BigDecimal.valueOf(rawPrice.toLong())
            else -> {
                val asDouble = rawPrice.toDouble()
                if (!asDouble.isFinite() || asDouble <= 0.0) return null
                BigDecimal.valueOf(asDouble)
            }
        }
        if (decimal <= BigDecimal.ZERO) return null
        return try {
            decimal
                .multiply(CENTAVOS_MULTIPLIER)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
                .takeIf { it > 0L }
        } catch (_: ArithmeticException) {
            null
        }
    }

    private fun findEnclosingObjectStart(text: String, anchorIndex: Int): Int? {
        var depth = 0
        var inString = false
        for (i in anchorIndex - 1 downTo 0) {
            val ch = text[i]
            if (ch == '"' && isUnescapedQuote(text, i)) {
                inString = !inString
                continue
            }
            if (inString) continue

            when (ch) {
                '}' -> depth++
                '{' -> {
                    if (depth == 0) return i
                    depth--
                }
            }
        }
        return null
    }

    private fun findMatchingObjectEnd(text: String, objectStart: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false

        for (i in objectStart until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun isUnescapedQuote(text: String, quoteIndex: Int): Boolean {
        var backslashCount = 0
        var idx = quoteIndex - 1
        while (idx >= 0 && text[idx] == '\\') {
            backslashCount++
            idx--
        }
        return backslashCount % 2 == 0
    }

    private fun unescapeJsonStringLayer(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (ch == '\\' && i + 1 < input.length) {
                when (val next = input[i + 1]) {
                    '"', '\\', '/' -> {
                        out.append(next)
                        i += 2
                        continue
                    }
                    'n' -> {
                        out.append('\n')
                        i += 2
                        continue
                    }
                    'r' -> {
                        out.append('\r')
                        i += 2
                        continue
                    }
                    't' -> {
                        out.append('\t')
                        i += 2
                        continue
                    }
                    'b' -> {
                        out.append('\b')
                        i += 2
                        continue
                    }
                    'f' -> {
                        out.append('\u000C')
                        i += 2
                        continue
                    }
                    'u' -> {
                        if (i + 5 < input.length) {
                            val code = input.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) {
                                out.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                    }
                }
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }
}
