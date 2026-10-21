package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FilterType { NONE, GRAYSCALE, BW_PRINT, MAGIC }

/**
 * Modern document enhancement engine inspired by CamScanner:
 * - Shadow & uneven lighting removal via background illumination estimation
 * - Crisp text contrast enhancement preserving smooth, anti-aliased font edges
 * - Noise-free, solid white paper background (ideal for photostat and laser printing)
 * - Pure color preservation for signatures, official stamps, and colored headers
 */
object ImageFilters {

    fun apply(src: Bitmap, type: FilterType): Bitmap = when (type) {
        FilterType.NONE -> src
        FilterType.GRAYSCALE -> applyGrayscaleEnhance(src)
        FilterType.MAGIC -> applyMagicColor(src)
        FilterType.BW_PRINT -> applyBwPhotostat(src)
    }

    /** BULK: apply the same filter to a whole scan session. */
    fun applyAll(pages: List<Bitmap>, type: FilterType): List<Bitmap> =
        if (type == FilterType.NONE) pages else pages.map { apply(it, type) }

    /** Legacy threshold function preserved for unit tests */
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
     * CamScanner B&W Photostat Mode:
     * Removes shadows across the page, boosts ink density for lines & text,
     * keeps edges smooth (anti-aliased) and whitens paper to 100% pure white.
     */
    private fun applyBwPhotostat(src: Bitmap): Bitmap {
        val cleaned = Orientation.cleanOuterBorderWedges(src)
        val w = cleaned.width
        val h = cleaned.height
        val px = IntArray(w * h)
        cleaned.getPixels(px, 0, w, 0, 0, w, h)

        // 1. Compute luminance
        val lum = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) and 0xFF
            if (a < 128) {
                lum[i] = 255
            } else {
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                lum[i] = (r * 299 + g * 587 + b * 114) / 1000
            }
        }

        // 2. Estimate local background brightness via block sampling (32x32 blocks)
        val blockW = 32
        val blockH = 32
        val gridCols = (w + blockW - 1) / blockW
        val gridRows = (h + blockH - 1) / blockH
        val bgGrid = IntArray(gridCols * gridRows)

        for (gy in 0 until gridRows) {
            val yStart = gy * blockH
            val yEnd = min(h, yStart + blockH)
            for (gx in 0 until gridCols) {
                val xStart = gx * blockW
                val xEnd = min(w, xStart + blockW)
                var maxLum = 0
                for (y in yStart until yEnd) {
                    val row = y * w
                    for (x in xStart until xEnd) {
                        val l = lum[row + x]
                        if (l > maxLum) maxLum = l
                    }
                }
                bgGrid[gy * gridCols + gx] = max(180, maxLum)
            }
        }

        // 3. Normalize pixels and apply smooth contrast curve
        val out = IntArray(w * h)
        val borderX = (w * 0.015f).toInt().coerceAtLeast(3)
        val borderY = (h * 0.015f).toInt().coerceAtLeast(3)

        for (y in 0 until h) {
            val row = y * w
            val gy = min(gridRows - 1, y / blockH)
            for (x in 0 until w) {
                if (x < borderX || x >= w - borderX || y < borderY || y >= h - borderY) {
                    out[row + x] = Color.WHITE
                    continue
                }
                val gx = min(gridCols - 1, x / blockW)
                val bg = bgGrid[gy * gridCols + gx].toFloat()
                val l = lum[row + x]

                // Normalized ratio (0..1): 1 means matches local paper background
                val ratio = (l.toFloat() / bg).coerceIn(0f, 1f)

                // Smooth S-curve contrast stretch:
                // > 0.88 -> pure white paper (255)
                // < 0.60 -> deep black ink (0)
                // in between -> smooth anti-aliased gradient
                val v = when {
                    ratio >= 0.86f -> 255
                    ratio <= 0.58f -> 0
                    else -> {
                        val t = (ratio - 0.58f) / (0.86f - 0.58f)
                        (t * t * 255f).roundToInt().coerceIn(0, 255)
                    }
                }
                out[row + x] = Color.rgb(v, v, v)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    /**
     * CamScanner Magic Color Mode:
     * Normalizes background paper illumination to clean white while keeping
     * colored stamps, signatures, and headers vibrant and sharp.
     */
    private fun applyMagicColor(src: Bitmap): Bitmap {
        val cleaned = Orientation.cleanOuterBorderWedges(src)
        val w = cleaned.width
        val h = cleaned.height
        val px = IntArray(w * h)
        cleaned.getPixels(px, 0, w, 0, 0, w, h)

        val blockW = 32
        val blockH = 32
        val gridCols = (w + blockW - 1) / blockW
        val gridRows = (h + blockH - 1) / blockH
        val bgGridR = IntArray(gridCols * gridRows)
        val bgGridG = IntArray(gridCols * gridRows)
        val bgGridB = IntArray(gridCols * gridRows)

        for (gy in 0 until gridRows) {
            val yStart = gy * blockH
            val yEnd = min(h, yStart + blockH)
            for (gx in 0 until gridCols) {
                val xStart = gx * blockW
                val xEnd = min(w, xStart + blockW)
                var maxR = 0; var maxG = 0; var maxB = 0
                for (y in yStart until yEnd) {
                    val row = y * w
                    for (x in xStart until xEnd) {
                        val c = px[row + x]
                        val r = (c shr 16) and 0xFF
                        val g = (c shr 8) and 0xFF
                        val b = c and 0xFF
                        if (r > maxR) maxR = r
                        if (g > maxG) maxG = g
                        if (b > maxB) maxB = b
                    }
                }
                val idx = gy * gridCols + gx
                bgGridR[idx] = max(170, maxR)
                bgGridG[idx] = max(170, maxG)
                bgGridB[idx] = max(170, maxB)
            }
        }

        val out = IntArray(w * h)
        val borderX = (w * 0.015f).toInt().coerceAtLeast(3)
        val borderY = (h * 0.015f).toInt().coerceAtLeast(3)

        for (y in 0 until h) {
            val row = y * w
            val gy = min(gridRows - 1, y / blockH)
            for (x in 0 until w) {
                if (x < borderX || x >= w - borderX || y < borderY || y >= h - borderY) {
                    out[row + x] = Color.WHITE
                    continue
                }
                val gx = min(gridCols - 1, x / blockW)
                val gridIdx = gy * gridCols + gx
                val bgR = bgGridR[gridIdx].toFloat()
                val bgG = bgGridG[gridIdx].toFloat()
                val bgB = bgGridB[gridIdx].toFloat()

                val c = px[row + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF

                fun stretchChannel(v: Int, bg: Float): Int {
                    val norm = (v.toFloat() / bg).coerceIn(0f, 1f)
                    return when {
                        norm >= 0.88f -> 255
                        norm <= 0.35f -> (norm * 0.8f * 255f).roundToInt()
                        else -> {
                            val t = (norm - 0.35f) / (0.88f - 0.35f)
                            (t * 255f).roundToInt().coerceIn(0, 255)
                        }
                    }
                }

                val nr = stretchChannel(r, bgR)
                val ng = stretchChannel(g, bgG)
                val nb = stretchChannel(b, bgB)

                out[row + x] = Color.rgb(nr, ng, nb)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        return result
    }

    private fun applyGrayscaleEnhance(src: Bitmap): Bitmap {
        val magic = applyMagicColor(src)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val out = Bitmap.createBitmap(magic.width, magic.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(magic, 0f, 0f, Paint().apply {
            colorFilter = ColorMatrixColorFilter(cm)
        })
        return out
    }
}
