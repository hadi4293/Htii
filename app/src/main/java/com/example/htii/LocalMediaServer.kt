package com.example.htii

import android.content.ContentResolver
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.util.UUID
import kotlin.math.min

class LocalMediaServer(
    private val contentResolver: ContentResolver,
) : NanoHTTPD(0) {
    private val token = UUID.randomUUID().toString()
    @Volatile
    private var source: VideoItem? = null

    fun publish(video: VideoItem): String {
        require(MIME_TYPE_PATTERN.matches(video.mimeType)) { "فرمت ویدیوی انتخاب‌شده معتبر نیست." }
        source = video.copy(sizeBytes = video.sizeBytes.takeIf { it > 0 } ?: contentLength(video))
        if (!isAlive) start(SOCKET_READ_TIMEOUT, false)
        val address = findLocalAddress()
            ?: throw IllegalStateException("اتصال Wi-Fi پیدا نشد؛ گوشی و تلویزیون را به یک شبکه وصل کنید.")
        return "http://$address:$listeningPort/$token"
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != "/$token") {
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
        val byteRange = if (rangeHeader == null) null else parseRange(rangeHeader, size)
        if (rangeHeader != null && (size <= 0 || byteRange == null)) {
            return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "")
                .apply { addHeader("Content-Range", "bytes */$size") }
        }
        if (session.method == Method.OPTIONS) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
                .apply {
                    addHeader("Access-Control-Allow-Origin", "*")
                    addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                    addHeader("Access-Control-Allow-Headers", "Range")
                }
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
            addHeader("Access-Control-Allow-Origin", "*")
            addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
            if (size > 0 && session.method != Method.HEAD) {
                addHeader("Content-Length", length.toString())
            }
            if (partial && size > 0) addHeader("Content-Range", "bytes $start-$end/$size")
        }
    }

    private fun parseRange(header: String, size: Long): ByteRange? {
        if (!header.startsWith("bytes=") || size <= 0) return null
        val parts = header.removePrefix("bytes=").split('-', limit = 2)
        if (parts.size != 2 || ',' in header) return null
        return runCatching {
            val start: Long
            val end: Long
            if (parts[0].isBlank()) {
                val suffixLength = parts[1].toLong()
                require(suffixLength > 0)
                start = (size - suffixLength).coerceAtLeast(0)
                end = size - 1
            } else {
                start = parts[0].toLong()
                end = if (parts[1].isBlank()) size - 1 else min(parts[1].toLong(), size - 1)
            }
            require(start >= 0 && start < size && end >= start)
            ByteRange(start, end)
        }.getOrNull()
    }

    private fun findLocalAddress(): String? =
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter {
                it.isUp && !it.isLoopback &&
                    (it.name.startsWith("wlan", ignoreCase = true) ||
                        it.name.startsWith("eth", ignoreCase = true))
            }
            ?.sortedBy { if (it.name.startsWith("wlan", true)) 0 else 1 }
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

    private data class ByteRange(val first: Long, val last: Long)

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
