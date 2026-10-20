package com.themastersahib.pdfviewer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Paint as APaint
import android.graphics.Path as APath
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.PdfRendererHelper
import com.themastersahib.pdfviewer.core.*
import com.themastersahib.pdfviewer.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class PageLayoutMode { SINGLE, FACING, COVER_FACING }
private enum class ColorThemeMode { DAY, PAGE_COLOR, NIGHT }
private enum class InlineEditTool { NONE, AREA_CAPTURE, COMMENT, HIGHLIGHT, MARKUP, PEN, ERASER, TEXT, SHAPE }

private class InlineStroke(
    val pageIndex: Int,
    val points: MutableList<Offset>, // normalized 0..1 of overlay
    val color: Color,
    val widthPx: Float,
    val alpha: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    uri: Uri,
    name: String,
    initialPage: Int,
    verticalScroll: Boolean,
    onToggleVerticalScroll: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onLoaded: (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onAnnotate: (Int) -> Unit,
    onSaveImage: (Int) -> Unit,
    onBookmark: (Int) -> Unit,
    onOpenNewUri: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    var page by remember { mutableIntStateOf(initialPage) }
    var count by remember { mutableIntStateOf(0) }
    var night by remember { mutableStateOf(false) }
    var colorTheme by remember { mutableStateOf(ColorThemeMode.DAY) }
    var pageTintColor by remember { mutableStateOf(Color(0xFFFBF3E4)) } // Warm Eye-Comfort Sepia
    var layoutMode by remember { mutableStateOf(PageLayoutMode.SINGLE) }
    var continuousScroll by remember { mutableStateOf(true) }
    var rightToLeft by remember { mutableStateOf(false) }
    var isVertical by remember { mutableStateOf(verticalScroll) }
    var fitPolicy by remember { mutableStateOf(FitPolicy.WIDTH) }
    var cropMargins by remember { mutableStateOf(false) }

    var showTopEditBar by remember { mutableStateOf(true) }
    var activeInlineTool by remember { mutableStateOf(InlineEditTool.NONE) }
    val inlineStrokes = remember { mutableListOf<InlineStroke>() }
    var inlineTick by remember { mutableIntStateOf(0) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var shapeStart by remember { mutableStateOf<Offset?>(null) }
    var shapeEnd by remember { mutableStateOf<Offset?>(null) }

    var showThumbs by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showViewSettingsSheet by remember { mutableStateOf(false) }
    var showOutlineSheet by remember { mutableStateOf(false) }

    // Dialog states for More Sheet options
    var showProtectDialog by remember { mutableStateOf(false) }
    var showCommentsFieldsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showReduceSizeDialog by remember { mutableStateOf(false) }
    var showOcrDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showReflowDialog by remember { mutableStateOf(false) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var showInlineTextDialog by remember { mutableStateOf(false) }
    var inlineTextValue by remember { mutableStateOf("") }

    var ocrResultText by remember { mutableStateOf<String?>(null) }
    var reflowText by remember { mutableStateOf("") }
    var reflowFontSizeSp by remember { mutableFloatStateOf(17f) }
    var docInfo by remember { mutableStateOf<PdfDocumentInfoSummary?>(null) }
    var firstPageThumb by remember { mutableStateOf<Bitmap?>(null) }
    var formFields by remember { mutableStateOf<List<PdfFormFieldItem>>(emptyList()) }
    var pageComments by remember { mutableStateOf<List<PdfCommentItem>>(emptyList()) }
    var tags by remember { mutableStateOf(settings.getTagsForUri(uri.toString())) }

    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchRectsByPage by remember { mutableStateOf<Map<Int, List<NormalizedRect>>>(emptyMap()) }
    var matchIdx by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var busyWorking by remember { mutableStateOf(false) }
    var thumbs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pdfView by remember { mutableStateOf<PDFView?>(null) }

    // Password-protected PDF state
    var pdfPassword by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    BackHandler { onBack() }

    // Save As launcher
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null) {
            scope.launch {
                busyWorking = true
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { inp ->
                            context.contentResolver.openOutputStream(destUri)?.use { out ->
                                inp.copyTo(out)
                            }
                        }
                        true
                    }.getOrDefault(false)
                }
                busyWorking = false
                Toast.makeText(
                    context,
                    if (ok) "Saved copy successfully" else "Save As failed",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun toolResult(block: suspend () -> String) {
        busyWorking = true
        scope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            busyWorking = false
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun jump(p: Int) {
        val target = p.coerceIn(0, (count - 1).coerceAtLeast(0))
        page = target
        pdfView?.jumpTo(target, true)
        pdfView?.invalidate()
    }

    fun doSearch() {
        if (query.isBlank()) return
        searching = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { PdfTextSearch.search(context, uri, query, pdfPassword ?: "") }.getOrDefault(emptyList())
            }
            searching = false
            matches = found.map { it.pageIndex }
            matchRectsByPage = found.associate { it.pageIndex to it.rects }
            matchIdx = 0
            pdfView?.invalidate()
            if (matches.isEmpty()) Toast.makeText(context, "No matches", Toast.LENGTH_SHORT).show()
            else jump(matches.first())
        }
    }

    fun captureCurrentScreen() {
        val view = pdfView
        if (view != null && view.width > 0 && view.height > 0) {
            scope.launch {
                val savedUri = withContext(Dispatchers.IO) {
                    val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    val c = ACanvas(bmp)
                    view.draw(c)
                    ImageSaver.saveToGallery(context, bmp, "capture_p${page + 1}_${System.currentTimeMillis()}")
                }
                if (savedUri != null) {
                    Toast.makeText(context, "Screen capture saved to Gallery ▸ Master Sahib PDF", Toast.LENGTH_LONG).show()
                    SharePrint.shareContentUri(context, savedUri, "image/jpeg")
                } else {
                    onSaveImage(page)
                }
            }
        } else {
            onSaveImage(page)
        }
    }

    fun saveInlineStrokesToPdf() {
        val pageStrokes = inlineStrokes.filter { it.pageIndex == page }
        if (pageStrokes.isEmpty() || overlaySize.width == 0 || overlaySize.height == 0) return
        val sw = overlaySize.width.toFloat()
        val sh = overlaySize.height.toFloat()
        toolResult {
            val paths = pageStrokes.map { s ->
                val argb = s.color.toArgb()
                StrokePath(
                    points = s.points.map { PointXY(it.x * sw, it.y * sh) },
                    r = (argb shr 16) and 0xFF,
                    g = (argb shr 8) and 0xFF,
                    b = argb and 0xFF,
                    width = s.widthPx,
                    alpha = s.alpha
                )
            }
            val f = PdfTools.annotateIntoPdf(context, uri, page, paths, sw, sh)
            val saved = FileExport.savePdfToDownloads(context, f, "edited_${name.ifBlank { "doc" }}_${System.currentTimeMillis()}.pdf")
            inlineStrokes.removeAll { it.pageIndex == page }
            activeInlineTool = InlineEditTool.NONE
            if (saved != null) "Saved edited PDF to Downloads ▸ Master Sahib PDF" else "Failed to save edits"
        }
    }

    // Load document info & page 1 thumbnail when More Sheet or Info opens
    LaunchedEffect(showMoreSheet, showInfoDialog, uri) {
        if ((showMoreSheet || showInfoDialog) && docInfo == null) {
            withContext(Dispatchers.IO) {
                runCatching {
                    docInfo = PdfTools.getDocumentInfo(context, uri, name, pdfPassword ?: "")
                }
                if (firstPageThumb == null) {
                    runCatching {
                        PdfRendererHelper(context.contentResolver, uri).use { r ->
                            if (r.pageCount > 0) firstPageThumb = r.renderPage(0, 160)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(showCommentsFieldsDialog, showOutlineSheet, uri) {
        if (showCommentsFieldsDialog || showOutlineSheet) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val (f, c) = PdfTools.getFormFieldsAndComments(context, uri, pdfPassword ?: "")
                    formFields = f
                    pageComments = c
                }
            }
        }
    }

    LaunchedEffect(showThumbs) {
        if (showThumbs && thumbs.isEmpty() && count > 0) {
            thumbs = withContext(Dispatchers.IO) {
                runCatching {
                    PdfRendererHelper(context.contentResolver, uri).use { r ->
                        (0 until r.pageCount).take(300).map { r.renderPage(it, targetWidthPx = 150) }
                    }
                }.getOrDefault(emptyList())
            }
        }
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(Color(0xFF1A1B22))) {
                // Row 1: Top Header matching Image 3 (<, [pageCount], Home pill, Search, More)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    // Page count / Jump badge
                    Surface(
                        onClick = { showJumpPageDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.5.dp, Color(0xFF8E929F)),
                        color = Color.Transparent,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${if (count > 0) count else 1}",
                            color = Color(0xFFD0D4E0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Center purple "Home v" pill button (toggles Top Edit Bar)
                    Surface(
                        onClick = { showTopEditBar = !showTopEditBar },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFA855F7)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeInlineTool == InlineEditTool.NONE) "Home" else "Edit",
                                color = Color.Black,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.UnfoldMore,
                                contentDescription = "Toggle Edit Bar",
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) {
                            matchRectsByPage = emptyMap()
                            matches = emptyList()
                            pdfView?.invalidate()
                        }
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color(0xFFD0D4E0))
                    }
                    IconButton(onClick = { showMoreSheet = true }) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "More Options", tint = Color(0xFFD0D4E0))
                    }
                }

                // Row 2: Top Edit Bar matching Image 3
                if (showTopEditBar) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF22242C))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Area / Screen Capture ([ ])
                        EditBarIcon(
                            icon = Icons.Filled.CropFree,
                            selected = activeInlineTool == InlineEditTool.AREA_CAPTURE,
                            tint = Color(0xFFB8BCC8),
                            label = "Capture"
                        ) {
                            captureCurrentScreen()
                        }

                        // 2. Comment / Sticky Note (Red speech bubble)
                        EditBarIcon(
                            icon = Icons.AutoMirrored.Filled.Comment,
                            selected = activeInlineTool == InlineEditTool.COMMENT,
                            tint = Color(0xFFEF4444),
                            label = "Comment"
                        ) {
                            showCommentsFieldsDialog = true
                        }

                        // 3. Highlighter (Yellow accent)
                        EditBarIcon(
                            icon = Icons.Filled.BorderColor,
                            selected = activeInlineTool == InlineEditTool.HIGHLIGHT,
                            tint = Color(0xFFFACC15),
                            label = "Highlight"
                        ) {
                            activeInlineTool = if (activeInlineTool == InlineEditTool.HIGHLIGHT) InlineEditTool.NONE else InlineEditTool.HIGHLIGHT
                        }

                        // 4. Text Markup / Copy Page Text (TI)
                        EditBarIcon(
                            icon = Icons.Filled.FormatUnderlined,
                            selected = activeInlineTool == InlineEditTool.MARKUP,
                            tint = Color(0xFF3B82F6),
                            label = "Markup"
                        ) {
                            toolResult {
                                val t = PdfTools.extractAllText(context, uri, pdfPassword ?: "")
                                if (t.isBlank()) "No selectable text on page — use Recognize Text (OCR)"
                                else {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("PDF Text", t))
                                    "Document text copied to clipboard"
                                }
                            }
                        }

                        // 5. Pen / Freehand Draw (Red underline accent)
                        EditBarIcon(
                            icon = Icons.Filled.Edit,
                            selected = activeInlineTool == InlineEditTool.PEN,
                            tint = Color(0xFFEF4444),
                            label = "Pen"
                        ) {
                            activeInlineTool = if (activeInlineTool == InlineEditTool.PEN) InlineEditTool.NONE else InlineEditTool.PEN
                        }

                        // 6. Eraser
                        EditBarIcon(
                            icon = Icons.Filled.AutoFixNormal,
                            selected = activeInlineTool == InlineEditTool.ERASER,
                            tint = Color(0xFFB8BCC8),
                            label = "Eraser"
                        ) {
                            activeInlineTool = if (activeInlineTool == InlineEditTool.ERASER) InlineEditTool.NONE else InlineEditTool.ERASER
                        }

                        // 7. Text Stamp ('T')
                        EditBarIcon(
                            icon = Icons.Filled.Title,
                            selected = activeInlineTool == InlineEditTool.TEXT,
                            tint = Color(0xFFB8BCC8),
                            label = "Text"
                        ) {
                            inlineTextValue = ""
                            showInlineTextDialog = true
                        }

                        // 8. Shapes (Circle / Box)
                        EditBarIcon(
                            icon = Icons.Filled.RadioButtonUnchecked,
                            selected = activeInlineTool == InlineEditTool.SHAPE,
                            tint = Color(0xFFB8BCC8),
                            label = "Shape"
                        ) {
                            activeInlineTool = if (activeInlineTool == InlineEditTool.SHAPE) InlineEditTool.NONE else InlineEditTool.SHAPE
                        }

                        // 9. Undo
                        EditBarIcon(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            selected = false,
                            tint = if (inlineStrokes.isNotEmpty()) Color.White else Color(0xFF686C78),
                            label = "Undo"
                        ) {
                            if (inlineStrokes.isNotEmpty()) {
                                inlineStrokes.removeLastOrNull()
                                inlineTick++
                            } else {
                                onAnnotate(page)
                            }
                        }

                        // Save button when inline strokes exist, or Full Canvas button
                        if (inlineStrokes.any { it.pageIndex == page }) {
                            FilledTonalButton(
                                onClick = { saveInlineStrokesToPdf() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Bottom Bar matching Image 3 (5 icons: Outline, View Settings, Thumbnails, Bookmark, Share)
            Surface(color = Color(0xFF1A1B22), tonalElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showOutlineSheet = true }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Outline & Notes", tint = Color(0xFFD0D4E0))
                    }
                    IconButton(onClick = { showViewSettingsSheet = true }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "View Settings", tint = Color(0xFFD0D4E0))
                    }
                    IconButton(onClick = { showThumbs = !showThumbs }) {
                        Icon(Icons.Filled.GridView, contentDescription = "Page Thumbnails", tint = if (showThumbs) Color(0xFFA855F7) else Color(0xFFD0D4E0))
                    }
                    IconButton(onClick = { onBookmark(page) }) {
                        Icon(Icons.Filled.BookmarkBorder, contentDescription = "Bookmark Page", tint = Color(0xFFD0D4E0))
                    }
                    IconButton(onClick = { SharePrint.shareContentUri(context, uri) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share PDF", tint = Color(0xFFD0D4E0))
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            if (busyWorking) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (showSearch) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        placeholder = { Text("Search text…") },
                        singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { doSearch() }) { Icon(Icons.Filled.Search, "Go") }
                    if (matches.isNotEmpty()) {
                        Text("${matchIdx + 1}/${matches.size}")
                        IconButton(onClick = {
                            matchIdx = (matchIdx - 1 + matches.size) % matches.size; jump(matches[matchIdx])
                        }) { Icon(Icons.Filled.KeyboardArrowUp, "Prev") }
                        IconButton(onClick = {
                            matchIdx = (matchIdx + 1) % matches.size; jump(matches[matchIdx])
                        }) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
                    }
                }
                if (searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val fillPaint = remember {
                    APaint(APaint.ANTI_ALIAS_FLAG).apply {
                        style = APaint.Style.FILL
                        color = android.graphics.Color.argb(95, 255, 235, 59)
                    }
                }
                val strokePaint = remember {
                    APaint(APaint.ANTI_ALIAS_FLAG).apply {
                        style = APaint.Style.STROKE
                        strokeWidth = 2.5f
                        color = android.graphics.Color.argb(210, 255, 152, 0)
                    }
                }

                // Facing (2-page spread) mode vs Single-page Pdfium viewer
                if (layoutMode != PageLayoutMode.SINGLE && thumbs.isNotEmpty()) {
                    val leftIdx = if (layoutMode == PageLayoutMode.COVER_FACING && page == 0) 0 else (page / 2) * 2
                    val rightIdx = if (layoutMode == PageLayoutMode.COVER_FACING && page == 0) -1 else leftIdx + 1
                    Row(
                        Modifier
                            .fillMaxSize()
                            .background(if (night) Color(0xFF121212) else Color(0xFF2B2D38))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val firstIdx = if (rightToLeft && rightIdx in thumbs.indices) rightIdx else leftIdx
                        val secondIdx = if (rightToLeft && rightIdx in thumbs.indices) leftIdx else rightIdx
                        if (firstIdx in thumbs.indices) {
                            Image(
                                bitmap = thumbs[firstIdx].asImageBitmap(),
                                contentDescription = "Page ${firstIdx + 1}",
                                modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                                    if (page > 0) jump((page - 2).coerceAtLeast(0))
                                }
                            )
                        }
                        if (secondIdx in thumbs.indices) {
                            Image(
                                bitmap = thumbs[secondIdx].asImageBitmap(),
                                contentDescription = "Page ${secondIdx + 1}",
                                modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                                    if (page + 2 < count) jump(page + 2)
                                }
                            )
                        }
                    }
                } else {
                    key(night, isVertical, continuousScroll, fitPolicy, cropMargins, uri, pdfPassword) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                PDFView(ctx, null).also { v ->
                                    pdfView = v
                                    v.fromUri(uri)
                                        .password(pdfPassword)
                                        .defaultPage(page)
                                        .swipeHorizontal(!isVertical)
                                        .pageSnap(!continuousScroll)
                                        .pageFling(!continuousScroll)
                                        .autoSpacing(!continuousScroll)
                                        .pageFitPolicy(fitPolicy)
                                        .fitEachPage(true)
                                        .enableSwipe(activeInlineTool == InlineEditTool.NONE)
                                        .enableDoubletap(true)
                                        .nightMode(night)
                                        .spacing(if (cropMargins) 0 else 8)
                                        .onDraw { canvas, pageW, pageH, displayedPage ->
                                            val rects = matchRectsByPage[displayedPage]
                                            if (showSearch && !rects.isNullOrEmpty()) {
                                                for (r in rects) {
                                                    val rf = RectF(
                                                        r.left * pageW,
                                                        r.top * pageH,
                                                        r.right * pageW,
                                                        r.bottom * pageH
                                                    )
                                                    canvas.drawRoundRect(rf, 4f, 4f, fillPaint)
                                                    canvas.drawRoundRect(rf, 4f, 4f, strokePaint)
                                                }
                                            }
                                        }
                                        .onLoad { nb ->
                                            count = nb
                                            onLoaded(nb)
                                            if (cropMargins) {
                                                v.zoomWithAnimation(1.14f)
                                            }
                                        }
                                        .onPageChange { p, _ -> page = p; onPageChanged(p) }
                                        .onError { err ->
                                            val msg = err?.javaClass?.simpleName.orEmpty() + " " + err?.message.orEmpty()
                                            if (msg.contains("Password", ignoreCase = true)) {
                                                needsPassword = true
                                            } else {
                                                Toast.makeText(ctx, "Cannot open PDF: ${err?.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        .load()
                                }
                            }
                        )
                    }
                }

                // Optional Page Color tint overlay (Eye-comfort mode from View Settings)
                if (colorTheme == ColorThemeMode.PAGE_COLOR) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(pageTintColor.copy(alpha = 0.28f))
                    )
                }

                // Inline drawing overlay when Pen / Highlighter / Eraser / Shape is active in Top Edit Bar
                if (activeInlineTool in listOf(InlineEditTool.PEN, InlineEditTool.HIGHLIGHT, InlineEditTool.ERASER, InlineEditTool.SHAPE) ||
                    inlineStrokes.any { it.pageIndex == page }
                ) {
                    Canvas(
                        Modifier
                            .fillMaxSize()
                            .onSizeChanged { overlaySize = it }
                            .then(
                                if (activeInlineTool in listOf(InlineEditTool.PEN, InlineEditTool.HIGHLIGHT, InlineEditTool.ERASER, InlineEditTool.SHAPE)) {
                                    Modifier.pointerInput(activeInlineTool, page) {
                                        detectDragGestures(
                                            onDragStart = { pos ->
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                val h = size.height.toFloat().coerceAtLeast(1f)
                                                val norm = Offset(pos.x / w, pos.y / h)
                                                when (activeInlineTool) {
                                                    InlineEditTool.PEN -> {
                                                        inlineStrokes.add(
                                                            InlineStroke(page, mutableListOf(norm), Color.Red, 6f, 1f)
                                                        )
                                                    }
                                                    InlineEditTool.HIGHLIGHT -> {
                                                        inlineStrokes.add(
                                                            InlineStroke(page, mutableListOf(norm), Color.Yellow, 32f, 0.38f)
                                                        )
                                                    }
                                                    InlineEditTool.ERASER -> {
                                                        inlineStrokes.removeAll { s ->
                                                            s.pageIndex == page && s.points.any { pt ->
                                                                hypot((pt.x - norm.x) * w, (pt.y - norm.y) * h) < 40f
                                                            }
                                                        }
                                                    }
                                                    InlineEditTool.SHAPE -> {
                                                        shapeStart = norm
                                                        shapeEnd = norm
                                                    }
                                                    else -> Unit
                                                }
                                                inlineTick++
                                            },
                                            onDrag = { change, _ ->
                                                val w = size.width.toFloat().coerceAtLeast(1f)
                                                val h = size.height.toFloat().coerceAtLeast(1f)
                                                val norm = Offset(change.position.x / w, change.position.y / h)
                                                when (activeInlineTool) {
                                                    InlineEditTool.PEN, InlineEditTool.HIGHLIGHT -> {
                                                        inlineStrokes.lastOrNull()?.points?.add(norm)
                                                    }
                                                    InlineEditTool.ERASER -> {
                                                        inlineStrokes.removeAll { s ->
                                                            s.pageIndex == page && s.points.any { pt ->
                                                                hypot((pt.x - norm.x) * w, (pt.y - norm.y) * h) < 40f
                                                            }
                                                        }
                                                    }
                                                    InlineEditTool.SHAPE -> {
                                                        shapeEnd = norm
                                                    }
                                                    else -> Unit
                                                }
                                                inlineTick++
                                            },
                                            onDragEnd = {
                                                val s = shapeStart
                                                val e = shapeEnd
                                                if (activeInlineTool == InlineEditTool.SHAPE && s != null && e != null) {
                                                    val pts = mutableListOf(
                                                        Offset(s.x, s.y),
                                                        Offset(e.x, s.y),
                                                        Offset(e.x, e.y),
                                                        Offset(s.x, e.y),
                                                        Offset(s.x, s.y)
                                                    )
                                                    inlineStrokes.add(InlineStroke(page, pts, Color.Red, 5f, 1f))
                                                }
                                                shapeStart = null
                                                shapeEnd = null
                                                inlineTick++
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        @Suppress("UNUSED_EXPRESSION") inlineTick
                        val w = size.width
                        val h = size.height
                        inlineStrokes.filter { it.pageIndex == page }.forEach { s ->
                            if (s.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(s.points.first().x * w, s.points.first().y * h)
                                    s.points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
                                }
                                drawPath(
                                    path = path,
                                    color = s.color.copy(alpha = s.alpha),
                                    style = DrawStroke(width = s.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                        val ss = shapeStart
                        val se = shapeEnd
                        if (ss != null && se != null) {
                            val path = Path().apply {
                                moveTo(ss.x * w, ss.y * h)
                                lineTo(se.x * w, ss.y * h)
                                lineTo(se.x * w, se.y * h)
                                lineTo(ss.x * w, se.y * h)
                                close()
                            }
                            drawPath(path, Color.Red, style = DrawStroke(width = 5f))
                        }
                    }
                }

                // Floating "1/2" Page Pill on bottom-left matching Image 3
                if (count > 0) {
                    Surface(
                        onClick = { showJumpPageDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xAA4B4E58),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 16.dp, bottom = 14.dp)
                    ) {
                        Text(
                            text = "${page + 1}/$count",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                if (showThumbs && thumbs.isNotEmpty()) {
                    Surface(
                        tonalElevation = 4.dp,
                        color = Color(0xF21E2028),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pages ($count)", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        toolResult {
                                            val f = PdfTools.rotate(context, uri, 90, pdfPassword ?: "")
                                            if (FileExport.savePdfToDownloads(context, f, "rotated_${System.currentTimeMillis()}.pdf") != null)
                                                "Rotated PDF saved to Downloads" else "Rotate failed"
                                        }
                                    }) { Text("Rotate 90°", color = Color(0xFFD8B4FE)) }
                                    TextButton(onClick = {
                                        toolResult {
                                            val files = PdfTools.splitEachPage(context, uri, pdfPassword ?: "")
                                            var ok = 0
                                            files.forEachIndexed { i, f ->
                                                if (FileExport.savePdfToDownloads(context, f, "split_${i + 1}_${System.currentTimeMillis()}.pdf") != null) ok++
                                            }
                                            "$ok page PDF(s) saved to Downloads"
                                        }
                                    }) { Text("Split All", color = Color(0xFFD8B4FE)) }
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                itemsIndexed(thumbs) { idx, t ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Image(
                                            bitmap = t.asImageBitmap(),
                                            contentDescription = "Page ${idx + 1}",
                                            modifier = Modifier
                                                .height(130.dp)
                                                .border(
                                                    width = if (idx == page) 2.dp else 0.dp,
                                                    color = if (idx == page) Color(0xFFA855F7) else Color.Transparent
                                                )
                                                .clickable { jump(idx); showThumbs = false }
                                        )
                                        Text("${idx + 1}", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // SHEET 1: MORE OPTIONS BOTTOM SHEET (Matches Image 1)
    // =========================================================================
    if (showMoreSheet) {
        val df = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
        val sizeMb = remember(docInfo) {
            val bytes = docInfo?.fileSizeBytes ?: 0L
            if (bytes > 0) String.format(Locale.US, "%.2fMB", bytes / (1024.0 * 1024.0)) else "PDF"
        }
        val dateStr = remember(docInfo) {
            val ms = docInfo?.lastModifiedMillis ?: System.currentTimeMillis()
            df.format(Date(ms))
        }

        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = Color(0xFF20222A),
            contentColor = Color(0xFFECEEF4)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // Header: Thumbnail + Title + Date & Size
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (firstPageThumb != null) {
                        Image(
                            bitmap = firstPageThumb!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF323644),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFEF4444))
                            }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = name.removeSuffix(".pdf").ifBlank { "Document" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "PDF · $dateStr · $sizeMb",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF)
                        )
                        if (tags.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = tags.joinToString(" · ") { "#$it" },
                                fontSize = 12.sp,
                                color = Color(0xFFC084FC)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF323542))

                // Top 2 Chevron rows: Protect & Comments & Fields
                MoreSheetChevronRow("Protect") {
                    showMoreSheet = false
                    showProtectDialog = true
                }
                MoreSheetChevronRow("Comments & Fields") {
                    showMoreSheet = false
                    showCommentsFieldsDialog = true
                }

                HorizontalDivider(color = Color(0xFF323542), modifier = Modifier.padding(vertical = 4.dp))

                // Icon rows matching Image 1
                MoreSheetIconRow(Icons.Filled.SaveAs, "Save As") {
                    showMoreSheet = false
                    val cleanName = if (name.endsWith(".pdf", true)) name else "$name.pdf"
                    saveAsLauncher.launch(cleanName)
                }
                MoreSheetIconRow(Icons.Filled.DriveFileMove, "Export PDF") {
                    showMoreSheet = false
                    showExportDialog = true
                }
                MoreSheetIconRow(Icons.Filled.Compress, "Reduce File Size") {
                    showMoreSheet = false
                    showReduceSizeDialog = true
                }
                MoreSheetIconRow(Icons.Filled.DocumentScanner, "Recognize Text") {
                    showMoreSheet = false
                    showOcrDialog = true
                }
                MoreSheetIconRow(Icons.Filled.LocalOffer, "Tags") {
                    showMoreSheet = false
                    showTagsDialog = true
                }
                MoreSheetIconRow(Icons.Filled.Print, "Print") {
                    showMoreSheet = false
                    SharePrint.printPdfUri(context, uri, name.ifEmpty { "document" })
                }
                MoreSheetIconRow(Icons.Filled.LayersClear, "Flatten") {
                    showMoreSheet = false
                    toolResult {
                        val f = PdfTools.flattenPdf(context, uri, pdfPassword ?: "")
                        val saved = FileExport.savePdfToDownloads(context, f, "flattened_${name.ifBlank { "doc" }}.pdf")
                        if (saved != null) "Flattened PDF saved to Downloads ▸ Master Sahib PDF" else "Flatten failed"
                    }
                }
                MoreSheetIconRow(Icons.Filled.Crop, "Screen Capture") {
                    showMoreSheet = false
                    captureCurrentScreen()
                }
                MoreSheetIconRow(Icons.Filled.Info, "Show Info") {
                    showMoreSheet = false
                    showInfoDialog = true
                }
            }
        }
    }

    // =========================================================================
    // SHEET 2: VIEW SETTINGS BOTTOM SHEET (Matches Image 2)
    // =========================================================================
    if (showViewSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showViewSettingsSheet = false },
            containerColor = Color(0xFF20222A),
            contentColor = Color(0xFFECEEF4)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    "View Settings",
                    color = Color(0xFF9CA3AF),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Row 1: Single | Facing | Cover Facing
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LayoutModeCard(
                        title = "Single",
                        icon = Icons.Filled.Description,
                        selected = layoutMode == PageLayoutMode.SINGLE
                    ) {
                        layoutMode = PageLayoutMode.SINGLE
                    }
                    LayoutModeCard(
                        title = "Facing",
                        icon = Icons.Filled.MenuBook,
                        selected = layoutMode == PageLayoutMode.FACING
                    ) {
                        layoutMode = PageLayoutMode.FACING
                        showThumbs = true // ensure thumbs are loaded for 2-page spread
                    }
                    LayoutModeCard(
                        title = "Cover Facing",
                        icon = Icons.Filled.AutoStories,
                        selected = layoutMode == PageLayoutMode.COVER_FACING
                    ) {
                        layoutMode = PageLayoutMode.COVER_FACING
                        showThumbs = true
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Row 2: Day | Page Color | Night ("Aa" circles)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ThemeCircleItem(
                        title = "Day",
                        bgColor = Color.White,
                        textColor = Color.Black,
                        selected = colorTheme == ColorThemeMode.DAY
                    ) {
                        colorTheme = ColorThemeMode.DAY
                        night = false
                    }
                    ThemeCircleItem(
                        title = "Page Color",
                        bgColor = Color(0xFFFBF3E4),
                        textColor = Color(0xFF3B2F2F),
                        selected = colorTheme == ColorThemeMode.PAGE_COLOR
                    ) {
                        colorTheme = ColorThemeMode.PAGE_COLOR
                        night = false
                    }
                    ThemeCircleItem(
                        title = "Night",
                        bgColor = Color(0xFF2E3038),
                        textColor = Color.White,
                        selected = colorTheme == ColorThemeMode.NIGHT
                    ) {
                        colorTheme = ColorThemeMode.NIGHT
                        night = true
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Continuous Scrolling toggle
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.SwapVert, null, tint = Color(0xFFB8BCC8))
                    Spacer(Modifier.width(16.dp))
                    Text("Continuous Scrolling", Modifier.weight(1f), fontSize = 16.sp, color = Color.White)
                    Switch(
                        checked = continuousScroll,
                        onCheckedChange = {
                            continuousScroll = it
                            isVertical = it
                            onToggleVerticalScroll(it)
                        }
                    )
                }

                // Right to Left toggle
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FormatTextdirectionRToL, null, tint = Color(0xFFB8BCC8))
                    Spacer(Modifier.width(16.dp))
                    Text("Right to Left", Modifier.weight(1f), fontSize = 16.sp, color = Color.White)
                    Switch(
                        checked = rightToLeft,
                        onCheckedChange = { rightToLeft = it }
                    )
                }

                Spacer(Modifier.height(4.dp))

                MoreSheetIconRow(Icons.Filled.FitScreen, "Fit Page", horizontalPad = 0.dp) {
                    fitPolicy = FitPolicy.BOTH
                    showViewSettingsSheet = false
                }
                MoreSheetIconRow(Icons.Filled.SettingsOverscan, "Fit Width", horizontalPad = 0.dp) {
                    fitPolicy = FitPolicy.WIDTH
                    showViewSettingsSheet = false
                }
                MoreSheetIconRow(Icons.Filled.TextFields, "Reflow", horizontalPad = 0.dp) {
                    showViewSettingsSheet = false
                    busyWorking = true
                    scope.launch {
                        reflowText = withContext(Dispatchers.IO) {
                            runCatching { PdfTools.extractAllText(context, uri, pdfPassword ?: "") }.getOrDefault("")
                        }
                        busyWorking = false
                        showReflowDialog = true
                    }
                }
                MoreSheetIconRow(
                    Icons.Filled.Crop,
                    if (cropMargins) "Crop Mode (Active — Tap to Reset)" else "Crop Mode",
                    horizontalPad = 0.dp
                ) {
                    cropMargins = !cropMargins
                    showViewSettingsSheet = false
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS FOR ALL MORE SHEET & VIEW SETTINGS ACTIONS
    // =========================================================================

    // 1. Protect PDF Dialog
    if (showProtectDialog) {
        var newPass by remember { mutableStateOf("") }
        var confirmPass by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showProtectDialog = false },
            title = { Text("Protect PDF with Password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set a password required to open this PDF document:")
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                    OutlinedTextField(
                        value = confirmPass,
                        onValueChange = { confirmPass = it },
                        label = { Text("Confirm Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newPass.isBlank() || newPass != confirmPass) {
                        Toast.makeText(context, "Passwords must match and not be empty", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val p = newPass
                    showProtectDialog = false
                    toolResult {
                        val f = PdfTools.protectPdf(context, uri, p, pdfPassword ?: "")
                        val saved = FileExport.savePdfToDownloads(context, f, "protected_${name.ifBlank { "doc" }}.pdf")
                        if (saved != null) "Protected PDF saved to Downloads ▸ Master Sahib PDF" else "Protection failed"
                    }
                }) { Text("Protect & Save") }
            },
            dismissButton = {
                if (!pdfPassword.isNullOrEmpty()) {
                    TextButton(onClick = {
                        showProtectDialog = false
                        toolResult {
                            val f = PdfTools.unprotectPdf(context, uri, pdfPassword ?: "")
                            val saved = FileExport.savePdfToDownloads(context, f, "unlocked_${name.ifBlank { "doc" }}.pdf")
                            if (saved != null) "Unlocked PDF saved to Downloads ▸ Master Sahib PDF" else "Unlock failed"
                        }
                    }) { Text("Remove Password") }
                } else {
                    TextButton(onClick = { showProtectDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    // 2. Comments & Fields Dialog
    if (showCommentsFieldsDialog || showOutlineSheet) {
        val fieldEdits = remember(formFields) {
            mutableStateMapOf<String, String>().apply {
                formFields.forEach { put(it.fullyQualifiedName, it.value) }
            }
        }
        var newComment by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCommentsFieldsDialog = false; showOutlineSheet = false },
            title = { Text("Comments & Form Fields (Page ${page + 1})") },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newComment,
                        onValueChange = { newComment = it },
                        label = { Text("Add comment/note on Page ${page + 1}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pageComments.isNotEmpty()) {
                        Text("Existing Comments:", fontWeight = FontWeight.SemiBold)
                        pageComments.forEach { c ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                tonalElevation = 2.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showCommentsFieldsDialog = false
                                        showOutlineSheet = false
                                        jump(c.pageIndex)
                                    }
                            ) {
                                Text(
                                    "Page ${c.pageIndex + 1}: ${c.text}",
                                    modifier = Modifier.padding(10.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    if (formFields.isNotEmpty()) {
                        Text("Interactive PDF Form Fields:", fontWeight = FontWeight.SemiBold)
                        formFields.forEach { f ->
                            OutlinedTextField(
                                value = fieldEdits[f.fullyQualifiedName] ?: "",
                                onValueChange = { fieldEdits[f.fullyQualifiedName] = it },
                                label = { Text(f.fullyQualifiedName) },
                                enabled = !f.isReadOnly,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else if (pageComments.isEmpty()) {
                        Text(
                            "No interactive AcroForm fields found in this PDF. You can add a comment/note above to stamp it onto Page ${page + 1}.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val commentToSave = newComment
                    val editsToSave = fieldEdits.toMap()
                    showCommentsFieldsDialog = false
                    showOutlineSheet = false
                    if (commentToSave.isNotBlank() || editsToSave.isNotEmpty()) {
                        toolResult {
                            val f = PdfTools.saveFormFieldsAndComment(
                                context, uri, editsToSave, page, commentToSave, pdfPassword ?: ""
                            )
                            val saved = FileExport.savePdfToDownloads(context, f, "updated_${name.ifBlank { "doc" }}.pdf")
                            if (saved != null) "Saved updated PDF to Downloads ▸ Master Sahib PDF" else "Save failed"
                        }
                    }
                }) { Text("Save to PDF") }
            },
            dismissButton = {
                TextButton(onClick = { showCommentsFieldsDialog = false; showOutlineSheet = false }) { Text("Close") }
            }
        )
    }

    // 3. Export PDF Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Choose export format:")
                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            onSaveImage(page)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Current Page (${page + 1}) as High-Res JPEG") }

                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            toolResult {
                                var ok = 0
                                PdfRendererHelper(context.contentResolver, uri).use { r ->
                                    for (i in 0 until r.pageCount) {
                                        val bmp = r.renderPage(i, 2000)
                                        if (ImageSaver.saveToGallery(context, bmp, "${name.removeSuffix(".pdf")}_p${i + 1}") != null) ok++
                                        bmp.recycle()
                                    }
                                }
                                "Exported $ok page(s) to Gallery ▸ Master Sahib PDF"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("All Pages ($count) as Images to Gallery") }

                    OutlinedButton(
                        onClick = {
                            showExportDialog = false
                            toolResult {
                                val text = PdfTools.extractAllText(context, uri, pdfPassword ?: "")
                                if (text.isBlank()) "No embedded text found — use Recognize Text (OCR) instead."
                                else {
                                    val saved = FileExport.saveTextToDownloads(context, text, "${name.removeSuffix(".pdf")}.txt")
                                    if (saved != null) "Exported text to Downloads ▸ Master Sahib PDF" else "Text export failed"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Plain Text (.txt) to Downloads") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 4. Reduce File Size Dialog
    if (showReduceSizeDialog) {
        AlertDialog(
            onDismissRequest = { showReduceSizeDialog = false },
            title = { Text("Reduce File Size") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select compression level:")
                    CompressionPreset.entries.forEach { preset ->
                        OutlinedButton(
                            onClick = {
                                showReduceSizeDialog = false
                                toolResult {
                                    val origSize = docInfo?.fileSizeBytes ?: 0L
                                    val compressed = PdfTools.reduceFileSize(context, uri, preset)
                                    val newSize = compressed.length()
                                    val saved = FileExport.savePdfToDownloads(
                                        context, compressed, "compressed_${name.removeSuffix(".pdf")}.pdf"
                                    )
                                    val origKb = (origSize / 1024).coerceAtLeast(1)
                                    val newKb = (newSize / 1024).coerceAtLeast(1)
                                    if (saved != null) "Compressed (${origKb} KB → ${newKb} KB) saved to Downloads ▸ Master Sahib PDF"
                                    else "Compression save failed"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(preset.label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReduceSizeDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 5. Recognize Text (OCR) Dialog
    if (showOcrDialog) {
        AlertDialog(
            onDismissRequest = { showOcrDialog = false },
            title = { Text("Recognize Text (OCR)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Run optical character recognition on Page ${page + 1}:")
                    Button(
                        onClick = {
                            showOcrDialog = false
                            busyWorking = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    PdfRendererHelper(context.contentResolver, uri).use { r ->
                                        val bmp = r.renderPage(page, 2000)
                                        Ocr.recognize(bmp)
                                    }
                                }
                                busyWorking = false
                                ocrResultText = res.ifBlank { "No text detected on this page." }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("English / Latin OCR (Page ${page + 1})") }

                    Button(
                        onClick = {
                            showOcrDialog = false
                            busyWorking = true
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    runCatching {
                                        PdfRendererHelper(context.contentResolver, uri).use { r ->
                                            val bmp = r.renderPage(page, 2000)
                                            UrduOcr.recognize(context, bmp)
                                        }
                                    }.getOrElse { "Error: ${it.message}" }
                                }
                                busyWorking = false
                                ocrResultText = res.ifBlank { "No Urdu text detected on this page." }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Urdu / Nastaliq + English OCR (Page ${page + 1})") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showOcrDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (ocrResultText != null) {
        AlertDialog(
            onDismissRequest = { ocrResultText = null },
            title = { Text("Recognized Text (Page ${page + 1})") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(ocrResultText!!)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val txt = ocrResultText ?: ""
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("OCR", txt))
                    scope.launch(Dispatchers.IO) {
                        FileExport.saveTextToDownloads(context, txt, "ocr_p${page + 1}_${System.currentTimeMillis()}.txt")
                    }
                    Toast.makeText(context, "Copied & saved to Downloads ▸ Master Sahib PDF", Toast.LENGTH_LONG).show()
                    ocrResultText = null
                }) { Text("Copy & Save .txt") }
            },
            dismissButton = {
                TextButton(onClick = { ocrResultText = null }) { Text("Close") }
            }
        )
    }

    // 6. Tags Dialog
    if (showTagsDialog) {
        val presetTags = listOf("Important", "Bill", "CNIC", "Official", "Study", "Tax", "Receipt")
        var customTag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTagsDialog = false },
            title = { Text("Document Tags") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tap tags to assign or remove:")
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetTags.forEach { t ->
                            val active = tags.contains(t)
                            FilterChip(
                                selected = active,
                                onClick = {
                                    tags = if (active) tags - t else tags + t
                                    settings.setTagsForUri(uri.toString(), tags)
                                },
                                label = { Text(t) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = customTag,
                        onValueChange = { customTag = it },
                        label = { Text("Add custom tag") },
                        singleLine = true,
                        trailingIcon = {
                            if (customTag.isNotBlank()) {
                                IconButton(onClick = {
                                    tags = tags + customTag.trim()
                                    settings.setTagsForUri(uri.toString(), tags)
                                    customTag = ""
                                }) { Icon(Icons.Filled.Add, "Add") }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagsDialog = false }) { Text("Done") }
            }
        )
    }

    // 7. Show Info Dialog
    if (showInfoDialog) {
        val info = docInfo
        val dfFull = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("Document Information") },
            text = {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoItem("File Name", info?.fileName ?: name)
                    InfoItem(
                        "File Size",
                        info?.fileSizeBytes?.let { b ->
                            String.format(Locale.US, "%.2f MB (%,d bytes)", b / (1024.0 * 1024.0), b)
                        } ?: "-"
                    )
                    InfoItem("Modified", info?.lastModifiedMillis?.let { dfFull.format(Date(it)) } ?: "-")
                    InfoItem("Pages", "${if (count > 0) count else info?.pageCount ?: 0}")
                    InfoItem("Page Size", info?.pageSizeMm ?: "-")
                    InfoItem("Encrypted", if (info?.encrypted == true) "Yes" else "No")
                    InfoItem("Title", info?.title ?: "-")
                    InfoItem("Author", info?.author ?: "-")
                    InfoItem("Subject", info?.subject ?: "-")
                    InfoItem("Creator", info?.creator ?: "-")
                    InfoItem("Producer", info?.producer ?: "-")
                    InfoItem("Created", info?.creationDate ?: "-")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }

    // 8. Reflow Reading Mode Dialog
    if (showReflowDialog) {
        AlertDialog(
            onDismissRequest = { showReflowDialog = false },
            title = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reflow Reader")
                    Row {
                        IconButton(onClick = { reflowFontSizeSp = (reflowFontSizeSp - 2f).coerceAtLeast(12f) }) {
                            Icon(Icons.Filled.TextDecrease, "Smaller")
                        }
                        IconButton(onClick = { reflowFontSizeSp = (reflowFontSizeSp + 2f).coerceAtMost(32f) }) {
                            Icon(Icons.Filled.TextIncrease, "Larger")
                        }
                    }
                }
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        text = reflowText.ifBlank {
                            "This PDF contains scanned images without an embedded text layer. Use 'Recognize Text' from the More (•••) menu to run OCR."
                        },
                        fontSize = reflowFontSizeSp.sp,
                        lineHeight = (reflowFontSizeSp * 1.45f).sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showReflowDialog = false }) { Text("Done") }
            }
        )
    }

    // 9. Jump to Page Dialog
    if (showJumpPageDialog) {
        var pageInput by remember { mutableStateOf("${page + 1}") }
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text("Jump to Page (1–$count)") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Page Number") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val p = (pageInput.toIntOrNull() ?: 1) - 1
                    showJumpPageDialog = false
                    jump(p)
                }) { Text("Go") }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 10. Inline Text Stamp ('T') Dialog
    if (showInlineTextDialog) {
        AlertDialog(
            onDismissRequest = { showInlineTextDialog = false },
            title = { Text("Add Text to Page ${page + 1}") },
            text = {
                OutlinedTextField(
                    value = inlineTextValue,
                    onValueChange = { inlineTextValue = it },
                    label = { Text("Text to stamp on page") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val txt = inlineTextValue.trim()
                    showInlineTextDialog = false
                    if (txt.isNotEmpty()) {
                        toolResult {
                            val stamp = TextStamp(txt, 0.15f, 0.15f, 220, 38, 38, 16f)
                            val f = PdfTools.annotateIntoPdf(context, uri, page, emptyList(), 1000f, 1400f, listOf(stamp))
                            val saved = FileExport.savePdfToDownloads(context, f, "text_${name.removeSuffix(".pdf")}.pdf")
                            if (saved != null) "Stamped text & saved PDF to Downloads ▸ Master Sahib PDF" else "Save failed"
                        }
                    }
                }) { Text("Stamp & Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showInlineTextDialog = false
                    onAnnotate(page)
                }) { Text("Open Full Canvas") }
            }
        )
    }

    if (needsPassword) {
        AlertDialog(
            onDismissRequest = { needsPassword = false; onBack() },
            title = { Text("Password-protected PDF") },
            text = {
                Column {
                    Text("Enter the password to open this PDF:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    needsPassword = false
                    pdfPassword = passwordInput
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { needsPassword = false; onBack() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditBarIcon(
    icon: ImageVector,
    selected: Boolean,
    tint: Color,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFF3B3E4D) else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, Color(0xFFA855F7)) else null
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MoreSheetChevronRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, color = Color.White)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color(0xFF9CA3AF))
    }
}

@Composable
private fun MoreSheetIconRow(
    icon: ImageVector,
    title: String,
    horizontalPad: androidx.compose.ui.unit.Dp = 20.dp,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = horizontalPad, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFFB8BCC8), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, fontSize = 16.sp, color = Color.White)
    }
}

@Composable
private fun LayoutModeCard(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF282B36),
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFFA855F7) else Color(0xFF3E4251)
            ),
            modifier = Modifier.size(66.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = if (selected) Color(0xFFA855F7) else Color(0xFFB8BCC8),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun ThemeCircleItem(
    title: String,
    bgColor: Color,
    textColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            border = BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) Color(0xFFA855F7) else Color(0xFF4B5563)
            ),
            modifier = Modifier.size(60.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Aa", color = textColor, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 13.sp, color = Color.White)
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.62f))
    }
}
