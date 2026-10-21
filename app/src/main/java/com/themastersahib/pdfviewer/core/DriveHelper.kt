package com.themastersahib.pdfviewer.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.themastersahib.pdfviewer.data.CloudStore
import java.io.File
import java.io.FileOutputStream

/**
 * Direct Google Drive integration for saving PDFs and scanned images to the user's Google Drive.
 * Targets the official Google Drive Android app (`com.google.android.apps.docs`) which opens the
 * native "Save to Drive" dialog pre-filled with the user's active Google account and folder picker.
 */
object DriveHelper {

    private const val DRIVE_PACKAGE = "com.google.android.apps.docs"

    /**
     * Save a PDF file directly to Google Drive.
     */
    fun savePdfToDrive(context: Context, pdfFile: File, displayName: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
        // Record in the user's database
        CloudStore.recordCreatedPdfAsync(
            context = context,
            uriString = uri.toString(),
            displayName = displayName,
            toolUsed = "Google Drive Upload",
            sizeBytes = pdfFile.length()
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(DRIVE_PACKAGE)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Google Drive app not installed directly -> open standard share sheet with Google Drive option
            intent.setPackage(null)
            val chooser = Intent.createChooser(intent, "Save to Google Drive / Cloud")
            context.startActivity(chooser)
        }
    }

    /**
     * Save an existing PDF content/file URI directly to Google Drive.
     */
    fun saveUriToDrive(context: Context, uri: Uri, displayName: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, displayName)
            putExtra(Intent.EXTRA_SUBJECT, displayName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(DRIVE_PACKAGE)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            intent.setPackage(null)
            val chooser = Intent.createChooser(intent, "Save to Google Drive / Cloud")
            context.startActivity(chooser)
        }
    }

    /**
     * Save scanned images directly to Google Drive as high-quality JPEGs.
     */
    fun saveImagesToDrive(context: Context, pages: List<Bitmap>, baseName: String) {
        if (pages.isEmpty()) return
        val uris = ArrayList<Uri>()
        val tmpDir = File(context.cacheDir, "drive_upload").apply { mkdirs() }

        pages.forEachIndexed { i, bmp ->
            val f = File(tmpDir, "${baseName}_page_${i + 1}.jpg")
            FileOutputStream(f).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f))
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris[0])
                putExtra(Intent.EXTRA_TITLE, "$baseName.jpg")
                putExtra(Intent.EXTRA_SUBJECT, "$baseName.jpg")
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/jpeg"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                putExtra(Intent.EXTRA_TITLE, "$baseName (${pages.size} images)")
                putExtra(Intent.EXTRA_SUBJECT, "$baseName (${pages.size} images)")
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage(DRIVE_PACKAGE)
        }

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            intent.setPackage(null)
            val chooser = Intent.createChooser(intent, "Save to Google Drive / Cloud")
            context.startActivity(chooser)
        }
    }
}
