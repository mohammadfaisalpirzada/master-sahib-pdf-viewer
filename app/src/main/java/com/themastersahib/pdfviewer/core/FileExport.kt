package com.themastersahib.pdfviewer.core

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.themastersahib.pdfviewer.data.CloudStore
import java.io.File

/** Decode a content/file Uri (e.g. a scanned page) into a straightened, EXIF-corrected Bitmap. */
object BitmapIO {
    fun decode(context: Context, uri: Uri, autoStraighten: Boolean = true): Bitmap? {
        val exifDegrees = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return null
        val exifFixed = if (exifDegrees != 0f) Orientation.rotate(raw, exifDegrees) else raw
        return if (autoStraighten) Orientation.autoDeskew(exifFixed) else exifFixed
    }
}

/** Save finished PDFs to the public Downloads folder (or app dir pre-Android 10). */
object FileExport {

    fun savePdfToDownloads(
        context: Context,
        pdf: File,
        displayName: String,
        toolCategory: String = "Created PDF"
    ): Uri? {
        val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Master Sahib PDF"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { out -> pdf.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Master Sahib PDF"
            ).apply { mkdirs() }
            val dest = File(dir, displayName)
            pdf.copyTo(dest, overwrite = true)
            Uri.fromFile(dest)
        }

        if (savedUri != null) {
            // Automatically record in the user's database
            CloudStore.recordCreatedPdfAsync(
                context = context,
                uriString = savedUri.toString(),
                displayName = displayName,
                toolUsed = toolCategory,
                sizeBytes = pdf.length()
            )
        }
        return savedUri
    }

    fun saveTextToDownloads(context: Context, text: String, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/Master Sahib PDF"
                )
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Master Sahib PDF"
            ).apply { mkdirs() }
            val dest = File(dir, displayName)
            dest.writeText(text)
            return Uri.fromFile(dest)
        }
    }
}
