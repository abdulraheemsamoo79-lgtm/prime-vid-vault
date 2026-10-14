package com.primevidvault.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free tikwm API client. No API key needed.
 * GET https://www.tikwm.com/api/?url=<tiktok link>&hd=1
 */
object TikWmClient {

    fun fetch(tiktokUrl: String): Result<VideoInfo> {
        return try {
            val encoded = URLEncoder.encode(tiktokUrl, "UTF-8")
            val url = URL("https://www.tikwm.com/api/?url=$encoded&hd=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.setRequestProperty("User-Agent", "PRIME-VID-VAULT/2.0")
            val code = conn.responseCode
            if (code != 200) {
                return Result.failure(Exception("Server error: $code. Try again."))
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val body = reader.readText()
            reader.close()

            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                return Result.failure(
                    Exception("Video not found. Check the link — the video may be private or deleted.")
                )
            }
            val d = json.getJSONObject("data")
            val author = d.optJSONObject("author")
            val info = VideoInfo(
                id = d.optString("id", System.currentTimeMillis().toString()),
                title = d.optString("title", "TikTok Video"),
                cover = d.optString("origin_cover", d.optString("cover", "")),
                playUrl = d.optString("play", ""),
                wmPlayUrl = d.optString("wmplay", ""),
                hdPlayUrl = d.optString("hdplay", d.optString("play", "")),
                musicUrl = d.optString("music", ""),
                authorName = author?.optString("nickname", "") ?: "",
                authorUsername = author?.optString("unique_id", "") ?: "",
                authorAvatar = author?.optString("avatar", "") ?: "",
                duration = d.optInt("duration", 0),
                playCount = d.optLong("play_count", 0),
                likeCount = d.optLong("digg_count", 0),
                commentCount = d.optLong("comment_count", 0),
                shareCount = d.optLong("share_count", 0)
            )
            if (info.playUrl.isEmpty()) {
                return Result.failure(Exception("Download link not found. Try again."))
            }
            Result.success(info)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
