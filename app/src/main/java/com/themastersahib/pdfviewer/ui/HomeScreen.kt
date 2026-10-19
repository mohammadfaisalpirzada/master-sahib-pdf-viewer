package com.themastersahib.pdfviewer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.themastersahib.pdfviewer.data.Bookmark
import com.themastersahib.pdfviewer.data.RecentFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    recents: List<RecentFile>,
    bookmarks: List<Bookmark>,
    darkMode: Boolean,
    onToggleDark: (Boolean) -> Unit,
    onLogout: () -> Unit,
    onPickPdf: () -> Unit,
    onScan: () -> Unit,
    onMerge: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onOpenBookmark: (Bookmark) -> Unit,
    onDeleteRecent: (RecentFile) -> Unit,
    onDeleteBookmark: (Bookmark) -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    var searchFilter by remember { mutableStateOf("") }
    val df = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    val filteredRecents = remember(recents, searchFilter) {
        if (searchFilter.isBlank()) recents
        else recents.filter { it.name.contains(searchFilter.trim(), ignoreCase = true) }
    }
    val filteredBookmarks = remember(bookmarks, searchFilter) {
        if (searchFilter.isBlank()) bookmarks
        else bookmarks.filter { it.name.contains(searchFilter.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photostat Master") },
                actions = {
                    IconButton(onClick = { onToggleDark(!darkMode) }) {
                        Icon(
                            if (darkMode) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle theme"
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Filled.Logout, contentDescription = "Log out")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onPickPdf,
                icon = { Icon(Icons.Filled.FolderOpen, null) },
                text = { Text("Open PDF") }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Quick actions
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text("Scan")
                }
                OutlinedButton(onClick = onMerge) {
                    Icon(Icons.Filled.MergeType, null); Spacer(Modifier.width(6.dp)); Text("Merge PDFs")
                }
                OutlinedButton(onClick = onPickPdf) {
                    Icon(Icons.Filled.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Browse Device")
                }
            }

            if (recents.size > 3 || bookmarks.size > 3) {
                OutlinedTextField(
                    value = searchFilter,
                    onValueChange = { searchFilter = it },
                    placeholder = { Text("Filter recent files or bookmarks…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchFilter.isNotEmpty()) {
                            IconButton(onClick = { searchFilter = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recent (${filteredRecents.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks (${filteredBookmarks.size})") })
            }

            when (tab) {
                0 -> {
                    if (filteredRecents.isEmpty()) EmptyHint("No recent files yet. Open or scan a PDF to get started.")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(filteredRecents) { r ->
                            ListItem(
                                headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    val pg = if (r.pageCount > 0) "p.${r.lastPage + 1}/${r.pageCount}" else ""
                                    Text("${df.format(Date(r.lastOpened))}  $pg")
                                },
                                leadingContent = { Icon(Icons.Filled.PictureAsPdf, null) },
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
                1 -> {
                    if (filteredBookmarks.isEmpty()) EmptyHint("No bookmarks yet. Open a PDF and tap the bookmark icon.")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(filteredBookmarks) { b ->
                            ListItem(
                                headlineContent = { Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("Page ${b.page + 1}") },
                                leadingContent = { Icon(Icons.Filled.Bookmark, null) },
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
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth().clickable { onClick() }
