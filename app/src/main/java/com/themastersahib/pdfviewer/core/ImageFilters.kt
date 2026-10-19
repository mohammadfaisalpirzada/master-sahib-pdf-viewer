package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class FilterType { NONE, GRAYSCALE, BW_PRINT, MAGIC }

object ImageFilters {

    fun apply(src: Bitmap, type: FilterType): Bitmap = when (type) {
        FilterType.NONE -> src
        FilterType.GRAYSCALE -> matrix(src, grayscaleMatrix())
        FilterType.MAGIC -> matrix(src, magicMatrix())
        FilterType.BW_PRINT -> blackWhiteForPrint(src)
    }

    /** BULK: apply the same filter to a whole scan session. */
    fun applyAll(pages: List<Bitmap>, type: FilterType): List<Bitmap> =
        if (type == FilterType.NONE) pages else pages.map { apply(it, type) }

    internal fun thresholdPixels(px: IntArray): IntArray {
        if (px.isEmpty()) return px
        var sum = 0L
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sum += (r * 299 + g * 587 + b * 114) / 1000
        }
        val mean = (sum / px.size).toInt()
        val threshold = (mean * 0.92f).toInt()

        val out = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            out[i] = if (lum < threshold) Color.BLACK else Color.WHITE
        }
        return out
    }

    private fun matrix(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        })
        return out
    }

    private fun grayscaleMatrix() = ColorMatrix().apply { setSaturation(0f) }

    private fun magicMatrix(): ColorMatrix {
        val contrast = 1.25f
        val t = (-0.5f * contrast + 0.5f) * 255f + 12f
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, t,
            0f, contrast, 0f, 0f, t,
            0f, 0f, contrast, 0f, t,
            0f, 0f, 0f, 1f, 0f
        ))
        cm.postConcat(ColorMatrix().apply { setSaturation(1.3f) })
        return cm
    }

    private fun blackWhiteForPrint(src: Bitmap): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val res = thresholdPixels(px)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(res, 0, w, 0, 0, w, h)
        return out
    }
}
