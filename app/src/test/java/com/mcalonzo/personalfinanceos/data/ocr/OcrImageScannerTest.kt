package com.mcalonzo.personalfinanceos.data.ocr

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OcrImageScannerTest {

    private lateinit var context: Context
    private lateinit var scanner: OcrImageScanner
    private val createdFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scanner = OcrImageScanner()
    }

    @After
    fun tearDown() {
        createdFiles.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }

    @Test
    fun createTempCameraFile_createsJpgInsideCacheScanTmpDir() {
        val file = scanner.createTempCameraFile(context)
        createdFiles.add(file)

        val expectedParent = File(context.cacheDir, "scan_tmp")
        assertThat(file.exists()).isTrue()
        assertThat(file.parentFile?.canonicalPath).isEqualTo(expectedParent.canonicalPath)
        assertThat(file.name).endsWith(".jpg")
    }

    @Test
    fun fileProvider_isConfiguredInManifestWithPathsMetadata() {
        val providerInfo = context.packageManager.resolveContentProvider(
            "com.mcalonzo.personalfinanceos.fileprovider",
            PackageManager.GET_META_DATA
        )

        assertThat(providerInfo).isNotNull()
        assertThat(providerInfo?.exported).isFalse()
        assertThat(providerInfo?.grantUriPermissions).isTrue()
        val pathsResId = providerInfo?.metaData?.getInt("android.support.FILE_PROVIDER_PATHS") ?: 0
        assertThat(pathsResId).isNotEqualTo(0)
    }

    @Test
    fun getTempCameraUri_returnsFileProviderContentUri() {
        // AndroidX FileProvider.SimplePathStrategy.belongsToRoot hardcodes '/' instead of
        // File.separatorChar, which fails against Windows canonical paths under Robolectric.
        assumeTrue(
            "FileProvider.SimplePathStrategy hardcodes '/' on Windows",
            File.separatorChar == '/'
        )

        val file = scanner.createTempCameraFile(context)
        createdFiles.add(file)

        val uri = scanner.getTempCameraUri(context, file)

        assertThat(uri.scheme).isEqualTo("content")
        assertThat(uri.authority).isEqualTo("com.mcalonzo.personalfinanceos.fileprovider")
        assertThat(uri.path).contains(file.name)
    }

    @Test
    fun deleteTempFileQuietly_removesExistingFileAndHandlesNull() {
        val file = scanner.createTempCameraFile(context)
        assertThat(file.exists()).isTrue()

        scanner.deleteTempFileQuietly(file)
        assertThat(file.exists()).isFalse()

        scanner.deleteTempFileQuietly(null)
    }

    @Test
    fun decodeSampledBitmap_decodesValidImageUriWithoutReturningNull() {
        val imageFile = File(context.cacheDir, "sample_receipt.png")
        createdFiles.add(imageFile)
        val sourceBitmap = Bitmap.createBitmap(120, 80, Bitmap.Config.ARGB_8888)
        FileOutputStream(imageFile).use { out ->
            sourceBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        sourceBitmap.recycle()

        val decoded = scanner.decodeSampledBitmap(context, Uri.fromFile(imageFile))
        assertThat(decoded).isNotNull()
        assertThat(decoded!!.width).isEqualTo(120)
        assertThat(decoded.height).isEqualTo(80)
        decoded.recycle()
    }
}
