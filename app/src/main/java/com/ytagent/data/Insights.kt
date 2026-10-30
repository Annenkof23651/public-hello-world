package com.ytagent.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Performance of videos published in one time slot, relative to the channel median (1.0 = typical). */
data class SlotStat(val label: String, val videos: Int, val relativePerformance: Double)

data class ChannelInsights(
    val medianViews: Long,
    val avgViews: Long,
    val engagementRate: Double,
    val uploadsPerWeek: Double,
    val daysSinceLastUpload: Long?,
    val shortsCount: Int,
    val longCount: Int,
    val shortsMedianViews: Long,
    val longMedianViews: Long,
    val byWeekday: List<SlotStat>,
    val byHour: List<SlotStat>,
    val topVideos: List<VideoInfo>,
    val weakVideos: List<VideoInfo>,
) {
    val bestWeekday: SlotStat? get() = byWeekday.filter { it.videos >= 2 }.maxByOrNull { it.relativePerformance }
    val bestHour: SlotStat? get() = byHour.filter { it.videos >= 2 }.maxByOrNull { it.relativePerformance }
}

object Insights {
    private val RU = Locale("ru")
    private val HOUR_BUCKETS = listOf(0, 6, 9, 12, 15, 18, 21) // bucket starts, local time

    fun compute(videos: List<VideoInfo>, zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): ChannelInsights {
        // Videos younger than 2 days have not collected their views yet and would skew slot stats.
        val mature = videos.filter { Duration.between(it.publishedAt, now).toHours() >= 48 }
        val median = median(mature.map { it.views }).coerceAtLeast(1)
        val totalViews = videos.sumOf { it.views }
        val interactions = videos.sumOf { it.likes + it.comments }

        val last90 = videos.count { Duration.between(it.publishedAt, now).toDays() <= 90 }
        val shorts = videos.filter { it.isShort }
        val longs = videos.filterNot { it.isShort }

        fun slots(key: (VideoInfo) -> Pair<Int, String>): List<SlotStat> =
            mature.groupBy(key).entries.sortedBy { it.key.first }.map { (k, list) ->
                SlotStat(k.second, list.size, list.map { it.views.toDouble() / median }.average())
            }

        val byDay = slots { v ->
            val d = v.publishedAt.atZone(zone).dayOfWeek
            d.value to d.getDisplayName(TextStyle.FULL, RU)
        }
        val byHour = slots { v ->
            val h = v.publishedAt.atZone(zone).hour
            val start = HOUR_BUCKETS.last { it <= h }
            val end = HOUR_BUCKETS.getOrNull(HOUR_BUCKETS.indexOf(start) + 1) ?: 24
            start to "%02d:00–%02d:00".format(start, end)
        }

        val ranked = mature.sortedByDescending { it.views }
        return ChannelInsights(
            medianViews = median,
            avgViews = if (videos.isEmpty()) 0 else totalViews / videos.size,
            engagementRate = if (totalViews == 0L) 0.0 else interactions.toDouble() / totalViews,
            uploadsPerWeek = last90 / (90.0 / 7),
            daysSinceLastUpload = videos.maxOfOrNull { it.publishedAt }?.let { Duration.between(it, now).toDays() },
            shortsCount = shorts.size,
            longCount = longs.size,
            shortsMedianViews = median(shorts.map { it.views }),
            longMedianViews = median(longs.map { it.views }),
            byWeekday = byDay,
            byHour = byHour,
            topVideos = ranked.take(5),
            weakVideos = ranked.takeLast(5).reversed().filter { it !in ranked.take(5) },
        )
    }

    /** Plain-text dossier on the channel that the agent receives as context. */
    fun buildContext(channel: ChannelInfo, videos: List<VideoInfo>, insights: ChannelInsights, zone: ZoneId = ZoneId.systemDefault()): String {
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE HH:mm", RU).withZone(zone)
        val now = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm", RU).withZone(zone).format(Instant.now())
        return buildString {
            appendLine("Текущее время пользователя: $now, часовой пояс: $zone")
            appendLine()
            appendLine("## Канал")
            appendLine("Название: ${channel.title} (${channel.handle})")
            appendLine("Подписчики: ${if (channel.hiddenSubscribers) "скрыты" else channel.subscribers}")
            appendLine("Всего просмотров: ${channel.totalViews}, видео: ${channel.videoCount}")
            channel.createdAt?.let { appendLine("Создан: ${fmt.format(it)}") }
            if (channel.description.isNotBlank()) appendLine("Описание: ${channel.description.take(800)}")
            appendLine()
            appendLine("## Сводка по последним ${videos.size} видео")
            appendLine("Медиана просмотров: ${insights.medianViews}, среднее: ${insights.avgViews}")
            appendLine("Вовлечённость (лайки+комментарии)/просмотры: ${"%.2f".format(insights.engagementRate * 100)}%")
            appendLine("Частота: ${"%.1f".format(insights.uploadsPerWeek)} видео в неделю (последние 90 дней)")
            insights.daysSinceLastUpload?.let { appendLine("Дней с последней публикации: $it") }
            appendLine("Shorts: ${insights.shortsCount} (медиана ${insights.shortsMedianViews}), длинные: ${insights.longCount} (медиана ${insights.longMedianViews})")
            appendLine()
            appendLine("## Эффективность по времени публикации (1.0 = медиана канала, локальное время)")
            insights.byWeekday.forEach { appendLine("${it.label}: ${"%.2f".format(it.relativePerformance)} (видео: ${it.videos})") }
            insights.byHour.forEach { appendLine("${it.label}: ${"%.2f".format(it.relativePerformance)} (видео: ${it.videos})") }
            appendLine()
            appendLine("## Видео (дата публикации | длительность | просмотры | лайки | комментарии | название | теги)")
            videos.forEach { v ->
                val dur = if (v.isShort) "short ${v.durationSec}с" else "${v.durationSec / 60}м"
                appendLine("${fmt.format(v.publishedAt)} | $dur | ${v.views} | ${v.likes} | ${v.comments} | ${v.title} | ${v.tags.take(8).joinToString(", ")}")
            }
        }
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }
}
