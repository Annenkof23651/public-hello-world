package com.ytagent.data

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

data class ChannelInfo(
    val id: String,
    val title: String,
    val description: String,
    val handle: String,
    val subscribers: Long,
    val hiddenSubscribers: Boolean,
    val totalViews: Long,
    val videoCount: Long,
    val createdAt: Instant?,
    val uploadsPlaylistId: String,
)

data class VideoInfo(
    val id: String,
    val title: String,
    val description: String,
    val tags: List<String>,
    val publishedAt: Instant,
    val durationSec: Long,
    val views: Long,
    val likes: Long,
    val comments: Long,
) {
    /** YouTube treats vertical videos up to 3 minutes as Shorts; the API has no explicit flag. */
    val isShort: Boolean get() = durationSec in 1..180
}

class YouTubeException(message: String) : IOException(message)

/** Minimal client for the public YouTube Data API v3 (API key, no OAuth). */
class YouTubeClient(private val apiKey: String) {

    fun fetchChannel(input: String): ChannelInfo {
        val query = parseChannelInput(input)
        val params = mutableMapOf("part" to "snippet,statistics,contentDetails")
        when (query) {
            is ChannelQuery.Id -> params["id"] = query.value
            is ChannelQuery.Handle -> params["forHandle"] = query.value
        }
        val items = get("channels", params).optJSONArray("items")
        if (items == null || items.length() == 0) {
            throw YouTubeException("Канал не найден. Проверьте @handle или ссылку.")
        }
        val item = items.getJSONObject(0)
        val snippet = item.getJSONObject("snippet")
        val stats = item.optJSONObject("statistics") ?: JSONObject()
        return ChannelInfo(
            id = item.getString("id"),
            title = snippet.optString("title"),
            description = snippet.optString("description"),
            handle = snippet.optString("customUrl"),
            subscribers = stats.optString("subscriberCount", "0").toLongOrNull() ?: 0,
            hiddenSubscribers = stats.optBoolean("hiddenSubscriberCount", false),
            totalViews = stats.optString("viewCount", "0").toLongOrNull() ?: 0,
            videoCount = stats.optString("videoCount", "0").toLongOrNull() ?: 0,
            createdAt = runCatching { Instant.parse(snippet.optString("publishedAt")) }.getOrNull(),
            uploadsPlaylistId = item.getJSONObject("contentDetails")
                .getJSONObject("relatedPlaylists").getString("uploads"),
        )
    }

    fun fetchRecentVideos(uploadsPlaylistId: String, max: Int = 50): List<VideoInfo> {
        val playlist = get(
            "playlistItems",
            mapOf(
                "part" to "contentDetails",
                "playlistId" to uploadsPlaylistId,
                "maxResults" to max.coerceIn(1, 50).toString(),
            ),
        )
        val items = playlist.optJSONArray("items") ?: return emptyList()
        val ids = (0 until items.length()).map {
            items.getJSONObject(it).getJSONObject("contentDetails").getString("videoId")
        }
        if (ids.isEmpty()) return emptyList()

        val videos = get(
            "videos",
            mapOf("part" to "snippet,statistics,contentDetails", "id" to ids.joinToString(",")),
        ).optJSONArray("items") ?: return emptyList()

        return (0 until videos.length()).map { i ->
            val v = videos.getJSONObject(i)
            val snippet = v.getJSONObject("snippet")
            val stats = v.optJSONObject("statistics") ?: JSONObject()
            val tags = snippet.optJSONArray("tags")
            VideoInfo(
                id = v.getString("id"),
                title = snippet.optString("title"),
                description = snippet.optString("description"),
                tags = if (tags == null) emptyList() else (0 until tags.length()).map { tags.getString(it) },
                publishedAt = Instant.parse(snippet.getString("publishedAt")),
                durationSec = parseDuration(v.optJSONObject("contentDetails")?.optString("duration")),
                views = stats.optString("viewCount", "0").toLongOrNull() ?: 0,
                likes = stats.optString("likeCount", "0").toLongOrNull() ?: 0,
                comments = stats.optString("commentCount", "0").toLongOrNull() ?: 0,
            )
        }.sortedByDescending { it.publishedAt }
    }

    private fun get(endpoint: String, params: Map<String, String>): JSONObject {
        val query = (params + ("key" to apiKey)).entries.joinToString("&") { (k, v) ->
            "$k=${URLEncoder.encode(v, "UTF-8")}"
        }
        val conn = URL("https://www.googleapis.com/youtube/v3/$endpoint?$query")
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val reason = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault(body.take(200))
                throw YouTubeException("YouTube API ($code): $reason")
            }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private sealed interface ChannelQuery {
        data class Id(val value: String) : ChannelQuery
        data class Handle(val value: String) : ChannelQuery
    }

    private fun parseChannelInput(raw: String): ChannelQuery {
        val input = raw.trim().removeSuffix("/")
        Regex("""(UC[\w-]{22})""").find(input)?.let { return ChannelQuery.Id(it.value) }
        Regex("""@([\w.\-]+)""").find(input)?.let { return ChannelQuery.Handle("@" + it.groupValues[1]) }
        val last = input.substringAfterLast('/')
        if (last.isBlank()) throw YouTubeException("Укажите канал: @handle, ссылку или ID (UC…)")
        return ChannelQuery.Handle("@$last")
    }

    private fun parseDuration(iso: String?): Long =
        if (iso.isNullOrBlank()) 0 else runCatching { Duration.parse(iso).seconds }.getOrDefault(0)
}
