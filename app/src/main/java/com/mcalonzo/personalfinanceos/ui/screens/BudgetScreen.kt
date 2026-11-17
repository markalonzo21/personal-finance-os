package com.mcalonzo.personalfinanceos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.domain.calculator.BudgetCalculator
import com.mcalonzo.personalfinanceos.ui.components.CurrencyFormatters
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors
import com.mcalonzo.personalfinanceos.ui.viewmodel.BillItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.BudgetSubTab
import com.mcalonzo.personalfinanceos.ui.viewmodel.BudgetUiState
import com.mcalonzo.personalfinanceos.ui.viewmodel.EnvelopeItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.LoanItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.TransactionItemUi

/**
 * Stateless Budget OS screen rendering Month Picker + SOA top bar, Safe-to-Spend Hero card,
 * 4-column equal-width sub-tabs, and Envelopes/Bills/Loans/Activity lists.
 */
@Composable
fun BudgetScreen(
    state: BudgetUiState,
    modifier: Modifier = Modifier,
    onSelectSubTab: (BudgetSubTab) -> Unit = {},
    onOpenMonthPicker: () -> Unit = {},
    onOpenSoaViewer: () -> Unit = {},
    onTapEnvelopeIcon: (EnvelopeItemUi) -> Unit = {},
    onQuickLogEnvelope: (EnvelopeItemUi) -> Unit = {},
    onAddEnvelopeClick: () -> Unit = {},
    onEditEnvelopeClick: (EnvelopeItemUi) -> Unit = {},
    onAddBillClick: () -> Unit = {},
    onPayBillClick: (BillItemUi) -> Unit = {},
    onEditBillClick: (BillItemUi) -> Unit = {},
    onAddLoanClick: () -> Unit = {},
    onSettleLoanClick: (LoanItemUi) -> Unit = {},
    onAdvanceLoanClick: (LoanItemUi) -> Unit = {},
    onEditLoanClick: (LoanItemUi) -> Unit = {},
    onToggleAmountsVisibility: () -> Unit = {},
    onEditTransactionClick: (TransactionItemUi) -> Unit = {},
    onDeleteTransactionClick: (TransactionItemUi) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OLEDColors.bgBase)
    ) {
        BudgetTopBar(
            yearMonthLabel = state.yearMonthLabel.ifBlank { "Budget Cycle" },
            hasSoa = !state.soaPdfLocalUri.isNullOrBlank(),
            onOpenMonthPicker = onOpenMonthPicker,
            onOpenSoaViewer = onOpenSoaViewer
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "hero_card") {
                SafeToSpendHeroCard(
                    state = state,
                    onToggleAmountsVisibility = onToggleAmountsVisibility
                )
            }

            item(key = "sub_tabs") {
                BudgetSubTabBar(
                    state = state,
                    onSelectSubTab = onSelectSubTab
                )
            }

            when (state.selectedSubTab) {
                BudgetSubTab.ENVELOPES -> {
                    item(key = "envelopes_header") {
                        SectionHeaderRow(
                            title = "ENVELOPES",
                            actionLabel = "+ Add Category",
                            onActionClick = onAddEnvelopeClick
                        )
                    }
                    if (state.envelopes.isEmpty()) {
                        item(key = "envelopes_empty") {
                            EmptyStateCard(
                                title = "No budget envelopes yet",
                                subtitle = "Create a category envelope to track monthly spending."
                            )
                        }
                    } else {
                        items(state.envelopes, key = { "env_${it.id}" }) { envelope ->
                            val envelopeItems = remember(state.transactions, envelope.id) {
                                state.transactions.filter { it.envelopeId == envelope.id }
                            }
                            EnvelopeCard(
                                envelope = envelope,
                                items = envelopeItems,
                                isAmountsHidden = state.isAmountsHidden,
                                onTapIcon = { onTapEnvelopeIcon(envelope) },
                                onQuickLog = { onQuickLogEnvelope(envelope) },
                                onEdit = { onEditEnvelopeClick(envelope) },
                                onEditItem = onEditTransactionClick,
                                onDeleteItem = onDeleteTransactionClick
                            )
                        }
                    }
                }

                BudgetSubTab.BILLS -> {
                    item(key = "bills_header") {
                        SectionHeaderRow(
                            title = "RECURRING BILLS",
                            actionLabel = "+ Add Bill",
                            onActionClick = onAddBillClick
                        )
                    }
                    if (state.bills.isEmpty()) {
                        item(key = "bills_empty") {
                            EmptyStateCard(
                                title = "No bills scheduled",
                                subtitle = "Add monthly utilities or subscriptions to track due dates."
                            )
                        }
                    } else {
                        items(state.bills, key = { "bill_${it.id}" }) { bill ->
                            BillCard(
                                bill = bill,
                                cycleLabel = state.yearMonthLabel,
                                isAmountsHidden = state.isAmountsHidden,
                                onPayNow = { onPayBillClick(bill) },
                                onEdit = { onEditBillClick(bill) }
                            )
                        }
                    }
                }

                BudgetSubTab.LOANS -> {
                    item(key = "loans_header") {
                        SectionHeaderRow(
                            title = "MONTHLY DEBT & OBLIGATIONS",
                            actionLabel = "+ Add Loan",
                            onActionClick = onAddLoanClick
                        )
                    }
                    item(key = "loans_summary") {
                        LoansSummaryBanner(
                            committedLoansFormatted = maskAmount(
                                state.committedLoansFormatted,
                                state.isAmountsHidden
                            ),
                            activeLoansCount = state.activeLoansCount
                        )
                    }
                    if (state.loans.isEmpty()) {
                        item(key = "loans_empty") {
                            EmptyStateCard(
                                title = "No installment loans",
                                subtitle = "Track vehicle or bank amortizations without double-deducting Safe-to-Spend."
                            )
                        }
                    } else {
                        items(state.loans, key = { "loan_${it.id}" }) { loan ->
                            LoanCard(
                                loan = loan,
                                isAmountsHidden = state.isAmountsHidden,
                                onMarkPaid = { onSettleLoanClick(loan) },
                                onPayAdvance = { onAdvanceLoanClick(loan) },
                                onEdit = { onEditLoanClick(loan) }
                            )
                        }
                    }
                }

                BudgetSubTab.ACTIVITY -> {
                    item(key = "activity_header") {
                        SectionHeaderRow(
                            title = "ACTIVITY",
                            actionLabel = "${state.transactions.size} logged",
                            onActionClick = null
                        )
                    }
                    if (state.transactions.isEmpty()) {
                        item(key = "activity_empty") {
                            EmptyStateCard(
                                title = "No transactions yet",
                                subtitle = "Tap + to log an expense, or settle a bill or loan."
                            )
                        }
                    } else {
                        items(state.transactions, key = { "tx_${it.id}" }) { tx ->
                            TransactionRowCard(
                                transaction = tx,
                                isAmountsHidden = state.isAmountsHidden,
                                onEdit = if (tx.envelopeId != null) {
                                    { onEditTransactionClick(tx) }
                                } else {
                                    null
                                },
                                onDelete = { onDeleteTransactionClick(tx) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetTopBar(
    yearMonthLabel: String,
    hasSoa: Boolean,
    onOpenMonthPicker: () -> Unit,
    onOpenSoaViewer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onOpenMonthPicker,
            color = OLEDColors.bgCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, OLEDColors.borderSubtle),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = yearMonthLabel,
                    style = FinanceTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OLEDColors.textPrimary
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Select month cycle",
                    tint = OLEDColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Surface(
            onClick = onOpenSoaViewer,
            color = if (hasSoa) OLEDColors.accentSky.copy(alpha = 0.14f) else OLEDColors.bgCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (hasSoa) OLEDColors.accentSky.copy(alpha = 0.45f) else OLEDColors.borderSubtle
            ),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PictureAsPdf,
                    contentDescription = null,
                    tint = if (hasSoa) OLEDColors.accentSky else OLEDColors.textPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "SOA",
                    style = FinanceTypography.labelLarge,
                    color = if (hasSoa) OLEDColors.accentSky else OLEDColors.textPrimary
                )
            }
        }
    }
}

private fun maskAmount(
    formatted: String,
    isHidden: Boolean,
    mask: String = "₱••••"
): String = if (isHidden) mask else formatted

@Composable
private fun SafeToSpendHeroCard(
    state: BudgetUiState,
    onToggleAmountsVisibility: () -> Unit
) {
    val heroBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF152238),
            Color(0xFF0A111E)
        )
    )
    val paceColor = if (state.isSafeToSpendNegative) {
        OLEDColors.accentRose
    } else {
        OLEDColors.accentEmerald
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(heroBrush)
            .clip(RoundedCornerShape(22.dp))
    ) {
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, OLEDColors.accentSky.copy(alpha = 0.24f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SAFE-TO-SPEND BALANCE",
                        style = FinanceTypography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = OLEDColors.accentSky,
                        maxLines = 1
                    )

                    Surface(
                        color = paceColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, paceColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = maskAmount(state.dailyPaceFormatted, state.isAmountsHidden, "₱•••• / day"),
                            style = NumericTextStyles.captionAmount.copy(fontWeight = FontWeight.Bold),
                            color = paceColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = maskAmount(state.safeToSpendFormatted, state.isAmountsHidden, "₱••••••"),
                            style = NumericTextStyles.heroBalance.copy(fontSize = 26.sp, lineHeight = 30.sp),
                            color = if (state.isSafeToSpendNegative) OLEDColors.accentRose else OLEDColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(
                            onClick = onToggleAmountsVisibility,
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isAmountsHidden) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (state.isAmountsHidden) {
                                    "Show amounts"
                                } else {
                                    "Hide amounts"
                                },
                                tint = OLEDColors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    val daysStatus = when {
                        state.isPastCycle -> "Past cycle"
                        state.isSafeToSpendNegative -> "${state.remainingDaysInMonth}d left • Over Budget"
                        else -> "${state.remainingDaysInMonth}d left • On Track"
                    }
                    Text(
                        text = daysStatus,
                        style = FinanceTypography.labelSmall,
                        color = if (state.isSafeToSpendNegative) OLEDColors.accentRose else OLEDColors.textSecondary,
                        maxLines = 1
                    )
                }

                // Single 3-segment progress track (Spent / Committed Loans / Safe Remaining)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (state.spentProgressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(state.spentProgressFraction.coerceIn(0.001f, 1f))
                                    .background(OLEDColors.accentSky)
                            )
                        }
                        if (state.committedLoansProgressFraction > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(state.committedLoansProgressFraction.coerceIn(0.001f, 1f))
                                    .background(OLEDColors.accentAmber)
                            )
                        }
                        val remainingFraction =
                            (1f - state.spentProgressFraction - state.committedLoansProgressFraction)
                                .coerceAtLeast(0f)
                        if (remainingFraction > 0f) {
                            Spacer(modifier = Modifier.weight(remainingFraction))
                        }
                    }
                }

                HorizontalDivider(color = OLEDColors.borderSubtle)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeroMetricColumn(
                        label = "TOTAL BUDGET",
                        value = maskAmount(state.totalBudgetFormatted, state.isAmountsHidden),
                        valueColor = OLEDColors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricColumn(
                        label = "SPENT (${state.spentPercent}%)",
                        value = maskAmount(state.spentSoFarFormatted, state.isAmountsHidden),
                        valueColor = OLEDColors.accentSky,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricColumn(
                        label = "FIXED / LOANS",
                        value = maskAmount(state.committedLoansFormatted, state.isAmountsHidden),
                        valueColor = OLEDColors.accentAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMetricColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = FinanceTypography.labelSmall,
            color = OLEDColors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = NumericTextStyles.bodyAmount.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BudgetSubTabBar(
    state: BudgetUiState,
    onSelectSubTab: (BudgetSubTab) -> Unit
) {
    Surface(
        color = OLEDColors.bgCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            SubTabButton(
                title = "Envelopes",
                badgeText = state.envelopes.size.toString(),
                isSelected = state.selectedSubTab == BudgetSubTab.ENVELOPES,
                isAlertBadge = false,
                onClick = { onSelectSubTab(BudgetSubTab.ENVELOPES) },
                modifier = Modifier.weight(1f)
            )
            SubTabButton(
                title = "Bills",
                badgeText = if (state.overdueBillsCount > 0) {
                    "${state.overdueBillsCount}!"
                } else {
                    state.bills.size.toString()
                },
                isSelected = state.selectedSubTab == BudgetSubTab.BILLS,
                isAlertBadge = state.overdueBillsCount > 0,
                onClick = { onSelectSubTab(BudgetSubTab.BILLS) },
                modifier = Modifier.weight(1f)
            )
            SubTabButton(
                title = "Loans",
                badgeText = state.activeLoansCount.toString(),
                isSelected = state.selectedSubTab == BudgetSubTab.LOANS,
                isAlertBadge = false,
                onClick = { onSelectSubTab(BudgetSubTab.LOANS) },
                modifier = Modifier.weight(1f)
            )
            SubTabButton(
                title = "Activity",
                badgeText = state.transactions.size.toString(),
                isSelected = state.selectedSubTab == BudgetSubTab.ACTIVITY,
                isAlertBadge = false,
                onClick = { onSelectSubTab(BudgetSubTab.ACTIVITY) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SubTabButton(
    title: String,
    badgeText: String,
    isSelected: Boolean,
    isAlertBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) OLEDColors.bgCardElevated else Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(1.dp, OLEDColors.borderSubtle) else null,
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = FinanceTypography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.sp
                ),
                color = if (isSelected) OLEDColors.textPrimary else OLEDColors.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Surface(
                color = when {
                    isAlertBadge -> OLEDColors.accentRose
                    isSelected -> OLEDColors.accentSky
                    else -> OLEDColors.bgCardSubtle
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = NumericTextStyles.captionAmount.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = when {
                        isAlertBadge -> OLEDColors.textPrimary
                        isSelected -> OLEDColors.bgBase
                        else -> OLEDColors.textSecondary
                    },
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    actionLabel: String,
    onActionClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = FinanceTypography.labelLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp
            ),
            color = OLEDColors.textSecondary
        )

        if (onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = FinanceTypography.labelLarge,
                    color = OLEDColors.accentSky
                )
            }
        } else {
            Text(
                text = actionLabel,
                style = NumericTextStyles.captionAmount,
                color = OLEDColors.textSecondary
            )
        }
    }
}

@Composable
private fun EnvelopeCard(
    envelope: EnvelopeItemUi,
    items: List<TransactionItemUi>,
    isAmountsHidden: Boolean,
    onTapIcon: () -> Unit,
    onQuickLog: () -> Unit,
    onEdit: () -> Unit,
    onEditItem: (TransactionItemUi) -> Unit,
    onDeleteItem: (TransactionItemUi) -> Unit
) {
    var isItemsExpanded by remember(envelope.id) { mutableStateOf(false) }
    val accentColor = CurrencyFormatters.parseColorHex(envelope.colorHex, OLEDColors.accentSky)
    val ratio = envelope.progressRatio
    val pct = (ratio * 100f).toInt().coerceAtLeast(0)
    val barColor = when {
        ratio > 0.85f -> OLEDColors.accentRose
        ratio > 0.65f -> OLEDColors.accentAmber
        else -> OLEDColors.accentEmerald
    }

    Surface(
        color = OLEDColors.bgCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        onClick = onTapIcon,
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Change ${envelope.name} icon" }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = envelope.iconEmoji, fontSize = 20.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = envelope.name,
                            style = FinanceTypography.titleMedium,
                            color = OLEDColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subtitle = if (isAmountsHidden) {
                            "₱•••• left of ₱••••"
                        } else if (envelope.overBudgetCentavos > 0L) {
                            "Over by ${BudgetCalculator.formatPeso(envelope.overBudgetCentavos, showDecimal = false)} of ${BudgetCalculator.formatPeso(envelope.allocatedLimitCentavos, showDecimal = false)}"
                        } else {
                            "${BudgetCalculator.formatPeso(envelope.remainingCentavos, showDecimal = false)} left of ${BudgetCalculator.formatPeso(envelope.allocatedLimitCentavos, showDecimal = false)}"
                        }
                        Text(
                            text = subtitle,
                            style = FinanceTypography.bodyMedium,
                            color = if (envelope.overBudgetCentavos > 0L) {
                                OLEDColors.accentRose
                            } else {
                                OLEDColors.textSecondary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = maskAmount(
                            BudgetCalculator.formatPeso(envelope.spentCentavos, showDecimal = false),
                            isAmountsHidden
                        ),
                        style = NumericTextStyles.cardAmount,
                        color = OLEDColors.textPrimary
                    )
                    Text(
                        text = "spent ($pct%)",
                        style = FinanceTypography.labelSmall,
                        color = OLEDColors.textSecondary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(3.dp))
                            .background(barColor)
                    )
                }

                Surface(
                    onClick = onQuickLog,
                    color = OLEDColors.bgCardElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Quick log to ${envelope.name}" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            tint = OLEDColors.accentSky,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Surface(
                onClick = { isItemsExpanded = !isItemsExpanded },
                color = OLEDColors.bgCardSubtle,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .semantics { contentDescription = "Toggle ${envelope.name} items" }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val itemCountLabel = when (items.size) {
                        0 -> "No added items"
                        1 -> "1 added item"
                        else -> "${items.size} added items"
                    }
                    Text(
                        text = itemCountLabel,
                        style = FinanceTypography.labelLarge,
                        color = OLEDColors.textSecondary
                    )
                    Icon(
                        imageVector = if (isItemsExpanded) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = OLEDColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (isItemsExpanded) {
                if (items.isEmpty()) {
                    Text(
                        text = "Tap + to log an item under ${envelope.name}.",
                        style = FinanceTypography.bodyMedium,
                        color = OLEDColors.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items.forEach { item ->
                            Surface(
                                color = OLEDColors.bgCardElevated,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, OLEDColors.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            style = FinanceTypography.bodyLarge,
                                            color = OLEDColors.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.timeLabel,
                                            style = FinanceTypography.labelSmall,
                                            color = OLEDColors.textSecondary
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = maskAmount(
                                                "-${BudgetCalculator.formatPeso(item.amountCentavos)}",
                                                isAmountsHidden,
                                                "-₱••••"
                                            ),
                                            style = NumericTextStyles.captionAmount,
                                            color = OLEDColors.accentSky
                                        )
                                        IconButton(
                                            onClick = { onEditItem(item) },
                                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Edit,
                                                contentDescription = "Edit ${item.title}",
                                                tint = OLEDColors.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteItem(item) },
                                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DeleteOutline,
                                                contentDescription = "Delete ${item.title}",
                                                tint = OLEDColors.accentRose,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillCard(
    bill: BillItemUi,
    cycleLabel: String,
    isAmountsHidden: Boolean,
    onPayNow: () -> Unit,
    onEdit: () -> Unit
) {
    val borderColor = when {
        bill.isOverdue -> OLEDColors.accentRose.copy(alpha = 0.45f)
        else -> OLEDColors.borderSubtle
    }
    val containerBrush = if (bill.isOverdue) {
        Brush.verticalGradient(
            listOf(OLEDColors.accentRose.copy(alpha = 0.08f), OLEDColors.bgCard)
        )
    } else {
        Brush.verticalGradient(listOf(OLEDColors.bgCard, OLEDColors.bgCard))
    }

    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBrush)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = bill.iconEmoji, fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bill.name,
                            style = FinanceTypography.titleMedium,
                            color = OLEDColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Due ${bill.dueDateLabel}",
                            style = FinanceTypography.bodyMedium,
                            color = OLEDColors.textSecondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = maskAmount(BudgetCalculator.formatPeso(bill.amountCentavos), isAmountsHidden),
                        style = NumericTextStyles.cardAmount,
                        color = OLEDColors.textPrimary
                    )
                    val (badgeText, badgeColor) = when {
                        bill.isPaid -> "✓ Paid" to OLEDColors.accentEmerald
                        bill.isOverdue -> {
                            val unit = if (bill.overdueDays == 1) "day" else "days"
                            "Overdue by ${bill.overdueDays} $unit" to OLEDColors.accentRose
                        }
                        else -> "Upcoming" to OLEDColors.accentAmber
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = badgeText,
                            style = FinanceTypography.labelSmall,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${bill.name}",
                        tint = OLEDColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (!bill.isPaid) {
                    Button(
                        onClick = onPayNow,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OLEDColors.accentEmerald.copy(alpha = 0.18f),
                            contentColor = OLEDColors.accentEmerald
                        ),
                        border = BorderStroke(1.dp, OLEDColors.accentEmerald.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Text(
                            text = "Pay Now",
                            style = FinanceTypography.labelLarge
                        )
                    }
                } else {
                    Text(
                        text = "Settled for $cycleLabel",
                        style = FinanceTypography.bodyMedium,
                        color = OLEDColors.accentEmerald
                    )
                }
            }
        }
    }
}

@Composable
private fun LoansSummaryBanner(
    committedLoansFormatted: String,
    activeLoansCount: Int
) {
    Surface(
        color = OLEDColors.bgCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OLEDColors.accentAmber.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TOTAL COMMITTED",
                    style = FinanceTypography.labelSmall,
                    color = OLEDColors.textSecondary
                )
                Text(
                    text = committedLoansFormatted,
                    style = NumericTextStyles.sectionAmount,
                    color = OLEDColors.textPrimary
                )
            }

            Surface(
                color = OLEDColors.accentAmber.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, OLEDColors.accentAmber.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "$activeLoansCount Active Loans",
                    style = FinanceTypography.labelLarge,
                    color = OLEDColors.accentAmber,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun LoanCard(
    loan: LoanItemUi,
    isAmountsHidden: Boolean,
    onMarkPaid: () -> Unit,
    onPayAdvance: () -> Unit,
    onEdit: () -> Unit
) {
    val isCompleted = !loan.isActive || loan.paidInstallments >= loan.totalInstallments
    Surface(
        color = OLEDColors.bgCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (loan.isOverdue) OLEDColors.accentRose.copy(alpha = 0.45f) else OLEDColors.borderSubtle
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = OLEDColors.accentAmber.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = loan.iconEmoji, fontSize = 18.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = loan.name,
                            style = FinanceTypography.titleMedium,
                            color = OLEDColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Installment ${loan.paidInstallments}/${loan.totalInstallments} • Due ${loan.dueDateLabel}",
                            style = FinanceTypography.bodyMedium,
                            color = OLEDColors.textSecondary
                        )
                        if (loan.isOverdue) {
                            val unit = if (loan.overdueDays == 1) "day" else "days"
                            Text(
                                text = "Overdue by ${loan.overdueDays} $unit",
                                style = FinanceTypography.labelSmall,
                                color = OLEDColors.accentRose
                            )
                        }
                    }
                }

                Text(
                    text = maskAmount(
                        BudgetCalculator.formatPeso(loan.monthlyAmortizationCentavos),
                        isAmountsHidden
                    ),
                    style = NumericTextStyles.cardAmount,
                    color = OLEDColors.textPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit ${loan.name}",
                        tint = OLEDColors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                when {
                    isCompleted -> {
                        Surface(
                            color = OLEDColors.accentEmerald.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, OLEDColors.accentEmerald.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = OLEDColors.accentEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Completed",
                                    style = FinanceTypography.labelLarge,
                                    color = OLEDColors.accentEmerald
                                )
                            }
                        }
                    }

                    loan.isPaidForCycle -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = OLEDColors.accentEmerald,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "✓ Paid",
                                    style = FinanceTypography.labelLarge,
                                    color = OLEDColors.bgBase,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onPayAdvance,
                                border = BorderStroke(1.dp, OLEDColors.accentAmber.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                            ) {
                                Text(
                                    text = "Pay Advance",
                                    style = FinanceTypography.labelLarge,
                                    color = OLEDColors.accentAmber
                                )
                            }
                        }
                    }

                    else -> {
                        Button(
                            onClick = onMarkPaid,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OLEDColors.accentEmerald.copy(alpha = 0.18f),
                                contentColor = OLEDColors.accentEmerald
                            ),
                            border = BorderStroke(1.dp, OLEDColors.accentEmerald.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Text(
                                text = "Mark Paid",
                                style = FinanceTypography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    transaction: TransactionItemUi,
    isAmountsHidden: Boolean,
    onEdit: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Surface(
        color = OLEDColors.bgCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title,
                    style = FinanceTypography.titleMedium,
                    color = OLEDColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${transaction.categoryLabel} • ${transaction.timeLabel}",
                    style = FinanceTypography.bodyMedium,
                    color = OLEDColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = maskAmount(
                        "-${BudgetCalculator.formatPeso(transaction.amountCentavos)}",
                        isAmountsHidden,
                        "-₱••••"
                    ),
                    style = NumericTextStyles.cardAmount,
                    color = OLEDColors.accentSky
                )
                if (onEdit != null) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit ${transaction.title}",
                            tint = OLEDColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete ${transaction.title}",
                        tint = OLEDColors.accentRose
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String
) {
    Surface(
        color = OLEDColors.bgCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, OLEDColors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = FinanceTypography.titleMedium,
                color = OLEDColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = FinanceTypography.bodyMedium,
                color = OLEDColors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
