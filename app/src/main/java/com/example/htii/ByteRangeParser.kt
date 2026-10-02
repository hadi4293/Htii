package com.example.htii

import kotlin.math.min

internal data class ByteRange(val first: Long, val last: Long)

internal object ByteRangeParser {
    fun parse(header: String, size: Long): ByteRange? {
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
}
