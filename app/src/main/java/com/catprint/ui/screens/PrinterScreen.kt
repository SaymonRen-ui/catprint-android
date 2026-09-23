package com.catprint.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.catprint.ble.PrinterState

@Composable
fun PrinterScreen(vm: AppViewModel, logLines: List<String>) {
    val state by vm.printer.state.collectAsState()
    val battery by vm.printer.lastBattery.collectAsState()
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            // Статус
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dot = when (state) {
                            PrinterState.CONNECTED -> Color(0xFF16A34A)
                            PrinterState.CONNECTING,
                            PrinterState.SCANNING -> Color(0xFFD97706)
                            else -> Color.Gray
                        }
                        Spacer(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(dot)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (state) {
                                PrinterState.CONNECTED -> vm.printer.deviceName.ifBlank { "Подключено" }
                                PrinterState.CONNECTING -> "Подключение..."
                                PrinterState.SCANNING -> "Сканирование..."
                                PrinterState.ERROR -> "Ошибка"
                                else -> "Не подключено"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    val level = battery
                    if (level != null) {
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = { vm.queryBattery() }) {
                            Text(
                                (if (level <= 20) "🪫" else "🔋") +
                                    " Батарея: $level% (обновить)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.scan() },
                            enabled = !vm.isScanning &&
                                state != PrinterState.CONNECTING
                        ) { Text(if (vm.isScanning) "..." else "🔍 Скан") }
                        OutlinedButton(onClick = { vm.quickConnect() }) {
                            Text("MXW01")
                        }
                        if (state == PrinterState.CONNECTED) {
                            OutlinedButton(onClick = { vm.disconnect() }) {
                                Text("Откл.")
                            }
                        }
                    }
                    if (vm.isScanning) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }

        // Найденные устройства
        if (vm.devices.isNotEmpty()) {
            item {
                Text("Устройства", style = MaterialTheme.typography.titleSmall)
            }
            items(vm.devices, key = { it.address }) { d ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${d.address} · ${d.rssi} dBm",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(onClick = { vm.connect(d) }) { Text("→") }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { vm.requestStatus() },
                enabled = state == PrinterState.CONNECTED,
                modifier = Modifier.fillMaxWidth()
            ) { Text("🔋 Запросить статус (A1)") }
        }

        // Журнал
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Журнал",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(logLines.joinToString("\n")))
                        }) { Text("Копия") }
                        TextButton(onClick = { vm.log.clear() }) { Text("Очист.") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = logLines.takeLast(30).joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
