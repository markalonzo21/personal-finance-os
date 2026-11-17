package com.mcalonzo.personalfinanceos.domain.calculator

import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.entity.ScanFormatProfileEntity
import com.mcalonzo.personalfinanceos.domain.model.OcrLineBox
import com.mcalonzo.personalfinanceos.domain.model.ScanDocumentType
import com.mcalonzo.personalfinanceos.domain.model.ScannedExpenseDraft
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

class OcrExpenseParserAndBillingCycleTest {

    @Test
    fun `resolveTargetMonth rolls over after billingCycleDay and falls back on null inputs`() {
        val fallback = YearMonth.of(2026, 11)

        assertThat(
            CardBillingCycleCalculator.resolveTargetMonth(
                transactionDate = LocalDate.of(2026, 11, 16),
                billingCycleDay = 15,
                fallbackMonth = fallback
            )
        ).isEqualTo(YearMonth.of(2026, 12))

        assertThat(
            CardBillingCycleCalculator.resolveTargetMonth(
                transactionDate = LocalDate.of(2026, 11, 15),
                billingCycleDay = 15,
                fallbackMonth = fallback
            )
        ).isEqualTo(YearMonth.of(2026, 11))

        assertThat(
            CardBillingCycleCalculator.resolveTargetMonth(
                transactionDate = null,
                billingCycleDay = 15,
                fallbackMonth = fallback
            )
        ).isEqualTo(fallback)

        assertThat(
            CardBillingCycleCalculator.resolveTargetMonth(
                transactionDate = LocalDate.of(2026, 11, 20),
                billingCycleDay = null,
                fallbackMonth = fallback
            )
        ).isEqualTo(fallback)
    }

    @Test
    fun `card mode parses signed debit and credit amounts with date and billing cycle`() {
        val lines = listOf(
            OcrLineBox("BDO CREDIT CARD", left = 40, top = 20, right = 420, bottom = 50),
            OcrLineBox("NOV 16", left = 40, top = 100, right = 120, bottom = 124),
            OcrLineBox("MERCURY DRUG", left = 140, top = 100, right = 340, bottom = 124),
            OcrLineBox("-250.00", left = 460, top = 102, right = 580, bottom = 126),
            OcrLineBox("NOV 14", left = 40, top = 160, right = 120, bottom = 184),
            OcrLineBox("PAYMENT THANK YOU", left = 140, top = 160, right = 380, bottom = 184),
            OcrLineBox("+1,500.00", left = 440, top = 160, right = 580, bottom = 184)
        )

        val result = OcrExpenseParser.parse(
            lines = lines,
            documentType = ScanDocumentType.CARD,
            billingCycleDay = 15,
            activeMonth = YearMonth.of(2026, 11),
            matchedProfile = null,
            cardDisplayName = "BDO Gold",
            referenceYear = 2026
        )

        assertThat(result.drafts).hasSize(2)

        val debit = result.drafts[0]
        assertThat(debit.title).contains("MERCURY DRUG")
        assertThat(debit.amountCentavos).isEqualTo(25_000L)
        assertThat(debit.isSelected).isTrue()
        assertThat(debit.isCredit).isFalse()
        assertThat(debit.parsedDate).isEqualTo(LocalDate.of(2026, 11, 16))
        assertThat(debit.targetYearMonth).isEqualTo(YearMonth.of(2026, 12))

        val credit = result.drafts[1]
        assertThat(credit.title).contains("PAYMENT THANK YOU")
        assertThat(credit.amountCentavos).isEqualTo(150_000L)
        assertThat(credit.isSelected).isFalse()
        assertThat(credit.isCredit).isTrue()
        assertThat(credit.parsedDate).isEqualTo(LocalDate.of(2026, 11, 14))
        assertThat(credit.targetYearMonth).isEqualTo(YearMonth.of(2026, 11))
    }

    @Test
    fun `receipt mode pairs left items and two-line quantity rows and flags summary noise`() {
        val lines = listOf(
            OcrLineBox("SM SUPERMARKET", left = 80, top = 20, right = 420, bottom = 52),
            OcrLineBox("Fresh Milk 1L", left = 40, top = 100, right = 240, bottom = 124),
            OcrLineBox("₱120.50", left = 460, top = 102, right = 580, bottom = 126),
            OcrLineBox("Eggs", left = 40, top = 150, right = 120, bottom = 174),
            OcrLineBox("2 x 45.00  90.00", left = 60, top = 182, right = 580, bottom = 206),
            OcrLineBox("TOTAL", left = 40, top = 240, right = 140, bottom = 264),
            OcrLineBox("210.50", left = 460, top = 240, right = 580, bottom = 264),
            OcrLineBox("VAT", left = 40, top = 280, right = 110, bottom = 304),
            OcrLineBox("22.55", left = 460, top = 280, right = 580, bottom = 304),
            OcrLineBox("CHANGE", left = 40, top = 320, right = 150, bottom = 344),
            OcrLineBox("89.50", left = 460, top = 320, right = 580, bottom = 344)
        )

        val result = OcrExpenseParser.parse(
            lines = lines,
            documentType = ScanDocumentType.RECEIPT,
            billingCycleDay = null,
            activeMonth = YearMonth.of(2026, 11),
            matchedProfile = null
        )

        assertThat(result.detectedStoreName).isEqualTo("SM SUPERMARKET")
        assertThat(result.drafts).hasSize(5)

        val milk = result.drafts[0]
        assertThat(milk.title).isEqualTo("Fresh Milk 1L")
        assertThat(milk.amountCentavos).isEqualTo(12_050L)
        assertThat(milk.isSelected).isTrue()
        assertThat(milk.isSummaryNoise).isFalse()

        val eggs = result.drafts[1]
        assertThat(eggs.title).isEqualTo("Eggs")
        assertThat(eggs.amountCentavos).isEqualTo(9_000L)
        assertThat(eggs.isSelected).isTrue()
        assertThat(eggs.isSummaryNoise).isFalse()

        val summaryRows = result.drafts.drop(2)
        assertThat(summaryRows).hasSize(3)
        summaryRows.forEach { row ->
            assertThat(row.isSelected).isFalse()
            assertThat(row.isSummaryNoise).isTrue()
        }
    }

    @Test
    fun `matched profile applies default envelope and auto-unchecks ignored keywords`() {
        val profile = ScanFormatProfileEntity(
            storeKey = "puregold",
            displayName = "Puregold",
            defaultEnvelopeName = "Groceries",
            ignoredKeywordsPipe = "POINTS EARNED|SAVINGS"
        )
        val lines = listOf(
            OcrLineBox("PUREGOLD", left = 80, top = 20, right = 300, bottom = 50),
            OcrLineBox("Rice 5kg", left = 40, top = 100, right = 200, bottom = 124),
            OcrLineBox("275.00", left = 460, top = 100, right = 580, bottom = 124),
            OcrLineBox("Points Earned", left = 40, top = 150, right = 220, bottom = 174),
            OcrLineBox("15.00", left = 460, top = 150, right = 580, bottom = 174)
        )

        val result = OcrExpenseParser.parse(
            lines = lines,
            documentType = ScanDocumentType.RECEIPT,
            billingCycleDay = null,
            activeMonth = YearMonth.of(2026, 11),
            matchedProfile = profile
        )

        assertThat(result.drafts).hasSize(2)

        val rice = result.drafts[0]
        assertThat(rice.isSelected).isTrue()
        assertThat(rice.envelopeName).isEqualTo("Groceries")

        val points = result.drafts[1]
        assertThat(points.title).isEqualTo("Points Earned")
        assertThat(points.isSelected).isFalse()
        assertThat(points.envelopeName).isEqualTo("Groceries")
    }

    @Test
    fun `flagDuplicates marks incoming rows matching normalized title and amount`() {
        val month = YearMonth.of(2026, 11)
        val existing = listOf(
            ScannedExpenseDraft(
                localId = "1",
                title = "Jollibee   BGC!",
                amountCentavos = 18_500L,
                parsedDate = LocalDate.of(2026, 11, 10),
                dateLabel = "Nov 10",
                targetYearMonth = month,
                isCredit = false,
                isSummaryNoise = false,
                isDuplicate = false,
                isSelected = true,
                envelopeName = "Dining"
            )
        )
        val incoming = listOf(
            ScannedExpenseDraft(
                localId = "2",
                title = "jollibee bgc",
                amountCentavos = 18_500L,
                parsedDate = LocalDate.of(2026, 11, 10),
                dateLabel = "Nov 10",
                targetYearMonth = month,
                isCredit = false,
                isSummaryNoise = false,
                isDuplicate = false,
                isSelected = true,
                envelopeName = null
            ),
            ScannedExpenseDraft(
                localId = "3",
                title = "Grab Ride",
                amountCentavos = 25_000L,
                parsedDate = LocalDate.of(2026, 11, 10),
                dateLabel = "Nov 10",
                targetYearMonth = month,
                isCredit = false,
                isSummaryNoise = false,
                isDuplicate = false,
                isSelected = true,
                envelopeName = null
            )
        )

        val flagged = OcrExpenseParser.flagDuplicates(existing, incoming)

        assertThat(flagged).hasSize(2)
        assertThat(flagged[0].isDuplicate).isTrue()
        assertThat(flagged[0].isSelected).isFalse()
        assertThat(flagged[1].isDuplicate).isFalse()
        assertThat(flagged[1].isSelected).isTrue()
    }
}
