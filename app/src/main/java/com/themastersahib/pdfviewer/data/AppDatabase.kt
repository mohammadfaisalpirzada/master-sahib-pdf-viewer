package com.themastersahib.pdfviewer.data

/**
 * Data models for per-user recent files, bookmarks, and created PDF history synced via CloudStore (Firestore).
 */
data class RecentFile(
    val uri: String,
    val name: String,
    val lastOpened: Long,
    val lastPage: Int = 0,
    val pageCount: Int = 0
)

data class Bookmark(
    val id: Long = 0,
    val uri: String,
    val name: String,
    val page: Int,
    val createdAt: Long
)

data class CreatedPdfRecord(
    val id: String,
    val uri: String,
    val name: String,
    val toolUsed: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val createdAt: Long
)
