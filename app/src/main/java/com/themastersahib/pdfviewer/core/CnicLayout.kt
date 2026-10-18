package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect

/**
 * CNIC DUPLEX LAYOUT — the photocopy-shop trick.
 *
 * One page, 2 columns × N rows (default 5):
 *   • LEFT  column  = CNIC FRONT, repeated N times
 *   • RIGHT column  = CNIC BACK,  repeated N times
 *
 * Why the back lands behind the front automatically:
 * The two columns are placed SYMMETRICALLY about the page's vertical centre
 * (equal left & right margins). So the front sits at x = marginX, and the back
 * sits at x = pageW − marginX − cardW. When you print this SAME page again on
 * the reverse side and flip the sheet along its LONG edge (left ↔ right),
 * every point at x maps to (pageW − x − cardW). The back column therefore maps
 * exactly onto the front column's position, row for row — so each card prints
 * front on one face and back on the other, perfectly aligned.
 *
 * Print instructions for the user:
 *   1. Print this page.
 *   2. Put the SAME sheet back in the tray and print the SAME page again.
 *   3. Flip on the LONG edge (left-to-right), NOT the short edge.
 *
 * Card size = ISO/IEC 7810 ID-1 (CNIC / credit-card) = 85.6 × 54 mm.
 */
object CnicLayout {

    private const val CARD_W_MM = 85.6f
    private const val CARD_H_MM = 54f

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
        // Cards are landscape on the sheet.
        val frontL = Orientation.toLandscape(front)
        val backL = Orientation.toLandscape(back)

        val pageW = mmToPx(paper.wMm, dpi)
        val pageH = mmToPx(paper.hMm, dpi)
        val cardW = mmToPx(CARD_W_MM, dpi)
        val cardH = mmToPx(CARD_H_MM, dpi)
        val colGap = mmToPx(columnGapMm, dpi)
        val rowGap = mmToPx(rowGapMm, dpi)

        // Symmetric columns — THIS is what makes the duplex flip align.
        val marginX = (pageW - (cardW * 2 + colGap)) / 2
        val leftX = marginX
        val rightX = pageW - marginX - cardW   // == leftX + cardW + colGap

        val blockH = cardH * copies + rowGap * (copies - 1)
        val marginY = (pageH - blockH) / 2

        val out = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        for (i in 0 until copies) {
            val top = marginY + i * (cardH + rowGap)
            val leftRect = Rect(leftX, top, leftX + cardW, top + cardH)
            val rightRect = Rect(rightX, top, rightX + cardW, top + cardH)
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
