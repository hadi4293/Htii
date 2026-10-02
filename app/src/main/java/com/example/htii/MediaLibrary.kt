package com.example.htii

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MediaLibrary(context: Context) {
    private val resolver = context.contentResolver

    fun loadVideos(): List<VideoItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
        )
        val videos = mutableListOf<VideoItem>()
        resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                videos += VideoItem(
                    uri = ContentUris.withAppendedId(collection, id),
                    title = cursor.getString(nameColumn) ?: "Video",
                    mimeType = cursor.getString(mimeColumn) ?: "video/mp4",
                    sizeBytes = cursor.getLong(sizeColumn),
                    durationMs = cursor.getLong(durationColumn),
                )
            }
        }
        return videos
    }
}
