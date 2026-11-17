package com.mcalonzo.personalfinanceos.domain.calculator

import com.mcalonzo.personalfinanceos.data.local.entity.ScanFormatProfileEntity
import com.mcalonzo.personalfinanceos.domain.model.OcrLineBox
import com.mcalonzo.personalfinanceos.domain.model.OcrParseResult
import com.mcalonzo.personalfinanceos.domain.model.ScanDocumentType
import com.mcalonzo.personalfinanceos.domain.model.ScannedExpenseDraft
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Geometry-aware parser that groups OCR line boxes into visual rows and extracts
 * normalized expense drafts for card statements and store receipts.
 */
object OcrExpenseParser {

    private val AMOUNT_REGEX = Regex(
        """([+-])?\s*(?:₱|\bPHP\b|\bP\b|\bP(?=\d))?\s*(\d{1,3}(?:,\d{3})*|\d+)\.(\d{2})(?:\s*\b(CR|DR)\b)?""",
        RegexOption.IGNORE_CASE
    )
    private val QUANTITY_EXPR_REGEX = Regex(
        """\b\d+(?:\.\d+)?\s*[xX*×]\s*(?:₱|\bPHP|\bP)?\s*\d+(?:,\d{3})*(?:\.\d{2})?\b"""
    )
    private val MONTH_DAY_REGEX = Regex(
        """\b(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC)[A-Z]*\.?\s+(\d{1,2})(?:,?\s+(\d{4}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val DAY_MONTH_REGEX = Regex(
        """\b(\d{1,2})\s+(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC)[A-Z]*(?:,?\s+(\d{4}))?\b""",
        RegexOption.IGNORE_CASE
    )
    private val NUMERIC_DATE_REGEX = Regex("""\b(\d{1,4})[/-](\d{1,2})(?:[/-](\d{2,4}))?\b""")

    private val SUMMARY_NOISE_REGEX = Regex(
        """\b(GRAND\s+TOTAL|SUB-?TOTAL|TOTAL|AMOUNT\s+DUE|BALANCE\s+DUE|PREVIOUS\s+BALANCE|NEW\s+BALANCE|MINIMUM\s+PAYMENT|VATABLE|VAT\s+EXEMPT|ZERO\s+RATED|VAT|TAX|CHANGE|CASH|TENDERED|DISCOUNT|SERVICE\s+CHARGE)\b""",
        RegexOption.IGNORE_CASE
    )

    private val DATE_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

    private data class ParsedAmount(
        val centavos: Long,
        val isCredit: Boolean,
        val range: IntRange
    )

    private data class ParsedDateMatch(
        val date: LocalDate,
        val range: IntRange
    )

    /**
     * Parses [lines] into a detected store/header name and a list of [ScannedExpenseDraft] items.
     */
    fun parse(
        lines: List<OcrLineBox>,
        documentType: ScanDocumentType,
        billingCycleDay: Int?,
        activeMonth: YearMonth,
        matchedProfile: ScanFormatProfileEntity?,
        cardDisplayName: String? = null,
        referenceYear: Int = activeMonth.year
    ): OcrParseResult {
        val rows = groupIntoRows(lines)
        val detectedStore = detectStoreCandidateFromRows(rows)
            .ifBlank { cardDisplayName.orEmpty().trim() }

        val ignoredKeywords = matchedProfile?.ignoredKeywordsPipe
            ?.split('|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val drafts = mutableListOf<ScannedExpenseDraft>()
        var pendingTextRow: String? = null
        var receiptHeaderDate: LocalDate? = null

        for (rowText in rows) {
            val amount = extractLineAmount(rowText)
            val dateMatch = extractDate(rowText, referenceYear)

            if (amount == null) {
                if (dateMatch != null && receiptHeaderDate == null) {
                    receiptHeaderDate = dateMatch.date
                }
                if (documentType == ScanDocumentType.RECEIPT && isCandidateItemTitle(rowText, referenceYear)) {
                    pendingTextRow = rowText
                }
                continue
            }

            val rawTitle = cleanTitle(rowText, amount.range, dateMatch?.range)
            val title = if (rawTitle.isBlank() && pendingTextRow != null) {
                pendingTextRow.also { pendingTextRow = null }
            } else {
                pendingTextRow = null
                rawTitle
            }

            if (title.isBlank()) continue

            val effectiveDate = dateMatch?.date
                ?: if (documentType == ScanDocumentType.RECEIPT) receiptHeaderDate else null
            val targetYearMonth = CardBillingCycleCalculator.resolveTargetMonth(
                transactionDate = effectiveDate,
                billingCycleDay = billingCycleDay,
                fallbackMonth = activeMonth
            )
            val isCredit = documentType == ScanDocumentType.CARD && amount.isCredit
            val isSummaryNoise = SUMMARY_NOISE_REGEX.containsMatchIn(title)
            val isIgnored = ignoredKeywords.any { kw -> title.contains(kw, ignoreCase = true) }
            val isSelected = !isCredit && !isSummaryNoise && !isIgnored

            drafts += ScannedExpenseDraft(
                localId = "draft-${drafts.size}",
                title = title,
                amountCentavos = amount.centavos,
                parsedDate = effectiveDate,
                dateLabel = effectiveDate?.let { DATE_LABEL_FORMATTER.format(it) }.orEmpty(),
                targetYearMonth = targetYearMonth,
                isCredit = isCredit,
                isSummaryNoise = isSummaryNoise,
                isDuplicate = false,
                isSelected = isSelected,
                envelopeName = matchedProfile?.defaultEnvelopeName
            )
        }

        return OcrParseResult(
            detectedStoreName = detectedStore,
            drafts = drafts
        )
    }

    /**
     * Detects the most likely merchant or statement header name from the top OCR lines.
     */
    fun detectStoreCandidate(lines: List<OcrLineBox>): String =
        detectStoreCandidateFromRows(groupIntoRows(lines))

    /**
     * Marks any draft in [incoming] whose normalized title and centavo amount match an item
     * in [existing] with `isDuplicate = true` and `isSelected = false`.
     */
    fun flagDuplicates(
        existing: List<ScannedExpenseDraft>,
        incoming: List<ScannedExpenseDraft>
    ): List<ScannedExpenseDraft> {
        val existingKeys = existing.mapTo(HashSet(existing.size)) { draft ->
            normalizeText(draft.title) to draft.amountCentavos
        }
        return incoming.map { draft ->
            val key = normalizeText(draft.title) to draft.amountCentavos
            if (key in existingKeys) {
                draft.copy(isDuplicate = true, isSelected = false)
            } else {
                draft
            }
        }
    }

    /**
     * Normalizes a store or merchant name into a canonical lookup key.
     */
    fun normalizeStoreKey(raw: String): String = normalizeText(raw)

    private fun groupIntoRows(lines: List<OcrLineBox>): List<String> {
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return emptyList()

        val sortedHeights = nonBlank.map { (it.bottom - it.top).coerceAtLeast(1) }.sorted()
        val medianHeight = sortedHeights[sortedHeights.size / 2]
        val yThreshold = max(12.0, medianHeight * 0.55)

        val sortedBoxes = nonBlank.sortedWith(
            compareBy<OcrLineBox> { (it.top + it.bottom) / 2.0 }.thenBy { it.left }
        )

        val rowBuckets = mutableListOf<MutableList<OcrLineBox>>()
        var anchorY = 0.0

        for (box in sortedBoxes) {
            val yCenter = (box.top + box.bottom) / 2.0
            if (rowBuckets.isEmpty() || abs(yCenter - anchorY) > yThreshold) {
                rowBuckets += mutableListOf(box)
                anchorY = yCenter
            } else {
                rowBuckets.last() += box
            }
        }

        return rowBuckets.map { bucket ->
            bucket.sortedBy { it.left }
                .joinToString(" ") { it.text.trim() }
                .replace(Regex("""\s+"""), " ")
                .trim()
        }
    }

    private fun detectStoreCandidateFromRows(rows: List<String>): String =
        rows.firstOrNull { row ->
            extractLineAmount(row) == null &&
                !SUMMARY_NOISE_REGEX.containsMatchIn(row) &&
                row.any { it.isLetter() }
        }.orEmpty()

    private fun isCandidateItemTitle(rowText: String, referenceYear: Int): Boolean {
        if (!rowText.any { it.isLetter() }) return false
        if (SUMMARY_NOISE_REGEX.containsMatchIn(rowText)) return false
        val dateMatch = extractDate(rowText, referenceYear)
        if (dateMatch != null && rowText.removeRange(dateMatch.range).isBlank()) return false
        return true
    }

    private fun extractLineAmount(rowText: String): ParsedAmount? {
        val match = AMOUNT_REGEX.findAll(rowText).lastOrNull() ?: return null
        val sign = match.groupValues[1]
        val wholePart = match.groupValues[2].replace(",", "").toLongOrNull() ?: return null
        val centPart = match.groupValues[3].toLongOrNull() ?: return null
        val suffix = match.groupValues[4].uppercase(Locale.ENGLISH)
        val centavos = wholePart * 100L + centPart
        val isCredit = sign == "+" || suffix == "CR"
        return ParsedAmount(
            centavos = centavos,
            isCredit = isCredit,
            range = match.range
        )
    }

    private fun extractDate(rowText: String, referenceYear: Int): ParsedDateMatch? {
        MONTH_DAY_REGEX.find(rowText)?.let { match ->
            val month = parseMonthAbbrev(match.groupValues[1]) ?: return@let
            val day = match.groupValues[2].toIntOrNull() ?: return@let
            val year = match.groupValues[3].toIntOrNull() ?: referenceYear
            safeLocalDate(year, month, day)?.let { return ParsedDateMatch(it, match.range) }
        }

        DAY_MONTH_REGEX.find(rowText)?.let { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@let
            val month = parseMonthAbbrev(match.groupValues[2]) ?: return@let
            val year = match.groupValues[3].toIntOrNull() ?: referenceYear
            safeLocalDate(year, month, day)?.let { return ParsedDateMatch(it, match.range) }
        }

        NUMERIC_DATE_REGEX.find(rowText)?.let { match ->
            val first = match.groupValues[1].toIntOrNull() ?: return@let
            val second = match.groupValues[2].toIntOrNull() ?: return@let
            val thirdRaw = match.groupValues[3]
            val date = if (match.groupValues[1].length == 4 && thirdRaw.isNotEmpty()) {
                val day = thirdRaw.toIntOrNull() ?: return@let
                safeLocalDate(first, second, day)
            } else {
                val year = when {
                    thirdRaw.isEmpty() -> referenceYear
                    thirdRaw.length == 2 -> 2000 + (thirdRaw.toIntOrNull() ?: return@let)
                    else -> thirdRaw.toIntOrNull() ?: return@let
                }
                safeLocalDate(year, first, second)
            }
            date?.let { return ParsedDateMatch(it, match.range) }
        }

        return null
    }

    private fun cleanTitle(
        rowText: String,
        amountRange: IntRange,
        dateRange: IntRange?
    ): String {
        val rangesToRemove = listOfNotNull(amountRange, dateRange)
            .sortedByDescending { it.first }
        var cleaned = rowText
        for (range in rangesToRemove) {
            if (range.first >= 0 && range.last < cleaned.length) {
                cleaned = cleaned.removeRange(range)
            }
        }
        return cleaned
            .replace(QUANTITY_EXPR_REGEX, "")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', '|', '*')
    }

    private fun parseMonthAbbrev(abbrev: String): Int? = when (abbrev.uppercase(Locale.ENGLISH).take(3)) {
        "JAN" -> Month.JANUARY.value
        "FEB" -> Month.FEBRUARY.value
        "MAR" -> Month.MARCH.value
        "APR" -> Month.APRIL.value
        "MAY" -> Month.MAY.value
        "JUN" -> Month.JUNE.value
        "JUL" -> Month.JULY.value
        "AUG" -> Month.AUGUST.value
        "SEP" -> Month.SEPTEMBER.value
        "OCT" -> Month.OCTOBER.value
        "NOV" -> Month.NOVEMBER.value
        "DEC" -> Month.DECEMBER.value
        else -> null
    }

    private fun safeLocalDate(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: DateTimeException) {
        null
    }

    private fun normalizeText(raw: String): String =
        raw.lowercase(Locale.ENGLISH)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
