package com.themastersahib.pdfviewer.data

import android.content.Context

/** User settings via SharedPreferences (dark mode, scroll direction). */
class SettingsStore(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = sp.getBoolean("dark_mode", false)
        set(v) = sp.edit().putBoolean("dark_mode", v).apply()

    /** true = vertical scroll (default), false = horizontal paging. */
    var verticalScroll: Boolean
        get() = sp.getBoolean("vertical_scroll", true)
        set(v) = sp.edit().putBoolean("vertical_scroll", v).apply()
}

/** Resolve a human-readable file name from a content/file Uri. */
object UriNames {
    fun displayName(context: Context, uri: android.net.Uri): String {
        var name = uri.lastPathSegment ?: "document.pdf"
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx) ?: name
            }
        }
        return name
    }
}
