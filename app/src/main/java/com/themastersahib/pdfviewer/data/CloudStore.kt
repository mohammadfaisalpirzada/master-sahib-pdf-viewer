package com.themastersahib.pdfviewer.data

import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Per-user cloud data in Firestore. Everything lives under users/{uid}/…, and
 * security rules restrict each user to their own uid — so a logged-in user only
 * ever sees their own recents/bookmarks. Firestore's offline persistence (on by
 * default) means the data is available offline and syncs when back online, so a
 * user can leave and continue where they left off.
 */
object CloudStore {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun recents(uid: String) = db.collection("users").document(uid).collection("recents")
    private fun bookmarks(uid: String) = db.collection("users").document(uid).collection("bookmarks")

    // Firestore doc ids can't contain '/', so encode the uri.
    private fun docId(uri: String): String =
        Base64.encodeToString(uri.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    suspend fun getRecents(uid: String): List<RecentFile> =
        recents(uid).orderBy("lastOpened", Query.Direction.DESCENDING).limit(100).get().await()
            .documents.mapNotNull { d ->
                val uri = d.getString("uri") ?: return@mapNotNull null
                RecentFile(
                    uri = uri,
                    name = d.getString("name") ?: uri,
                    lastOpened = d.getLong("lastOpened") ?: 0L,
                    lastPage = (d.getLong("lastPage") ?: 0L).toInt(),
                    pageCount = (d.getLong("pageCount") ?: 0L).toInt()
                )
            }

    suspend fun upsertRecent(uid: String, r: RecentFile) {
        recents(uid).document(docId(r.uri)).set(
            mapOf(
                "uri" to r.uri, "name" to r.name, "lastOpened" to r.lastOpened,
                "lastPage" to r.lastPage, "pageCount" to r.pageCount
            ), SetOptions.merge()
        ).await()
    }

    suspend fun updateLastPage(uid: String, uri: String, page: Int) {
        recents(uid).document(docId(uri)).set(mapOf("lastPage" to page), SetOptions.merge()).await()
    }

    suspend fun deleteRecent(uid: String, uri: String) {
        recents(uid).document(docId(uri)).delete().await()
    }

    suspend fun getBookmarks(uid: String): List<Bookmark> =
        bookmarks(uid).orderBy("createdAt", Query.Direction.DESCENDING).get().await()
            .documents.mapNotNull { d ->
                val uri = d.getString("uri") ?: return@mapNotNull null
                Bookmark(
                    id = 0,
                    uri = uri,
                    name = d.getString("name") ?: uri,
                    page = (d.getLong("page") ?: 0L).toInt(),
                    createdAt = d.getLong("createdAt") ?: 0L
                )
            }

    suspend fun addBookmark(uid: String, b: Bookmark) {
        bookmarks(uid).add(
            mapOf("uri" to b.uri, "name" to b.name, "page" to b.page, "createdAt" to b.createdAt)
        ).await()
    }
}
