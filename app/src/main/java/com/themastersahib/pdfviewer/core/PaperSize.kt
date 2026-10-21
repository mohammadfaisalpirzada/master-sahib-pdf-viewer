package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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

    /**
     * Rotate [src] by [degrees].
     * For 90/180/270 degrees, uses exact matrix rotation.
     * For arbitrary small angles (deskew), preserves original dimensions and fills exposed
     * corners with pure WHITE so B&W filters never turn corners black.
     */
    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val norm = ((degrees % 360f) + 360f) % 360f
        if (norm == 0f) return src
        if (norm == 90f || norm == 180f || norm == 270f) {
            val m = Matrix().apply { postRotate(norm) }
            return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val m = Matrix().apply {
            postRotate(degrees, src.width / 2f, src.height / 2f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, m, paint)
        return out
    }

    /**
     * Automatically detects document skew angle (-15°..+15°) using Horizontal Edge
     * variance maximization on inner text/form lines, then rotates the page onto a clean white
     * canvas so skewed text and lines become horizontal ("seedha").
     */
    fun autoDeskew(src: Bitmap): Bitmap {
        val angle = estimateSkewAngle(src)
        val cleaned = cleanOuterBorderWedges(src)
        return if (abs(angle) >= 0.25f) {
            rotate(cleaned, -angle)
        } else {
            cleaned
        }
    }

    /**
     * Estimates the clockwise tilt angle (in degrees) of horizontal text/lines in [src].
     * Uses vertical gradient edge transitions so thin form lines, text baselines,
     * and table dividers are detected with high sensitivity regardless of background lighting.
     */
    fun estimateSkewAngle(src: Bitmap): Float {
        if (src.width < 80 || src.height < 80) return 0f
        val targetW = 400
        val scale = targetW.toFloat() / src.width.toFloat()
        val w = targetW
        val h = (src.height * scale).roundToInt().coerceAtLeast(80)
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        // Ignore outer 8% margin so dark page borders/shadows don't bias the text angle
        val marginX = (w * 0.08f).toInt()
        val marginY = (h * 0.08f).toInt()

        // 1. Compute luminance map
        val lum = IntArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val c = px[rowOff + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                lum[rowOff + x] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }

        // 2. Collect coordinates with significant vertical gradient (top/bottom edges of text/lines)
        val maxPoints = (w * h) / 4
        val edgeX = IntArray(maxPoints)
        val edgeY = IntArray(maxPoints)
        var nEdges = 0
        val cx = w / 2
        val cy = h / 2

        for (y in (marginY + 1) until (h - marginY)) {
            val rowOff = y * w
            val prevRowOff = (y - 1) * w
            for (x in marginX until (w - marginX)) {
                val diff = abs(lum[rowOff + x] - lum[prevRowOff + x])
                // Strong horizontal edge (text baseline or form divider line)
                if (diff >= 28 && nEdges < maxPoints) {
                    edgeX[nEdges] = x - cx
                    edgeY[nEdges] = y - cy
                    nEdges++
                }
            }
        }
        if (nEdges < 40) return 0f

        val hist = IntArray(h * 3)
        val offset = h * 3 / 2

        fun scoreAngle(deg: Float): Long {
            val rad = Math.toRadians(deg.toDouble())
            val sinA = sin(rad)
            val cosA = cos(rad)
            java.util.Arrays.fill(hist, 0)
            for (i in 0 until nEdges) {
                val projY = (-edgeX[i] * sinA + edgeY[i] * cosA).roundToInt() + offset
                if (projY in hist.indices) {
                    hist[projY]++
                }
            }
            var sumSq = 0L
            for (i in 1 until hist.size) {
                val diff = (hist[i] - hist[i - 1]).toLong()
                sumSq += diff * diff
            }
            return sumSq
        }

        // Stage 1: Coarse search (-15.0° .. +15.0° in 0.5° steps)
        var bestAngle = 0f
        var bestScore = -1L
        var a = -15.0f
        while (a <= 15.0f) {
            val s = scoreAngle(a)
            if (s > bestScore) {
                bestScore = s
                bestAngle = a
            }
            a += 0.5f
        }

        // Stage 2: Fine search around bestAngle (-0.8° .. +0.8° in 0.1° steps)
        val center = bestAngle
        var fine = center - 0.8f
        while (fine <= center + 0.8f) {
            val s = scoreAngle(fine)
            if (s > bestScore) {
                bestScore = s
                bestAngle = fine
            }
            fine += 0.1f
        }

        return bestAngle
    }

    /**
     * Removes dark scanner background wedges along the outer edges of a scanned bitmap
     * by flood-whitening dark border pixels inward up to 3.5% of the page width/height.
     */
    fun cleanOuterBorderWedges(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 40 || h < 40) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        val maxBorderX = (w * 0.035f).toInt().coerceAtLeast(4)
        val maxBorderY = (h * 0.035f).toInt().coerceAtLeast(4)

        fun isDarkOrTransparent(c: Int): Boolean {
            val alpha = (c ushr 24) and 0xFF
            if (alpha < 128) return true
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            return lum < 155
        }

        // Clean left & right edges on each row
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until maxBorderX) {
                if (isDarkOrTransparent(px[row + x])) px[row + x] = Color.WHITE else break
            }
            for (x in (w - 1) downTo (w - maxBorderX)) {
                if (isDarkOrTransparent(px[row + x])) px[row + x] = Color.WHITE else break
            }
        }

        // Clean top & bottom edges on each column
        for (x in 0 until w) {
            for (y in 0 until maxBorderY) {
                val idx = y * w + x
                if (isDarkOrTransparent(px[idx])) px[idx] = Color.WHITE else break
            }
            for (y in (h - 1) downTo (h - maxBorderY)) {
                val idx = y * w + x
                if (isDarkOrTransparent(px[idx])) px[idx] = Color.WHITE else break
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }
}
