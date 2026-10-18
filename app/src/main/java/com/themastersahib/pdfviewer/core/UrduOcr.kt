package com.themastersahib.pdfviewer.core

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Urdu / nastaliq OCR via Tesseract (tesseract4android).
 *
 * REQUIRES trained data: drop `urd.traineddata` and `eng.traineddata`
 * (from tessdata_best: https://github.com/tesseract-ocr/tessdata_best) into
 *   app/src/main/assets/tessdata/
 * They are copied to the app's files dir on first run. If missing, recognize()
 * throws with a clear message (ML Kit stays available for Latin text).
 */
object UrduOcr {

    /** Copy any bundled tessdata files into filesDir/tessdata and return the PARENT dir. */
    private fun ensureData(context: Context): File {
        val dir = File(context.filesDir, "tessdata").apply { mkdirs() }
        runCatching {
            context.assets.list("tessdata")?.forEach { name ->
                if (name.endsWith(".traineddata")) {
                    val outFile = File(dir, name)
                    if (!outFile.exists()) {
                        context.assets.open("tessdata/$name").use { inp ->
                            outFile.outputStream().use { inp.copyTo(it) }
                        }
                    }
                }
            }
        }
        return context.filesDir
    }

    private fun hasLanguage(context: Context, lang: String): Boolean =
        File(File(context.filesDir, "tessdata"), "$lang.traineddata").exists()

    suspend fun recognize(context: Context, bitmap: Bitmap, langs: String = "urd+eng"): String =
        withContext(Dispatchers.Default) {
            val parent = ensureData(context)
            // Verify at least the first language exists.
            val first = langs.substringBefore('+')
            if (!hasLanguage(context, first)) {
                throw IllegalStateException(
                    "Missing $first.traineddata. Add it to app/src/main/assets/tessdata/ " +
                        "(from tesseract-ocr/tessdata_best)."
                )
            }
            val tess = TessBaseAPI()
            if (!tess.init(parent.absolutePath, langs)) {
                tess.recycle()
                throw IllegalStateException("Tesseract init failed for '$langs'.")
            }
            tess.setImage(bitmap)
            val text = tess.getUTF8Text() ?: ""
            tess.recycle()
            text
        }

    suspend fun recognizePages(context: Context, pages: List<Bitmap>, langs: String = "urd+eng"): String {
        val sb = StringBuilder()
        pages.forEachIndexed { i, b ->
            if (i > 0) sb.append("\n\n")
            sb.append("--- Page ${i + 1} ---\n")
            sb.append(recognize(context, b, langs))
        }
        return sb.toString().trim()
    }
}
