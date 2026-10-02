package com.example.htii

import android.content.ContentResolver
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.net.NetworkInterface
import java.util.UUID
import kotlin.math.min

class LocalMediaServer(
    private val contentResolver: ContentResolver,
) : NanoHTTPD(0) {
    private val token = UUID.randomUUID().toString()
    @Volatile
    private var source: VideoItem? = null
    @Volatile
    private var sourceVersion = 0L

    fun publish(video: VideoItem): String {
        require(MIME_TYPE_PATTERN.matches(video.mimeType)) { "فرمت ویدیوی انتخاب‌شده معتبر نیست." }
        val address = findLocalAddress()
            ?: throw IllegalStateException("اتصال Wi-Fi پیدا نشد؛ گوشی و تلویزیون را به یک شبکه وصل کنید.")
        source = video.copy(sizeBytes = video.sizeBytes.takeIf { it > 0 } ?: contentLength(video))
        sourceVersion++
        if (!isAlive) start(SOCKET_READ_TIMEOUT, false)
        return "http://$address:$listeningPort/$token/"
    }

    override fun serve(session: IHTTPSession): Response {
        val path = session.uri.removeSuffix("/")
        if (path == "/$token" && session.method == Method.GET) {
            return newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                receiverPage(),
            ).apply {
                addHeader("Cache-Control", "no-store")
                addHeader("X-Content-Type-Options", "nosniff")
            }
        }
        if (path == "/$token/status" && session.method == Method.GET) {
            val video = source
            val status = JSONObject()
                .put("available", video != null)
                .put("title", video?.title.orEmpty())
                .put("version", sourceVersion)
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                status.toString(),
            ).apply { addHeader("Cache-Control", "no-store") }
        }
        if (path != "/$token/video" || session.method !in setOf(Method.GET, Method.HEAD)) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
        val video = source
            ?: return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE,
                MIME_PLAINTEXT,
                "No video selected",
            )
        val size = video.sizeBytes
        val rangeHeader = session.headers["range"]
        val byteRange = if (rangeHeader == null) null else ByteRangeParser.parse(rangeHeader, size)
        if (rangeHeader != null && (size <= 0 || byteRange == null)) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                .apply { addHeader("Content-Range", "bytes */$size") }
        }
        val start = byteRange?.first ?: 0L
        val end = byteRange?.last ?: (size - 1)
        val length = if (size > 0) end - start + 1 else -1L
        val partial = byteRange != null
        val response = if (session.method == Method.HEAD) {
            newFixedLengthResponse(
                if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                video.mimeType,
                "",
            ).apply {
                if (size > 0) addHeader("Content-Length", length.toString())
            }
        } else {
            val input = try {
                contentResolver.openInputStream(video.uri)
                    ?: return newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        MIME_PLAINTEXT,
                        "Video unavailable",
                    )
            } catch (exception: Exception) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    exception.message ?: "Video unavailable",
                )
            }
            try {
                skipFully(input, start)
            } catch (exception: Exception) {
                input.close()
                return newFixedLengthResponse(
                    Response.Status.RANGE_NOT_SATISFIABLE,
                    MIME_PLAINTEXT,
                    exception.message ?: "Invalid range",
                )
            }
            if (size > 0) {
                newFixedLengthResponse(
                    if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
                    video.mimeType,
                    LimitedInputStream(input, length),
                    length,
                )
            } else {
                newChunkedResponse(Response.Status.OK, video.mimeType, input)
            }
        }
        return response.apply {
            addHeader("Accept-Ranges", "bytes")
            addHeader("Cache-Control", "no-store")
            addHeader("X-Content-Type-Options", "nosniff")
            addHeader("Content-Disposition", "inline")
            if (size > 0 && session.method != Method.HEAD) {
                addHeader("Content-Length", length.toString())
            }
            if (partial && size > 0) addHeader("Content-Range", "bytes $start-$end/$size")
        }
    }

    private fun findLocalAddress(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter {
                it.isUp && !it.isLoopback &&
                    (it.name.startsWith("wlan", ignoreCase = true) ||
                        it.name.startsWith("ap", ignoreCase = true) ||
                        it.name.startsWith("eth", ignoreCase = true))
            }
            ?.sortedBy {
                when {
                    it.name.startsWith("wlan", true) -> 0
                    it.name.startsWith("ap", true) -> 1
                    else -> 2
                }
            }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress

    private fun contentLength(video: VideoItem): Long {
        return contentResolver.openAssetFileDescriptor(video.uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it > 0 } ?: descriptor.parcelFileDescriptor.statSize
        }?.coerceAtLeast(0) ?: 0L
    }

    private fun skipFully(input: java.io.InputStream, count: Long) {
        var remaining = count
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped <= 0) {
                if (input.read() == -1) throw IllegalArgumentException("Video ended before requested range")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun receiverPage(): String {
        val statusUrl = "/$token/status"
        val videoUrl = "/$token/video"
        return """
            <!doctype html>
            <html lang="fa" dir="rtl">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>پرتو | پخش ویدیو</title>
              <style>
                * { box-sizing: border-box; }
                body { margin: 0; min-height: 100vh; display: grid; place-items: center;
                  padding: 24px; background: #080d19; color: #f1f4ff;
                  font: 20px system-ui, sans-serif; text-align: center; }
                main { width: min(100%, 1200px); }
                video { width: 100%; max-height: 78vh; background: #000; }
                #message { color: #a9b2cb; }
              </style>
            </head>
            <body>
              <main>
                <h1 id="title">پرتو</h1>
                <p id="message">در انتظار انتخاب ویدیو در گوشی…</p>
                <video id="player" controls playsinline></video>
              </main>
              <script>
                const player = document.getElementById('player');
                const title = document.getElementById('title');
                const message = document.getElementById('message');
                let currentVersion = -1;
                async function refresh() {
                  try {
                    const response = await fetch('$statusUrl', {cache: 'no-store'});
                    if (!response.ok) throw new Error('server');
                    const state = await response.json();
                    if (!state.available) {
                      message.textContent = 'در برنامهٔ گوشی یک ویدیو انتخاب کن.';
                    } else if (state.version !== currentVersion) {
                      currentVersion = state.version;
                      title.textContent = state.title;
                      message.textContent = '';
                      player.src = '$videoUrl?v=' + encodeURIComponent(state.version);
                      player.load();
                    }
                  } catch (_) {
                    message.textContent = 'اتصال به گوشی برقرار نشد؛ اتصال Wi-Fi را بررسی کن.';
                  }
                }
                player.addEventListener('error', () => {
                  message.textContent = 'این فرمت ویدیو ممکن است توسط تلویزیون پشتیبانی نشود.';
                });
                refresh();
                setInterval(refresh, 2000);
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private class LimitedInputStream(input: java.io.InputStream, private var remaining: Long) :
        java.io.FilterInputStream(input) {
        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = super.read()
            if (value != -1) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val count = super.read(buffer, offset, min(length.toLong(), remaining).toInt())
            if (count > 0) remaining -= count
            return count
        }
    }

    private companion object {
        val MIME_TYPE_PATTERN = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")
    }
}
