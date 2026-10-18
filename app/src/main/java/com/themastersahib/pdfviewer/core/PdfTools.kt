package com.themastersahib.pdfviewer.core

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File

/** A single annotation stroke in on-screen (surface) pixel coordinates. */
data class PointXY(val x: Float, val y: Float)
data class StrokePath(
    val points: List<PointXY>,
    val r: Int, val g: Int, val b: Int,
    val width: Float, val alpha: Float
)

/** PDF operations via PdfBox-Android (all offline, Apache 2.0). */
object PdfTools {

    private fun out(context: Context, prefix: String) =
        File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.pdf")

    fun rotate(context: Context, uri: Uri, degrees: Int): File {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input).use { doc ->
                for (p in doc.pages) p.rotation = (p.rotation + degrees) % 360
                val f = out(context, "rotated"); doc.save(f); return f
            }
        }
    }

    fun splitEachPage(context: Context, uri: Uri): List<File> {
        val result = mutableListOf<File>()
        context.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input).use { doc ->
                for (i in 0 until doc.numberOfPages) {
                    val single = PDDocument()
                    single.importPage(doc.getPage(i))
                    val f = out(context, "page_${i + 1}")
                    single.save(f); single.close()
                    result.add(f)
                }
            }
        }
        return result
    }

    fun merge(context: Context, uris: List<Uri>): File {
        val f = out(context, "merged")
        val merger = PDFMergerUtility()
        merger.destinationFileName = f.absolutePath
        uris.forEach { u -> context.contentResolver.openInputStream(u)?.let { merger.addSource(it) } }
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        return f
    }

    /**
     * Overlay annotation strokes onto [pageIndex] of the source PDF as VECTOR
     * lines, keeping the page's existing text layer (not a flattened raster).
     * Strokes are given in surface pixels; we map them to PDF points (origin
     * bottom-left, so Y is flipped).
     */
    fun annotateIntoPdf(
        context: Context, uri: Uri, pageIndex: Int,
        strokes: List<StrokePath>, surfaceW: Float, surfaceH: Float
    ): File {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            PDDocument.load(input).use { doc ->
                val page = doc.getPage(pageIndex)
                val box = page.mediaBox
                val sx = box.width / surfaceW
                val sy = box.height / surfaceH
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    strokes.forEach { s ->
                        val gs = PDExtendedGraphicsState().apply {
                            strokingAlphaConstant = s.alpha
                            nonStrokingAlphaConstant = s.alpha
                        }
                        cs.setGraphicsStateParameters(gs)
                        cs.setStrokingColor(s.r / 255f, s.g / 255f, s.b / 255f)
                        cs.setLineWidth(s.width * sx)
                        s.points.forEachIndexed { i, pt ->
                            val x = pt.x * sx
                            val y = box.height - pt.y * sy
                            if (i == 0) cs.moveTo(x, y) else cs.lineTo(x, y)
                        }
                        cs.stroke()
                    }
                }
                val f = out(context, "annotated_vector"); doc.save(f); return f
            }
        }
    }
}
