package com.themastersahib.pdfviewer.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.print.PrintHelper
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val ScanAccentPurple = Color(0xFF5E5CE6)

/**
 * Review screen shown AFTER a scan:
 * - All Filter & Paper/DPI options are shown in a compact 2-row div (no horizontal scroll needed).
 * - Scanned page thumbnails in the middle with clean header bar & bottom controls (no text covering the scan).
 * - All 8 Export/Action tools shown in a compact 2x4 bottom div card so every option is visible at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanExportScreen(
    rawPages: List<Bitmap>,
    onBack: () -> Unit,
    onAddPages: () -> Unit = {},
    onRotatePage: (Int) -> Unit = {},
    onDeletePage: (Int) -> Unit = {},
    onMovePage: (Int, Int) -> Unit = { _, _ -> },
    initialFilter: FilterType = FilterType.NONE
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bulkFilter by remember(initialFilter) { mutableStateOf(initialFilter) }
    var perPageOverrides by remember { mutableStateOf<Map<Int, FilterType>>(emptyMap()) }
    var selectedPaper by remember { mutableStateOf(PaperSize.A4) }
    var selectedDpi by remember { mutableIntStateOf(200) }

    val pages by remember(bulkFilter, perPageOverrides, rawPages) {
        mutableStateOf(
            rawPages.mapIndexed { idx, bmp ->
                val f = perPageOverrides[idx] ?: bulkFilter
                ImageFilters.apply(bmp, f)
            }
        )
    }
    var busy by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun run(block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            busy = false
            toast(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Scan · ${pages.size} page(s)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(enabled = !busy, onClick = onAddPages) {
                        Icon(Icons.Filled.AddAPhoto, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Page", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // --- 1. COMPACT FILTER & PAPER SETTINGS DIV (All visible without scrolling) ---
            Surface(
                tonalElevation = 2.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Filters (4 equal columns)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactPillButton(
                            label = "Original",
                            selected = bulkFilter == FilterType.NONE,
                            modifier = Modifier.weight(1f)
                        ) {
                            bulkFilter = FilterType.NONE
                            perPageOverrides = emptyMap()
                        }
                        CompactPillButton(
                            label = "Grayscale",
                            selected = bulkFilter == FilterType.GRAYSCALE,
                            modifier = Modifier.weight(1f)
                        ) {
                            bulkFilter = FilterType.GRAYSCALE
                            perPageOverrides = emptyMap()
                        }
                        CompactPillButton(
                            label = "B&W Print",
                            selected = bulkFilter == FilterType.BW_PRINT,
                            modifier = Modifier.weight(1f)
                        ) {
                            bulkFilter = FilterType.BW_PRINT
                            perPageOverrides = emptyMap()
                        }
                        CompactPillButton(
                            label = "Magic",
                            selected = bulkFilter == FilterType.MAGIC,
                            modifier = Modifier.weight(1f)
                        ) {
                            bulkFilter = FilterType.MAGIC
                            perPageOverrides = emptyMap()
                        }
                    }

                    // Row 2: Paper Size & DPI (4 equal columns)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactPillButton(
                            label = "Paper: A4",
                            selected = selectedPaper == PaperSize.A4,
                            modifier = Modifier.weight(1f)
                        ) { selectedPaper = PaperSize.A4 }

                        CompactPillButton(
                            label = "Letter",
                            selected = selectedPaper == PaperSize.LETTER,
                            modifier = Modifier.weight(1f)
                        ) { selectedPaper = PaperSize.LETTER }

                        CompactPillButton(
                            label = "Legal",
                            selected = selectedPaper == PaperSize.LEGAL,
                            modifier = Modifier.weight(1f)
                        ) { selectedPaper = PaperSize.LEGAL }

                        CompactPillButton(
                            label = if (selectedDpi == 300) "300 DPI HQ" else "200 DPI",
                            selected = selectedDpi == 300,
                            modifier = Modifier.weight(1f)
                        ) { selectedDpi = if (selectedDpi == 300) 200 else 300 }
                    }
                }
            }

            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            // --- 2. SCANNED PAGE THUMBNAILS (Clean cards, no giant text overlapping image) ---
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pages) { idx, b ->
                    val currentPageFilter = perPageOverrides[idx] ?: bulkFilter
                    ElevatedCard(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            // Clean header bar above image (tap to cycle per-page filter)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val all = FilterType.entries
                                        val next = all[(currentPageFilter.ordinal + 1) % all.size]
                                        perPageOverrides = perPageOverrides.toMutableMap().apply { put(idx, next) }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Page #${idx + 1}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = currentPageFilter.name,
                                    fontSize = 10.sp,
                                    color = ScanAccentPurple,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            // Scanned Bitmap preview
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp, max = 180.dp)
                                    .background(Color.White)
                            )

                            // Compact bottom toolbar: Move Left, Move Right, Rotate, Delete
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (idx > 0) onMovePage(idx, idx - 1) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowBackIosNew, "Move earlier", modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { if (idx < pages.lastIndex) onMovePage(idx, idx + 1) },
                                    enabled = idx < pages.lastIndex,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.ArrowForwardIos, "Move later", modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { onRotatePage(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.RotateRight, "Rotate", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onDeletePage(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. BOTTOM ACTION DIV (All 8 tools visible at once in a 2x4 grid, no scrolling!) ---
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 4.dp,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Export & Photostat Actions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    // Row 1: Make PDF, CNIC 2x5 Mode, Print Sheet, Save Images
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactActionTile(
                            title = "Make PDF",
                            icon = Icons.Filled.PictureAsPdf,
                            color = Color(0xFFE53935),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            run {
                                val out = File(context.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
                                PdfBuilder.build(pages, out, paper = selectedPaper, dpi = selectedDpi)
                                val uri = FileExport.savePdfToDownloads(context, out, out.name)
                                if (uri != null) "PDF (${selectedPaper.name}, $selectedDpi DPI) saved to Downloads/Master Sahib PDF"
                                else "PDF save failed"
                            }
                        }

                        CompactActionTile(
                            title = if (pages.size >= 2) "CNIC 2x5" else "CNIC (Need 2)",
                            icon = Icons.Filled.Badge,
                            color = Color(0xFF2E7D32),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (pages.size < 2) {
                                toast("Scan or add at least 2 pages (Front & Back) for CNIC Duplex Mode.")
                                onAddPages()
                            } else {
                                val sheet = CnicLayout.build(front = pages[0], back = pages[1], paper = selectedPaper)
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        ImageSaver.saveToGallery(context, sheet, "cnic_${System.currentTimeMillis()}")
                                    }
                                    PrintHelper(context as Activity).apply {
                                        scaleMode = PrintHelper.SCALE_MODE_FIT
                                    }.printBitmap("CNIC Duplex", sheet)
                                    toast("CNIC 2x5 sheet saved + print opened. Flip on LONG edge for back side.")
                                }
                            }
                        }

                        CompactActionTile(
                            title = "Print Now",
                            icon = Icons.Filled.Print,
                            color = Color(0xFF00897B),
                            enabled = !busy && pages.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            val bmpToPrint = if (pages.size == 1) {
                                pages[0]
                            } else {
                                CollageLayout.build(pages, dpi = selectedDpi, paper = selectedPaper)
                            }
                            PrintHelper(context as Activity).apply {
                                scaleMode = PrintHelper.SCALE_MODE_FIT
                            }.printBitmap("Photostat_Print", bmpToPrint)
                        }

                        CompactActionTile(
                            title = "Save Images",
                            icon = Icons.Filled.Image,
                            color = Color(0xFF5E35B1),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            run {
                                var ok = 0
                                pages.forEachIndexed { i, b ->
                                    if (ImageSaver.saveToGallery(context, b, "scan_${i + 1}_${System.currentTimeMillis()}") != null) ok++
                                }
                                "$ok image(s) saved to Gallery"
                            }
                        }
                    }

                    // Row 2: Collage, Urdu OCR, English OCR, Add Pages
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CompactActionTile(
                            title = "Collage",
                            icon = Icons.Filled.GridView,
                            color = Color(0xFFEF6C00),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            run {
                                val collage = CollageLayout.build(pages, dpi = selectedDpi, paper = selectedPaper)
                                val uri = ImageSaver.saveToGallery(context, collage, "collage_${System.currentTimeMillis()}")
                                if (uri != null) "Collage saved to Gallery" else "Collage save failed"
                            }
                        }

                        CompactActionTile(
                            title = "OCR Urdu",
                            icon = Icons.Filled.Translate,
                            color = Color(0xFF283593),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            run {
                                val text = UrduOcr.recognizePages(context, rawPages)
                                if (text.isBlank()) "No Urdu text found" else {
                                    val uri = FileExport.saveTextToDownloads(context, text, "ocr_urdu_${System.currentTimeMillis()}.txt")
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("OCR Urdu", text))
                                    if (uri != null) "Urdu text copied + saved to Downloads" else "Urdu text copied"
                                }
                            }
                        }

                        CompactActionTile(
                            title = "OCR English",
                            icon = Icons.Filled.TextFields,
                            color = Color(0xFF0277BD),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            run {
                                val text = Ocr.recognizePages(rawPages)
                                if (text.isBlank()) "No text found" else {
                                    val uri = FileExport.saveTextToDownloads(context, text, "ocr_${System.currentTimeMillis()}.txt")
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
                                    if (uri != null) "Text copied + saved to Downloads/Master Sahib PDF" else "Text copied to clipboard"
                                }
                            }
                        }

                        CompactActionTile(
                            title = "Add Pages",
                            icon = Icons.Filled.AddAPhoto,
                            color = Color(0xFF1E88E5),
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            onAddPages()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactPillButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) ScanAccentPurple else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(
                width = 1.dp,
                color = if (selected) ScanAccentPurple else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompactActionTile(
    title: String,
    icon: ImageVector,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.45f
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f * alpha))
            .border(1.dp, color.copy(alpha = 0.30f * alpha), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = color.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
