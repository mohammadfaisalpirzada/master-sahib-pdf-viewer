package com.themastersahib.pdfviewer.ui

import android.graphics.Bitmap
import android.graphics.Paint as APaint
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.barteksc.pdfviewer.PDFView
import com.themastersahib.pdfviewer.PdfRendererHelper
import com.themastersahib.pdfviewer.core.FileExport
import com.themastersahib.pdfviewer.core.NormalizedRect
import com.themastersahib.pdfviewer.core.PdfTextSearch
import com.themastersahib.pdfviewer.core.PdfTools
import com.themastersahib.pdfviewer.core.SharePrint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Pdfium-backed reader: pinch/double-tap zoom, night mode, vertical/horizontal
 * scroll, page slider, thumbnail strip, password-protected PDF support, and
 * text search with in-page word highlighting.
 */
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
    onBookmark: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var page by remember { mutableIntStateOf(initialPage) }
    var count by remember { mutableIntStateOf(0) }
    var night by remember { mutableStateOf(false) }
    var isVertical by remember { mutableStateOf(verticalScroll) }
    var showThumbs by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf<List<Int>>(emptyList()) }
    var matchRectsByPage by remember { mutableStateOf<Map<Int, List<NormalizedRect>>>(emptyMap()) }
    var matchIdx by remember { mutableIntStateOf(0) }
    var searching by remember { mutableStateOf(false) }
    var thumbs by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var pdfView by remember { mutableStateOf<PDFView?>(null) }
    var menu by remember { mutableStateOf(false) }

    // Password-protected PDF state
    var pdfPassword by remember { mutableStateOf<String?>(null) }
    var needsPassword by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }

    BackHandler { onBack() }

    fun toolResult(label: String, block: suspend () -> String) {
        scope.launch {
            val msg = try { withContext(Dispatchers.IO) { block() } } catch (e: Exception) { "Error: ${e.message}" }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    fun jump(p: Int) {
        pdfView?.jumpTo(p, true)
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
            TopAppBar(
                title = { Text(name.ifEmpty { "Viewer" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) {
                            matchRectsByPage = emptyMap()
                            matches = emptyList()
                            pdfView?.invalidate()
                        }
                    }) { Icon(Icons.Filled.Search, "Search") }
                    IconButton(onClick = { night = !night }) {
                        Icon(if (night) Icons.Filled.LightMode else Icons.Filled.DarkMode, "Night mode")
                    }
                    IconButton(onClick = { showThumbs = !showThumbs }) { Icon(Icons.Filled.GridView, "Thumbnails") }
                    IconButton(onClick = { onBookmark(page) }) { Icon(Icons.Filled.BookmarkAdd, "Bookmark") }
                    IconButton(onClick = { onAnnotate(page) }) { Icon(Icons.Filled.Edit, "Annotate") }
                    IconButton(onClick = { onSaveImage(page) }) { Icon(Icons.Filled.Image, "Save page image") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isVertical) "Switch to Horizontal Swipe" else "Switch to Vertical Scroll") },
                                onClick = {
                                    menu = false
                                    isVertical = !isVertical
                                    onToggleVerticalScroll(isVertical)
                                }
                            )
                            DropdownMenuItem(text = { Text("Share") }, onClick = {
                                menu = false; SharePrint.shareContentUri(context, uri)
                            })
                            DropdownMenuItem(text = { Text("Print") }, onClick = {
                                menu = false; SharePrint.printPdfUri(context, uri, name.ifEmpty { "document" })
                            })
                            DropdownMenuItem(text = { Text("Rotate 90° → save") }, onClick = {
                                menu = false
                                toolResult("Rotating") {
                                    val f = PdfTools.rotate(context, uri, 90)
                                    if (FileExport.savePdfToDownloads(context, f, "rotated_${System.currentTimeMillis()}.pdf") != null)
                                        "Rotated PDF saved to Downloads ▸ Master Sahib PDF" else "Save failed"
                                }
                            })
                            DropdownMenuItem(text = { Text("Split into pages") }, onClick = {
                                menu = false
                                toolResult("Splitting") {
                                    val files = PdfTools.splitEachPage(context, uri)
                                    var ok = 0
                                    files.forEachIndexed { i, f ->
                                        if (FileExport.savePdfToDownloads(context, f, "split_${i + 1}_${System.currentTimeMillis()}.pdf") != null) ok++
                                    }
                                    "$ok page PDF(s) saved to Downloads ▸ Master Sahib PDF"
                                }
                            })
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (count > 0) {
                BottomAppBar {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${page + 1}/$count")
                        Spacer(Modifier.width(12.dp))
                        Slider(
                            value = page.toFloat(),
                            onValueChange = { jump(it.toInt()) },
                            valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

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

                // Recreate the view when night mode, scroll direction, URI, or password changes.
                key(night, isVertical, uri, pdfPassword) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PDFView(ctx, null).also { v ->
                                pdfView = v
                                v.fromUri(uri)
                                    .password(pdfPassword)
                                    .defaultPage(page)
                                    .swipeHorizontal(!isVertical)
                                    .enableSwipe(true)
                                    .enableDoubletap(true)
                                    .nightMode(night)
                                    .spacing(8)
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
                                    .onLoad { nb -> count = nb; onLoaded(nb) }
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

                if (showThumbs && thumbs.isNotEmpty()) {
                    Surface(
                        tonalElevation = 3.dp,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    ) {
                        LazyRow(
                            Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(thumbs) { idx, t ->
                                Image(
                                    bitmap = t.asImageBitmap(),
                                    contentDescription = "Page ${idx + 1}",
                                    modifier = Modifier
                                        .height(140.dp)
                                        .clickable { jump(idx); showThumbs = false }
                                )
                            }
                        }
                    }
                }
            }
        }
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
