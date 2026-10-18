package com.themastersahib.pdfviewer.ui

import android.app.Activity
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Review screen shown AFTER a scan: pick a bulk filter, then save as images,
 * make a PDF, build a collage, or run CNIC duplex mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanExportScreen(
    rawPages: List<Bitmap>,
    onBack: () -> Unit,
    onAddPages: () -> Unit = {},
    onRotatePage: (Int) -> Unit = {},
    onDeletePage: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var filter by remember { mutableStateOf(FilterType.NONE) }
    // Filtered copy used for all exports (bulk).
    val pages by remember(filter, rawPages) {
        mutableStateOf(ImageFilters.applyAll(rawPages, filter))
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

            // --- Bulk filter chips ---
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip("Original", filter == FilterType.NONE) { filter = FilterType.NONE }
                FilterChip("Grayscale", filter == FilterType.GRAYSCALE) { filter = FilterType.GRAYSCALE }
                FilterChip("B&W Print", filter == FilterType.BW_PRINT) { filter = FilterType.BW_PRINT }
                FilterChip("Magic", filter == FilterType.MAGIC) { filter = FilterType.MAGIC }
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
                        PdfBuilder.build(pages, out)
                        val uri = FileExport.savePdfToDownloads(context, out, out.name)
                        if (uri != null) "PDF saved to Downloads ▸ Master Sahib PDF" else "PDF save failed"
                    }
                }) { Text("Make PDF") }

                Button(enabled = !busy, onClick = {
                    run("Building collage") {
                        val collage = CollageLayout.build(pages)
                        val uri = ImageSaver.saveToGallery(context, collage, "collage_${System.currentTimeMillis()}")
                        if (uri != null) "Collage saved to Gallery" else "Collage save failed"
                    }
                }) { Text("Collage") }

                Button(enabled = !busy, onClick = {
                    run("Running OCR") {
                        // OCR on ORIGINAL pages for best accuracy.
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
                    // CNIC: page 1 = front, page 2 = back.
                    val sheet = CnicLayout.build(front = pages[0], back = pages[1])
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ImageSaver.saveToGallery(context, sheet, "cnic_${System.currentTimeMillis()}")
                        }
                        // Open the system print dialog for immediate duplex printing.
                        PrintHelper(context as Activity).apply {
                            scaleMode = PrintHelper.SCALE_MODE_FIT
                        }.printBitmap("CNIC Duplex", sheet)
                        toast("CNIC sheet saved + print dialog opened. Reprint same page, flip on LONG edge.")
                    }
                }) { Text("CNIC Mode") }
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // --- Thumbnails ---
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(pages) { idx, b ->
                    Box {
                        Image(bitmap = b.asImageBitmap(), contentDescription = "Page ${idx + 1}",
                            modifier = Modifier.fillMaxWidth())
                        Row(Modifier.align(Alignment.TopEnd)) {
                            FilledTonalIconButton(onClick = { onRotatePage(idx) }) {
                                Icon(Icons.Filled.RotateRight, "Rotate")
                            }
                            FilledTonalIconButton(onClick = { onDeletePage(idx) }) {
                                Icon(Icons.Filled.Delete, "Delete")
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
