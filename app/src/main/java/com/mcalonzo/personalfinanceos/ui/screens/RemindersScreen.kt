package com.mcalonzo.personalfinanceos.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.ui.components.ReminderCadenceSheet
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.DueReminderItemUi
import com.mcalonzo.personalfinanceos.ui.viewmodel.RemindersUiState

@Composable
fun RemindersScreen(
    state: RemindersUiState,
    modifier: Modifier = Modifier,
    onToggleGlobalPush: (Boolean) -> Unit = {},
    onPreviewDueAlert: () -> Unit = {},
    onSettleBill: (Long) -> Unit = {},
    onSettleLoan: (Long) -> Unit = {},
    onUpdateCadence: (
        itemKey: String,
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) -> Unit = { _, _, _, _, _, _ -> }
) {
    var configuringItem by remember { mutableStateOf<DueReminderItemUi?>(null) }

    val overdueCount = state.items.count { it.isOverdue && !it.isPaid }
    val upcomingCount = state.items.count { !it.isOverdue && !it.isPaid }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.bgBase)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(
                        text = "SCHEDULED ALERTS & OBLIGATIONS",
                        style = FinanceTypography.labelSmall,
                        color = FinanceTheme.colors.accentSky,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Bill & Loan Reminders",
                        style = FinanceTypography.headlineMedium,
                        color = FinanceTheme.colors.textPrimary
                    )
                    Text(
                        text = "Chronological due dates and per-obligation notification triggers",
                        style = FinanceTypography.bodyMedium,
                        color = FinanceTheme.colors.textSecondary
                    )
                }
            }

            item(key = "push_controls_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = FinanceTheme.colors.bgCard),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(FinanceTheme.colors.borderSubtle))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (state.globalPushEnabled) FinanceTheme.colors.accentSky.copy(alpha = 0.15f) else FinanceTheme.colors.bgCardSubtle),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (state.globalPushEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                    contentDescription = null,
                                    tint = if (state.globalPushEnabled) FinanceTheme.colors.accentSky else FinanceTheme.colors.textTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Push Notifications",
                                    style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = FinanceTheme.colors.textPrimary
                                )
                                Text(
                                    text = "Daily 09:00 AM heads-up alerts & overdue tracking",
                                    style = FinanceTypography.labelSmall,
                                    color = FinanceTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = state.globalPushEnabled,
                                onCheckedChange = onToggleGlobalPush,
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .semantics { contentDescription = "Global Push Toggle" },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = FinanceTheme.colors.bgBase,
                                    checkedTrackColor = FinanceTheme.colors.accentSky,
                                    uncheckedThumbColor = FinanceTheme.colors.textTertiary,
                                    uncheckedTrackColor = FinanceTheme.colors.bgCardSubtle
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (overdueCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(FinanceTheme.colors.accentRose.copy(alpha = 0.15f))
                                        .border(1.dp, FinanceTheme.colors.accentRose.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "$overdueCount Overdue",
                                        style = FinanceTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = FinanceTheme.colors.accentRose
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FinanceTheme.colors.bgCardSubtle)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "$upcomingCount Upcoming",
                                    style = FinanceTypography.labelSmall,
                                    color = FinanceTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))

                            OutlinedButton(
                                onClick = onPreviewDueAlert,
                                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = FinanceTheme.colors.accentSky
                                )
                            ) {
                                Text("Preview Alert", style = FinanceTypography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (state.items.isEmpty()) {
                item(key = "empty_state") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = FinanceTheme.colors.bgCardSubtle),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 36.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "🎉",
                                fontSize = 36.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Due Obligations",
                                style = FinanceTypography.headlineSmall,
                                color = FinanceTheme.colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All recurring bills and installment loans for this cycle are either fully settled or inactive.",
                                style = FinanceTypography.bodyMedium,
                                color = FinanceTheme.colors.textSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = state.items,
                    key = { it.itemKey }
                ) { item ->
                    ReminderItemCard(
                        item = item,
                        onConfigureCadence = { configuringItem = item },
                        onSettle = {
                            if (item.isLoan) {
                                onSettleLoan(item.id)
                            } else {
                                onSettleBill(item.id)
                            }
                        }
                    )
                }
            }
        }
    }

    configuringItem?.let { item ->
        ReminderCadenceSheet(
            item = item,
            onDismiss = { configuringItem = null },
            onSaveCadence = { n7, n3, n1, nod, ndo ->
                onUpdateCadence(item.itemKey, n7, n3, n1, nod, ndo)
                configuringItem = null
            }
        )
    }
}

@Composable
private fun ReminderItemCard(
    item: DueReminderItemUi,
    onConfigureCadence: () -> Unit,
    onSettle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        item.isPaid -> FinanceTheme.colors.accentEmerald
        item.isOverdue -> FinanceTheme.colors.accentRose
        item.daysUntilDue == 0L -> FinanceTheme.colors.accentAmber
        else -> FinanceTheme.colors.textSecondary
    }

    val cardBorder = when {
        item.isOverdue && !item.isPaid -> FinanceTheme.colors.accentRose.copy(alpha = 0.4f)
        item.daysUntilDue == 0L && !item.isPaid -> FinanceTheme.colors.accentAmber.copy(alpha = 0.4f)
        else -> FinanceTheme.colors.borderSubtle
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = FinanceTheme.colors.bgCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(cardBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(FinanceTheme.colors.bgCardSubtle),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = item.iconEmoji, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = FinanceTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(FinanceTheme.colors.bgCardSubtle)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.categoryLabel,
                                style = FinanceTypography.labelSmall,
                                color = FinanceTheme.colors.textTertiary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Due ${item.dueDateLabel}",
                            style = FinanceTypography.labelSmall,
                            color = FinanceTheme.colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.dueStatusText,
                                style = FinanceTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = statusColor
                            )
                        }
                    }
                }
                Text(
                    text = item.amountFormatted,
                    style = NumericTextStyles.cardAmount,
                    color = FinanceTheme.colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onConfigureCadence,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FinanceTheme.colors.textSecondary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure Alert", style = FinanceTypography.labelMedium)
                }

                if (!item.isPaid) {
                    Button(
                        onClick = onSettle,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isOverdue) FinanceTheme.colors.accentRose else FinanceTheme.colors.accentSky,
                            contentColor = FinanceTheme.colors.bgBase
                        )
                    ) {
                        Text(
                            text = if (item.isOverdue) "Pay Overdue" else "Pay Now",
                            style = FinanceTypography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = FinanceTheme.colors.accentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paid",
                            style = FinanceTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = FinanceTheme.colors.accentEmerald
                        )
                    }
                }
            }
        }
    }
}
