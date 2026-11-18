package com.mcalonzo.personalfinanceos.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mcalonzo.personalfinanceos.data.local.ThemeMode
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography

@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FinanceTheme.colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = FinanceTypography.headlineMedium,
            color = FinanceTheme.colors.textPrimary
        )

        Surface(
            color = FinanceTheme.colors.bgCard,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Appearance",
                    style = FinanceTypography.titleMedium,
                    color = FinanceTheme.colors.textPrimary
                )
                Text(
                    text = "Choose how Omni looks across your device.",
                    style = FinanceTypography.bodyMedium,
                    color = FinanceTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                ThemeModeSegmentedControl(
                    selectedMode = themeMode,
                    onSelectMode = onSelectThemeMode
                )
            }
        }
    }
}

@Composable
private fun ThemeModeSegmentedControl(
    selectedMode: ThemeMode,
    onSelectMode: (ThemeMode) -> Unit
) {
    val options = listOf(
        ThemeMode.SYSTEM to "System",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(FinanceTheme.colors.bgCardSubtle)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selectedMode
            val containerColor = if (isSelected) {
                FinanceTheme.colors.bgCardElevated
            } else {
                Color.Transparent
            }
            val textColor = if (isSelected) {
                FinanceTheme.colors.accentSky
            } else {
                FinanceTheme.colors.textSecondary
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(containerColor)
                    .then(
                        if (isSelected) {
                            Modifier.border(
                                width = 1.dp,
                                color = FinanceTheme.colors.borderSubtle,
                                shape = RoundedCornerShape(10.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelectMode(mode) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = FinanceTypography.labelLarge.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = textColor
                )
            }
        }
    }
}
