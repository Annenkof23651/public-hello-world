package com.ytagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private data class QuickAction(val label: String, val prompt: String)

private val quickActions = listOf(
    QuickAction(
        "Разбор канала",
        "Сделай полный разбор канала: что работает, что нет, главные проблемы роста и 5 приоритетных шагов на ближайший месяц.",
    ),
    QuickAction(
        "Идеи видео",
        "Предложи 10 идей для новых видео на основе того, что лучше всего заходит на канале. Для каждой: рабочее название, крючок первых секунд, формат (Shorts или длинное) и почему сработает.",
    ),
    QuickAction(
        "План публикаций",
        "Составь план публикаций на ближайшие 2 недели начиная с сегодняшнего дня: конкретные даты, дни недели и время в моём часовом поясе, что именно выпускать в каждый слот. Объясни выбор времени по данным канала.",
    ),
    QuickAction(
        "Продвижение",
        "Дай конкретные советы по продвижению канала: названия и превью, описание и теги, Shorts, плейлисты, комьюнити, коллаборации и внешние площадки. Начни с того, что даст эффект быстрее всего.",
    ),
    QuickAction(
        "Названия",
        "Возьми 5 последних видео и предложи по 3 более кликабельных варианта названия и идею превью для каждого, без кликбейта-обмана.",
    ),
)

@Composable
fun AgentScreen(vm: AppViewModel, modifier: Modifier = Modifier) {
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(vm.chat.size, vm.agentBusy) {
        val count = vm.chat.size + if (vm.agentBusy) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YouTube-агент", style = MaterialTheme.typography.titleLarge)
                Text(
                    vm.channel?.let { "Канал: ${it.title}" } ?: "Канал не загружен — советы будут общими",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.newConversation() }, enabled = !vm.agentBusy) {
                Icon(Icons.Filled.Refresh, "Новый диалог")
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(quickActions) { action ->
                AssistChip(onClick = { vm.ask(action.prompt) }, label = { Text(action.label) }, enabled = !vm.agentBusy)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (vm.chat.isEmpty()) {
                item {
                    Text(
                        "Выберите действие выше или задайте вопрос: «О чём снять следующее видео?», «Почему упали просмотры?», «Когда выложить Shorts завтра?»",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            itemsIndexed(vm.chat) { _, msg -> Bubble(msg) }
            if (vm.agentBusy) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Агент анализирует… (до минуты)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Спросите агента…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            IconButton(
                onClick = { vm.ask(input); input = "" },
                enabled = input.isNotBlank() && !vm.agentBusy,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить") }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val colors = when {
        msg.isError -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        msg.fromUser -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else -> CardDefaults.cardColors()
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = if (msg.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(colors = colors, modifier = if (msg.fromUser) Modifier.widthIn(max = 320.dp) else Modifier.fillMaxWidth()) {
            SelectionContainer {
                Text(renderMarkdown(msg.text), Modifier.padding(12.dp))
            }
        }
    }
}

/** Light Markdown rendering: headings and **bold** become bold, list markers become bullets. */
private fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    text.lines().forEachIndexed { i, rawLine ->
        if (i > 0) append('\n')
        var line = rawLine
        val heading = line.trimStart().startsWith("#")
        if (heading) line = line.trimStart().trimStart('#').trim()
        line = line.replace(Regex("""^(\s*)[-*] """), "$1• ")
        if (line.trim() == "---") return@forEachIndexed
        if (heading) {
            withStyle(bold) { append(line.replace("**", "")) }
            return@forEachIndexed
        }
        val parts = line.split("**")
        parts.forEachIndexed { idx, part ->
            if (idx % 2 == 1 && idx < parts.lastIndex) withStyle(bold) { append(part) } else append(part)
        }
    }
}
