package com.tai.app.network

enum class MediaType {
    VIDEO,
    SINGLE_PHOTO_WITH_AUDIO,
    SINGLE_PHOTO,
    CAROUSEL_PHOTOS
}

data class MediaItem(
    val type: MediaType,
    val sourcePlatform: String,
    val title: String? = null,
    val author: String? = null,
    val videoUrl: String? = null,
    val imageUrls: List<String> = emptyList(),
    val audioUrl: String? = null,
    val audioDurationMs: Long = 0L
)
