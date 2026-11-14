package com.mcalonzo.personalfinanceos.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Metadata for an SOA PDF persisted inside the app's private storage directory.
 */
data class StoredSoaFile(
    val localPath: String,
    val fileName: String,
    val savedAtEpochMs: Long
)

/**
 * Manages atomic copy-then-replace storage of Statement of Account (SOA) PDFs
 * inside private app storage so prior files are preserved if a write fails.
 */
@Singleton
class SoaStorageManager(
    private val storageDir: File
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, SOA_SUBDIR))

    fun saveOrReplaceSoa(
        yearMonth: String,
        inputStream: InputStream,
        originalFileName: String,
        previousLocalPath: String?
    ): StoredSoaFile {
        validateYearMonth(yearMonth)
        val sanitizedFileName = sanitizeDisplayFileName(originalFileName, yearMonth)
        ensureStorageDirExists()

        val uniqueBase = "${yearMonth}_${UUID.randomUUID()}"
        val tempFile = File(storageDir, "$uniqueBase.pdf.tmp")
        val targetFile = File(storageDir, "$uniqueBase.pdf")

        try {
            FileOutputStream(tempFile).use { output ->
                inputStream.copyTo(output)
                output.flush()
                try {
                    output.fd.sync()
                } catch (_: IOException) {
                    // Best-effort sync; some filesystems do not support fsync on descriptors.
                }
            }

            moveTempToTarget(tempFile, targetFile)
            if (!targetFile.exists() || !targetFile.isFile) {
                throw IOException("Renamed SOA file missing after move: ${targetFile.absolutePath}")
            }
        } catch (t: Throwable) {
            tempFile.delete()
            if (targetFile.exists() && targetFile.absolutePath != previousLocalPath) {
                targetFile.delete()
            }
            throw t
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }

        deleteManagedFileIfSafe(previousLocalPath, excludePath = targetFile.absolutePath)

        return StoredSoaFile(
            localPath = targetFile.absolutePath,
            fileName = sanitizedFileName,
            savedAtEpochMs = System.currentTimeMillis()
        )
    }

    private fun ensureStorageDirExists() {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        if (!storageDir.isDirectory) {
            throw IOException("Failed to create SOA storage directory: ${storageDir.absolutePath}")
        }
    }

    private fun moveTempToTarget(tempFile: File, targetFile: File) {
        try {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    /**
     * Deletes [localPath] only when it resolves to a regular file directly inside
     * [storageDir] and does not match [excludePath].
     */
    fun deleteManagedFileIfSafe(localPath: String?, excludePath: String? = null) {
        if (localPath.isNullOrBlank()) return
        runCatching {
            val canonicalStorageDir = storageDir.canonicalFile
            val targetFile = File(localPath).canonicalFile
            val canonicalExclude = excludePath
                ?.takeIf { it.isNotBlank() }
                ?.let { File(it).canonicalFile }
            if (
                targetFile != canonicalExclude &&
                targetFile.parentFile == canonicalStorageDir &&
                targetFile.isFile
            ) {
                targetFile.delete()
            }
        }
    }

    companion object {
        private const val SOA_SUBDIR = "soa"
        private val YEAR_MONTH_REGEX = Regex("^\\d{4}-\\d{2}$")

        internal fun validateYearMonth(yearMonth: String) {
            require(YEAR_MONTH_REGEX.matches(yearMonth)) {
                "Invalid yearMonth '$yearMonth'; expected YYYY-MM"
            }
        }

        internal fun sanitizeDisplayFileName(originalFileName: String, yearMonth: String): String {
            val fallback = "SOA_${yearMonth}.pdf"
            val stripped = originalFileName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .replace("\u0000", "")
                .trim()
            return if (stripped.isBlank() || stripped == "." || stripped == "..") {
                fallback
            } else {
                stripped
            }
        }
    }
}
