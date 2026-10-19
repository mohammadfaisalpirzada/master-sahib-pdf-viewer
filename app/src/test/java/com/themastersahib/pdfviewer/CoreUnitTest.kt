package com.themastersahib.pdfviewer

import android.graphics.Color
import com.themastersahib.pdfviewer.core.CnicLayout
import com.themastersahib.pdfviewer.core.ImageFilters
import com.themastersahib.pdfviewer.core.PaperSize
import com.themastersahib.pdfviewer.core.PdfTextSearch
import com.themastersahib.pdfviewer.core.PointXY
import com.themastersahib.pdfviewer.core.StrokePath
import com.themastersahib.pdfviewer.core.mmToPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreUnitTest {

    @Test
    fun paperSizeAndMmToPxConversionIsAccurate() {
        // 25.4 mm = 1 inch -> at 300 DPI, 25.4 mm must be 300 px
        assertEquals(300, mmToPx(25.4f, 300))
        // A4 width (210 mm) at 300 DPI = 2480 px
        assertEquals(2480, mmToPx(PaperSize.A4.wMm, 300))
        assertEquals(3508, mmToPx(PaperSize.A4.hMm, 300))
    }

    @Test
    fun cnicDuplexGeometryIsSymmetricOnLongEdgeFlip() {
        val g = CnicLayout.computeGeometry(copies = 5, dpi = 300, paper = PaperSize.A4)
        // Left margin must equal right margin so flipping along the long edge maps right column onto left column
        val leftMargin = g.leftX
        val rightMargin = g.pageW - (g.rightX + g.cardW)
        assertEquals(leftMargin, rightMargin)
        // All 5 rows must fit within page height with positive top/bottom margins
        assertTrue(g.marginY > 0)
        assertTrue(g.leftX > 0)
        assertTrue(g.rightX > g.leftX + g.cardW)
    }

    @Test
    fun bwPrintFilterSeparatesDarkInkFromLightBackground() {
        val white = Color.rgb(245, 245, 245)
        val darkInk = Color.rgb(25, 25, 25)
        val input = intArrayOf(white, white, white, darkInk)
        val output = ImageFilters.thresholdPixels(input)
        assertEquals(Color.WHITE, output[0])
        assertEquals(Color.WHITE, output[1])
        assertEquals(Color.WHITE, output[2])
        assertEquals(Color.BLACK, output[3])
    }

    @Test
    fun pdfTextSearchHandlesEmptyNeedlesGracefully() {
        val rects = PdfTextSearch.findMatchRects("Photostat Master", emptyList(), "")
        assertTrue(rects.isEmpty())
    }

    @Test
    fun strokePathModelRetainsCoordinatesAndColor() {
        val stroke = StrokePath(
            points = listOf(PointXY(10f, 20f), PointXY(30f, 40f)),
            r = 255, g = 0, b = 0, width = 6f, alpha = 1f
        )
        assertEquals(2, stroke.points.size)
        assertEquals(255, stroke.r)
    }
}
