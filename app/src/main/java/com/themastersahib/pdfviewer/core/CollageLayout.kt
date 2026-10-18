package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Collage mode — arrange several images in a grid on one page.
 * If rows/cols aren't given, a near-square grid is chosen automatically.
 */
object CollageLayout {

    fun build(
        images: List<Bitmap>,
        dpi: Int = 200,
        paper: PaperSize = PaperSize.A4,
        rows: Int = 0,
        cols: Int = 0,
        gapMm: Float = 4f,
        marginMm: Float = 8f
    ): Bitmap {
        require(images.isNotEmpty()) { "No images for collage" }

        val n = images.size
        val c = if (cols > 0) cols else ceil(sqrt(n.toDouble())).toInt()
        val r = if (rows > 0) rows else ceil(n.toDouble() / c).toInt()

        val pageW = mmToPx(paper.wMm, dpi)
        val pageH = mmToPx(paper.hMm, dpi)
        val margin = mmToPx(marginMm, dpi)
        val gap = mmToPx(gapMm, dpi)

        val cellW = (pageW - margin * 2 - gap * (c - 1)) / c
        val cellH = (pageH - margin * 2 - gap * (r - 1)) / r

        val out = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        images.forEachIndexed { i, bmp ->
            val row = i / c
            val col = i % c
            if (row >= r) return@forEachIndexed
            val cellLeft = margin + col * (cellW + gap)
            val cellTop = margin + row * (cellH + gap)
            val dest = fit(bmp.width, bmp.height, cellLeft, cellTop, cellW, cellH)
            canvas.drawBitmap(bmp, null, dest, paint)
        }
        return out
    }

    private fun fit(sw: Int, sh: Int, cellL: Int, cellT: Int, cellW: Int, cellH: Int): Rect {
        val scale = minOf(cellW.toFloat() / sw, cellH.toFloat() / sh)
        val w = (sw * scale).toInt()
        val h = (sh * scale).toInt()
        val left = cellL + (cellW - w) / 2
        val top = cellT + (cellH - h) / 2
        return Rect(left, top, left + w, top + h)
    }
}
