package com.themastersahib.pdfviewer.core

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs

/** Normalized bounding box in page fraction coordinates (0f..1f). */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class PageSearchMatches(
    val pageIndex: Int,
    val rects: List<NormalizedRect>
)

/**
 * Text search via PdfBox-Android. Extracts text and exact glyph coordinates
 * page by page so the reader can both jump to matching pages and draw highlight
 * boxes directly over matching words on the rendered PDF page.
 */
object PdfTextSearch {

    fun findPages(context: Context, uri: Uri, query: String, password: String = ""): List<Int> =
        search(context, uri, query, password).map { it.pageIndex }

    fun search(
        context: Context,
        uri: Uri,
        query: String,
        password: String = ""
    ): List<PageSearchMatches> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        val results = mutableListOf<PageSearchMatches>()

        context.contentResolver.openInputStream(uri)?.use { input ->
            val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
            doc.use { pd ->
                for (pageNum in 1..pd.numberOfPages) {
                    val chars = mutableListOf<Char>()
                    val positions = mutableListOf<TextPosition?>()

                    val stripper = object : PDFTextStripper() {
                        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                            if (text.isNullOrEmpty()) return
                            for (i in text.indices) {
                                chars.add(text[i])
                                positions.add(textPositions?.getOrNull(i))
                            }
                            chars.add(' ')
                            positions.add(null)
                        }
                    }.apply {
                        sortByPosition = true
                        startPage = pageNum
                        endPage = pageNum
                    }

                    stripper.getText(pd)
                    val pageText = String(chars.toCharArray())
                    val rects = findMatchRects(pageText, positions, needle)
                    if (rects.isNotEmpty() || pageText.contains(needle, ignoreCase = true)) {
                        results.add(PageSearchMatches(pageIndex = pageNum - 1, rects = rects))
                    }
                }
            }
        }
        return results
    }

    internal fun findMatchRects(
        pageText: String,
        positions: List<TextPosition?>,
        needle: String
    ): List<NormalizedRect> {
        if (needle.isEmpty() || pageText.isEmpty()) return emptyList()
        val rects = mutableListOf<NormalizedRect>()
        var idx = pageText.indexOf(needle, startIndex = 0, ignoreCase = true)
        while (idx >= 0) {
            val end = (idx + needle.length).coerceAtMost(positions.size)
            val slice = positions.subList(idx, end).filterNotNull()
            if (slice.isNotEmpty()) {
                // Group glyphs by line (similar Y coordinate) so multi-line matches produce clean boxes
                val lines = mutableListOf<MutableList<TextPosition>>()
                for (tp in slice) {
                    val lastLine = lines.lastOrNull()
                    if (lastLine == null || abs(lastLine.first().yDirAdj - tp.yDirAdj) > tp.heightDir * 1.2f) {
                        lines.add(mutableListOf(tp))
                    } else {
                        lastLine.add(tp)
                    }
                }
                for (line in lines) {
                    val pw = line.first().pageWidth.takeIf { it > 0f } ?: continue
                    val ph = line.first().pageHeight.takeIf { it > 0f } ?: continue
                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (tp in line) {
                        val l = tp.xDirAdj
                        val r = tp.xDirAdj + tp.widthDirAdj
                        val b = tp.yDirAdj
                        val t = tp.yDirAdj - tp.heightDir.coerceAtLeast(4f) * 1.15f
                        if (l < minX) minX = l
                        if (r > maxX) maxX = r
                        if (t < minY) minY = t
                        if (b > maxY) maxY = b
                    }
                    rects.add(
                        NormalizedRect(
                            left = (minX / pw).coerceIn(0f, 1f),
                            top = (minY / ph).coerceIn(0f, 1f),
                            right = (maxX / pw).coerceIn(0f, 1f),
                            bottom = (maxY / ph).coerceIn(0f, 1f)
                        )
                    )
                }
            }
            idx = pageText.indexOf(needle, startIndex = idx + 1, ignoreCase = true)
        }
        return rects
    }
}
