package com.example.ui

import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLConnection
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min

/**
 * Lightweight loopback-only (127.0.0.1) HTTP/1.1 server supporting concurrent Range (206 Partial Content)
 * and HEAD probes for Chromium WebView <video>.
 *
 * Real socket streaming ensures MP4 files with trailing `moov` atoms, fragmented MP4s, and Chromium's
 * multi-range demuxing load and seek reliably without `WebResourceResponse` stream quirks.
 */
class LocalVideoHttpServer(
    private val htmlProvider: (videoUrl: String, queryParams: Map<String, String>) -> String
) {
    private val loopbackAddress: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
    private val serverSocket = ServerSocket(0, 32, loopbackAddress)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val isRunning = AtomicBoolean(true)
    private val activeFile = AtomicReference<File?>(null)
    private val authToken: String = UUID.randomUUID().toString()

    val port: Int get() = serverSocket.localPort

    init {
        executor.execute {
            while (isRunning.get() && !serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    executor.execute { handleClient(client) }
                } catch (_: IOException) {
                    if (!isRunning.get()) break
                }
            }
        }
    }

    fun setActiveFile(file: File?) {
        activeFile.set(file)
    }

    fun buildPlayerUrl(
        mediaId: Int,
        speed: Float,
        loop: Boolean,
        initialPositionMs: Long,
        autoStart: Boolean
    ): String {
        val streamNonce = "${mediaId}_${System.nanoTime()}"
        val spd = String.format(Locale.US, "%.2f", speed.coerceIn(0.25f, 3.0f))
        return "http://127.0.0.1:$port/player.html?tok=$authToken&v=$streamNonce&spd=$spd&lp=$loop&seek=${initialPositionMs.coerceAtLeast(0L)}&auto=$autoStart"
    }

    fun close() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                serverSocket.close()
            } catch (_: IOException) {
            }
            executor.shutdownNow()
        }
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            try {
                sock.soTimeout = 15_000
                val input = BufferedInputStream(sock.getInputStream(), 8192)
                val output = BufferedOutputStream(sock.getOutputStream(), 65536)

                val requestLine = readHttpLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeSimpleResponse(output, 400, "Bad Request", "text/plain", ByteArray(0))
                    return
                }
                val method = parts[0].uppercase(Locale.US)
                val rawTarget = parts[1]

                val headers = HashMap<String, String>()
                while (true) {
                    val line = readHttpLine(input) ?: break
                    if (line.isEmpty()) break
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val name = line.substring(0, colon).trim().lowercase(Locale.US)
                        val value = line.substring(colon + 1).trim()
                        headers[name] = value
                    }
                }

                val parsedUri = Uri.parse("http://127.0.0.1$rawTarget")
                val token = parsedUri.getQueryParameter("tok")
                if (token != authToken) {
                    writeSimpleResponse(output, 403, "Forbidden", "text/plain", ByteArray(0))
                    return
                }

                when (parsedUri.path) {
                    "/player.html" -> {
                        val streamNonce = parsedUri.getQueryParameter("v") ?: "0"
                        val videoUrl = "http://127.0.0.1:$port/video?tok=$authToken&v=$streamNonce"
                        val queryMap = HashMap<String, String>()
                        for (name in parsedUri.queryParameterNames) {
                            parsedUri.getQueryParameter(name)?.let { queryMap[name] = it }
                        }
                        val htmlBytes = htmlProvider(videoUrl, queryMap).toByteArray(Charsets.UTF_8)
                        writeSimpleResponse(
                            output = output,
                            statusCode = 200,
                            reason = "OK",
                            contentType = "text/html; charset=utf-8",
                            body = if (method == "HEAD") ByteArray(0) else htmlBytes,
                            overrideContentLength = htmlBytes.size.toLong()
                        )
                    }
                    "/video" -> {
                        val file = activeFile.get()
                        if (file == null || !file.exists() || !file.isFile || !file.canRead()) {
                            writeSimpleResponse(output, 404, "Not Found", "text/plain", ByteArray(0))
                            return
                        }
                        serveVideoFile(
                            output = output,
                            file = file,
                            isHead = method == "HEAD",
                            rangeHeader = headers["range"]
                        )
                    }
                    else -> {
                        writeSimpleResponse(output, 404, "Not Found", "text/plain", ByteArray(0))
                    }
                }
            } catch (_: IOException) {
                // Client disconnected mid-seek or closed WebView — normal for Range requests
            }
        }
    }

    private fun serveVideoFile(
        output: OutputStream,
        file: File,
        isHead: Boolean,
        rangeHeader: String?
    ) {
        val fileLength = file.length()
        val mimeType = guessVideoMimeType(file)

        if (fileLength <= 0L) {
            writeSimpleResponse(output, 200, "OK", mimeType, ByteArray(0))
            return
        }

        var start = 0L
        var end = fileLength - 1L
        var isPartial = false

        if (!rangeHeader.isNullOrBlank() && rangeHeader.startsWith("bytes=", ignoreCase = true)) {
            val spec = rangeHeader.substringAfter("=").substringBefore(",").trim()
            val dashParts = spec.split("-")
            if (dashParts.size == 2) {
                val startStr = dashParts[0].trim()
                val endStr = dashParts[1].trim()
                if (startStr.isNotEmpty()) {
                    start = startStr.toLongOrNull() ?: 0L
                    end = if (endStr.isNotEmpty()) (endStr.toLongOrNull() ?: (fileLength - 1L)) else (fileLength - 1L)
                    isPartial = true
                } else if (endStr.isNotEmpty()) {
                    val suffix = endStr.toLongOrNull() ?: 0L
                    if (suffix > 0L) {
                        start = (fileLength - suffix).coerceAtLeast(0L)
                        end = fileLength - 1L
                        isPartial = true
                    }
                }
            }
        }

        if (start < 0L || start >= fileLength || end < start) {
            val header = buildString {
                append("HTTP/1.1 416 Range Not Satisfiable\r\n")
                append("Content-Range: bytes */$fileLength\r\n")
                append("Connection: close\r\n\r\n")
            }
            output.write(header.toByteArray(Charsets.US_ASCII))
            output.flush()
            return
        }

        end = end.coerceAtMost(fileLength - 1L)
        val contentLength = end - start + 1L

        val headerText = buildString {
            if (isPartial) {
                append("HTTP/1.1 206 Partial Content\r\n")
                append("Content-Range: bytes $start-$end/$fileLength\r\n")
            } else {
                append("HTTP/1.1 200 OK\r\n")
            }
            append("Content-Type: $mimeType\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $contentLength\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headerText.toByteArray(Charsets.US_ASCII))
        output.flush()

        if (isHead) return

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(65536)
            var remaining = contentLength
            while (remaining > 0L && isRunning.get()) {
                val toRead = min(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, toRead)
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read.toLong()
            }
            output.flush()
        }
    }

    private fun writeSimpleResponse(
        output: OutputStream,
        statusCode: Int,
        reason: String,
        contentType: String,
        body: ByteArray,
        overrideContentLength: Long = body.size.toLong()
    ) {
        val headerText = buildString {
            append("HTTP/1.1 $statusCode $reason\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: $overrideContentLength\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(headerText.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) {
            output.write(body)
        }
        output.flush()
    }

    private fun readHttpLine(input: BufferedInputStream): String? {
        val baos = ByteArrayOutputStream(128)
        while (true) {
            val b = input.read()
            if (b == -1) {
                return if (baos.size() == 0) null else baos.toString("ISO-8859-1")
            }
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                baos.write(b)
            }
        }
        return baos.toString("ISO-8859-1")
    }

    private fun guessVideoMimeType(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            "ts", "m2ts" -> "video/mp2t"
            else -> URLConnection.guessContentTypeFromName(file.name) ?: "video/mp4"
        }
    }
}
