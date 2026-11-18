package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.viewmodel.DueReminderItemUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderCadenceSheet(
    item: DueReminderItemUi,
    onDismiss: () -> Unit,
    onSaveCadence: (
        notify7d: Boolean,
        notify3d: Boolean,
        notify1d: Boolean,
        notifyOnDue: Boolean,
        notifyDailyOverdue: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var notify7d by remember(item) { mutableStateOf(item.notify7d) }
    var notify3d by remember(item) { mutableStateOf(item.notify3d) }
    var notify1d by remember(item) { mutableStateOf(item.notify1d) }
    var notifyOnDue by remember(item) { mutableStateOf(item.notifyOnDue) }
    var notifyDailyOverdue by remember(item) { mutableStateOf(item.notifyDailyOverdue) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FinanceTheme.colors.bgCard,
        contentColor = FinanceTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(FinanceTheme.colors.borderSubtle)
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Reminder Cadence",
                style = FinanceTypography.headlineSmall,
                color = FinanceTheme.colors.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = item.iconEmoji,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    style = FinanceTypography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = FinanceTheme.colors.textPrimary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = item.amountFormatted,
                    style = NumericTextStyles.cardAmount,
                    color = FinanceTheme.colors.accentSky
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)
            Spacer(modifier = Modifier.height(8.dp))

            CadenceToggleRow(
                label = "7 days before",
                description = "Advance notification 1 week prior",
                checked = notify7d,
                onCheckedChange = { notify7d = it }
            )

            CadenceToggleRow(
                label = "3 days before",
                description = "Mid-week heads up",
                checked = notify3d,
                onCheckedChange = { notify3d = it }
            )

            CadenceToggleRow(
                label = "1 day before",
                description = "Urgent reminder the day before",
                checked = notify1d,
                onCheckedChange = { notify1d = it }
            )

            CadenceToggleRow(
                label = "On due date (9:00 AM)",
                description = "Morning alert on due date",
                checked = notifyOnDue,
                onCheckedChange = { notifyOnDue = it }
            )

            CadenceToggleRow(
                label = "Daily if overdue",
                description = "Persistent daily alert until settled",
                checked = notifyDailyOverdue,
                onCheckedChange = { notifyDailyOverdue = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FinanceTheme.colors.textSecondary
                    )
                ) {
                    Text("Cancel", style = FinanceTypography.labelLarge)
                }

                Button(
                    onClick = {
                        onSaveCadence(notify7d, notify3d, notify1d, notifyOnDue, notifyDailyOverdue)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FinanceTheme.colors.accentSky,
                        contentColor = FinanceTheme.colors.bgBase
                    )
                ) {
                    Text(
                        "Save Cadence",
                        style = FinanceTypography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun CadenceToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 52.dp)
            .semantics(mergeDescendants = true) {}
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = FinanceTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = FinanceTheme.colors.textPrimary
            )
            Text(
                text = description,
                style = FinanceTypography.labelSmall,
                color = FinanceTheme.colors.textSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = FinanceTheme.colors.bgBase,
                checkedTrackColor = FinanceTheme.colors.accentSky,
                uncheckedThumbColor = FinanceTheme.colors.textTertiary,
                uncheckedTrackColor = FinanceTheme.colors.bgCardSubtle
            )
        )
    }
}
