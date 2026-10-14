package com.primevidvault.app

data class VideoInfo(
    val id: String,
    val title: String,
    val cover: String,
    val playUrl: String, // no watermark
    val wmPlayUrl: String, // with watermark
    val hdPlayUrl: String, // HD no watermark
    val musicUrl: String,
    val authorName: String,
    val authorUsername: String,
    val authorAvatar: String,
    val duration: Int,
    val playCount: Long,
    val likeCount: Long,
    val commentCount: Long,
    val shareCount: Long
)
