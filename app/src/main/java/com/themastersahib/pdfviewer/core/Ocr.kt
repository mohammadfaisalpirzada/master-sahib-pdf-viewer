package com.themastersahib.pdfviewer.core

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * On-device OCR via ML Kit Text Recognition (Latin script — covers English &
 * Roman Urdu). Free, no network.
 */
object Ocr {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_CLIENT)

    /** Recognize text in a single bitmap. */
    suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { cont.resume(it.text) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /** Recognize across several pages, labelled per page. */
    suspend fun recognizePages(pages: List<Bitmap>): String {
        val sb = StringBuilder()
        pages.forEachIndexed { i, b ->
            if (i > 0) sb.append("\n\n")
            sb.append("--- Page ${i + 1} ---\n")
            sb.append(recognize(b))
        }
        return sb.toString().trim()
    }
}
