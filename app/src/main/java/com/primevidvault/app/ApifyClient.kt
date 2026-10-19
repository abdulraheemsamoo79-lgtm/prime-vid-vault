package com.primevidvault.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Instagram downloads via the user's Apify actor.
 * POST https://api.apify.com/v2/acts/snapinst~instagram-downloader-api/run-sync-get-dataset-items
 * Headers: Content-Type: application/json, Authorization: Bearer <token>
 * Body: {"url": "<instagram-url>"}
 * Response: JSON array of dataset items (run-sync-get-dataset-items).
 * Reuses CobaltMedia so the Instagram UI needs no changes.
 */
object ApifyClient {

    private const val ENDPOINT =
        "https://api.apify.com/v2/acts/snapinst~instagram-downloader-api/run-sync-get-dataset-items"

    // ================================================================
    //  ▼▼▼  PASTE YOUR APIFY API TOKEN BETWEEN THE QUOTES BELOW  ▼▼▼
    //
    //  1. Go to https://console.apify.com/account/integrations
    //  2. Copy your API token
    //  3. Paste it between the quotes on the next line, replacing
    //     PASTE_YOUR_APIFY_TOKEN_HERE — keep the quotes.
    //
    //  NOTE: anyone who extracts this APK can read the token, so set
    //  usage limits on your Apify account.
    // ================================================================
    private const val APIFY_TOKEN = "apify_api_OPB5eB0MTZfuLNm0BGXNyphyBthRY31gf1W1"

    /** Actor runs take a while — give it up to 2 minutes. */
    private const val TIMEOUT_MS = 120000

    fun fetch(pageUrl: String): Result<CobaltMedia> {
        return try {
            if (APIFY_TOKEN == "PASTE_YOUR_APIFY_TOKEN_HERE" || APIFY_TOKEN.isBlank()) {
                return Result.failure(
                    Exception("Apify API token is not set. Add it in ApifyClient.kt (APIFY_TOKEN).")
                )
            }
            val items = postForItems(pageUrl)
            if (items.length() == 0) {
                return Result.failure(Exception("No media found for this link."))
            }
            var videoUrl: String? = null
            val photos = mutableListOf<String>()
            var thumb = ""
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                val v = firstNonEmpty(
                    it, "videoUrl", "video_url", "video", "downloadUrl",
                    "download_url", "playUrl", "play_url"
                )
                val img = firstNonEmpty(
                    it, "displayUrl", "display_url", "imageUrl", "image_url",
                    "image", "photo", "photoUrl", "photo_url"
                )
                val t = firstNonEmpty(
                    it, "thumbnail", "thumbnailUrl", "thumbnail_url", "cover", "poster"
                )
                if (thumb.isEmpty()) thumb = t.ifEmpty { img }
                val type = firstNonEmpty(it, "type", "__typename", "mediaType", "media_type")
                    .lowercase()
                val genericUrl = it.optString("url")
                when {
                    // Explicitly a video/reel, or has a video URL and no image signal.
                    type.contains("video") || type.contains("reel") || type.contains("clip") -> {
                        if (videoUrl == null) {
                            val candidate = v.ifEmpty { genericUrl }
                            if (candidate.isNotEmpty()) videoUrl = candidate
                        }
                    }
                    type.contains("image") || type.contains("photo") || type.contains("carousel") -> {
                        val p = img.ifEmpty { genericUrl }
                        if (p.isNotEmpty()) photos.add(p)
                    }
                    v.isNotEmpty() && videoUrl == null -> videoUrl = v
                    img.isNotEmpty() -> photos.add(img)
                    genericUrl.isNotEmpty() && videoUrl == null -> videoUrl = genericUrl
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
                    audioUrl = null,
                    photoUrls = photos
                )
            )
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val friendly = when {
                msg.contains("401") || msg.contains("403") ->
                    "Apify token rejected (HTTP 401/403). Check the token in ApifyClient.kt."
                msg.contains("429") ->
                    "Apify rate limit hit. Try again in a bit."
                else -> "Instagram error: ${e.message}"
            }
            Result.failure(Exception(friendly))
        }
    }

    private fun firstNonEmpty(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = obj.optString(k)
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    /** POSTs to the actor and returns the dataset items array (handles a
     *  wrapped object too, in case the actor nests items). */
    private fun postForItems(pageUrl: String): JSONArray {
        val body = JSONObject().put("url", pageUrl).toString()
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $APIFY_TOKEN")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                .use { it.readText() }
            if (code == 401 || code == 403) throw Exception("HTTP $code")
            if (code == 429) throw Exception("HTTP 429")
            if (code !in 200..299) throw Exception("HTTP $code")
            val trimmed = text.trim()
            return when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val root = JSONObject(trimmed)
                    root.optJSONArray("items")
                        ?: root.optJSONArray("data")
                        ?: root.optJSONArray("result")
                        ?: JSONArray()
                }
                else -> throw Exception("Unexpected response from Apify.")
            }
        } finally {
            conn.disconnect()
        }
    }
}
