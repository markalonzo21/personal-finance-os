package com.mcalonzo.personalfinanceos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.OLEDColors

private val PRESET_ICONS: List<String> = listOf(
    "🛒", "💡", "⛽", "🍜", "🛡️",
    "💊", "🎮", "👕", "✈️", "📚",
    "🐾", "☕", "🏋️", "🎬", "🔧",
    "⚡", "📶", "🎵", "🏠", "🛵",
    "🏦", "💳", "🎓", "🧾", "🏷️"
)

/**
 * Dynamic emoji/icon selector supporting curated quick-picks and arbitrary user emoji entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPickerSheet(
    title: String = "Choose Category Icon",
    currentIcon: String,
    onSelectIcon: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customIconText by remember(currentIcon) { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = OLEDColors.bgCard,
        contentColor = OLEDColors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = FinanceTypography.titleLarge,
                    color = OLEDColors.textPrimary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close icon picker",
                        tint = OLEDColors.textSecondary
                    )
                }
            }

            Text(
                text = "Tap any icon below or enter a custom emoji:",
                style = FinanceTypography.bodyMedium,
                color = OLEDColors.textSecondary
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESET_ICONS.chunked(5).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowIcons.forEach { emoji ->
                            val isSelected = emoji == currentIcon
                            Surface(
                                onClick = {
                                    onSelectIcon(emoji)
                                    onDismiss()
                                },
                                color = if (isSelected) {
                                    OLEDColors.accentSky.copy(alpha = 0.2f)
                                } else {
                                    OLEDColors.bgCardElevated
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        OLEDColors.accentSky
                                    } else {
                                        OLEDColors.borderSubtle
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = emoji, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customIconText,
                    onValueChange = { value ->
                        if (value.length <= 8) customIconText = value
                    },
                    label = { Text("Custom emoji") },
                    placeholder = { Text("e.g. 🎁") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OLEDColors.accentSky,
                        unfocusedBorderColor = OLEDColors.borderSubtle,
                        focusedTextColor = OLEDColors.textPrimary,
                        unfocusedTextColor = OLEDColors.textPrimary,
                        focusedLabelColor = OLEDColors.accentSky,
                        unfocusedLabelColor = OLEDColors.textSecondary
                    ),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        val trimmed = customIconText.trim()
                        if (trimmed.isNotEmpty()) {
                            onSelectIcon(trimmed)
                            onDismiss()
                        }
                    },
                    enabled = customIconText.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = OLEDColors.accentSky,
                        contentColor = OLEDColors.bgBase
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 52.dp)
                ) {
                    Text(
                        text = "Apply",
                        style = FinanceTypography.labelLarge
                    )
                }
            }
        }
    }
}
