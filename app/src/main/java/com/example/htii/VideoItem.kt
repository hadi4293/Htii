package com.example.htii

import android.net.Uri
import java.net.URI

data class VideoItem(
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val durationMs: Long = 0,
)

data class WebVideo(
    val title: String,
    val url: String,
    val mimeType: String,
)

enum class AppTab {
    LIBRARY,
    RECEIVER,
}

data class StreamUiState(
    val videos: List<VideoItem> = emptyList(),
    val hasVideoPermission: Boolean = false,
    val isLoadingVideos: Boolean = false,
    val selectedTab: AppTab = AppTab.LIBRARY,
    val searchQuery: String = "",
    val isStreaming: Boolean = false,
    val serverUrl: String = "",
    val selectedVideoTitle: String? = null,
    val detectedWebVideos: List<WebVideo> = emptyList(),
    val browserUrl: String = "",
    val message: String? = null,
)

object VideoTypes {
    fun fromUrl(url: String): String? {
        val path = runCatching { URI(url).path?.lowercase().orEmpty() }.getOrDefault("")
        return when {
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mov") -> "video/quicktime"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".ogv") -> "video/ogg"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            else -> null
        }
    }

    fun validWebUrl(value: String): String? {
        val candidate = value.trim().let {
            if (it.startsWith("https://", true) || it.startsWith("http://", true)) it
            else "https://$it"
        }
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return candidate
    }
}
