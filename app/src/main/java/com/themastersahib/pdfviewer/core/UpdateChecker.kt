package com.themastersahib.pdfviewer.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.themastersahib.pdfviewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
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
            val j = JSONObject(httpGet("$MANIFEST_URL?t=${System.currentTimeMillis()}"))
            val info = UpdateInfo(
                versionCode = j.getInt("versionCode"),
                versionName = j.optString("versionName", ""),
                url = j.getString("url")
            )
            if (info.versionCode > BuildConfig.VERSION_CODE) info else null
        }.getOrNull()
    }

    suspend fun downloadApk(context: Context, url: String): File = withContext(Dispatchers.IO) {
        val tmp = File(context.cacheDir, "update.apk.tmp")
        val out = File(context.cacheDir, "update.apk")
        openFollowingRedirects(url, connectTimeout = 30000, readTimeout = 180000).inputStream.use { inp ->
            tmp.outputStream().use { inp.copyTo(it) }
        }
        if (tmp.length() < 1024L) {
            tmp.delete()
            throw IOException("Downloaded APK is incomplete")
        }
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        out
    }

    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(
                context,
                "Allow 'Install unknown apps' for Photostat Master to install the update.",
                Toast.LENGTH_LONG
            ).show()
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(permIntent) }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }

    private fun httpGet(u: String): String =
        openFollowingRedirects(u, connectTimeout = 15000, readTimeout = 20000)
            .inputStream.bufferedReader().use { it.readText() }

    private fun openFollowingRedirects(
        startUrl: String,
        connectTimeout: Int,
        readTimeout: Int,
        maxRedirects: Int = 5
    ): HttpURLConnection {
        var current = startUrl
        repeat(maxRedirects) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                this.connectTimeout = connectTimeout
                this.readTimeout = readTimeout
                setRequestProperty("User-Agent", "PhotostatMaster-Updater/1.0")
            }
            val code = conn.responseCode
            if (code in 301..308) {
                val loc = conn.getHeaderField("Location") ?: throw IOException("Redirect without Location")
                current = URL(URL(current), loc).toString()
                conn.disconnect()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IOException("HTTP $code from $current")
            }
        }
        throw IOException("Too many redirects for $startUrl")
    }
}
