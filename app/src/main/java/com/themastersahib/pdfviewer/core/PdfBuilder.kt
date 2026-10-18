package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Build a multi-page PDF from bitmaps using Android's built-in PdfDocument.
 * Each bitmap becomes one page, scaled to fit the chosen paper with margins.
 */
object PdfBuilder {

    fun build(
        pages: List<Bitmap>,
        outFile: File,
        paper: PaperSize = PaperSize.A4,
        dpi: Int = 200,
        marginMm: Float = 8f
    ): File {
        val pageW = mmToPx(paper.wMm, dpi)
        val pageH = mmToPx(paper.hMm, dpi)
        val margin = mmToPx(marginMm, dpi)

        val doc = PdfDocument()
        pages.forEachIndexed { index, bmp ->
            // If the image is landscape, make the PDF page landscape too.
            val landscape = bmp.width > bmp.height
            val w = if (landscape) pageH else pageW
            val h = if (landscape) pageW else pageH

            val info = PdfDocument.PageInfo.Builder(w, h, index + 1).create()
            val page = doc.startPage(info)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            val dest = fitRect(bmp.width, bmp.height, w, h, margin)
            canvas.drawBitmap(bmp, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
            doc.finishPage(page)
        }

        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun fitRect(srcW: Int, srcH: Int, pageW: Int, pageH: Int, margin: Int): Rect {
        val availW = pageW - margin * 2
        val availH = pageH - margin * 2
        val scale = minOf(availW.toFloat() / srcW, availH.toFloat() / srcH)
        val drawW = (srcW * scale).toInt()
        val drawH = (srcH * scale).toInt()
        val left = (pageW - drawW) / 2
        val top = (pageH - drawH) / 2
        return Rect(left, top, left + drawW, top + drawH)
    }
}
