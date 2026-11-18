package com.mcalonzo.personalfinanceos.ui.components

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTypography
import com.mcalonzo.personalfinanceos.ui.theme.NumericTextStyles
import com.mcalonzo.personalfinanceos.ui.theme.FinanceTheme
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val MAX_BITMAP_WIDTH_PX = 1440
private const val MAX_BITMAP_HEIGHT_PX = 2560
private const val MAX_CACHED_PAGES = 8
private const val MIN_ZOOM_SCALE = 1f
private const val MAX_ZOOM_SCALE = 4f
private const val DEFAULT_PAGE_ASPECT_RATIO = 8.5f / 11f

private sealed interface PdfViewerState {
    data object Empty : PdfViewerState
    data object Loading : PdfViewerState
    data class Loaded(val pageCount: Int) : PdfViewerState
    data class Error(val message: String) : PdfViewerState
}

/**
 * Thread-safe wrapper around a single [ParcelFileDescriptor] and [PdfRenderer] instance.
 * Ensures only one page is opened/rendered at a time and resources close cleanly even
 * when sheet dismissal races with an active IO render.
 */
private class PdfDocumentSession(private val file: File) {
    private val mutex = Mutex()
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    @Volatile
    private var disposed = false

    suspend fun open(): Result<Int> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (disposed) {
                return@withLock Result.failure(IllegalStateException("PDF session disposed"))
            }
            closeInternalLocked()

            var descriptor: ParcelFileDescriptor? = null
            var pdfRenderer: PdfRenderer? = null
            try {
                if (!file.exists() || !file.isFile || !file.canRead() || file.length() <= 0L) {
                    return@withLock Result.failure(IOException("SOA PDF file is missing or unreadable"))
                }
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(descriptor)
                val count = pdfRenderer.pageCount
                if (count <= 0) {
                    runCatching { pdfRenderer.close() }
                    runCatching { descriptor.close() }
                    Result.failure(IOException("SOA PDF has no pages"))
                } else {
                    pfd = descriptor
                    renderer = pdfRenderer
                    Result.success(count)
                }
            } catch (t: Throwable) {
                runCatching { pdfRenderer?.close() }
                runCatching { descriptor?.close() }
                Result.failure(t)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (disposed) return@withLock null
            val activeRenderer = renderer ?: return@withLock null
            if (pageIndex !in 0 until activeRenderer.pageCount) return@withLock null

            var page: PdfRenderer.Page? = null
            try {
                page = activeRenderer.openPage(pageIndex)
                val pageWidth = page.width.coerceAtLeast(1)
                val pageHeight = page.height.coerceAtLeast(1)
                val scale = minOf(
                    2f,
                    MAX_BITMAP_WIDTH_PX.toFloat() / pageWidth,
                    MAX_BITMAP_HEIGHT_PX.toFloat() / pageHeight
                )
                val targetWidth = (pageWidth * scale).roundToInt().coerceIn(1, MAX_BITMAP_WIDTH_PX)
                val targetHeight = (pageHeight * scale).roundToInt().coerceIn(1, MAX_BITMAP_HEIGHT_PX)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { page?.close() }
            }
        }
    }

    fun dispose() {
        disposed = true
        if (mutex.tryLock()) {
            try {
                closeInternalLocked()
            } finally {
                mutex.unlock()
            }
        } else {
            disposeCleanupScope.launch {
                mutex.withLock {
                    closeInternalLocked()
                }
            }
        }
    }

    private fun closeInternalLocked() {
        runCatching { renderer?.close() }
        renderer = null
        runCatching { pfd?.close() }
        pfd = null
    }

    companion object {
        private val disposeCleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * Modal bottom sheet displaying an attached SOA PDF via Android's native [PdfRenderer],
 * supporting pinch-to-zoom, page scrolling, and safe empty/corrupt fallback actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerSheet(
    localPath: String?,
    fileName: String?,
    onUploadOrReplaceClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasAttachment = !localPath.isNullOrBlank()
    val ctaLabel = if (hasAttachment) "Replace File" else "Upload SOA PDF"
    val displayTitle = fileName?.takeIf { it.isNotBlank() }
        ?: localPath?.takeIf { it.isNotBlank() }?.let { File(it).name }
        ?: "Statement of Account"

    val session = remember(localPath) {
        localPath?.takeIf { it.isNotBlank() }?.let { PdfDocumentSession(File(it)) }
    }
    var viewerState by remember(localPath) {
        mutableStateOf<PdfViewerState>(
            if (session == null) PdfViewerState.Empty else PdfViewerState.Loading
        )
    }
    val renderedPages = remember(localPath) { mutableStateMapOf<Int, Bitmap>() }
    val failedPages = remember(localPath) { mutableStateMapOf<Int, Boolean>() }

    DisposableEffect(session) {
        onDispose {
            session?.dispose()
            renderedPages.clear()
            failedPages.clear()
        }
    }

    LaunchedEffect(session) {
        if (session == null) {
            viewerState = PdfViewerState.Empty
            return@LaunchedEffect
        }
        viewerState = PdfViewerState.Loading
        val result = session.open()
        viewerState = result.fold(
            onSuccess = { count -> PdfViewerState.Loaded(count) },
            onFailure = {
                PdfViewerState.Error(
                    "Unable to open this SOA PDF. The file may be missing, corrupted, or password-protected."
                )
            }
        )
    }

    val listState = rememberLazyListState()
    val totalPages = (viewerState as? PdfViewerState.Loaded)?.pageCount ?: 0
    val currentPage by remember(totalPages, listState) {
        derivedStateOf {
            if (totalPages <= 0) {
                0
            } else {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                if (visibleItems.isEmpty()) {
                    (listState.firstVisibleItemIndex + 1).coerceIn(1, totalPages)
                } else {
                    val viewportCenter =
                        (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                    val centered = visibleItems.minByOrNull { item ->
                        abs((item.offset + item.size / 2) - viewportCenter)
                    }
                    ((centered?.index ?: listState.firstVisibleItemIndex) + 1)
                        .coerceIn(1, totalPages)
                }
            }
        }
    }

    val isExpandedLayout = viewerState is PdfViewerState.Loading || viewerState is PdfViewerState.Loaded

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FinanceTheme.colors.bgCard,
        contentColor = FinanceTheme.colors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isExpandedLayout) Modifier.fillMaxHeight(0.92f) else Modifier)
                .navigationBarsPadding()
        ) {
            PdfViewerHeader(
                title = displayTitle,
                subtitle = when (val state = viewerState) {
                    is PdfViewerState.Empty -> "No SOA PDF attached"
                    is PdfViewerState.Loading -> "Opening PDF..."
                    is PdfViewerState.Loaded -> "Page $currentPage of ${state.pageCount}"
                    is PdfViewerState.Error -> "File unavailable"
                },
                useNumericSubtitle = viewerState is PdfViewerState.Loaded,
                ctaLabel = ctaLabel,
                onUploadOrReplaceClick = onUploadOrReplaceClick,
                onDismiss = onDismiss
            )

            HorizontalDivider(color = FinanceTheme.colors.borderSubtle)

            when (val state = viewerState) {
                is PdfViewerState.Empty -> {
                    PdfStatusCard(
                        icon = Icons.Rounded.PictureAsPdf,
                        iconTint = FinanceTheme.colors.accentSky,
                        title = "No SOA PDF attached yet",
                        message = "Upload a credit card or billing statement PDF to view pages offline.",
                        ctaLabel = "Upload SOA PDF",
                        onCtaClick = onUploadOrReplaceClick
                    )
                }

                is PdfViewerState.Error -> {
                    PdfStatusCard(
                        icon = Icons.Rounded.ErrorOutline,
                        iconTint = FinanceTheme.colors.accentRose,
                        title = "Could not load SOA PDF",
                        message = state.message,
                        ctaLabel = ctaLabel,
                        onCtaClick = onUploadOrReplaceClick
                    )
                }

                is PdfViewerState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = FinanceTheme.colors.accentSky)
                            Text(
                                text = "Rendering statement pages...",
                                style = FinanceTypography.bodyMedium,
                                color = FinanceTheme.colors.textSecondary
                            )
                        }
                    }
                }

                is PdfViewerState.Loaded -> {
                    PdfPagesViewport(
                        localPath = localPath,
                        session = session,
                        pageCount = state.pageCount,
                        currentPage = currentPage,
                        listState = listState,
                        renderedPages = renderedPages,
                        failedPages = failedPages,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfViewerHeader(
    title: String,
    subtitle: String,
    useNumericSubtitle: Boolean,
    ctaLabel: String,
    onUploadOrReplaceClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = FinanceTypography.titleLarge,
                color = FinanceTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = if (useNumericSubtitle) {
                    NumericTextStyles.bodyAmount
                } else {
                    FinanceTypography.bodyMedium
                },
                color = FinanceTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onUploadOrReplaceClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = FinanceTheme.colors.accentSky,
                contentColor = FinanceTheme.colors.bgBase
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Text(
                text = ctaLabel,
                style = FinanceTypography.labelLarge
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close PDF viewer",
                tint = FinanceTheme.colors.textPrimary
            )
        }
    }
}

@Composable
private fun PdfStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    message: String,
    ctaLabel: String,
    onCtaClick: () -> Unit
) {
    Surface(
        color = FinanceTheme.colors.bgCardSubtle,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = title,
                style = FinanceTypography.titleMedium,
                color = FinanceTheme.colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = FinanceTypography.bodyMedium,
                color = FinanceTheme.colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onCtaClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FinanceTheme.colors.accentSky,
                    contentColor = FinanceTheme.colors.bgBase
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ctaLabel,
                    style = FinanceTypography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun PdfPagesViewport(
    localPath: String?,
    session: PdfDocumentSession?,
    pageCount: Int,
    currentPage: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    renderedPages: MutableMap<Int, Bitmap>,
    failedPages: MutableMap<Int, Boolean>,
    modifier: Modifier = Modifier
) {
    var scale by remember(localPath) { mutableFloatStateOf(MIN_ZOOM_SCALE) }
    var offset by remember(localPath) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var isMultiTouch by remember(localPath) { mutableStateOf(false) }
    val activePages = remember(localPath) { mutableSetOf<Int>() }

    Box(
        modifier = modifier
            .background(FinanceTheme.colors.bgBase)
            .clipToBounds()
            .onSizeChanged { viewportSize = it }
            .pointerInput(localPath) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        isMultiTouch = event.changes.count { it.pressed } >= 2
                    }
                }
            }
            .pointerInput(localPath) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(MIN_ZOOM_SCALE, MAX_ZOOM_SCALE)
                    if (nextScale <= 1.01f) {
                        scale = MIN_ZOOM_SCALE
                        offset = Offset.Zero
                    } else {
                        val maxOffsetX =
                            ((viewportSize.width * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                        val maxOffsetY =
                            ((viewportSize.height * (nextScale - 1f)) / 2f).coerceAtLeast(0f)
                        val nextOffset = offset + pan
                        scale = nextScale
                        offset = Offset(
                            x = nextOffset.x.coerceIn(-maxOffsetX, maxOffsetX),
                            y = nextOffset.y.coerceIn(-maxOffsetY, maxOffsetY)
                        )
                    }
                }
            }
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = scale <= 1.01f && !isMultiTouch,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        ) {
            items(count = pageCount, key = { it }) { pageIndex ->
                val bitmap = renderedPages[pageIndex]
                val hasFailed = failedPages[pageIndex] == true
                val needsRender = bitmap == null && !hasFailed

                DisposableEffect(session, pageIndex) {
                    activePages.add(pageIndex)
                    onDispose { activePages.remove(pageIndex) }
                }

                LaunchedEffect(session, pageIndex, needsRender) {
                    if (session != null && needsRender) {
                        val rendered = session.renderPage(pageIndex)
                        if (rendered != null) {
                            val visibleIndices = listState.layoutInfo.visibleItemsInfo
                                .mapTo(HashSet()) { it.index }
                            while (renderedPages.size >= MAX_CACHED_PAGES) {
                                val farthestOffscreenKey = renderedPages.keys
                                    .asSequence()
                                    .filter {
                                        it != pageIndex &&
                                            it !in visibleIndices &&
                                            it !in activePages
                                    }
                                    .maxByOrNull { abs(it - pageIndex) }
                                    ?: break
                                renderedPages.remove(farthestOffscreenKey)
                            }
                            renderedPages[pageIndex] = rendered
                        } else {
                            failedPages[pageIndex] = true
                        }
                    }
                }

                PdfPageCard(
                    pageNumber = pageIndex + 1,
                    pageCount = pageCount,
                    bitmap = bitmap,
                    hasFailed = hasFailed
                )
            }
        }

        Surface(
            color = FinanceTheme.colors.bgCardElevated.copy(alpha = 0.94f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Page $currentPage of $pageCount",
                style = NumericTextStyles.bodyAmount,
                color = FinanceTheme.colors.textPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun PdfPageCard(
    pageNumber: Int,
    pageCount: Int,
    bitmap: Bitmap?,
    hasFailed: Boolean
) {
    Surface(
        color = FinanceTheme.colors.bgCardSubtle,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, FinanceTheme.colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                when {
                    bitmap != null -> {
                        val ratio = (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                            .coerceAtLeast(0.25f)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "SOA page $pageNumber of $pageCount",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(ratio)
                        )
                    }

                    hasFailed -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(DEFAULT_PAGE_ASPECT_RATIO)
                                .background(FinanceTheme.colors.bgCardElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Unable to render page $pageNumber.",
                                style = FinanceTypography.bodyMedium,
                                color = FinanceTheme.colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(DEFAULT_PAGE_ASPECT_RATIO),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = FinanceTheme.colors.accentSky,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
