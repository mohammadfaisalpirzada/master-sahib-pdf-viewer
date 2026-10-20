package com.themastersahib.pdfviewer.core

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File

enum class TemplateStyle(val title: String, val subtitle: String) {
    BLANK("Blank Page", "Clean white A4 sheet for freehand notes & sketches"),
    RULED("Ruled / Lined Paper", "Standard 8mm horizontal lines with left margin"),
    GRID("Grid / Graph Paper", "5mm square engineering & math grid"),
    DOTTED("Dotted Journal", "5mm dot matrix for structured notes & diagrams"),
    CORNELL("Cornell Notes", "Cue column, main notes section, and summary footer"),
    CHECKLIST("Task / Photostat Log", "Lined checklist with checkboxes")
}

object PdfTemplates {

    fun generate(
        context: Context,
        style: TemplateStyle,
        pageCount: Int = 3,
        paper: PaperSize = PaperSize.A4
    ): File {
        val outFile = File(context.cacheDir, "template_${style.name.lowercase()}_${System.currentTimeMillis()}.pdf")
        val rect = when (paper) {
            PaperSize.A4 -> PDRectangle.A4
            PaperSize.LETTER -> PDRectangle.LETTER
            PaperSize.LEGAL -> PDRectangle.LEGAL
        }

        PDDocument().use { doc ->
            repeat(pageCount.coerceIn(1, 50)) {
                val page = PDPage(rect)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    drawTemplate(cs, rect.width, rect.height, style)
                }
            }
            doc.save(outFile)
        }
        return outFile
    }

    private fun drawTemplate(cs: PDPageContentStream, w: Float, h: Float, style: TemplateStyle) {
        when (style) {
            TemplateStyle.BLANK -> Unit
            TemplateStyle.RULED -> {
                val spacing = 22.6f // ~8mm in points
                val topMargin = h - 56f
                val bottomMargin = 42f
                cs.setStrokingColor(0.78f, 0.85f, 0.94f)
                cs.setLineWidth(0.6f)
                var y = topMargin
                while (y >= bottomMargin) {
                    cs.moveTo(36f, y)
                    cs.lineTo(w - 36f, y)
                    y -= spacing
                }
                cs.stroke()
                // Vertical red margin line
                cs.setStrokingColor(0.92f, 0.55f, 0.55f)
                cs.setLineWidth(0.8f)
                cs.moveTo(72f, bottomMargin)
                cs.lineTo(72f, topMargin + 14f)
                cs.stroke()
            }
            TemplateStyle.GRID -> {
                val step = 14.17f // 5mm in points
                val margin = 36f
                cs.setStrokingColor(0.84f, 0.89f, 0.94f)
                cs.setLineWidth(0.45f)
                var x = margin
                while (x <= w - margin) {
                    cs.moveTo(x, margin)
                    cs.lineTo(x, h - margin)
                    x += step
                }
                var y = margin
                while (y <= h - margin) {
                    cs.moveTo(margin, y)
                    cs.lineTo(w - margin, y)
                    y += step
                }
                cs.stroke()
            }
            TemplateStyle.DOTTED -> {
                val step = 14.17f
                val margin = 36f
                cs.setNonStrokingColor(0.72f, 0.76f, 0.82f)
                var x = margin
                while (x <= w - margin) {
                    var y = margin
                    while (y <= h - margin) {
                        cs.addRect(x - 0.7f, y - 0.7f, 1.4f, 1.4f)
                        y += step
                    }
                    x += step
                }
                cs.fill()
            }
            TemplateStyle.CORNELL -> {
                val margin = 36f
                val headerY = h - 85f
                val summaryY = 120f
                val cueX = 175f
                // Ruled lines in main notes area
                cs.setStrokingColor(0.84f, 0.88f, 0.94f)
                cs.setLineWidth(0.5f)
                var y = headerY - 22f
                while (y > summaryY + 10f) {
                    cs.moveTo(cueX, y)
                    cs.lineTo(w - margin, y)
                    y -= 22f
                }
                cs.stroke()
                // Structural dividers
                cs.setStrokingColor(0.45f, 0.35f, 0.65f)
                cs.setLineWidth(1.1f)
                cs.addRect(margin, margin, w - margin * 2, h - margin * 2)
                cs.moveTo(margin, headerY)
                cs.lineTo(w - margin, headerY)
                cs.moveTo(margin, summaryY)
                cs.lineTo(w - margin, summaryY)
                cs.moveTo(cueX, summaryY)
                cs.lineTo(cueX, headerY)
                cs.stroke()
            }
            TemplateStyle.CHECKLIST -> {
                val spacing = 26f
                val topMargin = h - 64f
                val bottomMargin = 48f
                cs.setStrokingColor(0.75f, 0.80f, 0.88f)
                cs.setLineWidth(0.7f)
                var y = topMargin
                while (y >= bottomMargin) {
                    cs.addRect(44f, y + 4f, 11f, 11f)
                    cs.moveTo(66f, y)
                    cs.lineTo(w - 40f, y)
                    y -= spacing
                }
                cs.stroke()
            }
        }
    }
}
