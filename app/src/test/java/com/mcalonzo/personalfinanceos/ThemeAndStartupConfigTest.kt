package com.mcalonzo.personalfinanceos

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.ThemeMode
import com.mcalonzo.personalfinanceos.ui.theme.DarkOLEDColors
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.JetBrainsMonoFamily
import com.mcalonzo.personalfinanceos.ui.theme.LightWarmPaperColors
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.PersonalFinanceTheme
import com.mcalonzo.personalfinanceos.ui.theme.PlusJakartaSansFamily
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeAndStartupConfigTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `bundled font families cover all weights used in typography and numeric styles`() {
        val plusJakartaFonts = (PlusJakartaSansFamily as FontListFontFamily).fonts
            .filterIsInstance<ResourceFont>()
        val jetBrainsFonts = (JetBrainsMonoFamily as FontListFontFamily).fonts
            .filterIsInstance<ResourceFont>()

        val expectedWeights = setOf(
            FontWeight.Normal,
            FontWeight.Medium,
            FontWeight.SemiBold,
            FontWeight.Bold,
            FontWeight.ExtraBold
        )
        assertThat(plusJakartaFonts.map { it.weight }.toSet()).containsAtLeastElementsIn(expectedWeights)
        assertThat(jetBrainsFonts.map { it.weight }.toSet()).containsAtLeastElementsIn(expectedWeights)

        val sansStyles = listOf(
            FinanceTypography.displayLarge,
            FinanceTypography.headlineMedium,
            FinanceTypography.titleLarge,
            FinanceTypography.titleMedium,
            FinanceTypography.bodyLarge,
            FinanceTypography.bodyMedium,
            FinanceTypography.labelLarge,
            FinanceTypography.labelMedium,
            FinanceTypography.labelSmall
        )
        val plusJakartaWeights = plusJakartaFonts.map { it.weight }.toSet()
        sansStyles.forEach { style ->
            assertThat(style.fontFamily).isEqualTo(PlusJakartaSansFamily)
            assertThat(plusJakartaWeights).contains(style.fontWeight)
        }

        val numericStyles = listOf(
            NumericTextStyles.heroBalance,
            NumericTextStyles.sectionAmount,
            NumericTextStyles.cardAmount,
            NumericTextStyles.bodyAmount,
            NumericTextStyles.captionAmount
        )
        val jetBrainsWeights = jetBrainsFonts.map { it.weight }.toSet()
        numericStyles.forEach { style ->
            assertThat(style.fontFamily).isEqualTo(JetBrainsMonoFamily)
            assertThat(style.fontFeatureSettings).isEqualTo("tnum")
            assertThat(jetBrainsWeights).contains(style.fontWeight)
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        (plusJakartaFonts + jetBrainsFonts).forEach { font ->
            context.resources.openRawResource(font.resId).use { stream ->
                val header = ByteArray(4)
                assertThat(stream.read(header)).isEqualTo(4)
                assertThat(header.toList()).isEqualTo(listOf<Byte>(0x00, 0x01, 0x00, 0x00))
            }
        }
    }

    @Test
    fun `WorkManagerInitializer is removed from InitializationProvider and HiltWorkerFactory configures WorkManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providerComponent = ComponentName(context, "androidx.startup.InitializationProvider")
        val providerInfo = context.packageManager.getProviderInfo(
            providerComponent,
            PackageManager.GET_META_DATA
        )
        assertThat(providerInfo.metaData?.containsKey("androidx.work.WorkManagerInitializer")).isFalse()

        val app = context.applicationContext as FinanceOsApplication
        val config = app.workManagerConfiguration
        assertThat(config.workerFactory).isSameInstanceAs(app.workerFactory)
        val testConfig = Configuration.Builder()
            .setWorkerFactory(app.workerFactory)
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, testConfig)
        try {
            assertThat(WorkManager.getInstance(context)).isNotNull()
        } finally {
            WorkManagerTestInitHelper.closeWorkDatabase()
        }
    }

    @Test
    fun `PersonalFinanceTheme provides DarkOLEDColors and LightWarmPaperColors based on darkTheme`() {
        var darkColorsBg: Color? = null
        var darkMaterialBg: Color? = null
        var lightColorsBg: Color? = null
        var lightColorsText: Color? = null
        var lightMaterialBg: Color? = null

        composeRule.setContent {
            PersonalFinanceTheme(darkTheme = true) {
                darkColorsBg = FinanceTheme.colors.bgBase
                darkMaterialBg = MaterialTheme.colorScheme.background
            }
            PersonalFinanceTheme(darkTheme = false) {
                lightColorsBg = FinanceTheme.colors.bgBase
                lightColorsText = FinanceTheme.colors.textPrimary
                lightMaterialBg = MaterialTheme.colorScheme.background
            }
        }
        composeRule.waitForIdle()

        assertThat(darkColorsBg).isEqualTo(DarkOLEDColors.bgBase)
        assertThat(darkMaterialBg).isEqualTo(Color(0xFF06090E))
        assertThat(lightColorsBg).isEqualTo(LightWarmPaperColors.bgBase)
        assertThat(lightColorsBg).isEqualTo(Color(0xFFF7F6F2))
        assertThat(lightColorsText).isEqualTo(Color(0xFF1C1917))
        assertThat(lightMaterialBg).isEqualTo(Color(0xFFF7F6F2))
    }

    @Test
    fun `resolveDarkTheme handles all ThemeMode and system appearance combinations`() {
        assertThat(resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = true)).isTrue()
        assertThat(resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = false)).isFalse()
        assertThat(resolveDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = true)).isFalse()
        assertThat(resolveDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = false)).isFalse()
        assertThat(resolveDarkTheme(ThemeMode.DARK, systemInDarkTheme = true)).isTrue()
        assertThat(resolveDarkTheme(ThemeMode.DARK, systemInDarkTheme = false)).isTrue()
    }
}
