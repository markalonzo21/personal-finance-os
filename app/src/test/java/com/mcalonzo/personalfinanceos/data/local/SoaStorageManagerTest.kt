package com.mcalonzo.personalfinanceos.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.mcalonzo.personalfinanceos.data.local.entity.BudgetCycleEntity
import com.mcalonzo.personalfinanceos.data.repository.BudgetRepository
import com.mcalonzo.personalfinanceos.domain.usecase.MonthRolloverUseCase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.YearMonth
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class SoaStorageManagerTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repo: BudgetRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(SeedDatabaseCallback({ db }) { YearMonth.of(2026, 11) })
            .allowMainThreadQueries()
            .build()
        repo = BudgetRepository(db, MonthRolloverUseCase(db))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `replacing SOA PDF copies new file first and deletes old file only after write succeeds`() {
        val manager = SoaStorageManager(tempFolder.root)
        val first = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 old".byteInputStream(),
            originalFileName = "BPI_Oct.pdf",
            previousLocalPath = null
        )
        assertThat(File(first.localPath).exists()).isTrue()
        assertThat(first.fileName).isEqualTo("BPI_Oct.pdf")

        val failingStream = object : InputStream() {
            override fun read(): Int = throw IOException("Disk read failed")
        }
        assertThrows(IOException::class.java) {
            manager.saveOrReplaceSoa("2026-11", failingStream, "Broken.pdf", first.localPath)
        }
        assertThat(File(first.localPath).exists()).isTrue() // Old file preserved on failure
        assertThat(tempFolder.root.listFiles()?.any { it.name.endsWith(".tmp") }).isFalse()

        val second = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 new".byteInputStream(),
            originalFileName = "BPI_Nov.pdf",
            previousLocalPath = first.localPath
        )
        assertThat(File(second.localPath).exists()).isTrue()
        assertThat(File(second.localPath).readText()).isEqualTo("%PDF-1.4 new")
        assertThat(File(first.localPath).exists()).isFalse() // Old file cleaned up after success
    }

    @Test
    fun `sanitizes original file name and rejects invalid yearMonth path traversal`() {
        val manager = SoaStorageManager(tempFolder.root)

        listOf("../2026", "2026/11", "2026-11/../../evil", "bad", "2026-1").forEach { badMonth ->
            assertThrows(IllegalArgumentException::class.java) {
                manager.saveOrReplaceSoa(badMonth, "%PDF-1.4".byteInputStream(), "soa.pdf", null)
            }
        }

        val unixPath = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 unix".byteInputStream(),
            originalFileName = "../../secret/BPI_Nov.pdf",
            previousLocalPath = null
        )
        assertThat(unixPath.fileName).isEqualTo("BPI_Nov.pdf")
        assertThat(File(unixPath.localPath).canonicalFile.parentFile)
            .isEqualTo(tempFolder.root.canonicalFile)
        assertThat(unixPath.localPath).endsWith(".pdf")

        val windowsPath = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 win".byteInputStream(),
            originalFileName = "C:\\fakepath\\Statement.pdf",
            previousLocalPath = unixPath.localPath
        )
        assertThat(windowsPath.fileName).isEqualTo("Statement.pdf")

        val blankFallback = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 blank".byteInputStream(),
            originalFileName = "dir/..",
            previousLocalPath = windowsPath.localPath
        )
        assertThat(blankFallback.fileName).isEqualTo("SOA_2026-11.pdf")
    }

    @Test
    fun `does not delete previousLocalPath outside storage directory`() {
        val soaDir = tempFolder.newFolder("soa")
        val outsideDir = tempFolder.newFolder("outside")
        val nestedDir = File(soaDir, "nested").apply { mkdirs() }

        val outsideFile = File(outsideDir, "important.pdf").apply { writeText("keep me") }
        val nestedFile = File(nestedDir, "nested.pdf").apply { writeText("keep nested") }

        val manager = SoaStorageManager(soaDir)

        val first = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 a".byteInputStream(),
            originalFileName = "a.pdf",
            previousLocalPath = outsideFile.absolutePath
        )
        assertThat(outsideFile.exists()).isTrue()

        val traversalPath =
            "${soaDir.absolutePath}${File.separator}..${File.separator}outside${File.separator}important.pdf"
        val second = manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 b".byteInputStream(),
            originalFileName = "b.pdf",
            previousLocalPath = traversalPath
        )
        assertThat(outsideFile.exists()).isTrue()
        assertThat(File(first.localPath).exists()).isTrue()

        manager.saveOrReplaceSoa(
            yearMonth = "2026-11",
            inputStream = "%PDF-1.4 c".byteInputStream(),
            originalFileName = "c.pdf",
            previousLocalPath = nestedFile.absolutePath
        )
        assertThat(nestedFile.exists()).isTrue()
        assertThat(File(second.localPath).exists()).isTrue()
    }

    @Test
    fun `attachSoaPdf updates BudgetCycleEntity and preserves old PDF on stream failure`() = runTest {
        val soaDir = tempFolder.newFolder("repo_soa")
        val manager = SoaStorageManager(soaDir)
        val cycleId = db.budgetDao().insertCycle(
            BudgetCycleEntity(
                yearMonth = "2026-11",
                totalBudgetCentavos = 500_000L,
                createdAtEpochMs = 1_000L
            )
        )

        val sourcePdf = tempFolder.newFile("source_soa.pdf").apply {
            writeBytes("%PDF-1.4 cycle-1".toByteArray())
        }
        val authority = "com.mcalonzo.personalfinanceos.test.soaprovider"
        FakeSoaContentProvider.backingFile = sourcePdf
        FakeSoaContentProvider.displayName = "../Downloads/BPI_2026_11.pdf"
        Robolectric.buildContentProvider(FakeSoaContentProvider::class.java)
            .create(authority)

        val firstUri = Uri.parse("content://$authority/soa/1")
        val firstStored = repo.attachSoaPdf(
            cycleId = cycleId,
            yearMonth = "2026-11",
            uri = firstUri,
            contextOverride = context,
            storageOverride = manager
        )

        assertThat(firstStored.fileName).isEqualTo("BPI_2026_11.pdf")
        assertThat(File(firstStored.localPath).exists()).isTrue()
        assertThat(File(firstStored.localPath).readText()).isEqualTo("%PDF-1.4 cycle-1")

        val cycleAfterFirst = db.budgetDao().getCycleById(cycleId)!!
        assertThat(cycleAfterFirst.soaPdfLocalUri).isEqualTo(firstStored.localPath)
        assertThat(cycleAfterFirst.soaFileName).isEqualTo("BPI_2026_11.pdf")
        assertThat(cycleAfterFirst.soaUploadedAtEpochMs).isEqualTo(firstStored.savedAtEpochMs)

        // Failing replacement stream must leave existing file & DB metadata untouched
        val failingUri = Uri.parse("content://com.mcalonzo.personalfinanceos.test.missing/broken")
        Shadows.shadowOf(context.contentResolver).registerInputStream(
            failingUri,
            object : InputStream() {
                override fun read(): Int = throw IOException("Stream read failed")
            }
        )
        try {
            repo.attachSoaPdf(
                cycleId = cycleId,
                yearMonth = "2026-11",
                uri = failingUri,
                contextOverride = context,
                storageOverride = manager
            )
            fail("Expected IOException when stream fails")
        } catch (_: IOException) {
            // Expected
        }
        assertThat(soaDir.listFiles()?.any { it.name.endsWith(".tmp") }).isFalse()
        assertThat(File(firstStored.localPath).exists()).isTrue()
        val cycleAfterFailure = db.budgetDao().getCycleById(cycleId)!!
        assertThat(cycleAfterFailure.soaPdfLocalUri).isEqualTo(firstStored.localPath)
        assertThat(cycleAfterFailure.soaFileName).isEqualTo("BPI_2026_11.pdf")

        // Successful replacement without OpenableColumns.DISPLAY_NAME uses SOA_<yearMonth>.pdf fallback
        val secondUri = Uri.parse("content://com.mcalonzo.personalfinanceos.test.raw/2")
        Shadows.shadowOf(context.contentResolver)
            .registerInputStream(secondUri, "%PDF-1.4 cycle-2".byteInputStream())

        val secondStored = repo.attachSoaPdf(
            cycleId = cycleId,
            yearMonth = "2026-11",
            uri = secondUri,
            contextOverride = context,
            storageOverride = manager
        )

        assertThat(secondStored.fileName).isEqualTo("SOA_2026-11.pdf")
        assertThat(File(secondStored.localPath).exists()).isTrue()
        assertThat(File(secondStored.localPath).readText()).isEqualTo("%PDF-1.4 cycle-2")
        assertThat(File(firstStored.localPath).exists()).isFalse()

        val cycleAfterSecond = db.budgetDao().getCycleById(cycleId)!!
        assertThat(cycleAfterSecond.soaPdfLocalUri).isEqualTo(secondStored.localPath)
        assertThat(cycleAfterSecond.soaFileName).isEqualTo("SOA_2026-11.pdf")
        assertThat(cycleAfterSecond.soaUploadedAtEpochMs).isEqualTo(secondStored.savedAtEpochMs)
    }

    class FakeSoaContentProvider : ContentProvider() {
        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?
        ): Cursor {
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
                addRow(arrayOf<Any?>(displayName))
            }
        }

        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            return ParcelFileDescriptor.open(backingFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }

        override fun getType(uri: Uri): String = "application/pdf"

        override fun insert(uri: Uri, values: ContentValues?): Uri? = null

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?
        ): Int = 0

        companion object {
            lateinit var backingFile: File
            var displayName: String? = null
        }
    }
}
