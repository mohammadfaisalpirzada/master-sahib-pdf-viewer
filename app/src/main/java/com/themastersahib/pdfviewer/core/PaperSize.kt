package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Matrix
import kotlin.math.roundToInt

/** Physical paper sizes in millimetres. */
enum class PaperSize(val wMm: Float, val hMm: Float) {
    A4(210f, 297f),
    LETTER(216f, 279f),
    LEGAL(216f, 356f)
}

/** Convert millimetres to pixels at a given DPI (dots per inch). */
fun mmToPx(mm: Float, dpi: Int): Int = (mm / 25.4f * dpi).roundToInt()

object Orientation {
    /** Auto: rotate so the image becomes landscape (width >= height). */
    fun toLandscape(src: Bitmap): Bitmap =
        if (src.width >= src.height) src else rotate(src, 90f)

    /** Auto: rotate so the image becomes portrait (height >= width). */
    fun toPortrait(src: Bitmap): Bitmap =
        if (src.height >= src.width) src else rotate(src, 90f)

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
