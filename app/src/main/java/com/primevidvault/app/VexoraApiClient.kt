package com.primevidvault.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Instagram downloads via our own VEXORA API.
 * GET https://vexora-api-orcin.vercel.app/api/instagram?url=<instagram-url>
 * Response: { status, success, downloadUrl,
 *             result: { type, video, image, caption } }
 *           or { status: false, message } when nothing is downloadable.
 *
 * This replaced the direct-Apify client in v4.5 (2026-11-13): the Apify
 * token embedded in the app was rejected by Apify (HTTP 401), which broke
 * the Instagram tab for every user. The API keeps its backends
 * server-side, so no token ever ships inside the APK again.
 * Reuses CobaltMedia so the Instagram UI needs no changes.
 */
object VexoraApiClient {

    private const val ENDPOINT = "https://vexora-api-orcin.vercel.app/api/instagram"

    /** The API may need a while when its first backend is slow. */
    private const val TIMEOUT_MS = 90000

    fun fetch(pageUrl: String): Result<CobaltMedia> {
        return try {
            val root = getJson(pageUrl)
            if (root.opt("status") == false || root.optBoolean("success", true).not()) {
                val msg = root.optString("message")
                return Result.failure(
                    Exception(
                        if (msg.contains("No downloadable", ignoreCase = true))
                            "No media found for this link. It may be private."
                        else "Instagram error. Please try again in a moment."
                    )
                )
            }
            val result = root.optJSONObject("result") ?: JSONObject()
            val downloadUrl = root.optString("downloadUrl")
            val type = result.optString("type")
            val video = result.optString("video").ifEmpty { null }
            val image = result.optString("image").ifEmpty { null }

            val videoUrl: String?
            val photos: List<String>
            var thumb = ""
            when {
                video != null -> {
                    videoUrl = video
                    photos = emptyList()
                    thumb = image.orEmpty()
                }
                type.equals("Video", ignoreCase = true) && downloadUrl.isNotEmpty() -> {
                    videoUrl = downloadUrl
                    photos = emptyList()
                    thumb = image.orEmpty()
                }
                image != null -> {
                    videoUrl = null
                    photos = listOf(image)
                    thumb = image
                }
                downloadUrl.isNotEmpty() -> {
                    videoUrl = null
                    photos = listOf(downloadUrl)
                    thumb = downloadUrl
                }
                else -> return Result.failure(Exception("No downloadable media found."))
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
            Result.failure(Exception("Instagram error: ${e.message}"))
        }
    }

    private fun getJson(pageUrl: String): JSONObject {
        val full = ENDPOINT + "?url=" + URLEncoder.encode(pageUrl, "UTF-8")
        val conn = (URL(full).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "PRIMEVIDVAULT/4.5")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                .use { it.readText() }
            if (code !in 200..299) throw Exception("HTTP $code")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}
