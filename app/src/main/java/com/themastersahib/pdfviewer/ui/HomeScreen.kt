package com.themastersahib.pdfviewer.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.themastersahib.pdfviewer.BuildConfig
import com.themastersahib.pdfviewer.ImageSaver
import com.themastersahib.pdfviewer.PdfRendererHelper
import com.themastersahib.pdfviewer.core.*
import com.themastersahib.pdfviewer.data.Auth
import com.themastersahib.pdfviewer.data.Bookmark
import com.themastersahib.pdfviewer.data.RecentFile
import com.themastersahib.pdfviewer.data.SettingsStore
import com.themastersahib.pdfviewer.data.UserProfileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentPurple = Color(0xFF5E5CE6)

private data class LauncherToolItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<RecentFile>,
    bookmarks: List<Bookmark>,
    darkMode: Boolean,
    userProfile: UserProfileInfo?,
    onToggleDark: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onRequestLogin: () -> Unit,
    onPickPdf: () -> Unit,
    onScan: () -> Unit,
    onReadyPhotostat: () -> Unit,
    onMerge: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteRecent: (RecentFile) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit = {},
    onOpenUri: (Uri) -> Unit = {},
    onSendBitmapsToScanReview: (List<Bitmap>, FilterType) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    // Bottom navigation: 0 = Home, 1 = Files, 2 = Template, 3 = Discover
    var bottomNavIndex by remember { mutableIntStateOf(0) }
    var homeTab by remember { mutableIntStateOf(0) }
    var searchFilter by remember { mutableStateOf("") }
    var showSearchField by remember { mutableStateOf(false) }

    // Sub-screens inside Files tab
    var filesSubScreen by remember { mutableStateOf<String?>(null) }
    var showWifiDialog by remember { mutableStateOf(false) }
    var showCreateSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }
    var showCnicMakerDialog by remember { mutableStateOf(false) }
    var savedPlaces by remember { mutableStateOf(loadSavedPlaces(context)) }

    // Standalone tool state when triggered from the Home Launcher Grid
    var pendingToolAction by remember { mutableStateOf<String?>(null) }
    var toolTargetUri by remember { mutableStateOf<Uri?>(null) }
    var busyToolMessage by remember { mutableStateOf<String?>(null) }
    var ocrResultDialogText by remember { mutableStateOf<String?>(null) }
    var ocrResultTitle by remember { mutableStateOf("Recognized Text") }

    // Load user's profile avatar bitmap if available
    var avatarBitmap by remember(userProfile?.photoUrl) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(userProfile?.photoUrl) {
        val url = userProfile?.photoUrl
        avatarBitmap = if (!url.isNullOrBlank()) {
            Auth.loadAvatarBitmap(url)
        } else null
    }

    val df = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    val filteredRecents = remember(recents, searchFilter) {
        if (searchFilter.isBlank()) recents
        else recents.filter { it.name.contains(searchFilter.trim(), ignoreCase = true) }
    }
    val filteredBookmarks = remember(bookmarks, searchFilter) {
        if (searchFilter.isBlank()) bookmarks
        else bookmarks.filter { it.name.contains(searchFilter.trim(), ignoreCase = true) }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val label = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Folder"
            val updated = (savedPlaces + "$label|$treeUri").distinct()
            savePlaces(context, updated)
            savedPlaces = updated
            Toast.makeText(context, "Added place: $label", Toast.LENGTH_SHORT).show()
        }
    }

    // Picker for standalone PDF tools (Split, Compress, Protect, PDF to Image, OCR PDF)
    val toolPdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            toolTargetUri = uri
        } else {
            pendingToolAction = null
        }
    }

    // Picker for images from gallery (for Photostat / OCR / CNIC Maker)
    val galleryImagesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val action = pendingToolAction
        if (uris.isEmpty()) {
            pendingToolAction = null
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busyToolMessage = "Loading selected images..."
            val bitmaps = withContext(Dispatchers.IO) {
                uris.mapNotNull { BitmapIO.decode(context, it) }
            }
            busyToolMessage = null
            if (bitmaps.isEmpty()) {
                Toast.makeText(context, "Could not decode selected images", Toast.LENGTH_SHORT).show()
                pendingToolAction = null
                return@launch
            }
            when (action) {
                "GALLERY_PHOTOSTAT" -> {
                    pendingToolAction = null
                    onSendBitmapsToScanReview(bitmaps, FilterType.BW_PRINT)
                }
                "GALLERY_URDU_OCR" -> {
                    pendingToolAction = null
                    busyToolMessage = "Running Urdu OCR (Tesseract)..."
                    val text = withContext(Dispatchers.IO) { UrduOcr.recognizePages(context, bitmaps) }
                    busyToolMessage = null
                    ocrResultTitle = "Urdu OCR Result"
                    ocrResultDialogText = text.ifBlank { "No Urdu text detected." }
                }
                "GALLERY_ENG_OCR" -> {
                    pendingToolAction = null
                    busyToolMessage = "Running English OCR (ML Kit)..."
                    val text = withContext(Dispatchers.IO) { Ocr.recognizePages(bitmaps) }
                    busyToolMessage = null
                    ocrResultTitle = "English OCR Result"
                    ocrResultDialogText = text.ifBlank { "No text detected." }
                }
                else -> {
                    pendingToolAction = null
                    onSendBitmapsToScanReview(bitmaps, FilterType.NONE)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (bottomNavIndex) {
                            0 -> "Photostat Master"
                            1 -> when (filesSubScreen) {
                                "PHONE" -> "On This Phone"
                                "CLOUD" -> "Cloud Documents"
                                "TAGS" -> "Tag List"
                                else -> "Files"
                            }
                            2 -> "Templates"
                            else -> "Discover & Tools"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    if (bottomNavIndex == 1 && filesSubScreen != null) {
                        IconButton(onClick = { filesSubScreen = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSearchField = !showSearchField }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { onToggleDark(!darkMode) }) {
                        Icon(
                            if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle theme"
                        )
                    }
                    // User Profile Round Icon (Photo if logged in, '?' if not logged in)
                    UserProfileAvatarButton(
                        userProfile = userProfile,
                        avatarBitmap = avatarBitmap,
                        onClick = { showProfileSheet = true }
                    )
                    Spacer(Modifier.width(6.dp))
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 4.dp, shadowElevation = 8.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavTab(
                        icon = Icons.Filled.Home,
                        label = "Home",
                        selected = bottomNavIndex == 0,
                        onClick = { bottomNavIndex = 0 }
                    )
                    BottomNavTab(
                        icon = Icons.Filled.Folder,
                        label = "Files",
                        selected = bottomNavIndex == 1,
                        onClick = { bottomNavIndex = 1; filesSubScreen = null }
                    )
                    // Center purple '+' button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentPurple)
                            .clickable { showCreateSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Create or Scan",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    BottomNavTab(
                        icon = Icons.Filled.DashboardCustomize,
                        label = "Template",
                        selected = bottomNavIndex == 2,
                        onClick = { bottomNavIndex = 2 }
                    )
                    BottomNavTab(
                        icon = Icons.Filled.Explore,
                        label = "Discover",
                        selected = bottomNavIndex == 3,
                        onClick = { bottomNavIndex = 3 }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (busyToolMessage != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    busyToolMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = AccentPurple,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (showSearchField) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Search PDFs, recent files, bookmarks...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchFilter.isNotEmpty()) {
                            IconButton(onClick = { searchFilter = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            when (bottomNavIndex) {
                0 -> {
                    // --- HOME TAB: Android Launcher-Style Icon Grid + Recent / Bookmarks ---
                    val launcherTools = remember {
                        listOf(
                            LauncherToolItem("Open PDF", Icons.Filled.PictureAsPdf, Color(0xFFE53935)) {
                                onPickPdf()
                            },
                            LauncherToolItem("Scan Doc", Icons.Filled.DocumentScanner, Color(0xFF1E88E5)) {
                                onScan()
                            },
                            LauncherToolItem("Ready Photostat", Icons.Filled.Print, Color(0xFF00897B)) {
                                pendingToolAction = "ASK_PHOTOSTAT_SOURCE"
                            },
                            LauncherToolItem("CNIC Maker", Icons.Filled.Badge, Color(0xFF2E7D32)) {
                                showCnicMakerDialog = true
                            },
                            LauncherToolItem("Merge PDFs", Icons.Filled.MergeType, Color(0xFF6A1B9A)) {
                                onMerge()
                            },
                            LauncherToolItem("Split PDF", Icons.Filled.CallSplit, Color(0xFFF4511E)) {
                                pendingToolAction = "SPLIT_PDF"
                                toolPdfPicker.launch(arrayOf("application/pdf"))
                            },
                            LauncherToolItem("Urdu OCR", Icons.Filled.Translate, Color(0xFF283593)) {
                                pendingToolAction = "ASK_URDU_OCR_SOURCE"
                            },
                            LauncherToolItem("English OCR", Icons.Filled.TextFields, Color(0xFF0277BD)) {
                                pendingToolAction = "ASK_ENG_OCR_SOURCE"
                            },
                            LauncherToolItem("Compress PDF", Icons.Filled.Compress, Color(0xFFEF6C00)) {
                                pendingToolAction = "COMPRESS_PDF"
                                toolPdfPicker.launch(arrayOf("application/pdf"))
                            },
                            LauncherToolItem("Protect PDF", Icons.Filled.Lock, Color(0xFFAD1457)) {
                                pendingToolAction = "PROTECT_PDF"
                                toolPdfPicker.launch(arrayOf("application/pdf"))
                            },
                            LauncherToolItem("PDF to Image", Icons.Filled.Image, Color(0xFF5E35B1)) {
                                pendingToolAction = "PDF_TO_IMAGE"
                                toolPdfPicker.launch(arrayOf("application/pdf"))
                            },
                            LauncherToolItem("Wi-Fi PC", Icons.Filled.Wifi, Color(0xFF1565C0)) {
                                showWifiDialog = true
                            }
                        )
                    }

                    // Android Phone Home Screen Style Icon Grid (4 columns x 3 rows)
                    Surface(
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(vertical = 12.dp, horizontal = 6.dp)) {
                            launcherTools.chunked(4).forEachIndexed { rowIdx, rowItems ->
                                if (rowIdx > 0) Spacer(Modifier.height(12.dp))
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowItems.forEach { item ->
                                        AndroidLauncherAppIcon(
                                            title = item.title,
                                            icon = item.icon,
                                            color = item.color,
                                            onClick = item.onClick,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    TabRow(selectedTabIndex = homeTab) {
                        Tab(
                            selected = homeTab == 0,
                            onClick = { homeTab = 0 },
                            text = { Text("Recent (${filteredRecents.size})") }
                        )
                        Tab(
                            selected = homeTab == 1,
                            onClick = { homeTab = 1 },
                            text = { Text("Bookmarks (${filteredBookmarks.size})") }
                        )
                    }

                    when (homeTab) {
                        0 -> {
                            if (filteredRecents.isEmpty()) {
                                EmptyHint("No recent files yet. Tap 'Open PDF' or 'Scan Doc' above to get started.")
                            } else {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(filteredRecents) { r ->
                                        val tags = remember(r.uri) { settings.getTagsForUri(r.uri) }
                                        ListItem(
                                            headlineContent = {
                                                Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            },
                                            supportingContent = {
                                                val pg = if (r.pageCount > 0) "p.${r.lastPage + 1}/${r.pageCount}" else ""
                                                val tagStr = if (tags.isNotEmpty()) "  ·  [${tags.joinToString(", ")}]" else ""
                                                Text("${df.format(Date(r.lastOpened))}  $pg$tagStr")
                                            },
                                            leadingContent = {
                                                Box(
                                                    Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFFE53935).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.PictureAsPdf,
                                                        contentDescription = null,
                                                        tint = Color(0xFFE53935)
                                                    )
                                                }
                                            },
                                            trailingContent = {
                                                IconButton(onClick = { onDeleteRecent(r) }) {
                                                    Icon(Icons.Filled.Close, "Remove")
                                                }
                                            },
                                            modifier = Modifier.clickableRow { onOpenRecent(r) }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                        1 -> {
                            if (filteredBookmarks.isEmpty()) {
                                EmptyHint("No bookmarks yet. Open any PDF and tap the bookmark icon in the bottom bar.")
                            } else {
                                LazyColumn(Modifier.fillMaxSize()) {
                                    items(filteredBookmarks) { b ->
                                        ListItem(
                                            headlineContent = {
                                                Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            },
                                            supportingContent = { Text("Page ${b.page + 1}") },
                                            leadingContent = {
                                                Icon(Icons.Filled.Bookmark, null, tint = AccentPurple)
                                            },
                                            trailingContent = {
                                                IconButton(onClick = { onDeleteBookmark(b) }) {
                                                    Icon(Icons.Filled.Close, "Remove bookmark")
                                                }
                                            },
                                            modifier = Modifier.clickableRow { onOpenBookmark(b) }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // --- FILES TAB (Matches Screenshot 4) ---
                    FilesTabContent(
                        subScreen = filesSubScreen,
                        onSelectSubScreen = { filesSubScreen = it },
                        recents = recents,
                        bookmarks = bookmarks,
                        settings = settings,
                        savedPlaces = savedPlaces,
                        onBrowseMoreFiles = onPickPdf,
                        onOpenWifiTransfer = { showWifiDialog = true },
                        onAddPlace = { folderPicker.launch(null) },
                        onOpenRecent = onOpenRecent,
                        onOpenFile = { file -> onOpenUri(Uri.fromFile(file)) }
                    )
                }

                2 -> {
                    // --- TEMPLATE TAB ---
                    TemplatesTabContent(
                        onCreated = { file -> onOpenUri(Uri.fromFile(file)) }
                    )
                }

                3 -> {
                    // --- DISCOVER TAB ---
                    DiscoverTabContent(
                        onPickPdf = onPickPdf,
                        onScan = onScan,
                        onReadyPhotostat = { pendingToolAction = "ASK_PHOTOSTAT_SOURCE" },
                        onCnicMaker = { showCnicMakerDialog = true },
                        onMerge = onMerge,
                        onWifiTransfer = { showWifiDialog = true },
                        onGoTemplates = { bottomNavIndex = 2 }
                    )
                }
            }
        }
    }

    // --- USER PROFILE & LOGIN / LOGOUT BOTTOM SHEET ---
    if (showProfileSheet) {
        UserProfileModalSheet(
            userProfile = userProfile,
            avatarBitmap = avatarBitmap,
            recentsCount = recents.size,
            bookmarksCount = bookmarks.size,
            darkMode = darkMode,
            onToggleDark = onToggleDark,
            onDismiss = { showProfileSheet = false },
            onLogout = {
                showProfileSheet = false
                onLogout()
            },
            onSignIn = {
                showProfileSheet = false
                onRequestLogin()
            }
        )
    }

    // --- SOURCE PICKER DIALOG FOR PHOTOSTAT / OCR ---
    if (pendingToolAction in listOf("ASK_PHOTOSTAT_SOURCE", "ASK_URDU_OCR_SOURCE", "ASK_ENG_OCR_SOURCE")) {
        val isPhotostat = pendingToolAction == "ASK_PHOTOSTAT_SOURCE"
        val isUrdu = pendingToolAction == "ASK_URDU_OCR_SOURCE"
        val title = when {
            isPhotostat -> "Ready to Photostat (B&W Copy)"
            isUrdu -> "Urdu OCR (اردو متن شناخت)"
            else -> "English OCR (Text Recognition)"
        }
        val subtitle = when {
            isPhotostat -> "Scan a document with the camera or pick existing photos from Gallery to convert into a crisp, high-contrast B&W photostat sheet ready for printing."
            isUrdu -> "Extract Urdu Nastaliq text from camera scans, gallery images, or an existing PDF document."
            else -> "Extract English/Latin text from camera scans, gallery images, or an existing PDF document."
        }
        AlertDialog(
            onDismissRequest = { pendingToolAction = null },
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = {
                            val act = pendingToolAction
                            pendingToolAction = null
                            if (act == "ASK_PHOTOSTAT_SOURCE") onReadyPhotostat() else onScan()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DocumentScanner, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Use Camera Scanner")
                    }
                    OutlinedButton(
                        onClick = {
                            pendingToolAction = when (pendingToolAction) {
                                "ASK_PHOTOSTAT_SOURCE" -> "GALLERY_PHOTOSTAT"
                                "ASK_URDU_OCR_SOURCE" -> "GALLERY_URDU_OCR"
                                else -> "GALLERY_ENG_OCR"
                            }
                            galleryImagesPicker.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Pick Images from Gallery")
                    }
                    if (!isPhotostat) {
                        OutlinedButton(
                            onClick = {
                                pendingToolAction = if (isUrdu) "PDF_URDU_OCR" else "PDF_ENG_OCR"
                                toolPdfPicker.launch(arrayOf("application/pdf"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick a PDF Document")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingToolAction = null }) { Text("Cancel") }
            }
        )
    }

    // --- STANDALONE PDF TOOL EXECUTION DIALOGS (Split, Compress, Protect, PDF to Image, PDF OCR) ---
    val targetUri = toolTargetUri
    if (targetUri != null && pendingToolAction != null) {
        StandalonePdfToolDialog(
            action = pendingToolAction!!,
            uri = targetUri,
            onDismiss = {
                toolTargetUri = null
                pendingToolAction = null
            },
            onOpenResultFile = { file ->
                toolTargetUri = null
                pendingToolAction = null
                onOpenUri(Uri.fromFile(file))
            },
            onShowOcrText = { title, text ->
                toolTargetUri = null
                pendingToolAction = null
                ocrResultTitle = title
                ocrResultDialogText = text
            }
        )
    }

    // --- OCR RESULT DIALOG ---
    if (ocrResultDialogText != null) {
        val text = ocrResultDialogText!!
        AlertDialog(
            onDismissRequest = { ocrResultDialogText = null },
            title = { Text(ocrResultTitle, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    SelectionContainer {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
                    scope.launch(Dispatchers.IO) {
                        FileExport.saveTextToDownloads(context, text, "ocr_${System.currentTimeMillis()}.txt")
                    }
                    Toast.makeText(context, "Copied & saved to Downloads/Master Sahib PDF", Toast.LENGTH_LONG).show()
                    ocrResultDialogText = null
                }) { Text("Copy & Save .txt") }
            },
            dismissButton = {
                TextButton(onClick = { ocrResultDialogText = null }) { Text("Close") }
            }
        )
    }

    // --- CNIC DUPLEX 2x5 SHEET MAKER DIALOG ---
    if (showCnicMakerDialog) {
        CnicMakerDialog(
            onDismiss = { showCnicMakerDialog = false },
            onLaunchScanner = {
                showCnicMakerDialog = false
                onReadyPhotostat()
            }
        )
    }

    // --- CENTER '+' QUICK CREATE / SCAN BOTTOM SHEET ---
    if (showCreateSheet) {
        ModalBottomSheet(onDismissRequest = { showCreateSheet = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ListItem(
                    headlineContent = { Text("Scan Document (Auto-Crop)") },
                    supportingContent = { Text("Scan pages, apply B&W Photostat filter, OCR, or CNIC sheet") },
                    leadingContent = { Icon(Icons.Filled.DocumentScanner, null, tint = AccentPurple) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreateSheet = false; onScan() }
                )
                ListItem(
                    headlineContent = { Text("CNIC 2x5 Duplex Maker") },
                    supportingContent = { Text("Select Front & Back of CNIC for 10-card flip-duplex printing") },
                    leadingContent = { Icon(Icons.Filled.Badge, null, tint = Color(0xFF2E7D32)) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreateSheet = false; showCnicMakerDialog = true }
                )
                ListItem(
                    headlineContent = { Text("Open PDF from Device") },
                    supportingContent = { Text("Browse local storage or SD card") },
                    leadingContent = { Icon(Icons.Filled.FolderOpen, null, tint = AccentPurple) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreateSheet = false; onPickPdf() }
                )
                ListItem(
                    headlineContent = { Text("Merge Multiple PDFs") },
                    supportingContent = { Text("Combine two or more PDF files into one document") },
                    leadingContent = { Icon(Icons.Filled.MergeType, null, tint = AccentPurple) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreateSheet = false; onMerge() }
                )
                ListItem(
                    headlineContent = { Text("New PDF from Template") },
                    supportingContent = { Text("Blank, Ruled, Grid, Dotted, Cornell Notes, Checklist") },
                    leadingContent = { Icon(Icons.Filled.NoteAdd, null, tint = AccentPurple) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showCreateSheet = false; bottomNavIndex = 2 }
                )
            }
        }
    }

    // --- PC · FILE TRANSFER WITH WIFI DIALOG ---
    if (showWifiDialog) {
        WifiTransferDialog(
            onDismiss = { showWifiDialog = false },
            onFileReceived = { file ->
                showWifiDialog = false
                onOpenUri(Uri.fromFile(file))
            }
        )
    }
}

/**
 * Android Phone Launcher-Style App Icon:
 * Colorful rounded squircle icon container with subtle shadow + crisp white icon + label underneath.
 */
@Composable
private fun AndroidLauncherAppIcon(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(elevation = 3.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Top-right circular user profile avatar button:
 * - Shows user's profile photo if logged in and available
 * - Or user's monogram initial with a green online dot if logged in without photo
 * - Or '?' mark if not logged in
 */
@Composable
private fun UserProfileAvatarButton(
    userProfile: UserProfileInfo?,
    avatarBitmap: Bitmap?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (userProfile != null) {
            if (avatarBitmap != null) {
                Image(
                    bitmap = avatarBitmap.asImageBitmap(),
                    contentDescription = "User Profile",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, AccentPurple, CircleShape)
                )
            } else {
                val initial = (userProfile.displayName.firstOrNull()
                    ?: userProfile.email.firstOrNull()
                    ?: 'U').uppercaseChar()
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AccentPurple)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
            // Small green logged-in status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32))
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        } else {
            // Not logged in -> circular '?' icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Rich User Account Details & Logout / Sign-In Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileModalSheet(
    userProfile: UserProfileInfo?,
    avatarBitmap: Bitmap?,
    recentsCount: Int,
    bookmarksCount: Int,
    darkMode: Boolean,
    onToggleDark: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onSignIn: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (userProfile != null) {
                // Large Avatar
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap.asImageBitmap(),
                        contentDescription = "User Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(2.dp, AccentPurple, CircleShape)
                    )
                } else {
                    val initial = (userProfile.displayName.firstOrNull()
                        ?: userProfile.email.firstOrNull()
                        ?: 'U').uppercaseChar()
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .background(AccentPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = userProfile.displayName.ifBlank { "Master Sahib User" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (userProfile.email.isNotBlank()) {
                    Text(
                        text = userProfile.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFF2E7D32)
                            )
                        },
                        label = { Text("Signed in via ${userProfile.providerName}") }
                    )
                    AssistChip(
                        onClick = {},
                        leadingIcon = {
                            Icon(
                                Icons.Filled.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AccentPurple
                            )
                        },
                        label = { Text("Cloud Sync On") }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Surface(
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$recentsCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Recent PDFs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Box(
                            Modifier
                                .width(1.dp)
                                .height(36.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$bookmarksCount",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Bookmarks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                ListItem(
                    headlineContent = { Text("Dark Mode Theme") },
                    supportingContent = { Text(if (darkMode) "Dark appearance active" else "Light appearance active") },
                    leadingContent = {
                        Icon(if (darkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode, null)
                    },
                    trailingContent = {
                        Switch(checked = darkMode, onCheckedChange = onToggleDark)
                    }
                )

                val ctx = LocalContext.current
                val updateScope = rememberCoroutineScope()
                var checkingUpdate by remember { mutableStateOf(false) }
                ListItem(
                    headlineContent = {
                        Text(if (checkingUpdate) "Checking/Downloading Update..." else "App Version v${BuildConfig.VERSION_NAME}")
                    },
                    supportingContent = { Text("Tap to check for new update now") },
                    leadingContent = { Icon(Icons.Filled.SystemUpdate, null, tint = AccentPurple) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !checkingUpdate) {
                            checkingUpdate = true
                            updateScope.launch {
                                val info = runCatching { UpdateChecker.check() }.getOrNull()
                                if (info != null) {
                                    Toast.makeText(ctx, "Downloading v${info.versionName}...", Toast.LENGTH_SHORT).show()
                                    val apk = runCatching { UpdateChecker.downloadApk(ctx, info.url) }.getOrNull()
                                    checkingUpdate = false
                                    if (apk != null) UpdateChecker.install(ctx, apk)
                                    else Toast.makeText(ctx, "Download failed", Toast.LENGTH_LONG).show()
                                } else {
                                    checkingUpdate = false
                                    Toast.makeText(ctx, "You are on the latest version (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                )

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Log Out of Account", fontWeight = FontWeight.SemiBold)
                }
            } else {
                // Not logged in state ('?')
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 36.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Not Signed In (Guest Mode)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sign in with your Google Account or Email to back up and sync your recent PDFs, bookmarks, and reading progress across all devices.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(16.dp))
                ListItem(
                    headlineContent = { Text("Dark Mode Theme") },
                    leadingContent = {
                        Icon(if (darkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode, null)
                    },
                    trailingContent = {
                        Switch(checked = darkMode, onCheckedChange = onToggleDark)
                    }
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In / Create Account", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Dedicated CNIC Duplex 2x5 Sheet Maker Dialog so users can pick Front & Back from Gallery
 * or launch the camera scanner directly from the Home screen icon.
 */
@Composable
private fun CnicMakerDialog(
    onDismiss: () -> Unit,
    onLaunchScanner: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var frontBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var backBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var bwFilter by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    val frontPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                frontBitmap = withContext(Dispatchers.IO) { BitmapIO.decode(context, uri) }
            }
        }
    }
    val backPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                backBitmap = withContext(Dispatchers.IO) { BitmapIO.decode(context, uri) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CNIC 2x5 Duplex Sheet Maker", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Create an A4 Photostat Sheet with 5 pairs (2 columns x 5 rows) of CNIC Front & Back aligned for long-edge flip duplex printing.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = onLaunchScanner,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.DocumentScanner, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Scan Front & Back",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider()
                Text("Or pick existing CNIC images from Gallery:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { frontPicker.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (frontBitmap != null) "1. Front Done" else "1. Pick Front",
                            maxLines = 1,
                            softWrap = false,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    OutlinedButton(
                        onClick = { backPicker.launch("image/*") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (backBitmap != null) "2. Back Done" else "2. Pick Back",
                            maxLines = 1,
                            softWrap = false,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Apply B&W Photostat Filter",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = bwFilter, onCheckedChange = { bwFilter = it })
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = frontBitmap != null && backBitmap != null && !busy,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                onClick = {
                    val f = frontBitmap ?: return@Button
                    val b = backBitmap ?: return@Button
                    busy = true
                    scope.launch {
                        val sheet = withContext(Dispatchers.IO) {
                            val ff = if (bwFilter) ImageFilters.apply(f, FilterType.BW_PRINT) else f
                            val bb = if (bwFilter) ImageFilters.apply(b, FilterType.BW_PRINT) else b
                            val built = CnicLayout.build(front = ff, back = bb, paper = PaperSize.A4)
                            ImageSaver.saveToGallery(context, built, "cnic_duplex_${System.currentTimeMillis()}")
                            built
                        }
                        busy = false
                        onDismiss()
                        PrintHelper(context as Activity).apply {
                            scaleMode = PrintHelper.SCALE_MODE_FIT
                        }.printBitmap("CNIC Duplex Sheet", sheet)
                        Toast.makeText(
                            context,
                            "CNIC 2x5 Sheet saved to Gallery & Print opened!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            ) {
                Text("Build & Print", maxLines = 1, softWrap = false)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", maxLines = 1, softWrap = false)
            }
        }
    )
}

/**
 * Handles standalone tools launched from the Home Launcher Grid after the user picks a PDF.
 */
@Composable
private fun StandalonePdfToolDialog(
    action: String,
    uri: Uri,
    onDismiss: () -> Unit,
    onOpenResultFile: (File) -> Unit,
    onShowOcrText: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var preset by remember { mutableStateOf(CompressionPreset.BALANCED) }

    when (action) {
        "SPLIT_PDF" -> {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text("Split PDF into Pages") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Split every page of the selected PDF into individual PDF files saved in Downloads/Master Sahib PDF.")
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val count = withContext(Dispatchers.IO) {
                                    val files = PdfTools.splitEachPage(context, uri)
                                    var saved = 0
                                    files.forEach { f ->
                                        if (FileExport.savePdfToDownloads(context, f, f.name) != null) saved++
                                    }
                                    saved
                                }
                                busy = false
                                Toast.makeText(
                                    context,
                                    "Split $count page(s) to Downloads/Master Sahib PDF",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                        }
                    ) { Text("Split All Pages") }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
                }
            )
        }

        "COMPRESS_PDF" -> {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text("Compress / Reduce PDF Size") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose compression level to shrink the PDF file size:")
                        CompressionPreset.entries.forEach { p ->
                            FilterChip(
                                selected = preset == p,
                                onClick = { preset = p },
                                label = { Text(p.label, fontSize = 11.sp) }
                            )
                        }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val outFile = withContext(Dispatchers.IO) {
                                    val f = PdfTools.reduceFileSize(context, uri, preset = preset)
                                    FileExport.savePdfToDownloads(context, f, "Compressed_${System.currentTimeMillis()}.pdf")
                                    f
                                }
                                busy = false
                                Toast.makeText(
                                    context,
                                    "Compressed PDF (${outFile.length() / 1024} KB) saved to Downloads",
                                    Toast.LENGTH_LONG
                                ).show()
                                onOpenResultFile(outFile)
                            }
                        }
                    ) { Text("Compress & Open") }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
                }
            )
        }

        "PROTECT_PDF" -> {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text("Protect PDF with Password") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Encrypt this PDF with 128-bit AES password protection:")
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Enter password") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !busy && password.isNotBlank(),
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val f = PdfTools.protectPdf(context, uri, password.trim())
                                        FileExport.savePdfToDownloads(
                                            context,
                                            f,
                                            "Protected_${System.currentTimeMillis()}.pdf"
                                        ) != null
                                    }.getOrDefault(false)
                                }
                                busy = false
                                Toast.makeText(
                                    context,
                                    if (ok) "Protected PDF saved to Downloads/Master Sahib PDF" else "Protection failed",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                        }
                    ) { Text("Encrypt & Save") }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
                }
            )
        }

        "PDF_TO_IMAGE" -> {
            AlertDialog(
                onDismissRequest = { if (!busy) onDismiss() },
                title = { Text("Export PDF Pages to Gallery Images") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Render all pages of this PDF as high-resolution PNG images in Gallery > Master Sahib PDF.")
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
                confirmButton = {
                    Button(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                val savedCount = withContext(Dispatchers.IO) {
                                    var saved = 0
                                    PdfRendererHelper(context.contentResolver, uri).use { helper ->
                                        for (i in 0 until helper.pageCount) {
                                            val bmp = helper.renderPage(i, 2000)
                                            if (ImageSaver.saveToGallery(
                                                    context,
                                                    bmp,
                                                    "pdf_page_${i + 1}_${System.currentTimeMillis()}"
                                                ) != null
                                            ) {
                                                saved++
                                            }
                                        }
                                    }
                                    saved
                                }
                                busy = false
                                Toast.makeText(
                                    context,
                                    "Saved $savedCount page image(s) to Gallery",
                                    Toast.LENGTH_LONG
                                ).show()
                                onDismiss()
                            }
                        }
                    ) { Text("Export All Pages") }
                },
                dismissButton = {
                    TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") }
                }
            )
        }

        "PDF_URDU_OCR", "PDF_ENG_OCR" -> {
            val isUrdu = action == "PDF_URDU_OCR"
            LaunchedEffect(uri, action) {
                busy = true
                val text: String = withContext(Dispatchers.IO) {
                    val bitmaps = PdfRendererHelper(context.contentResolver, uri).use { helper ->
                        val pagesToScan = minOf(helper.pageCount, 5)
                        (0 until pagesToScan).map { helper.renderPage(it, 1800) }
                    }
                    if (isUrdu) {
                        UrduOcr.recognizePages(context, bitmaps)
                    } else {
                        val embedded = PdfTools.extractAllText(context, uri)
                        if (embedded.isNotBlank() && embedded.length > 20) embedded
                        else Ocr.recognizePages(bitmaps)
                    }
                }
                busy = false
                onShowOcrText(
                    if (isUrdu) "Urdu OCR Result" else "English OCR Result",
                    text.ifBlank { "No text found in document." }
                )
            }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(if (isUrdu) "Running Urdu OCR..." else "Running English OCR...") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Processing PDF pages, please wait...")
                    }
                },
                confirmButton = {}
            )
        }
    }
}

@Composable
private fun FilesTabContent(
    subScreen: String?,
    onSelectSubScreen: (String?) -> Unit,
    recents: List<RecentFile>,
    bookmarks: List<Bookmark>,
    settings: SettingsStore,
    savedPlaces: List<String>,
    onBrowseMoreFiles: () -> Unit,
    onOpenWifiTransfer: () -> Unit,
    onAddPlace: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onOpenFile: (File) -> Unit
) {
    val context = LocalContext.current

    when (subScreen) {
        null -> {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                FileOptionRow(
                    icon = Icons.Filled.PhoneAndroid,
                    title = "On This Phone",
                    subtitle = null,
                    onClick = { onSelectSubScreen("PHONE") }
                )
                FileOptionRow(
                    icon = Icons.Filled.CloudQueue,
                    title = "Cloud Documents",
                    subtitle = "${recents.size} synced recent · ${bookmarks.size} bookmarks",
                    onClick = { onSelectSubScreen("CLOUD") }
                )
                FileOptionRow(
                    icon = Icons.Filled.Devices,
                    title = "PC",
                    subtitle = "File transfer with wifi",
                    onClick = onOpenWifiTransfer
                )
                FileOptionRow(
                    icon = Icons.Filled.LocalOffer,
                    title = "Tag List",
                    subtitle = null,
                    onClick = { onSelectSubScreen("TAGS") }
                )
                FileOptionRow(
                    icon = Icons.Filled.FolderOpen,
                    title = "Browse More Files",
                    subtitle = null,
                    onClick = onBrowseMoreFiles
                )

                if (savedPlaces.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "Saved Places",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    savedPlaces.forEach { entry ->
                        val label = entry.substringBefore('|')
                        FileOptionRow(
                            icon = Icons.Filled.FolderSpecial,
                            title = label,
                            subtitle = "Quick access folder",
                            onClick = onBrowseMoreFiles
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onAddPlace() }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = AccentPurple)
                    Spacer(Modifier.width(20.dp))
                    Text(
                        "Add a Place",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AccentPurple,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        "PHONE" -> {
            val localPdfs = remember { scanLocalPdfs(context) }
            if (localPdfs.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No PDFs found in app Downloads folder yet.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBrowseMoreFiles) {
                        Text("Browse All Device Storage")
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        ListItem(
                            headlineContent = { Text("Browse All System Folders (SAF)", color = AccentPurple) },
                            leadingContent = { Icon(Icons.Filled.FolderOpen, null, tint = AccentPurple) },
                            modifier = Modifier.clickableRow { onBrowseMoreFiles() }
                        )
                        HorizontalDivider()
                    }
                    items(localPdfs) { file ->
                        ListItem(
                            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${file.length() / 1024} KB  ·  ${file.parentFile?.name ?: ""}") },
                            leadingContent = { Icon(Icons.Filled.PictureAsPdf, null, tint = Color(0xFFE53935)) },
                            modifier = Modifier.clickableRow { onOpenFile(file) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        "CLOUD" -> {
            if (recents.isEmpty()) {
                EmptyHint("No Cloud Documents synced yet. Open a PDF to sync your reading history to Firebase Cloud.")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(recents) { r ->
                        ListItem(
                            headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("Last page: ${r.lastPage + 1}/${r.pageCount}") },
                            leadingContent = { Icon(Icons.Filled.CloudDone, null, tint = AccentPurple) },
                            modifier = Modifier.clickableRow { onOpenRecent(r) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }

        "TAGS" -> {
            val allTagCategories = listOf("Important", "Work", "Study", "CNIC", "Invoice", "To Print")
            var selectedTag by remember { mutableStateOf<String?>(null) }
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allTagCategories.take(4).forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag) }
                        )
                    }
                }
                val taggedRecents = recents.filter { r ->
                    val tags = settings.getTagsForUri(r.uri)
                    if (selectedTag == null) tags.isNotEmpty() else selectedTag in tags
                }
                if (taggedRecents.isEmpty()) {
                    EmptyHint("No tagged PDFs found. Open any PDF -> More (...) -> Tags to assign tags.")
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(taggedRecents) { r ->
                            val tags = settings.getTagsForUri(r.uri)
                            ListItem(
                                headlineContent = { Text(r.name) },
                                supportingContent = { Text("Tags: ${tags.joinToString(", ")}") },
                                leadingContent = { Icon(Icons.Filled.LocalOffer, null, tint = AccentPurple) },
                                modifier = Modifier.clickableRow { onOpenRecent(r) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TemplatesTabContent(onCreated: (File) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageCount by remember { mutableIntStateOf(5) }
    var paper by remember { mutableStateOf(PaperSize.A4) }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Create New PDF from Template",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperSize.entries.forEach { p ->
                FilterChip(
                    selected = paper == p,
                    onClick = { paper = p },
                    label = { Text(p.name) }
                )
            }
            Spacer(Modifier.width(8.dp))
            listOf(1, 5, 10).forEach { count ->
                FilterChip(
                    selected = pageCount == count,
                    onClick = { pageCount = count },
                    label = { Text("$count p") }
                )
            }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        TemplateStyle.entries.forEach { tpl ->
            ElevatedCard(
                onClick = {
                    if (busy) return@ElevatedCard
                    busy = true
                    scope.launch {
                        val file = withContext(Dispatchers.IO) {
                            val generated = PdfTemplates.generate(context, tpl, pageCount = pageCount, paper = paper)
                            FileExport.savePdfToDownloads(context, generated, generated.name)
                            generated
                        }
                        busy = false
                        Toast.makeText(
                            context,
                            "Created ${tpl.title} in Downloads/Master Sahib PDF",
                            Toast.LENGTH_SHORT
                        ).show()
                        onCreated(file)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                ListItem(
                    headlineContent = { Text(tpl.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("${tpl.subtitle}  ·  ${paper.name} ($pageCount pages)") },
                    leadingContent = {
                        Icon(Icons.Filled.Description, contentDescription = null, tint = AccentPurple)
                    },
                    trailingContent = {
                        Icon(Icons.Filled.AddCircleOutline, contentDescription = "Create", tint = AccentPurple)
                    }
                )
            }
        }
    }
}

@Composable
private fun DiscoverTabContent(
    onPickPdf: () -> Unit,
    onScan: () -> Unit,
    onReadyPhotostat: () -> Unit,
    onCnicMaker: () -> Unit,
    onMerge: () -> Unit,
    onWifiTransfer: () -> Unit,
    onGoTemplates: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Photostat Master · Complete Toolkit",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        ElevatedCard(onClick = onReadyPhotostat, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Ready to Photostat (B&W Laser Filter)") },
                supportingContent = { Text("Instant high-contrast B&W document enhancement for crisp photostat printing") },
                leadingContent = { Icon(Icons.Filled.Print, null, tint = Color(0xFF00897B)) }
            )
        }
        ElevatedCard(onClick = onCnicMaker, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("CNIC 2x5 Duplex Sheet Maker") },
                supportingContent = { Text("Automatic 10-card symmetric layout for front/back long-edge flip printing") },
                leadingContent = { Icon(Icons.Filled.Badge, null, tint = Color(0xFF2E7D32)) }
            )
        }
        ElevatedCard(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Urdu & English OCR Scanner") },
                supportingContent = { Text("Extract Urdu (Nastaliq) & English text from scanned pages to .txt or clipboard") },
                leadingContent = { Icon(Icons.Filled.DocumentScanner, null, tint = AccentPurple) }
            )
        }
        ElevatedCard(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Merge Multiple PDFs") },
                supportingContent = { Text("Combine multiple PDF documents into a single file") },
                leadingContent = { Icon(Icons.Filled.MergeType, null, tint = AccentPurple) }
            )
        }
        ElevatedCard(onClick = onWifiTransfer, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("PC · Wi-Fi File Transfer") },
                supportingContent = { Text("Send & receive PDFs between your phone and computer browser over Wi-Fi") },
                leadingContent = { Icon(Icons.Filled.Wifi, null, tint = AccentPurple) }
            )
        }
        ElevatedCard(onClick = onGoTemplates, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("PDF Stationery & Note Templates") },
                supportingContent = { Text("Generate Blank, Ruled, Grid, Dotted, Cornell, and Checklist PDFs") },
                leadingContent = { Icon(Icons.Filled.DashboardCustomize, null, tint = AccentPurple) }
            )
        }
        ElevatedCard(onClick = onPickPdf, modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Protect, Flatten, Compress & Annotate") },
                supportingContent = { Text("Open any PDF and tap More (...) or the Top Edit Bar for advanced tools") },
                leadingContent = { Icon(Icons.Filled.Security, null, tint = AccentPurple) }
            )
        }
    }
}

@Composable
private fun WifiTransferDialog(
    onDismiss: () -> Unit,
    onFileReceived: (File) -> Unit
) {
    val context = LocalContext.current
    var server by remember { mutableStateOf<WifiTransferServer?>(null) }
    var serverUrl by remember { mutableStateOf("") }
    var lastUploaded by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        val srv = WifiTransferServer(context) { uploaded ->
            lastUploaded = uploaded
        }
        val url = runCatching { srv.start() }.getOrDefault("http://${WifiTransferServer.getLocalIpAddress()}:8080")
        server = srv
        serverUrl = url
        onDispose {
            runCatching { srv.stop() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Wifi, contentDescription = null, tint = AccentPurple) },
        title = { Text("PC · File Transfer with Wi-Fi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Connect your PC and phone to the same Wi-Fi network.")
                Text("2. Open this address in your PC browser:")
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        serverUrl,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Text(
                    "You can upload PDFs from your PC to this phone or download PDFs from this phone to your PC.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (lastUploaded != null) {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { onFileReceived(lastUploaded!!) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Received: ${lastUploaded!!.name}")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Stop & Close") }
        }
    )
}

@Composable
private fun BottomNavTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (selected) AccentPurple else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = tint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

private fun scanLocalPdfs(context: Context): List<File> {
    val result = mutableListOf<File>()
    val dirs = listOfNotNull(
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Master Sahib PDF"
        ),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        context.filesDir,
        context.cacheDir
    )
    dirs.forEach { dir ->
        if (dir.exists() && dir.isDirectory) {
            dir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.endsWith(".pdf", ignoreCase = true)) {
                    result.add(f)
                }
            }
        }
    }
    return result.distinctBy { it.absolutePath }.sortedByDescending { it.lastModified() }
}

private fun loadSavedPlaces(context: Context): List<String> {
    val prefs = context.getSharedPreferences("psm_places", Context.MODE_PRIVATE)
    return (prefs.getStringSet("places", emptySet()) ?: emptySet()).toList()
}

private fun savePlaces(context: Context, places: List<String>) {
    val prefs = context.getSharedPreferences("psm_places", Context.MODE_PRIVATE)
    prefs.edit().putStringSet("places", places.toSet()).apply()
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth().clickable { onClick() }
