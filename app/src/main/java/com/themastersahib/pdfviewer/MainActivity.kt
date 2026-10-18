package com.themastersahib.pdfviewer

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.themastersahib.pdfviewer.core.BitmapIO
import com.themastersahib.pdfviewer.core.FileExport
import com.themastersahib.pdfviewer.core.Orientation
import com.themastersahib.pdfviewer.core.PdfTools
import com.themastersahib.pdfviewer.data.Bookmark
import com.themastersahib.pdfviewer.data.Db
import com.themastersahib.pdfviewer.data.RecentFile
import com.themastersahib.pdfviewer.data.SettingsStore
import com.themastersahib.pdfviewer.data.UriNames
import com.themastersahib.pdfviewer.ui.AnnotationScreen
import com.themastersahib.pdfviewer.ui.HomeScreen
import com.themastersahib.pdfviewer.ui.ReaderScreen
import com.themastersahib.pdfviewer.ui.ScanExportScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val incoming: Uri? = if (intent?.action == Intent.ACTION_VIEW) intent.data else null
        setContent { AppRoot(incomingPdf = incoming) }
    }
}

private enum class Screen { HOME, VIEWER, SCAN_REVIEW, ANNOTATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(incomingPdf: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { Db.get(context) }
    val settings = remember { SettingsStore(context) }

    var darkMode by remember { mutableStateOf(settings.darkMode) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfName by remember { mutableStateOf("") }
    var startPage by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }

    var recents by remember { mutableStateOf<List<RecentFile>>(emptyList()) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var scannedPages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var annotPage by remember { mutableStateOf<Bitmap?>(null) }
    var annotSourcePage by remember { mutableIntStateOf(0) }

    fun refreshLists() = scope.launch {
        recents = withContext(Dispatchers.IO) { db.recentDao().all() }
        bookmarks = withContext(Dispatchers.IO) { db.bookmarkDao().all() }
    }

    suspend fun renderPage(page: Int, width: Int): Bitmap? {
        val uri = pdfUri ?: return null
        return withContext(Dispatchers.IO) {
            PdfRendererHelper(context.contentResolver, uri).use { it.renderPage(page, width) }
        }
    }

    fun loadPdf(uri: Uri, jumpTo: Int = 0) {
        pdfUri = uri
        pdfName = UriNames.displayName(context, uri)
        startPage = jumpTo
        currentPage = jumpTo
        screen = Screen.VIEWER
    }

    fun recordRecent(count: Int) {
        val uri = pdfUri ?: return
        scope.launch(Dispatchers.IO) {
            db.recentDao().upsert(
                RecentFile(
                    uri = uri.toString(), name = pdfName,
                    lastOpened = System.currentTimeMillis(),
                    lastPage = currentPage, pageCount = count
                )
            )
        }
    }

    fun saveLastPage() {
        val uri = pdfUri ?: return
        scope.launch(Dispatchers.IO) { db.recentDao().updatePage(uri.toString(), currentPage) }
    }

    fun addBookmark(page: Int) {
        val uri = pdfUri ?: return
        scope.launch(Dispatchers.IO) {
            db.bookmarkDao().add(Bookmark(uri = uri.toString(), name = pdfName, page = page, createdAt = System.currentTimeMillis()))
        }
        Toast.makeText(context, "Bookmarked page ${page + 1}", Toast.LENGTH_SHORT).show()
    }

    fun saveImage(page: Int) {
        scope.launch {
            val saved = renderPage(page, 2500)?.let {
                withContext(Dispatchers.IO) { ImageSaver.saveToGallery(context, it, "page_${page + 1}_${System.currentTimeMillis()}") }
            }
            Toast.makeText(
                context,
                if (saved != null) "Page ${page + 1} saved to Gallery ▸ Pictures/Master Sahib PDF" else "Save failed",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun annotate(page: Int) {
        annotSourcePage = page
        scope.launch {
            val bmp = renderPage(page, 1600)
            if (bmp != null) { annotPage = bmp; screen = Screen.ANNOTATE }
        }
    }

    LaunchedEffect(incomingPdf) { incomingPdf?.let { loadPdf(it) } }
    LaunchedEffect(screen) { if (screen == Screen.HOME) refreshLists() }

    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            loadPdf(uri)
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val uris = scan?.pages?.mapNotNull { it.imageUri } ?: emptyList()
            scope.launch {
                val decoded = withContext(Dispatchers.IO) { uris.mapNotNull { BitmapIO.decode(context, it) } }
                // Append when already reviewing a scan ("Add Pages"), else start fresh.
                scannedPages = if (screen == Screen.SCAN_REVIEW) scannedPages + decoded else decoded
                if (scannedPages.isNotEmpty()) screen = Screen.SCAN_REVIEW
            }
        }
    }

    fun launchScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(context as Activity)
            .addOnSuccessListener { sender -> scannerLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { Toast.makeText(context, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    val mergePdfs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        when {
            uris.size >= 2 -> scope.launch {
                val msg = withContext(Dispatchers.IO) {
                    val f = PdfTools.merge(context, uris)
                    if (FileExport.savePdfToDownloads(context, f, "merged_${System.currentTimeMillis()}.pdf") != null)
                        "Merged ${uris.size} PDFs → Downloads ▸ Master Sahib PDF" else "Merge failed"
                }
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
            uris.isNotEmpty() -> Toast.makeText(context, "Pick at least 2 PDFs", Toast.LENGTH_SHORT).show()
        }
    }

    val colors = if (darkMode) darkColorScheme() else lightColorScheme()

    MaterialTheme(colorScheme = colors) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                recents = recents,
                bookmarks = bookmarks,
                darkMode = darkMode,
                onToggleDark = { darkMode = it; settings.darkMode = it },
                onPickPdf = { openPdf.launch(arrayOf("application/pdf")) },
                onScan = { launchScanner() },
                onMerge = { mergePdfs.launch(arrayOf("application/pdf")) },
                onOpenRecent = { loadPdf(Uri.parse(it.uri), it.lastPage) },
                onOpenBookmark = { loadPdf(Uri.parse(it.uri), it.page) },
                onDeleteRecent = { r ->
                    scope.launch { withContext(Dispatchers.IO) { db.recentDao().delete(r) }; refreshLists() }
                }
            )

            Screen.SCAN_REVIEW -> ScanExportScreen(
                rawPages = scannedPages,
                onBack = { screen = Screen.HOME },
                onAddPages = { launchScanner() },
                onRotatePage = { i ->
                    scannedPages = scannedPages.toMutableList().also { it[i] = Orientation.rotate(it[i], 90f) }
                },
                onDeletePage = { i ->
                    scannedPages = scannedPages.filterIndexed { idx, _ -> idx != i }
                    if (scannedPages.isEmpty()) screen = Screen.HOME
                }
            )

            Screen.ANNOTATE -> {
                val p = annotPage
                if (p != null) AnnotationScreen(
                    page = p, onBack = { screen = Screen.VIEWER },
                    sourceUri = pdfUri, sourcePage = annotSourcePage
                ) else screen = Screen.VIEWER
            }

            Screen.VIEWER -> {
                val uri = pdfUri
                if (uri == null) { screen = Screen.HOME }
                else ReaderScreen(
                    uri = uri,
                    name = pdfName,
                    initialPage = startPage,
                    verticalScroll = settings.verticalScroll,
                    onBack = { saveLastPage(); screen = Screen.HOME },
                    onLoaded = { c -> pageCount = c; recordRecent(c) },
                    onPageChanged = { currentPage = it },
                    onAnnotate = { annotate(it) },
                    onSaveImage = { saveImage(it) },
                    onBookmark = { addBookmark(it) }
                )
            }
        }
    }
}
