package com.mcalonzo.personalfinanceos.domain.model

import java.time.LocalDate
import java.time.YearMonth

/**
 * Document kind scanned by the OCR pipeline.
 */
enum class ScanDocumentType {
    CARD,
    RECEIPT
}

/**
 * Pure domain representation of a recognized OCR text line with bounding box coordinates.
 */
data class OcrLineBox(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Editable expense draft extracted from a scanned statement or receipt.
 */
data class ScannedExpenseDraft(
    val localId: String,
    val title: String,
    val amountCentavos: Long,
    val parsedDate: LocalDate?,
    val dateLabel: String,
    val targetYearMonth: YearMonth,
    val isCredit: Boolean,
    val isSummaryNoise: Boolean,
    val isDuplicate: Boolean,
    val isSelected: Boolean,
    val envelopeName: String?
)

/**
 * Output of parsing OCR line boxes into a detected merchant/header and expense drafts.
 */
data class OcrParseResult(
    val detectedStoreName: String,
    val drafts: List<ScannedExpenseDraft>
)
