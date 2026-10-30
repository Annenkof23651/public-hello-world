package com.ytagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ytagent.data.ChannelInsights
import com.ytagent.data.SlotStat
import com.ytagent.data.VideoInfo
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM, EE HH:mm", Locale("ru")).withZone(ZoneId.systemDefault())

@Composable
fun ChannelScreen(vm: AppViewModel, modifier: Modifier = Modifier, onOpenSettings: () -> Unit) {
    val channel = vm.channel
    val insights = vm.insights
    when {
        !vm.isConfigured -> Centered(modifier) {
            Text("Сначала укажите канал и API-ключи")
            Button(onClick = onOpenSettings) { Text("Открыть настройки") }
        }
        channel == null && vm.loadingChannel -> Centered(modifier) {
            CircularProgressIndicator()
            Text("Загружаю статистику канала…")
        }
        channel == null || insights == null -> Centered(modifier) {
            Text(vm.channelError ?: "Нет данных", color = MaterialTheme.colorScheme.error)
            Button(onClick = { vm.refreshChannel() }) { Text("Повторить") }
        }
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(channel.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(channel.handle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (vm.loadingChannel) {
                        CircularProgressIndicator()
                    } else {
                        IconButton(onClick = { vm.refreshChannel() }) { Icon(Icons.Filled.Refresh, "Обновить") }
                    }
                }
                vm.channelError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Stat("Подписчики", if (channel.hiddenSubscribers) "скрыты" else compact(channel.subscribers))
                        Stat("Просмотры канала", compact(channel.totalViews))
                        Stat("Видео", channel.videoCount.toString())
                        Stat("Медиана просмотров (посл. ${vm.videos.size})", compact(insights.medianViews))
                        Stat("Вовлечённость", "%.2f%%".format(insights.engagementRate * 100))
                        Stat("Публикаций в неделю", "%.1f".format(insights.uploadsPerWeek))
                        insights.daysSinceLastUpload?.let { Stat("Дней с последнего видео", it.toString()) }
                        Stat("Shorts / длинные", "${insights.shortsCount} / ${insights.longCount}")
                    }
                }
            }
            item { TimingCard(insights) }
            item { Text("Последние видео", style = MaterialTheme.typography.titleMedium) }
            items(vm.videos, key = { it.id }) { VideoRow(it, insights.medianViews) }
        }
    }
}

@Composable
private fun TimingCard(insights: ChannelInsights) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Когда публиковать", style = MaterialTheme.typography.titleMedium)
            val day = insights.bestWeekday
            val hour = insights.bestHour
            if (day == null && hour == null) {
                Text("Пока мало видео для выводов. Спросите агента — он подскажет по нише.")
            } else {
                day?.let { Text("Лучший день: ${it.label} (×${"%.1f".format(it.relativePerformance)} к медиане)", fontWeight = FontWeight.SemiBold) }
                hour?.let { Text("Лучшее время: ${it.label} (×${"%.1f".format(it.relativePerformance)})", fontWeight = FontWeight.SemiBold) }
            }
            insights.byWeekday.forEach { SlotRow(it) }
            insights.byHour.forEach { SlotRow(it) }
            Text(
                "×1.0 — обычный результат канала. Расчёт по вашим прошлым публикациям, время местное.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SlotRow(s: SlotStat) {
    Row(Modifier.fillMaxWidth()) {
        Text(s.label, Modifier.weight(1f))
        Text("×${"%.2f".format(s.relativePerformance)}  (${s.videos})")
    }
}

@Composable
private fun VideoRow(v: VideoInfo, median: Long) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val ratio = if (median > 0) v.views.toDouble() / median else 0.0
            Text(
                "${dateFmt.format(v.publishedAt)} · ${if (v.isShort) "Shorts" else "${v.durationSec / 60} мин"} · " +
                    "${compact(v.views)} просм. (×${"%.1f".format(ratio)}) · ${compact(v.likes)} лайков",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

private fun compact(n: Long): String = when {
    n >= 1_000_000 -> "%.1f млн".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1f тыс".format(n / 1_000.0)
    else -> n.toString()
}
