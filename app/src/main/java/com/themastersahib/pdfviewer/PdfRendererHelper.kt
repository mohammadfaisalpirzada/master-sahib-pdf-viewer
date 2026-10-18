package com.themastersahib.pdfviewer

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Wraps Android's built-in PdfRenderer.
 *
 * Renders any page to a Bitmap at a chosen scale — this is the engine behind
 * both on-screen display AND the "Save page as image" feature. Rendering at a
 * high scale (e.g. 2x–4x the page's point size) produces a crisp image, far
 * sharper than a screenshot which is capped at the device's screen resolution.
 */
class PdfRendererHelper(
    resolver: ContentResolver,
    uri: Uri
) : AutoCloseable {

    private val fileDescriptor: ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, "r")
            ?: throw IllegalArgumentException("Cannot open PDF: $uri")

    private val renderer = PdfRenderer(fileDescriptor)

    val pageCount: Int get() = renderer.pageCount

    /**
     * Render [pageIndex] to a Bitmap.
     *
     * @param targetWidthPx desired output width in pixels. Height is derived
     *        from the page aspect ratio. For a "proper image" export pass a
     *        large value (e.g. 2000–3000 px) for print-quality output.
     */
    fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap {
        renderer.openPage(pageIndex).use { page ->
            val ratio = page.height.toFloat() / page.width.toFloat()
            val width = targetWidthPx
            val height = (width * ratio).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            // White background so transparent PDF areas don't render black.
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        }
    }

    override fun close() {
        renderer.close()
        fileDescriptor.close()
    }

    companion object {
        /** Convenience for a local File (e.g. a scanned PDF). */
        fun fromFile(file: File): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}
