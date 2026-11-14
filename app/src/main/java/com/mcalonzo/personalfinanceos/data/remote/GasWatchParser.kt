package com.mcalonzo.personalfinanceos.data.remote

import java.math.BigDecimal
import java.math.RoundingMode
import org.json.JSONException
import org.json.JSONObject

data class ParsedGasPrice(
    val stationId: Int,
    val stationName: String,
    val premium95Centavos: Long
)

/**
 * Extracts Petron Commonwealth Ave Fairview (`id = 621`) Premium 95 prices from
 * standard inline `<script>` JSON or Next.js escaped script payloads on GasWatchPH.
 */
object GasWatchParser {

    private const val DEFAULT_STATION_ID = 621
    private val CENTAVOS_MULTIPLIER = BigDecimal(100)
    private val SCRIPT_BLOCK_REGEX = Regex("<script\\b[^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
    private val SCRIPT_TAG_PRESENT_REGEX = Regex("<script\\b", RegexOption.IGNORE_CASE)

    fun parseStationPremium95(
        html: String,
        targetStationId: Int = DEFAULT_STATION_ID
    ): ParsedGasPrice? {
        if (html.isBlank()) return null

        return try {
            val scriptBlocks = SCRIPT_BLOCK_REGEX.findAll(html)
                .map { it.groupValues[1] }
                .filter { it.isNotBlank() }
                .toList()

            val candidateBlocks = when {
                scriptBlocks.isNotEmpty() -> scriptBlocks
                !SCRIPT_TAG_PRESENT_REGEX.containsMatchIn(html) -> listOf(html)
                else -> return null
            }

            val idPattern = Regex(""""id"\s*:\s*${Regex.escape(targetStationId.toString())}(?![0-9])""")
            val idToken = targetStationId.toString()

            for (block in candidateBlocks) {
                if (!block.contains(idToken)) continue

                parseFromText(block, idPattern, targetStationId)?.let { return it }

                if (block.contains("\\\"")) {
                    val unescapedLayer1 = unescapeJsonStringLayer(block)
                    parseFromText(unescapedLayer1, idPattern, targetStationId)?.let { return it }

                    if (unescapedLayer1.contains("\\\"")) {
                        val unescapedLayer2 = unescapeJsonStringLayer(unescapedLayer1)
                        parseFromText(unescapedLayer2, idPattern, targetStationId)?.let { return it }
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseFromText(
        text: String,
        idPattern: Regex,
        targetStationId: Int
    ): ParsedGasPrice? {
        for (match in idPattern.findAll(text)) {
            val objectStart = findEnclosingObjectStart(text, match.range.first) ?: continue
            val objectEnd = findMatchingObjectEnd(text, objectStart) ?: continue
            val jsonCandidate = text.substring(objectStart, objectEnd + 1)
            parseStationJson(jsonCandidate, targetStationId)?.let { return it }
        }
        return null
    }

    private fun parseStationJson(jsonStr: String, targetStationId: Int): ParsedGasPrice? {
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
        if (pricesObject.isNull("premium95")) return null
        val rawPrice = pricesObject.opt("premium95") as? Number ?: return null
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
