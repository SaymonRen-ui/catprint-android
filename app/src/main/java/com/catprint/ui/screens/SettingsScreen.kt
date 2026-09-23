package com.catprint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import com.catprint.ui.theme.AppSwitch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.catprint.core.printer.BitOrder

@Composable
fun SettingsScreen(vm: AppViewModel) {
    // Локальные состояния для мгновенного отклика, пишем в store напрямую
    var intensity by remember { mutableStateOf(vm.settings.intensity) }
    var copies by remember { mutableStateOf(vm.settings.copies) }
    var autoDensity by remember { mutableStateOf(vm.settings.autoDensity) }
    var adaptiveHeat by remember { mutableStateOf(vm.settings.adaptiveHeat) }
    var bitMsb by remember { mutableStateOf(vm.settings.bitOrder == BitOrder.MSB_FIRST) }
    var lineDelay by remember { mutableStateOf(vm.settings.lineDelayMs.toFloat()) }
    var blockLines by remember { mutableStateOf(vm.settings.blockLines.toFloat()) }
    var blockPause by remember { mutableStateOf(vm.settings.blockPauseMs.toFloat()) }
    var split by remember { mutableStateOf(vm.settings.splitEnabled) }
    var splitLines by remember { mutableStateOf(vm.settings.splitLines.toFloat()) }
    var splitPause by remember { mutableStateOf(vm.settings.splitPauseMs.toFloat()) }
    var themeMenu by remember { mutableStateOf(false) }

    fun powerText(): String {
        val pct = ((intensity - 0x30) / 0.74).toInt().coerceIn(0, 100)
        val word = when {
            pct <= 30 -> "Эконом"
            pct <= 70 -> "Стандарт"
            else -> "Максимум"
        }
        return "$pct% · $word"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🖨 Печать", style = MaterialTheme.typography.titleMedium)

                Text("Пресеты", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = {
                        vm.applyPreset("doc"); reload(vm,
                            { intensity = it }, { lineDelay = it },
                            { blockLines = it }, { blockPause = it },
                            { split = it }, { splitLines = it }, { splitPause = it })
                    }) { Text("📄") }
                    OutlinedButton(onClick = {
                        vm.applyPreset("photo"); reload(vm,
                            { intensity = it }, { lineDelay = it },
                            { blockLines = it }, { blockPause = it },
                            { split = it }, { splitLines = it }, { splitPause = it })
                    }) { Text("📷") }
                    OutlinedButton(onClick = {
                        vm.applyPreset("fast"); reload(vm,
                            { intensity = it }, { lineDelay = it },
                            { blockLines = it }, { blockPause = it },
                            { split = it }, { splitLines = it }, { splitPause = it })
                    }) { Text("⚡") }
                    OutlinedButton(onClick = {
                        vm.applyPreset("safe"); reload(vm,
                            { intensity = it }, { lineDelay = it },
                            { blockLines = it }, { blockPause = it },
                            { split = it }, { splitLines = it }, { splitPause = it })
                    }) { Text("🛡") }
                }

                Spacer(Modifier.height(6.dp))
                Text("Плотность: ${powerText()}")
                Slider(
                    value = ((intensity - 0x30) / 0.74).toFloat().coerceIn(0f, 100f),
                    onValueChange = {
                        intensity = (0x30 + (it * 0.74).toInt()).coerceIn(0x30, 0x7A)
                        vm.settings.intensity = intensity
                    },
                    valueRange = 0f..100f
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Экземпляров: $copies")
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            copies = (copies - 1).coerceAtLeast(1)
                            vm.settings.copies = copies
                        },
                        enabled = copies > 1
                    ) { Text("−") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(
                        onClick = {
                            copies = (copies + 1).coerceAtMost(10)
                            vm.settings.copies = copies
                        },
                        enabled = copies < 10
                    ) { Text("+") }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автоплотность")
                    Spacer(Modifier.weight(1f))
                    AppSwitch(
                        checked = autoDensity,
                        onCheckedChange = {
                            autoDensity = it
                            vm.settings.autoDensity = it
                        }
                    )
                }
                Text(
                    "Текст — чётко на настроенной, фото — мягче: " +
                        "жар снижается по заливке.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Адаптивный жар")
                    Spacer(Modifier.weight(1f))
                    AppSwitch(
                        checked = adaptiveHeat,
                        onCheckedChange = {
                            adaptiveHeat = it
                            vm.settings.adaptiveHeat = it
                        }
                    )
                }
                Text(
                    "Жар отдельно для плотных и редких мест прямо " +
                        "по ходу печати: лицо пропекается, фон не плывёт.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Порядок бит: ${if (bitMsb) "MSB" else "LSB"}")
                    Spacer(Modifier.weight(1f))
                    AppSwitch(
                        checked = bitMsb,
                        onCheckedChange = {
                            bitMsb = it
                            vm.settings.bitOrder =
                                if (it) BitOrder.MSB_FIRST else BitOrder.LSB_FIRST
                        }
                    )
                }

                Text("Пауза строк: ${lineDelay.toInt()} мс")
                Slider(
                    value = lineDelay,
                    onValueChange = {
                        lineDelay = it
                        vm.settings.lineDelayMs = it.toLong()
                    },
                    valueRange = 5f..80f
                )

                Text("Строк подряд: ${blockLines.toInt()}")
                Slider(
                    value = blockLines,
                    onValueChange = {
                        blockLines = it
                        vm.settings.blockLines = it.toInt()
                    },
                    valueRange = 10f..100f
                )

                Text("Пауза пачки: ${blockPause.toInt()} мс")
                Slider(
                    value = blockPause,
                    onValueChange = {
                        blockPause = it
                        vm.settings.blockPauseMs = it.toLong()
                    },
                    valueRange = 0f..2000f
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Делить длинные задания ⚠ разрывы")
                    Spacer(Modifier.weight(1f))
                    AppSwitch(
                        checked = split,
                        onCheckedChange = {
                            split = it
                            vm.settings.splitEnabled = it
                        }
                    )
                }
                Text(
                    "Каждый кусок закрывается отдельно — принтер тянет " +
                        "бумагу между частями. Для сплошных картинок держать выкл.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Строк в части: ${splitLines.toInt()}")
                Slider(
                    value = splitLines,
                    onValueChange = {
                        splitLines = it
                        vm.settings.splitLines = it.toInt()
                    },
                    valueRange = 60f..240f
                )

                Text("Пауза между частями: ${splitPause.toInt()} мс")
                Slider(
                    value = splitPause,
                    onValueChange = {
                        splitPause = it
                        vm.settings.splitPauseMs = it.toLong()
                    },
                    valueRange = 200f..3000f
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🎨 Приложение", style = MaterialTheme.typography.titleMedium)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { themeMenu = true }) {
                        Text(
                            when (vm.themeMode) {
                                1 -> "Светлая"
                                2 -> "Тёмная"
                                else -> "Системная"
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = themeMenu,
                        onDismissRequest = { themeMenu = false }
                    ) {
                        listOf("Системная" to 0, "Светлая" to 1, "Тёмная" to 2)
                            .forEach { (name, mode) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        vm.themeMode = mode
                                        vm.settings.darkTheme = mode
                                        themeMenu = false
                                    }
                                )
                            }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "CatPrint 1.0.0 · MXW01 · протокол A2/A9/AD",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🧪 Диагностика", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                val state by vm.printer.state.collectAsState()
                OutlinedButton(
                    onClick = { vm.printTestPattern() },
                    enabled = state ==
                        com.catprint.ble.PrinterState.CONNECTED && !vm.isPrinting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сетка + диагональ") }
                Text(
                    "Печать тестового растра в обход редактора.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun reload(
    vm: AppViewModel,
    setIntensity: (Int) -> Unit,
    setLine: (Float) -> Unit,
    setBlockLines: (Float) -> Unit,
    setBlockPause: (Float) -> Unit,
    setSplit: (Boolean) -> Unit,
    setSplitLines: (Float) -> Unit,
    setSplitPause: (Float) -> Unit
) {
    setIntensity(vm.settings.intensity)
    setLine(vm.settings.lineDelayMs.toFloat())
    setBlockLines(vm.settings.blockLines.toFloat())
    setBlockPause(vm.settings.blockPauseMs.toFloat())
    setSplit(vm.settings.splitEnabled)
    setSplitLines(vm.settings.splitLines.toFloat())
    setSplitPause(vm.settings.splitPauseMs.toFloat())
}
