package com.mcalonzo.personalfinanceos.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.entity.CardEntity
import com.mcalonzo.personalfinanceos.domain.model.ScanDocumentType
import com.mcalonzo.personalfinanceos.domain.model.ScannedExpenseDraft
import com.mcalonzo.personalfinanceos.ui.components.AddEditCardSheet
import com.mcalonzo.personalfinanceos.ui.components.ScanReviewSheet
import com.mcalonzo.personalfinanceos.ui.components.ScanSourceSheet
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.ScanBatchUiState
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScanSheetsTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `scan source sheet hides card chips in Receipt mode, shows saved cards and add button in Card mode, and triggers source callbacks`() {
        val cards = listOf(
            CardEntity(id = 1L, name = "BPI Amore", cardType = "CREDIT", billingCycleDay = 15),
            CardEntity(id = 2L, name = "GCash", cardType = "EWALLET", billingCycleDay = null)
        )
        var uploadImageCall: Pair<ScanDocumentType, CardEntity?>? = null
        var uploadPdfCall: Pair<ScanDocumentType, CardEntity?>? = null
        var takePhotoCall: Pair<ScanDocumentType, CardEntity?>? = null
        var addCardClicked = false

        rule.setContent {
            PersonalFinanceTheme {
                ScanSourceSheet(
                    cards = cards,
                    onUploadImage = { type, card -> uploadImageCall = type to card },
                    onUploadPdf = { type, card -> uploadPdfCall = type to card },
                    onTakePhoto = { type, card -> takePhotoCall = type to card },
                    onAddCardClick = { addCardClicked = true },
                    onDismiss = {}
                )
            }
        }

        // Receipt mode is default: card chips and + Add Card are hidden
        rule.onNodeWithText("BPI Amore").assertDoesNotExist()
        rule.onNodeWithText("+ Add Card").assertDoesNotExist()

        // Trigger source actions in Receipt mode
        rule.onNodeWithText("Upload Image / Screenshot")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(uploadImageCall).isEqualTo(ScanDocumentType.RECEIPT to null)

        // Switch to Card mode
        rule.onNodeWithText("Card / Statement")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("BPI Amore").assertExists()
        rule.onNodeWithText("GCash").assertExists()
        rule.onNodeWithText("+ Add Card").assertExists()

        // Select second card and trigger PDF and Camera
        rule.onNodeWithText("GCash")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Upload PDF")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(uploadPdfCall).isEqualTo(ScanDocumentType.CARD to cards[1])

        rule.onNodeWithText("Take Photo")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(takePhotoCall).isEqualTo(ScanDocumentType.CARD to cards[1])

        rule.onNodeWithText("+ Add Card")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(addCardClicked).isTrue()
    }

    @Test
    fun `add edit card sheet validates card name and billing cycle day and submits values`() {
        var saved: Triple<String, String, Int?>? = null

        rule.setContent {
            PersonalFinanceTheme {
                AddEditCardSheet(
                    onSave = { _, name, cardType, billingCycleDay ->
                        saved = Triple(name, cardType, billingCycleDay)
                    },
                    onDismiss = {}
                )
            }
        }

        // Blank name shows validation error
        rule.onNodeWithText("Save Card")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Enter a card name.").assertExists()
        assertThat(saved).isNull()

        // Invalid billing cycle day shows validation error
        rule.onNodeWithText("Card Name").performTextInput("UnionBank Rewards")
        rule.onNodeWithText("Billing Cycle Day (1-31, optional)").performTextInput("35")
        rule.onNodeWithText("Save Card")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Billing cycle day must be between 1 and 31.").assertExists()
        assertThat(saved).isNull()

        // Valid input submits
        rule.onNodeWithText("35").performTextReplacement("20")
        rule.onNodeWithText("Debit")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Save Card")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(saved).isEqualTo(Triple("UnionBank Rewards", "DEBIT", 20))
    }

    @Test
    fun `scan review sheet renders drafts with duplicate and credit states, allows editing, toggling, appending, and confirming`() {
        val envelopes = listOf(
            EnvelopeItemUi(1L, "Groceries", "🛒", "#38BDF8", 1_500_000L, 1_500_000L),
            EnvelopeItemUi(2L, "Dining", "🍽️", "#10B981", 800_000L, 800_000L)
        )
        var batchState by mutableStateOf(
            ScanBatchUiState(
                isSheetVisible = true,
                documentType = ScanDocumentType.CARD,
                storeName = "BPI Amore",
                drafts = listOf(
                    ScannedExpenseDraft(
                        localId = "draft-0",
                        title = "SM SUPERMARKET",
                        amountCentavos = 125_000L,
                        parsedDate = LocalDate.of(2026, 11, 10),
                        dateLabel = "Nov 10",
                        targetYearMonth = YearMonth.of(2026, 11),
                        isCredit = false,
                        isSummaryNoise = false,
                        isDuplicate = true,
                        isSelected = true,
                        envelopeName = "Groceries"
                    ),
                    ScannedExpenseDraft(
                        localId = "draft-1",
                        title = "PAYMENT THANK YOU",
                        amountCentavos = 500_000L,
                        parsedDate = LocalDate.of(2026, 11, 11),
                        dateLabel = "Nov 11",
                        targetYearMonth = YearMonth.of(2026, 11),
                        isCredit = true,
                        isSummaryNoise = false,
                        isDuplicate = false,
                        isSelected = false,
                        envelopeName = null
                    )
                )
            )
        )
        var scanAnotherClicked = false
        var confirmClicked = false

        rule.setContent {
            PersonalFinanceTheme {
                ScanReviewSheet(
                    scanBatchState = batchState,
                    envelopes = envelopes,
                    onUpdateStoreName = { name -> batchState = batchState.copy(storeName = name) },
                    onApplyDefaultEnvelope = {},
                    onUpdateDraft = { localId, title, amount, env ->
                        batchState = batchState.copy(
                            drafts = batchState.drafts.map {
                                if (it.localId == localId) {
                                    it.copy(title = title, amountCentavos = amount, envelopeName = env)
                                } else {
                                    it
                                }
                            }
                        )
                    },
                    onToggleDraft = { localId ->
                        batchState = batchState.copy(
                            drafts = batchState.drafts.map {
                                if (it.localId == localId) it.copy(isSelected = !it.isSelected) else it
                            }
                        )
                    },
                    onScanAnotherPart = { scanAnotherClicked = true },
                    onConfirm = { confirmClicked = true },
                    onDismiss = {}
                )
            }
        }

        // Duplicate & Credit badges visible
        rule.onNodeWithText("Duplicate").assertExists()
        rule.onNodeWithText("Credit").assertExists()

        // Confirm button shows count = 1
        rule.onNodeWithText("Log 1 Expense").assertExists()

        // Edit row title
        rule.onNodeWithText("SM SUPERMARKET").performTextReplacement("SM Hypermarket")
        assertThat(batchState.drafts[0].title).isEqualTo("SM Hypermarket")

        // Edit row amount
        rule.onNodeWithText("1250").performTextReplacement("1350.50")
        assertThat(batchState.drafts[0].amountCentavos).isEqualTo(135_050L)

        // Toggle second draft (credit) on -> count becomes 2
        val checkboxes = rule.onAllNodes(isToggleable())
        checkboxes[0].assertIsOn()
        checkboxes[1].assertIsOff()
        checkboxes[1].performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        rule.onNodeWithText("Log 2 Expenses").assertExists()

        // Tap + Scan Another Part
        rule.onNodeWithText("+ Scan Another Part")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(scanAnotherClicked).isTrue()

        // Confirm
        rule.onNodeWithText("Log 2 Expenses")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
        assertThat(confirmClicked).isTrue()
    }
}
