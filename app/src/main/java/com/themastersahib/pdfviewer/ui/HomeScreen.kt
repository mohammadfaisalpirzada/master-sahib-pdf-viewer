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
    onDeleteRecent: (RecentFile) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val df = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

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
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScan) {
                    Icon(Icons.Filled.DocumentScanner, null); Spacer(Modifier.width(6.dp)); Text("Scan")
                }
                OutlinedButton(onClick = onMerge) {
                    Icon(Icons.Filled.MergeType, null); Spacer(Modifier.width(6.dp)); Text("Merge PDFs")
                }
            }

            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Recent") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bookmarks") })
            }

            when (tab) {
                0 -> {
                    if (recents.isEmpty()) EmptyHint("No recent files yet. Open or scan a PDF to get started.")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(recents) { r ->
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
                    if (bookmarks.isEmpty()) EmptyHint("No bookmarks yet. Open a PDF and tap the bookmark icon.")
                    else LazyColumn(Modifier.fillMaxSize()) {
                        items(bookmarks) { b ->
                            ListItem(
                                headlineContent = { Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("Page ${b.page + 1}") },
                                leadingContent = { Icon(Icons.Filled.Bookmark, null) },
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
