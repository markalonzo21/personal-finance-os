package com.mcalonzo.personalfinanceos

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.JetBrainsMonoFamily
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.PlusJakartaSansFamily
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeAndStartupConfigTest {

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
        assertThat(WorkManager.getInstance(context)).isNotNull()
    }
}
