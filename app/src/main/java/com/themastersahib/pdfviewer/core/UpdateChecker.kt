package com.themastersahib.pdfviewer.core

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.themastersahib.pdfviewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val url: String)

/**
 * In-app update without Play Store. Reads a small JSON published with every
 * release (stable URL on the "latest" tag), compares its versionCode with this
 * build's, and if newer, offers to download + install the APK.
 */
object UpdateChecker {
    private const val MANIFEST_URL =
        "https://github.com/mohammadfaisalpirzada/master-sahib-pdf-viewer/releases/download/latest/latest-version.json"

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val j = JSONObject(httpGet(MANIFEST_URL))
            val info = UpdateInfo(
                versionCode = j.getInt("versionCode"),
                versionName = j.optString("versionName", ""),
                url = j.getString("url")
            )
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        }.getOrNull()
    }

    suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val out = File(context.cacheDir, "update.apk")
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30000; readTimeout = 120000
        }
        c.inputStream.use { inp -> out.outputStream().use { inp.copyTo(it) } }
        out
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    private fun httpGet(u: String): String {
        val c = (URL(u).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true; connectTimeout = 15000; readTimeout = 20000
        }
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
