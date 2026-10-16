package com.primevidvault.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Normalized Instagram media resolved through the Cobalt API.
 * Only fields Cobalt actually returns are populated — the app must never
 * assume a URL is a video: branch on status/type before labeling anything.
 */
data class CobaltMedia(
    val id: String,
    val title: String,
    val author: String,
    val thumbnail: String,
    val videoUrl: String?,
    val audioUrl: String?,
    val photoUrls: List<String>
)

/**
 * Cobalt API client (v10/v11 schema) with automatic failover across three
 * public instances.
 *
 * Request: POST {instance} (root, no path) with
 *   {"url": ..., "videoQuality": "1080", "downloadMode": "auto", "audioFormat": "mp3"}
 * Response status: "tunnel"/"redirect" -> single file at "url";
 *   "picker" -> multi-item array, per-item "type" ("photo"/"video"/"gif") + "url";
 *   "error" -> {"error": {"code": ...}}.
 *
 * Failover triggers (try next instance): auth gating, rate limits, capacity,
 * disabled services, network errors. Content errors (private/deleted/bad link)
 * are final — no point retrying them elsewhere.
 */
object CobaltClient {

    private val INSTANCES = listOf(
        "https://kityune.imput.net",
        "https://cobalt-backend.canine.tools",
        "https://cobalt-api.meowing.de"
    )
    private const val TIMEOUT_MS = 25000

    /** This instance can't serve the request — try the next one. */
    private class FailoverException(msg: String) : Exception(msg)

    /** The link itself is bad/private — final, don't retry other instances. */
    private class ContentException(msg: String) : Exception(msg)

    /** Error codes that mean "try the next instance". */
    private val FAILOVER_CODES = setOf(
        "error.api.auth.jwt.missing",
        "error.api.auth.key.invalid",
        "error.api.auth.turnstile.missing",
        "error.api.rate_exceeded",
        "error.api.capacity",
        "error.api.service.disabled",
        "error.api.service.unsupported"
    )

    fun fetch(pageUrl: String): Result<CobaltMedia> {
        var lastError: Exception =
            Exception("Couldn't reach the download servers. Try again.")
        for (base in INSTANCES) {
            try {
                return Result.success(resolve(base, pageUrl))
            } catch (e: ContentException) {
                return Result.failure(e)
            } catch (e: Exception) {
                lastError = e
            }
        }
        return Result.failure(lastError)
    }

    private fun resolve(base: String, pageUrl: String): CobaltMedia {
        val root = postJson(
            base, mapOf(
                "url" to pageUrl,
                "videoQuality" to "1080",
                "downloadMode" to "auto",
                "audioFormat" to "mp3"
            )
        )
        val status = root.optString("status")
        if (status == "error") handleApiError(root)
        val id = extractId(pageUrl)
        return when (status) {
            "tunnel", "redirect" -> {
                val videoUrl = root.optString("url").ifEmpty { null }
                    ?: throw FailoverException("Empty response from server")
                CobaltMedia(id, "", "", "", videoUrl, tryAudio(base, pageUrl), emptyList())
            }
            "picker" -> {
                val photos = mutableListOf<String>()
                var videoUrl: String? = null
                var thumb = ""
                val picker = root.optJSONArray("picker")
                if (picker != null) {
                    for (i in 0 until picker.length()) {
                        val item = picker.optJSONObject(i) ?: continue
                        val u = item.optString("url")
                        if (u.isEmpty()) continue
                        when (item.optString("type")) {
                            "photo" -> photos.add(u)
                            "video", "gif" ->
                                if (videoUrl == null) {
                                    videoUrl = u
                                    thumb = item.optString("thumb")
                                }
                        }
                    }
                }
                val audioUrl = root.optString("audio").ifEmpty { null }
                if (videoUrl == null && photos.isEmpty() && audioUrl == null) {
                    throw FailoverException("Nothing downloadable in response")
                }
                CobaltMedia(id, "", "", thumb, videoUrl, audioUrl, photos)
            }
            "local-processing" -> throw FailoverException("Server busy, trying another")
            else -> throw FailoverException("Unexpected response: $status")
        }
    }

    /** Best-effort audio-only resolve for the MP3 button. Null on any failure. */
    private fun tryAudio(base: String, pageUrl: String): String? {
        return try {
            val root = postJson(
                base, mapOf(
                    "url" to pageUrl,
                    "downloadMode" to "audio",
                    "audioFormat" to "mp3"
                )
            )
            if (root.optString("status") == "error") return null
            root.optString("url").ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }

    private fun handleApiError(root: JSONObject): Nothing {
        val code = root.optJSONObject("error")?.optString("code").orEmpty()
        if (code in FAILOVER_CODES) throw FailoverException(code)
        throw ContentException(
            when {
                code.contains("private") ->
                    "This post is private or unavailable."
                code.startsWith("error.api.content.") ->
                    "Couldn't fetch this post — it may be deleted or region-locked."
                code == "error.api.link.invalid" ->
                    "That link didn't work. Check it and try again."
                else -> "Couldn't fetch this post. Try again."
            }
        )
    }

    private fun postJson(base: String, fields: Map<String, String>): JSONObject {
        val body = JSONObject().apply {
            for ((k, v) in fields) put(k, v)
        }.toString()
        val conn = (URL(base).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "PRIMEVIDVAULT/4.0")
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
                .use { it.readText() }
            if (code !in 200..299) throw FailoverException("HTTP $code")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractId(pageUrl: String): String {
        val m = Regex("instagram\\.com/(?:reel|reels|p|tv)/([A-Za-z0-9_-]+)").find(pageUrl)
        return m?.groupValues?.get(1) ?: System.currentTimeMillis().toString()
    }
}
