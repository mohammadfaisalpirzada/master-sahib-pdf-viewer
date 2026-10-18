package com.themastersahib.pdfviewer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/**
 * KILLER FEATURE 1: save any page as a proper image to the device gallery.
 *
 * Uses MediaStore so the image shows up instantly in Gallery/Photos with NO
 * storage permission needed on Android 10+ (scoped storage). On Android 9 and
 * below the app needs WRITE_EXTERNAL_STORAGE (add it to the manifest if you
 * keep minSdk < 29).
 */
object ImageSaver {

    /**
     * @return the content Uri of the saved image, or null on failure.
     */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = 95
    ): Uri? {
        val mimeType = if (format == Bitmap.CompressFormat.PNG) "image/png" else "image/jpeg"
        val fileName = if (displayName.contains('.')) displayName
                       else "$displayName.${if (mimeType == "image/png") "png" else "jpg"}"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Save into Pictures/Master Sahib PDF so pages are grouped.
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Master Sahib PDF"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val uri = resolver.insert(collection, values) ?: return null

        return try {
            resolver.openOutputStream(uri)?.use { out: OutputStream ->
                bitmap.compress(format, quality, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }
}
