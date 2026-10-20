package com.themastersahib.pdfviewer.core

import android.content.Context
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import kotlin.concurrent.thread

/**
 * Lightweight embedded HTTP server for "PC · File transfer with WiFi".
 * Allows transferring PDFs between a PC browser and the Android device on the same Wi-Fi network.
 */
class WifiTransferServer(
    private val context: Context,
    private val onPdfUploaded: (File) -> Unit
) {
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile var runningPort: Int = 8080
        private set

    private val sharedDir: File
        get() = File(context.filesDir, "wifi_shared").apply { mkdirs() }

    fun start(): String {
        stop()
        val socket = (8080..8095).firstNotNullOfOrNull { p ->
            runCatching { ServerSocket(p) }.getOrNull()
        } ?: ServerSocket(0)
        serverSocket = socket
        runningPort = socket.localPort

        thread(isDaemon = true, name = "PhotostatWifiServer") {
            while (serverSocket != null && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { handleClient(client) }
            }
        }
        return "http://${getLocalIpAddress()}:$runningPort"
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    fun getUploadedFiles(): List<File> =
        (sharedDir.listFiles()?.filter { it.extension.equals("pdf", ignoreCase = true) } ?: emptyList()) +
            (context.cacheDir.listFiles()?.filter { it.extension.equals("pdf", ignoreCase = true) } ?: emptyList())

    private fun handleClient(client: Socket) {
        client.use { sock ->
            sock.soTimeout = 30000
            val inp = BufferedInputStream(sock.getInputStream())
            val out = sock.getOutputStream()

            val headerBytes = ByteArrayOutputStream()
            var prev3 = 0; var prev2 = 0; var prev1 = 0
            while (true) {
                val b = inp.read()
                if (b == -1) break
                headerBytes.write(b)
                if (prev3 == '\r'.code && prev2 == '\n'.code && prev1 == '\r'.code && b == '\n'.code) break
                prev3 = prev2; prev2 = prev1; prev1 = b
                if (headerBytes.size() > 32768) break
            }
            val headerStr = headerBytes.toString("UTF-8")
            val lines = headerStr.split("\r\n")
            val reqLine = lines.firstOrNull() ?: return
            val parts = reqLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val path = parts[1]

            if (method == "GET" && path.startsWith("/download/")) {
                val name = URLDecoder.decode(path.removePrefix("/download/"), "UTF-8")
                val target = getUploadedFiles().firstOrNull { it.name == name }
                if (target != null && target.exists()) {
                    val bytes = target.readBytes()
                    sendResponse(out, "200 OK", "application/pdf", bytes, "Content-Disposition: attachment; filename=\"${target.name}\"\r\n")
                } else {
                    sendResponse(out, "404 Not Found", "text/plain", "File not found".toByteArray())
                }
                return
            }

            if (method == "POST" && path == "/upload") {
                val contentLength = lines.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
                val fileNameHeader = lines.firstOrNull { it.startsWith("X-File-Name:", ignoreCase = true) }
                    ?.substringAfter(":")?.trim()?.let { URLDecoder.decode(it, "UTF-8") }
                    ?: "uploaded_${System.currentTimeMillis()}.pdf"

                if (contentLength in 1..(150 * 1024 * 1024)) {
                    val safeName = if (fileNameHeader.endsWith(".pdf", true)) fileNameHeader else "$fileNameHeader.pdf"
                    val dest = File(sharedDir, safeName.replace(Regex("[^a-zA-Z0-9._ -]"), "_"))
                    dest.outputStream().use { fos ->
                        var remaining = contentLength
                        val buf = ByteArray(8192)
                        while (remaining > 0) {
                            val r = inp.read(buf, 0, minOf(buf.size, remaining))
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            remaining -= r
                        }
                    }
                    FileExport.savePdfToDownloads(context, dest, dest.name)
                    onPdfUploaded(dest)
                    sendResponse(out, "200 OK", "text/plain", "Uploaded ${dest.name}".toByteArray())
                    return
                }
            }

            val filesHtml = getUploadedFiles().distinctBy { it.name }.joinToString("") { f ->
                val kb = (f.length() / 1024).coerceAtLeast(1)
                "<li><a href='/download/${f.name}'>${f.name}</a> <span>($kb KB)</span></li>"
            }.ifEmpty { "<li>No cached/uploaded PDFs yet.</li>" }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width,initial-scale=1"/>
                  <title>Photostat Master — Wi-Fi File Transfer</title>
                  <style>
                    body { font-family: system-ui, sans-serif; background: #16181d; color: #f1f3f5; max-width: 680px; margin: 32px auto; padding: 20px; }
                    .card { background: #22252d; border-radius: 12px; padding: 20px; margin-bottom: 20px; }
                    h2 { margin-top: 0; color: #c084fc; }
                    button, input[type=file] { margin-top: 10px; }
                    button { background: #9333ea; color: white; border: none; padding: 10px 18px; border-radius: 8px; cursor: pointer; font-weight: 600; }
                    ul { line-height: 1.9; }
                    a { color: #d8b4fe; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h2>Photostat Master — PC Wi-Fi Transfer</h2>
                    <p>Select a PDF file on your PC to send directly to your phone:</p>
                    <input type="file" id="pdfFile" accept="application/pdf" />
                    <br/>
                    <button onclick="upload()">Send PDF to Phone</button>
                    <p id="status"></p>
                  </div>
                  <div class="card">
                    <h2>PDFs on Phone</h2>
                    <ul>$filesHtml</ul>
                  </div>
                  <script>
                    async function upload() {
                      const inp = document.getElementById('pdfFile');
                      if (!inp.files.length) { alert('Choose a PDF file first'); return; }
                      const f = inp.files[0];
                      document.getElementById('status').innerText = 'Uploading ' + f.name + '...';
                      const res = await fetch('/upload', {
                        method: 'POST',
                        headers: { 'X-File-Name': encodeURIComponent(f.name) },
                        body: f
                      });
                      document.getElementById('status').innerText = await res.text();
                      setTimeout(() => location.reload(), 800);
                    }
                  </script>
                </body>
                </html>
            """.trimIndent()
            sendResponse(out, "200 OK", "text/html; charset=utf-8", html.toByteArray(Charsets.UTF_8))
        }
    }

    private fun sendResponse(
        out: OutputStream,
        status: String,
        contentType: String,
        body: ByteArray,
        extraHeaders: String = ""
    ) {
        val headers = "HTTP/1.1 $status\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            extraHeaders +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    companion object {
        fun getLocalIpAddress(): String {
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val nif = interfaces.nextElement()
                    if (!nif.isUp || nif.isLoopback) continue
                    val addrs = nif.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            }
            return "127.0.0.1"
        }
    }
}
