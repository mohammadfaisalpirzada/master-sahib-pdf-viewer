package com.themastersahib.pdfviewer.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Review screen shown AFTER a scan: pick a bulk filter (or override per-page),
 * reorder/rotate/delete pages, choose paper size/DPI, then save as images,
 * make a PDF, build a collage, run OCR, or print CNIC duplex mode.
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

    fun run(label: String, block: suspend () -> String) {
        busy = true
        scope.launch {
            val msg = try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { "Error: ${e.message}" }
            busy = false
            toast(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan · ${pages.size} page(s)") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // --- Bulk filter chips + Paper size options ---
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip("Original", bulkFilter == FilterType.NONE) {
                    bulkFilter = FilterType.NONE; perPageOverrides = emptyMap()
                }
                FilterChip("Grayscale", bulkFilter == FilterType.GRAYSCALE) {
                    bulkFilter = FilterType.GRAYSCALE; perPageOverrides = emptyMap()
                }
                FilterChip("B&W Print", bulkFilter == FilterType.BW_PRINT) {
                    bulkFilter = FilterType.BW_PRINT; perPageOverrides = emptyMap()
                }
                FilterChip("Magic", bulkFilter == FilterType.MAGIC) {
                    bulkFilter = FilterType.MAGIC; perPageOverrides = emptyMap()
                }
                Spacer(Modifier.width(8.dp))
                FilterChip("A4", selectedPaper == PaperSize.A4) { selectedPaper = PaperSize.A4 }
                FilterChip("Letter", selectedPaper == PaperSize.LETTER) { selectedPaper = PaperSize.LETTER }
                FilterChip("Legal", selectedPaper == PaperSize.LEGAL) { selectedPaper = PaperSize.LEGAL }
                FilterChip(if (selectedDpi == 300) "300 DPI (HQ)" else "200 DPI", selectedDpi == 300) {
                    selectedDpi = if (selectedDpi == 300) 200 else 300
                }
            }

            // --- Action buttons ---
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(enabled = !busy, onClick = onAddPages) {
                    Icon(Icons.Filled.AddAPhoto, null); Spacer(Modifier.width(6.dp)); Text("Add Pages")
                }

                Button(enabled = !busy, onClick = {
                    run("Saving images") {
                        var ok = 0
                        pages.forEachIndexed { i, b ->
                            if (ImageSaver.saveToGallery(context, b, "scan_${i + 1}_${System.currentTimeMillis()}") != null) ok++
                        }
                        "$ok image(s) saved to Gallery"
                    }
                }) { Text("Save Images") }

                Button(enabled = !busy, onClick = {
                    run("Making PDF") {
                        val out = File(context.cacheDir, "scan_${System.currentTimeMillis()}.pdf")
                        PdfBuilder.build(pages, out, paper = selectedPaper, dpi = selectedDpi)
                        val uri = FileExport.savePdfToDownloads(context, out, out.name)
                        if (uri != null) "PDF (${selectedPaper.name}, $selectedDpi DPI) saved to Downloads ▸ Master Sahib PDF"
                        else "PDF save failed"
                    }
                }) { Text("Make PDF") }

                Button(enabled = !busy, onClick = {
                    run("Building collage") {
                        val collage = CollageLayout.build(pages, dpi = selectedDpi, paper = selectedPaper)
                        val uri = ImageSaver.saveToGallery(context, collage, "collage_${System.currentTimeMillis()}")
                        if (uri != null) "Collage saved to Gallery" else "Collage save failed"
                    }
                }) { Text("Collage") }

                Button(enabled = !busy, onClick = {
                    run("Running OCR") {
                        val text = Ocr.recognizePages(rawPages)
                        if (text.isBlank()) "No text found" else {
                            val uri = FileExport.saveTextToDownloads(
                                context, text, "ocr_${System.currentTimeMillis()}.txt"
                            )
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
                            if (uri != null) "Text copied + saved to Downloads ▸ Master Sahib PDF" else "Text copied to clipboard"
                        }
                    }
                }) { Text("OCR → Text") }

                Button(enabled = !busy, onClick = {
                    run("Running Urdu OCR") {
                        val text = UrduOcr.recognizePages(context, rawPages)
                        if (text.isBlank()) "No text found" else {
                            val uri = FileExport.saveTextToDownloads(context, text, "ocr_urdu_${System.currentTimeMillis()}.txt")
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("OCR Urdu", text))
                            if (uri != null) "Urdu text copied + saved to Downloads" else "Urdu text copied"
                        }
                    }
                }) { Text("OCR Urdu") }

                Button(enabled = !busy && pages.size >= 2, onClick = {
                    val sheet = CnicLayout.build(front = pages[0], back = pages[1], paper = selectedPaper)
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ImageSaver.saveToGallery(context, sheet, "cnic_${System.currentTimeMillis()}")
                        }
                        PrintHelper(context as Activity).apply {
                            scaleMode = PrintHelper.SCALE_MODE_FIT
                        }.printBitmap("CNIC Duplex", sheet)
                        toast("CNIC sheet saved + print dialog opened. Reprint same page, flip on LONG edge.")
                    }
                }) { Text("CNIC Mode") }
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // --- Thumbnails with per-page filter cycling, reordering, rotate, delete ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pages) { idx, b ->
                    val currentPageFilter = perPageOverrides[idx] ?: bulkFilter
                    Card {
                        Box {
                            Image(
                                bitmap = b.asImageBitmap(),
                                contentDescription = "Page ${idx + 1}",
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Top-left: page number + per-page filter cycle button
                            AssistChip(
                                onClick = {
                                    val all = FilterType.entries
                                    val next = all[(currentPageFilter.ordinal + 1) % all.size]
                                    perPageOverrides = perPageOverrides.toMutableMap().apply { put(idx, next) }
                                },
                                label = { Text("#${idx + 1} · ${currentPageFilter.name}") },
                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                            )
                            // Bottom toolbar: Move Left, Move Right, Rotate, Delete
                            Row(
                                Modifier.align(Alignment.BottomEnd).padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (idx > 0) {
                                    FilledTonalIconButton(
                                        onClick = { onMovePage(idx, idx - 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowBackIosNew, "Move earlier", modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (idx < pages.lastIndex) {
                                    FilledTonalIconButton(
                                        onClick = { onMovePage(idx, idx + 1) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ArrowForwardIos, "Move later", modifier = Modifier.size(16.dp))
                                    }
                                }
                                FilledTonalIconButton(
                                    onClick = { onRotatePage(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.RotateRight, "Rotate", modifier = Modifier.size(18.dp))
                                }
                                FilledTonalIconButton(
                                    onClick = { onDeletePage(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, "Delete", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) }
    )
}
