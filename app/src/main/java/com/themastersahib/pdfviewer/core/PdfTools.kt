package com.themastersahib.pdfviewer.core

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.themastersahib.pdfviewer.PdfRendererHelper
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/** A single annotation stroke in on-screen (surface) pixel coordinates. */
data class PointXY(val x: Float, val y: Float)
data class StrokePath(
    val points: List<PointXY>,
    val r: Int, val g: Int, val b: Int,
    val width: Float, val alpha: Float
)

/** Text annotation placed on a page in normalized (0..1) coordinates. */
data class TextStamp(
    val text: String,
    val normX: Float,
    val normY: Float,
    val r: Int, val g: Int, val b: Int,
    val fontSizePt: Float = 14f
)

enum class CompressionPreset(val label: String, val targetWidthPx: Int, val jpegQuality: Int) {
    MAX_COMPRESSION("Maximum Compression (Smallest Size)", 1000, 50),
    BALANCED("Balanced (Good Quality, Smaller File)", 1400, 68),
    HIGH_QUALITY("High Quality (Light Compression)", 1800, 82)
}

data class PdfFormFieldItem(
    val fullyQualifiedName: String,
    val value: String,
    val isReadOnly: Boolean
)

data class PdfCommentItem(
    val pageIndex: Int,
    val text: String
)

data class PdfDocumentInfoSummary(
    val fileName: String,
    val fileSizeBytes: Long,
    val lastModifiedMillis: Long,
    val pageCount: Int,
    val pageSizeMm: String,
    val title: String,
    val author: String,
    val subject: String,
    val creator: String,
    val producer: String,
    val creationDate: String,
    val encrypted: Boolean
)

/** PDF operations via PdfBox-Android (all offline, Apache 2.0). */
object PdfTools {

    private fun out(context: Context, prefix: String) =
        File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.pdf")

    fun rotate(context: Context, uri: Uri, degrees: Int, password: String = ""): File {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
            doc.use {
                for (p in it.pages) p.rotation = (p.rotation + degrees) % 360
                val f = out(context, "rotated"); it.save(f); return f
            }
        }
    }

    fun splitEachPage(context: Context, uri: Uri, password: String = ""): List<File> {
        val result = mutableListOf<File>()
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
            doc.use {
                for (i in 0 until it.numberOfPages) {
                    val single = PDDocument()
                    single.importPage(it.getPage(i))
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
     * Protect (encrypt) a PDF with a password using 128-bit standard encryption.
     */
    fun protectPdf(
        context: Context,
        uri: Uri,
        newPassword: String,
        currentPassword: String = ""
    ): File {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val doc = if (currentPassword.isNotEmpty()) PDDocument.load(input, currentPassword) else PDDocument.load(input)
            doc.use {
                val ap = AccessPermission()
                val policy = StandardProtectionPolicy(newPassword, newPassword, ap).apply {
                    encryptionKeyLength = 128
                }
                it.protect(policy)
                val f = out(context, "protected")
                it.save(f)
                return f
            }
        }
    }

    /**
     * Remove password protection from an encrypted PDF and save an unlocked copy.
     */
    fun unprotectPdf(context: Context, uri: Uri, currentPassword: String): File {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val doc = if (currentPassword.isNotEmpty()) PDDocument.load(input, currentPassword) else PDDocument.load(input)
            doc.use {
                it.isAllSecurityToBeRemoved = true
                val f = out(context, "unlocked")
                it.save(f)
                return f
            }
        }
    }

    /**
     * Reduce PDF file size by rendering pages at controlled resolution and writing
     * DCT/JPEG streams directly into a compact PDF document.
     */
    fun reduceFileSize(
        context: Context,
        uri: Uri,
        preset: CompressionPreset = CompressionPreset.BALANCED
    ): File {
        val outFile = out(context, "compressed")
        PdfRendererHelper(context.contentResolver, uri).use { renderer ->
            PDDocument().use { outDoc ->
                for (i in 0 until renderer.pageCount) {
                    val bmp = renderer.renderPage(i, preset.targetWidthPx)
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, preset.jpegQuality, baos)
                    val jpegBytes = baos.toByteArray()

                    val isLandscape = bmp.width > bmp.height
                    val rect = if (isLandscape) {
                        PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                    } else {
                        PDRectangle(PDRectangle.A4.width, PDRectangle.A4.height)
                    }
                    bmp.recycle()

                    val page = PDPage(rect)
                    outDoc.addPage(page)
                    val pdImage = JPEGFactory.createFromStream(outDoc, ByteArrayInputStream(jpegBytes))
                    PDPageContentStream(outDoc, page).use { cs ->
                        cs.drawImage(pdImage, 0f, 0f, rect.width, rect.height)
                    }
                }
                outDoc.save(outFile)
            }
        }
        return outFile
    }

    /**
     * Flatten all interactive form fields and annotations into static page content.
     */
    fun flattenPdf(context: Context, uri: Uri, password: String = ""): File {
        val outFile = out(context, "flattened")
        PdfRendererHelper(context.contentResolver, uri).use { renderer ->
            PDDocument().use { outDoc ->
                for (i in 0 until renderer.pageCount) {
                    val bmp = renderer.renderPage(i, 1800)
                    val baos = ByteArrayOutputStream()
                    bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
                    val jpegBytes = baos.toByteArray()
                    val isLandscape = bmp.width > bmp.height
                    val rect = if (isLandscape) {
                        PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                    } else {
                        PDRectangle(PDRectangle.A4.width, PDRectangle.A4.height)
                    }
                    bmp.recycle()
                    val page = PDPage(rect)
                    outDoc.addPage(page)
                    val pdImage = JPEGFactory.createFromStream(outDoc, ByteArrayInputStream(jpegBytes))
                    PDPageContentStream(outDoc, page).use { cs ->
                        cs.drawImage(pdImage, 0f, 0f, rect.width, rect.height)
                    }
                }
                outDoc.save(outFile)
            }
        }
        return outFile
    }

    /**
     * Extract all text from the PDF for Export -> Plain Text (.txt).
     */
    fun extractAllText(context: Context, uri: Uri, password: String = ""): String {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
            doc.use {
                return PDFTextStripper().getText(it).trim()
            }
        }
        return ""
    }

    /**
     * Read interactive form fields and page comments/notes from the PDF.
     */
    fun getFormFieldsAndComments(
        context: Context,
        uri: Uri,
        password: String = ""
    ): Pair<List<PdfFormFieldItem>, List<PdfCommentItem>> {
        val fields = mutableListOf<PdfFormFieldItem>()
        val comments = mutableListOf<PdfCommentItem>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
            doc.use { pd ->
                runCatching {
                    val acro = pd.documentCatalog?.acroForm
                    acro?.fields?.forEach { f ->
                        fields.add(
                            PdfFormFieldItem(
                                fullyQualifiedName = f.fullyQualifiedName ?: "",
                                value = f.valueAsString ?: "",
                                isReadOnly = f.isReadOnly
                            )
                        )
                    }
                }
                for (i in 0 until pd.numberOfPages) {
                    runCatching {
                        val page = pd.getPage(i)
                        page.annotations?.forEach { ann ->
                            val c = ann.contents?.trim().orEmpty()
                            if (c.isNotEmpty()) {
                                comments.add(PdfCommentItem(pageIndex = i, text = c))
                            }
                        }
                    }
                }
            }
        }
        return fields to comments
    }

    /**
     * Save updated AcroForm field values and/or add a new comment note on [commentPageIndex].
     */
    fun saveFormFieldsAndComment(
        context: Context,
        uri: Uri,
        fieldUpdates: Map<String, String>,
        commentPageIndex: Int,
        newComment: String,
        password: String = ""
    ): File {
        context.contentResolver.openInputStream(uri)!!.use { input ->
            val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
            doc.use { pd ->
                if (fieldUpdates.isNotEmpty()) {
                    val acro = pd.documentCatalog?.acroForm
                    fieldUpdates.forEach { (name, value) ->
                        runCatching { acro?.getField(name)?.setValue(value) }
                    }
                }
                if (newComment.isNotBlank() && commentPageIndex in 0 until pd.numberOfPages) {
                    val page = pd.getPage(commentPageIndex)
                    val box = page.mediaBox
                    val note = PDAnnotationText().apply {
                        contents = newComment.trim()
                        rectangle = PDRectangle(box.width - 48f, box.height - 48f, 28f, 28f)
                    }
                    page.annotations.add(note)
                    PDPageContentStream(pd, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        cs.setNonStrokingColor(0.95f, 0.85f, 0.2f)
                        cs.addRect(24f, 18f, box.width - 48f, 22f)
                        cs.fill()
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, 9f)
                        cs.setNonStrokingColor(0.1f, 0.1f, 0.1f)
                        cs.newLineAtOffset(30f, 25f)
                        val safeText = newComment.trim().replace(Regex("[^\\x20-\\x7E]"), " ").take(95)
                        cs.showText("Note: $safeText")
                        cs.endText()
                    }
                }
                val f = out(context, "comments_fields")
                pd.save(f)
                return f
            }
        }
    }

    /**
     * Retrieve detailed PDF document metadata and file properties for "Show Info".
     */
    fun getDocumentInfo(
        context: Context,
        uri: Uri,
        fallbackName: String,
        password: String = ""
    ): PdfDocumentInfoSummary {
        var fileName = fallbackName.ifBlank { "document.pdf" }
        var fileSize = 0L
        var lastModified = System.currentTimeMillis()
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) fileName = c.getString(nameIdx) ?: fileName
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !c.isNull(sizeIdx)) fileSize = c.getLong(sizeIdx)
                    val modIdx = c.getColumnIndex("last_modified")
                    if (modIdx >= 0 && !c.isNull(modIdx)) lastModified = c.getLong(modIdx)
                }
            }
        }
        if (fileSize == 0L) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    fileSize = pfd.statSize.coerceAtLeast(0L)
                }
            }
        }

        var pageCount = 0
        var pageSizeMm = "-"
        var title = "-"
        var author = "-"
        var subject = "-"
        var creator = "-"
        var producer = "-"
        var creationDate = "-"
        var encrypted = false

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val doc = if (password.isNotEmpty()) PDDocument.load(input, password) else PDDocument.load(input)
                doc.use { pd ->
                    pageCount = pd.numberOfPages
                    encrypted = pd.isEncrypted
                    if (pageCount > 0) {
                        val box = pd.getPage(0).mediaBox
                        val wMm = (box.width / 72f * 25.4f).roundToInt()
                        val hMm = (box.height / 72f * 25.4f).roundToInt()
                        pageSizeMm = "$wMm × $hMm mm (${box.width.roundToInt()} × ${box.height.roundToInt()} pt)"
                    }
                    val info = pd.documentInformation
                    if (info != null) {
                        title = info.title?.takeIf { it.isNotBlank() } ?: "-"
                        author = info.author?.takeIf { it.isNotBlank() } ?: "-"
                        subject = info.subject?.takeIf { it.isNotBlank() } ?: "-"
                        creator = info.creator?.takeIf { it.isNotBlank() } ?: "-"
                        producer = info.producer?.takeIf { it.isNotBlank() } ?: "-"
                        info.creationDate?.time?.let { d ->
                            creationDate = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(d)
                        }
                    }
                }
            }
        }

        return PdfDocumentInfoSummary(
            fileName = fileName,
            fileSizeBytes = fileSize,
            lastModifiedMillis = lastModified,
            pageCount = pageCount,
            pageSizeMm = pageSizeMm,
            title = title,
            author = author,
            subject = subject,
            creator = creator,
            producer = producer,
            creationDate = creationDate,
            encrypted = encrypted
        )
    }

    /**
     * Overlay annotation strokes and text stamps onto [pageIndex] of the source PDF as VECTOR
     * lines/text, keeping the page's existing text layer (not a flattened raster).
     */
    fun annotateIntoPdf(
        context: Context, uri: Uri, pageIndex: Int,
        strokes: List<StrokePath>, surfaceW: Float, surfaceH: Float,
        textStamps: List<TextStamp> = emptyList()
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
                    textStamps.forEach { ts ->
                        val gs = PDExtendedGraphicsState().apply {
                            strokingAlphaConstant = 1f
                            nonStrokingAlphaConstant = 1f
                        }
                        cs.setGraphicsStateParameters(gs)
                        cs.beginText()
                        cs.setFont(PDType1Font.HELVETICA_BOLD, ts.fontSizePt)
                        cs.setNonStrokingColor(ts.r / 255f, ts.g / 255f, ts.b / 255f)
                        val x = (ts.normX * box.width).coerceIn(8f, (box.width - 40f).coerceAtLeast(8f))
                        val y = (box.height - ts.normY * box.height).coerceIn(12f, (box.height - 16f).coerceAtLeast(12f))
                        cs.newLineAtOffset(x, y)
                        val safe = ts.text.replace(Regex("[^\\x20-\\x7E]"), " ")
                        cs.showText(safe)
                        cs.endText()
                    }
                }
                val f = out(context, "annotated_vector"); doc.save(f); return f
            }
        }
    }
}
