package com.themastersahib.pdfviewer.core

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/**
 * Text search via PdfBox-Android. Extracts text page by page and returns the
 * 0-based indices of pages that contain the query.
 *
 * Note: this finds and navigates to matching PAGES. Highlighting the exact word
 * on the rendered page needs glyph coordinates (a later enhancement).
 */
object PdfTextSearch {

    fun findPages(context: Context, uri: Uri, query: String): List<Int> {
        if (query.isBlank()) return emptyList()
        val matches = mutableListOf<Int>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                for (i in 1..doc.numberOfPages) {
                    stripper.startPage = i
                    stripper.endPage = i
                    val text = stripper.getText(doc)
                    if (text.contains(query, ignoreCase = true)) matches.add(i - 1)
                }
            }
        }
        return matches
    }
}
