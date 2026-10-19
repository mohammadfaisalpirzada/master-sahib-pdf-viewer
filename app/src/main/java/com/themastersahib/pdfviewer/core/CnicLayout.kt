package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

data class CnicSheetGeometry(
    val pageW: Int,
    val pageH: Int,
    val cardW: Int,
    val cardH: Int,
    val leftX: Int,
    val rightX: Int,
    val marginY: Int,
    val rowGap: Int
)

/**
 * CNIC DUPLEX LAYOUT — the photocopy-shop trick.
 *
 * One page, 2 columns × N rows (default 5):
 *   • LEFT  column  = CNIC FRONT, repeated N times
 *   • RIGHT column  = CNIC BACK,  repeated N times
 *
 * The two columns are placed SYMMETRICALLY about the page's vertical centre
 * (equal left & right margins). When printed again on the reverse side and
 * flipped along the LONG edge (left ↔ right), the back column maps onto the
 * front column's position, row for row.
 *
 * Card size = ISO/IEC 7810 ID-1 (CNIC / credit-card) = 85.6 × 54 mm.
 */
object CnicLayout {

    private const val CARD_W_MM = 85.6f
    private const val CARD_H_MM = 54f

    internal fun computeGeometry(
        copies: Int = 5,
        dpi: Int = 300,
        paper: PaperSize = PaperSize.A4,
        columnGapMm: Float = 8f,
        rowGapMm: Float = 4f
    ): CnicSheetGeometry {
        val pageW = mmToPx(paper.wMm, dpi)
        val pageH = mmToPx(paper.hMm, dpi)
        val cardW = mmToPx(CARD_W_MM, dpi)
        val cardH = mmToPx(CARD_H_MM, dpi)
        val colGap = mmToPx(columnGapMm, dpi)
        val rowGap = mmToPx(rowGapMm, dpi)

        val marginX = (pageW - (cardW * 2 + colGap)) / 2
        val leftX = marginX
        val rightX = pageW - marginX - cardW

        val blockH = cardH * copies + rowGap * (copies - 1)
        val marginY = (pageH - blockH) / 2

        return CnicSheetGeometry(pageW, pageH, cardW, cardH, leftX, rightX, marginY, rowGap)
    }

    fun build(
        front: Bitmap,
        back: Bitmap,
        copies: Int = 5,
        dpi: Int = 300,
        paper: PaperSize = PaperSize.A4,
        columnGapMm: Float = 8f,
        rowGapMm: Float = 4f,
        drawCutMarks: Boolean = true
    ): Bitmap {
        val frontL = Orientation.toLandscape(front)
        val backL = Orientation.toLandscape(back)
        val g = computeGeometry(copies, dpi, paper, columnGapMm, rowGapMm)

        val out = Bitmap.createBitmap(g.pageW, g.pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until copies) {
            val top = g.marginY + i * (g.cardH + g.rowGap)
            val leftRect = Rect(g.leftX, top, g.leftX + g.cardW, top + g.cardH)
            val rightRect = Rect(g.rightX, top, g.rightX + g.cardW, top + g.cardH)
            canvas.drawBitmap(frontL, null, leftRect, paint)
            canvas.drawBitmap(backL, null, rightRect, paint)
            if (drawCutMarks) {
                drawBorder(canvas, leftRect)
                drawBorder(canvas, rightRect)
            }
        }
        return out
    }

    private fun drawBorder(canvas: Canvas, r: Rect) {
        val p = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 1.5f
        }
        canvas.drawRect(r, p)
    }
}
