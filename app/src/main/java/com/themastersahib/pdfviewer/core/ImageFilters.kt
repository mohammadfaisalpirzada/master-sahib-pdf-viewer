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

    /**
     * Local adaptive thresholding (Bradley-Roth integral image method) + border cleanup
     * so uneven lighting or page edge shadows turn clean white while text/lines stay crisp black.
     */
    internal fun adaptiveThresholdPixels(px: IntArray, w: Int, h: Int): IntArray {
        if (px.isEmpty() || w <= 0 || h <= 0) return px
        val lum = IntArray(w * h)
        val integral = LongArray(w * h)

        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = y * w
            for (x in 0 until w) {
                val p = px[rowOff + x]
                val a = (p ushr 24) and 0xFF
                val l = if (a < 128) {
                    255
                } else {
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    (r * 299 + g * 587 + b * 114) / 1000
                }
                lum[rowOff + x] = l
                rowSum += l
                integral[rowOff + x] = if (y == 0) rowSum else integral[(y - 1) * w + x] + rowSum
            }
        }

        val radius = (minOf(w, h) / 16).coerceIn(12, 96)
        val out = IntArray(w * h)
        val borderMarginX = (w * 0.015f).toInt().coerceAtLeast(2)
        val borderMarginY = (h * 0.015f).toInt().coerceAtLeast(2)

        for (y in 0 until h) {
            val y1 = (y - radius).coerceAtLeast(0)
            val y2 = (y + radius).coerceAtMost(h - 1)
            val rowOff = y * w
            for (x in 0 until w) {
                // Force outer 1.5% page edge frame to clean white to avoid scanner border lines
                if (x < borderMarginX || x >= w - borderMarginX || y < borderMarginY || y >= h - borderMarginY) {
                    out[rowOff + x] = Color.WHITE
                    continue
                }
                val x1 = (x - radius).coerceAtLeast(0)
                val x2 = (x + radius).coerceAtMost(w - 1)
                val count = (x2 - x1 + 1) * (y2 - y1 + 1)

                var sum = integral[y2 * w + x2]
                if (y1 > 0) sum -= integral[(y1 - 1) * w + x2]
                if (x1 > 0) sum -= integral[y2 * w + (x1 - 1)]
                if (y1 > 0 && x1 > 0) sum += integral[(y1 - 1) * w + (x1 - 1)]

                val localMean = (sum / count).toInt()
                val l = lum[rowOff + x]
                val isInk = l < (localMean * 0.87f).toInt() && l < 210
                out[rowOff + x] = if (isInk) Color.BLACK else Color.WHITE
            }
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
        val cleaned = Orientation.cleanOuterBorderWedges(src)
        val w = cleaned.width
        val h = cleaned.height
        val px = IntArray(w * h)
        cleaned.getPixels(px, 0, w, 0, 0, w, h)
        val res = adaptiveThresholdPixels(px, w, h)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(res, 0, w, 0, 0, w, h)
        return out
    }
}
