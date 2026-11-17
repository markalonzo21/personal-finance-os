package com.mcalonzo.personalfinanceos.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mcalonzo.personalfinanceos.domain.model.OcrLineBox
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Ephemeral on-device OCR scanner using bundled ML Kit Text Recognition.
 * Never persists captured photos or uploaded documents to permanent storage.
 */
@Singleton
class OcrImageScanner @Inject constructor() {

    /**
     * Creates a temporary JPEG file inside `cacheDir/scan_tmp/` for camera capture.
     */
    fun createTempCameraFile(context: Context): File {
        val tempDir = File(context.cacheDir, SCAN_TMP_DIR).apply { mkdirs() }
        return File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, tempDir)
    }

    /**
     * Returns a `FileProvider` content URI for the given temporary camera [file].
     */
    fun getTempCameraUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}$FILE_PROVIDER_AUTHORITY_SUFFIX",
            file
        )
    }

    /**
     * Deletes a temporary scan file without throwing if it is null or already removed.
     */
    fun deleteTempFileQuietly(file: File?) {
        if (file != null && file.exists()) {
            runCatching { file.delete() }
        }
    }

    /**
     * Decodes an image from [uri] (downsampled to `maxDimension <= 2048px`), runs on-device
     * ML Kit text recognition, and immediately recycles the decoded [Bitmap].
     */
    suspend fun scanImageUri(context: Context, uri: Uri): List<OcrLineBox> =
        withContext(Dispatchers.IO) {
            val bitmap = decodeSampledBitmap(context, uri)
                ?: throw IllegalStateException("Unable to decode image from URI: $uri")
            try {
                recognizeBitmap(bitmap)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }

    /**
     * Renders up to [maxPages] pages of a PDF from [uri] using [PdfRenderer], runs on-device
     * ML Kit text recognition per page, and recycles each rendered [Bitmap] immediately.
     */
    suspend fun scanPdfUri(
        context: Context,
        uri: Uri,
        maxPages: Int = DEFAULT_MAX_PDF_PAGES
    ): List<OcrLineBox> = withContext(Dispatchers.IO) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open PDF URI: $uri")

        val collectedLines = mutableListOf<OcrLineBox>()
        var cumulativeYOffset = 0

        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pagesToRender = minOf(renderer.pageCount, maxPages.coerceAtLeast(0))
                for (pageIndex in 0 until pagesToRender) {
                    val page = renderer.openPage(pageIndex)
                    var bitmap: Bitmap? = null
                    try {
                        val maxPageDim = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = if (maxPageDim > MAX_DIMENSION_PX) {
                            MAX_DIMENSION_PX.toFloat() / maxPageDim
                        } else {
                            minOf(2f, MAX_DIMENSION_PX.toFloat() / maxPageDim)
                        }
                        val targetWidth = (page.width * scale).toInt().coerceAtLeast(1)
                        val targetHeight = (page.height * scale).toInt().coerceAtLeast(1)

                        bitmap = Bitmap.createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            eraseColor(Color.WHITE)
                        }

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pageLines = recognizeBitmap(bitmap, yOffset = cumulativeYOffset)
                        collectedLines.addAll(pageLines)
                        cumulativeYOffset += targetHeight
                    } finally {
                        page.close()
                        if (bitmap != null && !bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }

        collectedLines
    }

    internal fun decodeSampledBitmap(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val decodedWithImageDecoder = runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val size = info.size
                    val sampleSize = calculateInSampleSize(
                        width = size.width,
                        height = size.height,
                        maxDimension = MAX_DIMENSION_PX
                    )
                    if (sampleSize > 1) {
                        decoder.setTargetSampleSize(sampleSize)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            }.getOrNull()
            if (decodedWithImageDecoder != null) {
                return decodedWithImageDecoder
            }
        }

        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val openedBoundsStream = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
            true
        } ?: false
        if (!openedBoundsStream) {
            return null
        }

        val sampleSize = calculateInSampleSize(
            width = boundsOptions.outWidth,
            height = boundsOptions.outHeight,
            maxDimension = MAX_DIMENSION_PX
        )

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        if (width <= 0 || height <= 0) return sampleSize
        var currentMax = maxOf(width, height)
        while (currentMax / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private suspend fun recognizeBitmap(
        bitmap: Bitmap,
        yOffset: Int = 0
    ): List<OcrLineBox> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        continuation.invokeOnCancellation {
            runCatching { recognizer.close() }
        }

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                runCatching { recognizer.close() }
                if (continuation.isActive) {
                    continuation.resume(extractLineBoxes(visionText, yOffset))
                }
            }
            .addOnFailureListener { error ->
                runCatching { recognizer.close() }
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
    }

    private fun extractLineBoxes(visionText: Text, yOffset: Int): List<OcrLineBox> {
        val lines = mutableListOf<OcrLineBox>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox
                lines.add(
                    OcrLineBox(
                        text = line.text,
                        left = box?.left ?: 0,
                        top = (box?.top ?: 0) + yOffset,
                        right = box?.right ?: 0,
                        bottom = (box?.bottom ?: 0) + yOffset
                    )
                )
            }
        }
        return lines
    }

    private companion object {
        const val SCAN_TMP_DIR = "scan_tmp"
        const val TEMP_FILE_PREFIX = "scan_"
        const val TEMP_FILE_SUFFIX = ".jpg"
        const val FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider"
        const val MAX_DIMENSION_PX = 2048
        const val DEFAULT_MAX_PDF_PAGES = 10
    }
}
