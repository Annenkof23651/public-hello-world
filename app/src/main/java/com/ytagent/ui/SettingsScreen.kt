package com.ytagent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: AppViewModel, modifier: Modifier = Modifier, onSaved: () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = vm.channelInput,
            onValueChange = { vm.channelInput = it },
            label = { Text("Канал: @handle, ссылка или ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.youtubeKey,
            onValueChange = { vm.youtubeKey = it },
            label = { Text("YouTube Data API ключ") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = vm.anthropicKey,
            onValueChange = { vm.anthropicKey = it },
            label = { Text("Anthropic API ключ (sk-ant-…)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.saveSettings(); onSaved() },
            enabled = vm.isConfigured,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить и загрузить канал") }

        Text(
            """
            Где взять ключи:
            • YouTube Data API: console.cloud.google.com → создать проект → «APIs & Services» → включить «YouTube Data API v3» → «Credentials» → «Create credentials» → «API key». Бесплатно.
            • Anthropic API: console.anthropic.com → «API Keys» → «Create Key». Оплата по факту использования.

            Ключи хранятся только на этом телефоне.
            """.trimIndent(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
