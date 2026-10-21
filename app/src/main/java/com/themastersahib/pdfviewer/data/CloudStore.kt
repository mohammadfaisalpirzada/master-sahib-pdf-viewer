package com.themastersahib.pdfviewer.data

import android.content.Context
import android.util.Base64
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-user cloud data in Firestore. Everything lives under users/{uid}/…, and
 * security rules restrict each user to their own uid — so a logged-in user only
 * ever sees their own recents, bookmarks, and created PDFs history.
 * Also maintains a local mirror in SharedPreferences so created PDF records are
 * never lost offline and automatically sync to Firestore when logged in.
 */
object CloudStore {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()
    private val bgScope = CoroutineScope(Dispatchers.IO)

    private fun recents(uid: String) = db.collection("users").document(uid).collection("recents")
    private fun bookmarks(uid: String) = db.collection("users").document(uid).collection("bookmarks")
    private fun createdPdfs(uid: String) = db.collection("users").document(uid).collection("created_pdfs")

    // Firestore doc ids can't contain '/', so encode the uri.
    private fun docId(uri: String): String =
        Base64.encodeToString(uri.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            .take(120)

    private fun bookmarkDocId(uri: String, page: Int): String = "${docId(uri)}_p$page"

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
                    id = d.id.hashCode().toLong(),
                    uri = uri,
                    name = d.getString("name") ?: uri,
                    page = (d.getLong("page") ?: 0L).toInt(),
                    createdAt = d.getLong("createdAt") ?: 0L
                )
            }

    suspend fun addBookmark(uid: String, b: Bookmark) {
        bookmarks(uid).document(bookmarkDocId(b.uri, b.page)).set(
            mapOf("uri" to b.uri, "name" to b.name, "page" to b.page, "createdAt" to b.createdAt),
            SetOptions.merge()
        ).await()
    }

    suspend fun deleteBookmark(uid: String, b: Bookmark) {
        bookmarks(uid).document(bookmarkDocId(b.uri, b.page)).delete().await()
        val legacy = bookmarks(uid)
            .whereEqualTo("uri", b.uri)
            .whereEqualTo("page", b.page)
            .get().await()
        for (doc in legacy.documents) {
            doc.reference.delete().await()
        }
    }

    /**
     * Fetch the user's created PDFs history from Firestore, merged with local records.
     */
    suspend fun getCreatedPdfs(context: Context, uid: String?): List<CreatedPdfRecord> {
        val local = getLocalCreatedPdfs(context)
        if (uid.isNullOrBlank()) return local
        val remote = runCatching {
            createdPdfs(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(150).get().await()
                .documents.mapNotNull { d ->
                    val uri = d.getString("uri") ?: return@mapNotNull null
                    CreatedPdfRecord(
                        id = d.id,
                        uri = uri,
                        name = d.getString("name") ?: "Document.pdf",
                        toolUsed = d.getString("toolUsed") ?: "PDF Tool",
                        sizeBytes = d.getLong("sizeBytes") ?: 0L,
                        pageCount = (d.getLong("pageCount") ?: 0L).toInt(),
                        createdAt = d.getLong("createdAt") ?: 0L
                    )
                }
        }.getOrDefault(emptyList())

        // Sync any local-only records up to Firestore
        val remoteIds = remote.map { it.id }.toSet()
        local.filter { it.id !in remoteIds }.forEach { item ->
            runCatching { upsertCreatedPdfRemote(uid, item) }
        }

        return (remote + local)
            .distinctBy { it.uri }
            .sortedByDescending { it.createdAt }
    }

    suspend fun upsertCreatedPdfRemote(uid: String, record: CreatedPdfRecord) {
        val id = record.id.ifBlank { docId(record.uri) }
        createdPdfs(uid).document(id).set(
            mapOf(
                "uri" to record.uri,
                "name" to record.name,
                "toolUsed" to record.toolUsed,
                "sizeBytes" to record.sizeBytes,
                "pageCount" to record.pageCount,
                "createdAt" to record.createdAt
            ),
            SetOptions.merge()
        ).await()
        // Also add to recents so it appears in both places
        upsertRecent(
            uid,
            RecentFile(
                uri = record.uri,
                name = record.name,
                lastOpened = record.createdAt,
                lastPage = 0,
                pageCount = record.pageCount
            )
        )
    }

    suspend fun deleteCreatedPdf(context: Context, uid: String?, record: CreatedPdfRecord) {
        deleteLocalCreatedPdf(context, record.uri)
        if (!uid.isNullOrBlank()) {
            runCatching { createdPdfs(uid).document(record.id.ifBlank { docId(record.uri) }).delete().await() }
        }
    }

    /**
     * Fire-and-forget helper called whenever any PDF is created/saved in the app.
     * Saves immediately to local SharedPreferences and syncs to Firestore under users/{uid}/created_pdfs.
     */
    fun recordCreatedPdfAsync(
        context: Context,
        uriString: String,
        displayName: String,
        toolUsed: String,
        sizeBytes: Long = 0L,
        pageCount: Int = 0
    ) {
        val now = System.currentTimeMillis()
        val id = docId(uriString)
        val record = CreatedPdfRecord(
            id = id,
            uri = uriString,
            name = displayName,
            toolUsed = toolUsed,
            sizeBytes = sizeBytes,
            pageCount = pageCount,
            createdAt = now
        )
        saveLocalCreatedPdf(context, record)
        val currentUid = Auth.uid
        if (!currentUid.isNullOrBlank()) {
            bgScope.launch {
                runCatching { upsertCreatedPdfRemote(currentUid, record) }
            }
        }
    }

    private fun saveLocalCreatedPdf(context: Context, record: CreatedPdfRecord) {
        val prefs = context.getSharedPreferences("psm_created_pdfs", Context.MODE_PRIVATE)
        val existing = getLocalCreatedPdfs(context).filterNot { it.uri == record.uri }
        val updated = (listOf(record) + existing).take(150)
        val arr = JSONArray()
        updated.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("uri", r.uri)
                    put("name", r.name)
                    put("toolUsed", r.toolUsed)
                    put("sizeBytes", r.sizeBytes)
                    put("pageCount", r.pageCount)
                    put("createdAt", r.createdAt)
                }
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun deleteLocalCreatedPdf(context: Context, uriString: String) {
        val prefs = context.getSharedPreferences("psm_created_pdfs", Context.MODE_PRIVATE)
        val updated = getLocalCreatedPdfs(context).filterNot { it.uri == uriString }
        val arr = JSONArray()
        updated.forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("uri", r.uri)
                    put("name", r.name)
                    put("toolUsed", r.toolUsed)
                    put("sizeBytes", r.sizeBytes)
                    put("pageCount", r.pageCount)
                    put("createdAt", r.createdAt)
                }
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    private fun getLocalCreatedPdfs(context: Context): List<CreatedPdfRecord> {
        val prefs = context.getSharedPreferences("psm_created_pdfs", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<CreatedPdfRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val uri = o.optString("uri", "")
                if (uri.isNotBlank()) {
                    list.add(
                        CreatedPdfRecord(
                            id = o.optString("id", docId(uri)),
                            uri = uri,
                            name = o.optString("name", "Document.pdf"),
                            toolUsed = o.optString("toolUsed", "PDF Tool"),
                            sizeBytes = o.optLong("sizeBytes", 0L),
                            pageCount = o.optInt("pageCount", 0),
                            createdAt = o.optLong("createdAt", 0L)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }
}
