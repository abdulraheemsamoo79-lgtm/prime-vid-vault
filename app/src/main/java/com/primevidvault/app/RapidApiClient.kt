package com.primevidvault.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Instagram downloads via the user's RapidAPI subscription.
 * GET https://<host>/instagram?url=<instagram-url>
 * Headers: x-rapidapi-key, x-rapidapi-host
 * Response: {"status":true,"result":[{"url":...,"type":"video/mp4|image/jpeg", "thumb":...}]}
 * Reuses CobaltMedia so the Instagram UI needs no changes.
 */
object RapidApiClient {

    private const val HOST = "instagram-post-reels-stories-downloader-api.p.rapidapi.com"
    private const val API_KEY = "c89ad315bdmsh1cc19d9b636e3d02p12b738jsnf0d102d42111"
    private const val TIMEOUT_MS = 30000

    fun fetch(pageUrl: String): Result<CobaltMedia> {
        return try {
            val endpoint = "https://$HOST/instagram?url=" +
                    URLEncoder.encode(pageUrl, "UTF-8")
            val root = getJson(endpoint)
            if (!root.optBoolean("status", false)) {
                return Result.failure(Exception("Instagram lookup failed. Try again."))
            }
            val items = root.optJSONArray("result")
            if (items == null || items.length() == 0) {
                return Result.failure(Exception("No media found for this link."))
            }
            var videoUrl: String? = null
            var audioUrl: String? = null
            val photos = mutableListOf<String>()
            var thumb = ""
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val u = it.optString("url")
                if (u.isEmpty()) continue
                val t = it.optString("type").lowercase()
                if (thumb.isEmpty()) thumb = it.optString("thumb")
                when {
                    t.startsWith("video") && videoUrl == null -> videoUrl = u
                    t.startsWith("audio") && audioUrl == null -> audioUrl = u
                    t.startsWith("image") -> photos.add(u)
                    videoUrl == null -> videoUrl = u // unknown type: treat as video
                }
            }
            if (videoUrl == null && photos.isEmpty()) {
                return Result.failure(Exception("No downloadable media found."))
            }
            Result.success(
                CobaltMedia(
                    id = pageUrl.hashCode().toString(),
                    title = "Instagram media",
                    author = "",
                    thumbnail = thumb,
                    videoUrl = videoUrl,
                    audioUrl = audioUrl,
                    photoUrls = photos
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Instagram error: ${e.message}"))
        }
    }

    private fun getJson(urlStr: String): JSONObject {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-rapidapi-host", HOST)
            setRequestProperty("x-rapidapi-key", API_KEY)
        }
        try {
            val code = conn.responseCode
            if (code == 403) {
                throw Exception("API subscription not active yet. Try again in a bit.")
            }
            if (code != 200) throw Exception("HTTP $code")
            val text = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
