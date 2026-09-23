package com.catprint.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.catprint.R
import com.catprint.ui.theme.AppSwitch
import com.catprint.core.doc.DocBlock
import com.catprint.core.doc.DocRender
import com.catprint.core.doc.DocSerializer
import com.catprint.core.doc.FrameStyle
import com.catprint.core.doc.StyleAttr
import com.catprint.core.doc.TextRun
import com.catprint.core.doc.getAttr
import com.catprint.core.printer.AppFonts
import com.catprint.core.printer.DitherMode
import java.io.ByteArrayOutputStream

private val ANDROID_FONTS = AppFonts.allKeys
private val FONT_SIZES = listOf(14f, 18f, 22f, 28f, 36f, 48f, 64f)

private fun fontDisplayName(key: String): String =
    AppFonts.displayNames[key] ?: key.ifBlank { "Шрифт" }

private fun fontFamilyFor(key: String): FontFamily {
    // API 29+: цепочка со ч/б эмодзи — и текст своим шрифтом, и смайлы ч/б.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        try {
            AppFonts.chain(key)?.let { return FontFamily(it) }
        } catch (e: Exception) {
        }
    }
    return AppFonts.text(key)?.let { FontFamily(it) } ?: when (key) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}

/** Ч/б семейство для пикера эмодзи (API 29+, иначе системное). */
private fun emojiPickerFamily(): FontFamily? {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        try {
            AppFonts.chain(AppFonts.KEY_SANS)?.let { return FontFamily(it) }
        } catch (e: Exception) {
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var showBit by remember { mutableStateOf(false) }
    var showEgg by remember { mutableStateOf(false) }
    var docMenu by remember { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var zoomBmp by remember { mutableStateOf<Bitmap?>(null) }
    var imageSheet by remember { mutableStateOf<DocBlock.Image?>(null) }
    var formattingTarget by remember { mutableStateOf<DocBlock.Text?>(null) }
    // Выделение живёт в vm.fmtSel (см. AppViewModel): иначе каждое
    // движение ручки выделения рекомпозило весь список и жест обрывался
    // на первом символе. Короткий тап Ж/К/Ч красит выделение,
    // долгий — весь абзац.

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(DocSerializer.save(vm.blocks.toList()).toByteArray())
                }
                vm.log.log("Документ сохранён.")
            } catch (e: Exception) {
                vm.log.log("Ошибка сохранения: ${e.message}")
            }
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val list = DocSerializer.load(stream.bufferedReader().readText())
                    vm.blocks.clear()
                    vm.blocks.addAll(list)
                    vm.docName = "Документ"
                    vm.touch()
                    vm.log.log("Документ открыт.")
                }
            } catch (e: Exception) {
                vm.log.log("Ошибка открытия: ${e.message}")
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) vm.importAndAdd(uri)
    }
    // Фото с камеры: системное приложение снимает в наш кэш
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) photoUri?.let { vm.importAndAdd(it) }
    }
    fun launchCamera() {
        try {
            val dir = java.io.File(context.cacheDir, "shared")
            dir.mkdirs()
            val file = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "com.catprint.fileprovider", file
            )
            photoUri = uri
            camera.launch(uri)
        } catch (e: Exception) {
            vm.log.log("Камера недоступна: ${e.message}")
        }
    }
    val pngSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val rendered = vm.renderForPrint()
                val bmp = Bitmap.createBitmap(
                    rendered.width, rendered.height, Bitmap.Config.ARGB_8888
                )
                val px = IntArray(rendered.black.size) { i ->
                    if (rendered.black[i]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                bmp.setPixels(px, 0, rendered.width, 0, 0,
                    rendered.width, rendered.height)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                vm.log.log("PNG сохранён.")
            } catch (e: Exception) {
                vm.log.log("Ошибка PNG: ${e.message}")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(vm.docName, maxLines = 1) },
            navigationIcon = {
                // Лого-кот в шапке — пасхалка как на десктопе
                IconButton(onClick = { showEgg = true }) {
                    Image(
                        painter = painterResource(R.drawable.logo256),
                        contentDescription = "🐱",
                        modifier = Modifier
                            .size(32.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    )
                }
            },
            actions = {
                TextButton(
                    onClick = { vm.undo() },
                    enabled = vm.canUndo
                ) { Text("↩", fontSize = 24.sp) }
                TextButton(
                    onClick = { vm.redo() },
                    enabled = vm.canRedo
                ) { Text("↪", fontSize = 24.sp) }
                IconButton(
                    onClick = { showBit = !showBit },
                    enabled = vm.bitPreview != null
                ) {
                    Text(
                        text = "👁",
                        fontSize = 20.sp,
                        color = if (showBit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { vm.print() },
                    enabled = !vm.isPrinting && vm.blocks.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Печать")
                }
                // Нов/Откр/Сохр — в меню: на узком экране действия
                // не влезают и кнопку печати сплющивает.
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { docMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Документ"
                        )
                    }
                    DropdownMenu(
                        expanded = docMenu,
                        onDismissRequest = { docMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Новый документ") },
                            onClick = { vm.newDocument(); docMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Открыть…") },
                            onClick = {
                                openLauncher.launch(arrayOf("*/*"))
                                docMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Сохранить…") },
                            onClick = {
                                saveLauncher.launch(
                                    (vm.docName.ifBlank { "doc" }) + ".catdoc"
                                )
                                docMenu = false
                            }
                        )
                    }
                }
            }
        )

        // Статус принтера и батарея — чтобы не ходить на вкладку.
        // Тап обновляет батарею (троттлинг 30с внутри queryBattery).
        PrinterStatusStrip(vm)

        if (vm.isPrinting) {
            LinearProgressIndicator(
                progress = { vm.printProgress.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Панель формата активного текстового блока
        FormatBar(
            target = formattingTarget,
            vm = vm,
            onTarget = { formattingTarget = it },
            onPickImage = { imagePicker.launch("image/*") },
            onEmoji = { showEmoji = true }
        )

        // Подписка на revision: рекомпозиция БЕЗ пересоздания полей,
    // поэтому фокус и клавиатура живут, а стили обновляются.
    @Suppress("UNUSED_VARIABLE")
    val tick = vm.revision

        // В альбоме с включённым превью: редактор слева, бумага справа.
        // В портрете превью остаётся карточкой внизу списка.
        val landscape = LocalConfiguration.current.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val bit = vm.bitPreview
        val sidePreview = landscape && showBit && bit != null
        if (sidePreview && bit != null) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                DocBlocksList(
                    vm = vm,
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surface),
                    showPreviewCard = false,
                    onFocusText = { b, sel ->
                        formattingTarget = b
                        vm.updateSel(b.uid, sel.start, sel.end)
                    },
                    // Только пишем в VM (не читаем): список не должен
                    // рекомпозиться на каждый кадр драга выделения.
                    onCaret = { uid, sel -> vm.updateSel(uid, sel.start, sel.end) },
                    onEditImage = { imageSheet = it },
                    onZoomImage = { zoomBmp = it },
                    onPickImage = { imagePicker.launch("image/*") },
                    onTakePhoto = { launchCamera() },
                    onSavePng = { pngSaver.launch("catprint.png") }
                )
                SidePreviewPane(
                    bmp = bit,
                    intensity = vm.previewIntensity,
                    baseIntensity = vm.settings.intensity,
                    auto = vm.settings.autoDensity,
                    adaptive = vm.settings.adaptiveHeat,
                    onIntensity = {
                        vm.settings.intensity = it
                        vm.refreshPreview()
                    },
                    onAuto = {
                        vm.settings.autoDensity = it
                        vm.refreshPreview()
                    },
                    onAdaptive = {
                        vm.settings.adaptiveHeat = it
                        vm.refreshPreview()
                    },
                    onSavePng = { pngSaver.launch("catprint.png") },
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                )
            }
        } else {
            DocBlocksList(
                vm = vm,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                showPreviewCard = showBit,
                onFocusText = { b, sel ->
                    formattingTarget = b
                    vm.updateSel(b.uid, sel.start, sel.end)
                },
                // Только пишем в VM (не читаем): список не должен
                // рекомпозиться на каждый кадр драга выделения.
                onCaret = { uid, sel -> vm.updateSel(uid, sel.start, sel.end) },
                onEditImage = { imageSheet = it },
                onZoomImage = { zoomBmp = it },
                onPickImage = { imagePicker.launch("image/*") },
                onTakePhoto = { launchCamera() },
                onSavePng = { pngSaver.launch("catprint.png") }
            )
        }
    }

    // Нижняя шторка настроек картинки
    imageSheet?.let { img ->
        ImageSettingsSheet(
            block = img,
            vm = vm,
            onClose = {
                imageSheet = null
                vm.touch()
            }
        )
    }

    // Пикер эмодзи
    if (showEmoji) {
        EmojiSheet(
            vm = vm,
            onPick = { emoji ->
                var target = formattingTarget
                if (target == null || !vm.blocks.contains(target)) {
                    val nb = DocBlock.Text(emoji, fontSize = 24f, alignment = 1)
                    vm.blocks.add(nb)
                    formattingTarget = nb
                    vm.touch()
                } else {
                    vm.queueEmoji(target.uid, emoji)
                }
            },
            onClose = { showEmoji = false }
        )
    }

    // Пасхалка — кот из шапки
    if (showEgg) {
        EggDialog(onClose = { showEgg = false })
    }

    // Лупа: полноэкранный просмотр картинки с pinch-зумом
    zoomBmp?.let { zb ->
        ZoomDialog(bmp = zb, onClose = { zoomBmp = null })
    }
}

@Composable
private fun DocBlocksList(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    showPreviewCard: Boolean,
    onFocusText: (DocBlock.Text, TextRange) -> Unit,
    onCaret: (String, TextRange) -> Unit,
    onEditImage: (DocBlock.Image) -> Unit,
    onZoomImage: (Bitmap) -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onSavePng: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(
            vm.blocks.toList(),
            key = { _, b -> b.uid }
        ) { index, block ->
            when (block) {
                is DocBlock.Text -> TextBlockCard(
                    block = block,
                    vm = vm,
                    onText = { nt, sel ->
                        vm.applyTextEdit(block.uid, nt)
                        onCaret(block.uid, sel)
                    },
                    onFocus = { sel -> onFocusText(block, sel) },
                    onSplit = { vm.splitBlock(index) },
                    onUp = { vm.moveBlock(index, index - 1) },
                    onDown = { vm.moveBlock(index, index + 1) },
                    onDup = { vm.duplicateBlock(index) },
                    onDel = { vm.deleteBlock(index) }
                )
                    is DocBlock.Image -> ImageBlockCard(
                        block = block,
                        vm = vm,
                        onEdit = { onEditImage(block) },
                        onZoom = { onZoomImage(it) },
                    onUp = { vm.moveBlock(index, index - 1) },
                    onDown = { vm.moveBlock(index, index + 1) },
                    onDup = { vm.duplicateBlock(index) },
                    onDel = { vm.deleteBlock(index) }
                )
                is DocBlock.Divider -> DividerBlockCard(
                    block = block,
                    vm = vm,
                    onUp = { vm.moveBlock(index, index - 1) },
                    onDown = { vm.moveBlock(index, index + 1) },
                    onDel = { vm.deleteBlock(index) }
                )
                    is DocBlock.Qr -> QrBlockCard(
                        block = block,
                        vm = vm,
                        onUp = { vm.moveBlock(index, index - 1) },
                        onDown = { vm.moveBlock(index, index + 1) },
                        onDup = { vm.duplicateBlock(index) },
                        onDel = { vm.deleteBlock(index) }
                    )
                is DocBlock.Gap -> GapBlockCard(
                    block = block,
                    vm = vm,
                    onUp = { vm.moveBlock(index, index - 1) },
                    onDown = { vm.moveBlock(index, index + 1) },
                    onDel = { vm.deleteBlock(index) }
                )
            }
        }

        item {
            // Панель вставки и шаблонов (переносится на узких экранах)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(
                    8.dp, Alignment.CenterHorizontally
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { vm.addText() }) { Text("+ Текст") }
                OutlinedButton(onClick = { vm.addQr() }) { Text("QR") }
                OutlinedButton(onClick = onPickImage) {
                    Text("🖼 Картинка")
                }
                OutlinedButton(onClick = onTakePhoto) {
                    Text("📷 Фото")
                }
                OutlinedButton(onClick = { vm.addDivider() }) { Text("Линия") }
                OutlinedButton(onClick = { vm.addGap() }) { Text("Отступ") }
                OutlinedButton(onClick = { vm.templateChecklist() }) { Text("📋 Список") }
                OutlinedButton(onClick = { vm.templateNote() }) { Text("📝 Заметка") }
                OutlinedButton(onClick = { vm.templateHeader() }) { Text("🏷 Шапка") }
            }
        }

        if (showPreviewCard && vm.bitPreview != null) {
            item {
                Card(
                    modifier = Modifier.padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    PreviewCardBody(
                        bmp = vm.bitPreview!!,
                        intensity = vm.previewIntensity,
                        baseIntensity = vm.settings.intensity,
                        auto = vm.settings.autoDensity,
                        adaptive = vm.settings.adaptiveHeat,
                        onIntensity = {
                            vm.settings.intensity = it
                            vm.refreshPreview()
                        },
                        onAuto = {
                            vm.settings.autoDensity = it
                            vm.refreshPreview()
                        },
                        onAdaptive = {
                            vm.settings.adaptiveHeat = it
                            vm.refreshPreview()
                        },
                        onSavePng = onSavePng
                    )
                }
            }
        }
    }
}

/** Статус принтера и батарея одной строкой под шапкой редактора. */
@Composable
private fun PrinterStatusStrip(vm: AppViewModel) {
    val state by vm.printer.state.collectAsState()
    val battery by vm.printer.lastBattery.collectAsState()
    val connected = state == com.catprint.ble.PrinterState.CONNECTED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = connected) { vm.queryBattery() }
            .padding(horizontal = 12.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dot = when (state) {
            com.catprint.ble.PrinterState.CONNECTED -> Color(0xFF16A34A)
            com.catprint.ble.PrinterState.CONNECTING,
            com.catprint.ble.PrinterState.SCANNING -> Color(0xFFD97706)
            else -> Color.Gray
        }
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dot)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (connected) {
                vm.printer.deviceName.ifBlank { "Подключено" }
            } else {
                "Принтер не подключён"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        val level = battery
        if (connected && level != null) {
            Text(
                text = (if (level <= 20) "🪫" else "🔋") + "$level%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
    }
}

/** Общее содержимое карточки «Как напечатается» + плотность без похода в настройки. */
@Composable
private fun PreviewCardBody(
    bmp: Bitmap,
    intensity: Int,
    baseIntensity: Int,
    auto: Boolean,
    adaptive: Boolean,
    onIntensity: (Int) -> Unit,
    onAuto: (Boolean) -> Unit,
    onAdaptive: (Boolean) -> Unit,
    onSavePng: () -> Unit
) {
    // Обёртку кэшируем: иначе каждая рекомпозиция (прогресс печати!)
    // создаёт новую и GPU перезаливает текстуру.
    val img = remember(bmp) { bmp.asImageBitmap() }
    var base by remember(baseIntensity) { mutableStateOf(baseIntensity) }
    Column(modifier = Modifier.padding(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Как напечатается · 0x" +
                    intensity.toString(16).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSavePng) { Text("💾 PNG") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Плотность",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                powerText(base),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Slider(
            value = ((base - 0x30) / 0.74).toFloat().coerceIn(0f, 100f),
            onValueChange = {
                base = (0x30 + (it * 0.74).toInt()).coerceIn(0x30, 0x7A)
                onIntensity(base)
            },
            valueRange = 0f..100f
        )
        if (auto) {
            Text(
                "Автоплотность вкл: на плотной заливке жар снизится сам",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Автоплотность",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            AppSwitch(checked = auto, onCheckedChange = onAuto)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Адаптивный жар",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f)
            )
            AppSwitch(checked = adaptive, onCheckedChange = onAdaptive)
        }
        Spacer(Modifier.height(4.dp))
        Image(
            bitmap = img,
            contentDescription = null
        )
    }
}

private fun powerText(intensity: Int): String {
    val pct = ((intensity - 0x30) / 0.74).toInt().coerceIn(0, 100)
    val word = when {
        pct <= 30 -> "Эконом"
        pct <= 70 -> "Стандарт"
        else -> "Максимум"
    }
    return "$pct% · $word"
}

/** Боковая панель превью для альбома: редактор слева, бумага справа. */
@Composable
private fun SidePreviewPane(
    bmp: Bitmap,
    intensity: Int,
    baseIntensity: Int,
    auto: Boolean,
    adaptive: Boolean,
    onIntensity: (Int) -> Unit,
    onAuto: (Boolean) -> Unit,
    onAdaptive: (Boolean) -> Unit,
    onSavePng: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            PreviewCardBody(
                bmp = bmp,
                intensity = intensity,
                baseIntensity = baseIntensity,
                auto = auto,
                adaptive = adaptive,
                onIntensity = onIntensity,
                onAuto = onAuto,
                onAdaptive = onAdaptive,
                onSavePng = onSavePng
            )
        }
    }
}

/** Стиль отрезка для показа в поле редактора (зеркалит рендер). */
private fun runSpanStyle(r: TextRun, invert: Boolean): SpanStyle {
    val sh = (r.fontSize * 0.04f).coerceAtLeast(0.6f)
    return SpanStyle(
        fontFamily = fontFamilyFor(r.fontFamily),
        fontSize = r.fontSize.sp,
        fontWeight = if (r.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (r.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration =
            if (r.underline) TextDecoration.Underline else TextDecoration.None,
        textGeometricTransform =
            if (r.italic) TextGeometricTransform(skewX = -0.25f) else null,
        shadow =
            if (r.bold) Shadow(
                color = if (invert) Color.White else Color.Black,
                offset = Offset(sh, sh),
                blurRadius = 0f
            ) else null
    )
}

/**
 * Аннотированная строка поля из ранов (только показ).
 * Раны должны покрывать ровно text, иначе — plain (рассинхрон).
 */
private fun annotatedFor(block: DocBlock.Text, text: String): AnnotatedString {
    if (block.runs.isEmpty()) return AnnotatedString(text)
    val sb = StringBuilder()
    for (r in block.runs) sb.append(r.text)
    if (sb.toString() != text) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var off = 0
        for (r in block.runs) {
            val s = off
            off += r.text.length
            addStyle(runSpanStyle(r, block.invert), s, off)
        }
    }
}

/** Отрезки, целиком лежащие в [a, b). */
private fun coveredRuns(
    block: DocBlock.Text, a: Int, b: Int
): List<TextRun> {
    val out = mutableListOf<TextRun>()
    var off = 0
    for (r in block.runs) {
        val s = off
        off += r.text.length
        if (s >= a && off <= b && r.text.isNotEmpty()) out.add(r)
    }
    return out
}

@Composable
private fun TextBlockCard(
    block: DocBlock.Text,
    vm: AppViewModel,
    onText: (String, TextRange) -> Unit,
    onFocus: (TextRange) -> Unit,
    onSplit: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDup: () -> Unit,
    onDel: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    // Локальное состояние поля: переживает рекомпозиции списка (ключ uid),
    // поэтому фокус и клавиатура не сбрасываются при обновлении стилей/превью.
    // TextFieldValue хранит курсор — эмодзи вставляются в позицию каретки.
    // Храним PLAIN-текст: стили идут из block.runs (источник правды — VM),
    // показ склеивается ниже в annotated (display-only).
    var field by remember(block.uid) { mutableStateOf(TextFieldValue(block.text)) }
    // Внешняя вставка (эмодзи) и сброс состояний после Undo/открытия
    val emojiTick = vm.emojiTick
    val rev = vm.revision
    LaunchedEffect(emojiTick, rev) {
        vm.takeEmoji(block.uid)?.let { ins ->
            val cur = field
            val sel = cur.selection
            val nt = cur.text.substring(0, sel.start) + ins +
                cur.text.substring(sel.end)
            val pos = sel.start + ins.length
            field = cur.copy(text = nt, selection = TextRange(pos))
            onText(nt, TextRange(pos))
        }
        // Программное выделение (кнопки −/+): только двигаем границы,
        // текст не трогаем (applyTextEdit на тот же текст — no-op).
        vm.takeSel(block.uid)?.let { rng ->
            val cur = field
            val len = cur.text.length
            val ns = TextRange(
                rng.start.coerceIn(0, len), rng.end.coerceIn(0, len)
            )
            field = cur.copy(selection = ns)
            onText(cur.text, ns)
        }
    }
    // Показ: раны поверх текста. При ранах базовый textStyle нейтральный
    // (иначе базовая жирность легла бы и на нежирные отрезки).
    val uniform = block.runs.isEmpty()
    val annotated = remember(block, field.text) {
        annotatedFor(block, field.text)
    }
    val shown = field.copy(annotatedString = annotated)
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .width(384.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            TextField(
                value = shown,
                onValueChange = { field = it; onText(it.text, it.selection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onFocus(field.selection)
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Enter &&
                            event.type == KeyEventType.KeyDown
                        ) {
                            onSplit()
                            true
                        } else false
                    }
                    .padding(0.dp),
                textStyle = TextStyle(
                    fontSize = block.fontSize.sp,
                    fontWeight =
                        if (!uniform) FontWeight.Normal
                        else if (block.bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle =
                        if (!uniform) FontStyle.Normal
                        else if (block.italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration =
                        if (!uniform) TextDecoration.None
                        else if (block.underline) TextDecoration.Underline
                        else TextDecoration.None,
                    textGeometricTransform =
                        if (!uniform || !block.italic) null
                        else TextGeometricTransform(skewX = -0.25f),
                    shadow =
                        if (!uniform || !block.bold) null
                        else {
                            val sh = (block.fontSize * 0.04f).coerceAtLeast(0.6f)
                            Shadow(
                                color = if (block.invert) Color.White else Color.Black,
                                offset = Offset(sh, sh),
                                blurRadius = 0f
                            )
                        },
                    textAlign = when (block.alignment) {
                        0 -> TextAlign.Left
                        2 -> TextAlign.Right
                        else -> TextAlign.Center
                    },
                    color = if (block.invert) Color.White else Color.Black,
                    fontFamily = fontFamilyFor(block.fontFamily)
                ),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = if (block.invert) Color.Black else Color.White,
                    unfocusedContainerColor = if (block.invert) Color.Black else Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = false,
                maxLines = 40,
                // Автокорректа нет: принтер печатает буквы как есть, а IME
                // с подсказками дёргает выделение (ручки едут по 1 символу).
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
            )
            BlockActions(
                visible = focused,
                onUp = onUp, onDown = onDown, onDup = onDup, onDel = onDel
            )
        }
    }
}

@Composable
private fun BlockActions(
    visible: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDup: () -> Unit,
    onDel: () -> Unit
) {
    if (!visible) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Выше") }
        IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
        IconButton(onClick = onDup) { Icon(Icons.Filled.ContentCopy, "Дублировать") }
        IconButton(onClick = onDel) { Icon(Icons.Filled.Delete, "Удалить") }
    }
}

@Composable
private fun QrBlockCard(
    block: DocBlock.Qr,
    vm: AppViewModel,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDup: () -> Unit,
    onDel: () -> Unit
) {
    var field by remember(block.uid) { mutableStateOf(TextFieldValue(block.text)) }
    val bmp = remember(field.text, block.size) { DocRender.previewQr(block) }
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .width(384.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            TextField(
                value = field,
                onValueChange = {
                    field = it
                    block.text = it.text
                    vm.refreshPreview()
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("QR: ссылка или текст") },
                singleLine = false,
                maxLines = 6
            )
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Размер:", color = Color.Gray)
                listOf("Мал" to 0, "Сред" to 1, "Круп" to 2).forEach { (name, s) ->
                    FilterChip(
                        selected = block.size == s,
                        onClick = { vm.updateQr(block.uid) { it.size = s } },
                        label = { Text(name) }
                    )
                }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            } else {
                Text(
                    "Введи текст — тут будет код",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
                IconButton(onClick = onDup) { Icon(Icons.Filled.ContentCopy, "Дублировать") }
                IconButton(onClick = onDel) { Icon(Icons.Filled.Delete, "Удалить") }
            }
        }
    }
}

@Composable
private fun ImageBlockCard(
    block: DocBlock.Image,
    vm: AppViewModel,
    onEdit: () -> Unit,
    onZoom: (Bitmap) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDup: () -> Unit,
    onDel: () -> Unit
) {
    // Цветной оригинал (фон) + ч/б превью — оба в фоне с дебаунсом,
    // битмап переиспользуется между тиками (ноль аллокаций).
    // Пересчитывается только свой блок (previewUid) — остальные спят.
    val pv = imagePreview(block, vm)
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .width(384.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onEdit
    ) {
        Column {
            val show = pv.preview ?: pv.color
            val showImg = pv.previewImg ?: pv.colorImg
            if (show != null && showImg != null) {
                Image(
                    bitmap = showImg,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Text(
                    "нет изображения",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray
                )
            }
            Text(
                text = DitherMode.values().getOrElse(block.dither) {
                    DitherMode.FLOYD_STEINBERG
                }.displayName + " • порог ${block.threshold}" +
                    settingsSummary(block),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(8.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = {
                    show?.let { onZoom(it) }
                }) { Text("🔍", fontSize = 20.sp) }
                IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
                IconButton(onClick = onDup) { Icon(Icons.Filled.ContentCopy, "Дублировать") }
                IconButton(onClick = onDel) { Icon(Icons.Filled.Delete, "Удалить") }
            }
        }
    }
}

/** Подпись нестандартных настроек блока (как десктопная Summary). */
private fun settingsSummary(block: DocBlock.Image): String {
    val sb = StringBuilder()
    if (block.brightness != 0.0) sb.append(" • ярк ${block.brightness.toInt()}")
    if (block.contrast != 0.0) sb.append(" • конт ${block.contrast.toInt()}")
    if (block.saturation != 100.0) sb.append(" • нас ${block.saturation.toInt()}")
    if (kotlin.math.abs(block.gamma - 1.0) > 0.001) {
        sb.append(" • γ %.2f".format(block.gamma))
    }
    if (block.invert) sb.append(" • инв")
    if (block.rotation != 0) sb.append(" • ${block.rotation}°")
    if (block.mirror) sb.append(" • зерк")
    if (block.frame != FrameStyle.NONE) {
        sb.append(" • рамка: ${FrameStyle.displayName(block.frame).lowercase()}")
    }
    return sb.toString()
}

@Composable
private fun DividerBlockCard(
    block: DocBlock.Divider,
    vm: AppViewModel,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .width(384.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = when (block.style) {
                    1 -> "╴╴╴ Пунктир"
                    2 -> "··· Точки"
                    3 -> "═══ Двойной"
                    else -> "━━━ Сплошной"
                },
                color = Color.Black
            )
            Row {
                TextButton(onClick = { expanded = !expanded }) { Text("Стиль") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
                IconButton(onClick = onDel) { Icon(Icons.Filled.Delete, "Удалить") }
            }
            if (expanded) {
                listOf("Сплошной", "Пунктир", "Точки", "Двойной").forEachIndexed { i, name ->
                    TextButton(onClick = {
                        vm.updateDivider(block.uid) { it.style = i }
                        expanded = false
                    }) {
                        Text(name)
                    }
                }
            }
        }
    }
}

@Composable
private fun GapBlockCard(
    block: DocBlock.Gap,
    vm: AppViewModel,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDel: () -> Unit
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .width(384.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFA))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("⇕ Отступ: ${block.height} px", color = Color.Gray)
            Slider(
                value = block.height.toFloat(),
                onValueChange = { v -> vm.updateGap(block.uid) { it.height = v.toInt() } },
                valueRange = 4f..200f
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onUp) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                IconButton(onClick = onDown) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
                IconButton(onClick = onDel) { Icon(Icons.Filled.Delete, "Удалить") }
            }
        }
    }
}

/**
 * Чип стиля с долгим нажатием: тап — выделение (или абзац без него),
 * долгий тап — всегда весь абзац. M3-FilterChip лонг-пресс не умеет,
 * поэтому свой (вид — как чип).
 */
@Composable
private fun StyleChip(
    selected: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    label: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .combinedClickable(
                enabled = enabled,
                onClick = onTap,
                onLongClick = onLongPress,
                onLongClickLabel = "Весь абзац",
                indication = LocalIndication.current,
                interactionSource = remember { MutableInteractionSource() }
            )
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) { label() }
    }
}

@Composable
private fun FormatBar(
    target: DocBlock.Text?,
    vm: AppViewModel,
    onTarget: (DocBlock.Text?) -> Unit,
    onPickImage: () -> Unit,
    onEmoji: () -> Unit
) {
    var fontMenu by remember { mutableStateOf(false) }
    var sizeMenu by remember { mutableStateOf(false) }
    var lhMenu by remember { mutableStateOf(false) }
    var frameMenu by remember { mutableStateOf(false) }

    val fonts = remember(vm.settings.recentFonts) {
        val recents = vm.settings.recentFonts.filter { it in ANDROID_FONTS }.distinct().take(5)
        recents + ANDROID_FONTS.filter { it !in recents }
    }
    // Живой объект из списка: plain-var мутации ссылку не меняют,
    // поэтому captured target протухает — читаем состояние из списка
    // (заодно подписываемся на него: замена объекта видна сразу).
    val live: DocBlock.Text? = target?.uid?.let { uid ->
        vm.blocks.filterIsInstance<DocBlock.Text>().firstOrNull { it.uid == uid }
    } ?: target
    // Выделение из VM (пишет карточка поля). FormatBar — единственный
    // читатель, поэтому драг выделения рекомпозит только панель.
    val sel = vm.fmtSel
    // Валидное НЕпустое выделение в живом блоке (иначе null = весь абзац).
    val range: IntRange? = run {
        val t = live ?: return@run null
        val s = sel?.takeIf { it.first == t.uid } ?: return@run null
        val len = t.text.length
        val A = s.second.coerceIn(0, len)
        val B = s.third.coerceIn(0, len)
        if (A < B) A until B else null
    }
    // Обновление заменой объекта (см. AppViewModel.updateText).
    fun upd(change: (DocBlock.Text) -> Unit) {
        val t = live ?: return
        vm.updateText(t.uid, change)?.let(onTarget)
    }
    // Каретка для вооружения (схлопнутое выделение или конец текста).
    fun caret(): Int {
        val t = live ?: return 0
        return sel?.takeIf { it.first == t.uid }?.second?.coerceIn(0, t.text.length)
            ?: t.text.length
    }
    // Короткий тап Ж/К/Ч: выделение; без выделения — вооружить стиль
    // для нового текста (повторный тап снимает).
    fun tapAttr(attr: StyleAttr) {
        val t = live ?: return
        val r = range
        if (r != null) {
            vm.toggleStyle(t.uid, r.first, r.last + 1, attr)?.let(onTarget)
        } else {
            vm.armAttr(t.uid, caret(), attr)
        }
    }
    // Долгий тап Ж/К/Ч: всегда весь абзац.
    fun longAttr(attr: StyleAttr) {
        val t = live ?: return
        vm.wholeStyle(t.uid, attr)?.let(onTarget)
    }
    fun tapSize(s: Float) {
        val t = live ?: return
        val r = range
        if (r != null) {
            vm.setSize(t.uid, r.first, r.last + 1, s)?.let(onTarget)
        } else {
            vm.setPendingSize(t.uid, caret(), s)
        }
    }
    fun tapFont(f: String) {
        val t = live ?: return
        val r = range
        if (r != null) {
            vm.setFont(t.uid, r.first, r.last + 1, f)?.let(onTarget)
        } else {
            vm.setPendingFont(t.uid, caret(), f)
        }
        vm.pushRecentFont(f)
    }
    // Кнопки −/+: сдвиг правой границы выделения (или каретки).
    // Пальцем-«ползунком» драг рвётся на части прошивок/IME, а кнопки
    // едут надёжным каналом queueSel (как вставка эмодзи).
    fun nudge(d: Int) {
        val t = live ?: return
        if (vm.blocks.none { it.uid == t.uid }) return
        val len = t.text.length
        val r = range
        if (r != null) {
            val nb = (r.last + 1 + d).coerceIn(r.first + 1, len)
            vm.queueSel(t.uid, TextRange(r.first, nb))
        } else {
            vm.queueSel(t.uid, TextRange((caret() + d).coerceIn(0, len)))
        }
    }
    // Показ: выделение → по покрытым; иначе pending (вооружённое или
    // стиль каретки); иначе база абзаца.
    fun showAttr(attr: StyleAttr): Boolean {
        val t = live ?: return false
        val r = range
        if (r != null && t.runs.isNotEmpty()) {
            val cov = coveredRuns(t, r.first, r.last + 1)
            if (cov.isNotEmpty()) return cov.all { it.getAttr(attr) }
        }
        return vm.pendingStyles[t.uid]?.getAttr(attr) ?: t.getAttr(attr)
    }
    fun showSize(): Float {
        val t = live ?: return 24f
        val r = range
        if (r != null && t.runs.isNotEmpty()) {
            val sizes = coveredRuns(t, r.first, r.last + 1).map { it.fontSize }.distinct()
            if (sizes.size == 1) return sizes[0]
        }
        return vm.pendingStyles[t.uid]?.fontSize ?: t.fontSize
    }
    fun showFont(): String {
        val t = live ?: return ""
        val r = range
        if (r != null && t.runs.isNotEmpty()) {
            val fontsCov = coveredRuns(t, r.first, r.last + 1).map { it.fontFamily }.distinct()
            if (fontsCov.size == 1) return fontsCov[0]
        }
        return vm.pendingStyles[t.uid]?.fontFamily ?: t.fontFamily
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Граница выделения ±1 символ — ПЕРВЫМИ: системный попап
        // «Копировать» перекрывает середину панели при выделении.
        // Есть выделение — двигаем правую границу, иначе — каретку.
        OutlinedButton(
            enabled = live != null,
            onClick = { nudge(-1) }
        ) { Text("−", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        OutlinedButton(
            enabled = live != null,
            onClick = { nudge(1) }
        ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        // Шрифт
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { fontMenu = true },
                enabled = live != null
            ) { Text(fontDisplayName(showFont()), maxLines = 1) }
            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                fonts.forEach { f ->
                    DropdownMenuItem(
                        text = { Text(fontDisplayName(f)) },
                        onClick = {
                            tapFont(f)
                            fontMenu = false
                        }
                    )
                }
            }
        }
        // Размер
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { sizeMenu = true },
                enabled = live != null
            ) { Text(showSize().toInt().toString(), maxLines = 1) }
            DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                FONT_SIZES.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.toInt().toString()) },
                        onClick = {
                            tapSize(s)
                            sizeMenu = false
                        }
                    )
                }
            }
        }
        StyleChip(
            selected = showAttr(StyleAttr.BOLD),
            enabled = live != null,
            onTap = { tapAttr(StyleAttr.BOLD) },
            onLongPress = { longAttr(StyleAttr.BOLD) },
            label = { Text("Ж", fontWeight = FontWeight.Bold) }
        )
        StyleChip(
            selected = showAttr(StyleAttr.ITALIC),
            enabled = live != null,
            onTap = { tapAttr(StyleAttr.ITALIC) },
            onLongPress = { longAttr(StyleAttr.ITALIC) },
            label = { Text("К", fontStyle = FontStyle.Italic) }
        )
        StyleChip(
            selected = showAttr(StyleAttr.UNDERLINE),
            enabled = live != null,
            onTap = { tapAttr(StyleAttr.UNDERLINE) },
            onLongPress = { longAttr(StyleAttr.UNDERLINE) },
            label = {
                Text("Ч", textDecoration = TextDecoration.Underline)
            }
        )
        // Жирная точка для пунктов списка: вставляет «• » в позицию каретки.
        OutlinedButton(
            enabled = live != null,
            onClick = {
                val t = live ?: return@OutlinedButton
                if (vm.blocks.any { it.uid == t.uid }) vm.queueInsert(t.uid, "• ")
            }
        ) { Text("•", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        FilterChip(
            selected = live?.alignment == 0,
            enabled = live != null,
            onClick = { upd { it.alignment = 0 } },
            label = { Text("◀≡") }
        )
        FilterChip(
            selected = live?.alignment == 1,
            enabled = live != null,
            onClick = { upd { it.alignment = 1 } },
            label = { Text("≡") }
        )
        FilterChip(
            selected = live?.alignment == 2,
            enabled = live != null,
            onClick = { upd { it.alignment = 2 } },
            label = { Text("≡▶") }
        )
        FilterChip(
            selected = live?.invert == true,
            enabled = live != null,
            onClick = { upd { it.invert = !it.invert } },
            label = { Text("Инв") }
        )
        // Рамка вокруг текстового блока
        androidx.compose.foundation.layout.Box {
            OutlinedButton(
                onClick = { frameMenu = true },
                enabled = live != null
            ) { Text(FrameStyle.displayName(live?.frame ?: 0), maxLines = 1) }
            DropdownMenu(expanded = frameMenu, onDismissRequest = { frameMenu = false }) {
                FrameStyle.all().forEach { f ->
                    DropdownMenuItem(
                        text = { Text(FrameStyle.displayName(f)) },
                        onClick = {
                            upd { it.frame = f }
                            frameMenu = false
                        }
                    )
                }
            }
        }
        // Межстрочный интервал
        androidx.compose.foundation.layout.Box {
            val curLh = live?.let { t ->
                val lh = t.lineHeight
                if (lh == null) "↕"
                else "↕%.2f".format(lh / t.fontSize.coerceAtLeast(1f))
            } ?: "↕"
            OutlinedButton(
                onClick = { lhMenu = true },
                enabled = live != null
            ) { Text(curLh, maxLines = 1) }
            DropdownMenu(expanded = lhMenu, onDismissRequest = { lhMenu = false }) {
                DropdownMenuItem(
                    text = { Text("↕ Авто") },
                    onClick = {
                        upd { it.lineHeight = null }
                        lhMenu = false
                    }
                )
                listOf(1.0f, 1.25f, 1.5f, 2.0f).forEach { f ->
                    DropdownMenuItem(
                        text = { Text("↕ %.2f".format(f)) },
                        onClick = {
                            upd { it.lineHeight = it.fontSize * f }
                            lhMenu = false
                        }
                    )
                }
            }
        }
        OutlinedButton(onClick = onEmoji) { Text("😀") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageSettingsSheet(
    block: DocBlock.Image,
    vm: AppViewModel,
    onClose: () -> Unit
) {
    // То же вычисление, что у карточки (общий хелпер imagePreview):
    // расхождение превью исключено конструктивно.
    // Тихое обновление через bumpPreview(uid) (без touch/snapshot).
    // Снапшот один раз при закрытии (onClose -> vm.touch()).
    val scope = rememberCoroutineScope()
    val pv = imagePreview(block, vm)
    val show = pv.preview ?: pv.color
    val showImg = pv.previewImg ?: pv.colorImg

    fun refresh() {
        vm.bumpPreview(block.uid)
    }

    // Сразу высоко: превью крупное, низ доступен скроллом
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Картинка", style = MaterialTheme.typography.titleMedium)
            if (show != null && showImg != null) {
                // Строго 1:1 без ресемплинга (как десктопный Stretch=None):
                // любой даунскейл 1-битного растра даёт муар и «портит» картинку.
                val density = LocalDensity.current
                val wDp = with(density) { show.width.toDp() }
                val hDp = with(density) { show.height.toDp() }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Image(
                        bitmap = showImg,
                        contentDescription = null,
                        modifier = Modifier.size(wDp, hDp),
                        filterQuality = FilterQuality.None
                    )
                }
                Text(
                    "1:1 • ${show.width}×${show.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }

            var ditherMenu by remember { mutableStateOf(false) }
            var frameMenu by remember { mutableStateOf(false) }
            // Режим обрезки: первое нажатие «✂ Обрезать» — выделение
            // области пальцем, второе — применить.
            var cropMode by remember { mutableStateOf(false) }
            var cropSel by remember { mutableStateOf<Rect?>(null) }
            var cropAnchor by remember { mutableStateOf(Offset.Zero) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { ditherMenu = true }) {
                        Text(DitherMode.fromOrdinalSafe(block.dither).displayName)
                    }
                    DropdownMenu(expanded = ditherMenu, onDismissRequest = { ditherMenu = false }) {
                        DitherMode.values().forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.displayName) },
                                onClick = {
                                    block.dither = m.ordinal
                                    refresh()
                                    ditherMenu = false
                                }
                            )
                        }
                    }
                }
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { frameMenu = true }) {
                        Text("🖼 " + FrameStyle.displayName(block.frame))
                    }
                    DropdownMenu(expanded = frameMenu, onDismissRequest = { frameMenu = false }) {
                        FrameStyle.all().forEach { f ->
                            DropdownMenuItem(
                                text = { Text(FrameStyle.displayName(f)) },
                                onClick = {
                                    block.frame = f
                                    refresh()
                                    frameMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedButton(onClick = {
                    block.dither = DitherMode.FLOYD_STEINBERG.ordinal
                    block.threshold = 128
                    block.brightness = 0.0
                    block.contrast = 0.0
                    block.saturation = 100.0
                    block.gamma = 1.0
                    block.invert = false
                    block.rotation = 0
                    block.mirror = false
                    block.frame = FrameStyle.NONE
                    block.cropL = 0.0
                    block.cropT = 0.0
                    block.cropR = 0.0
                    block.cropB = 0.0
                    refresh()
                }) { Text("Сброс") }
            }

            SliderRow("Яркость", block.brightness.toFloat(), -100f..100f) {
                block.brightness = it.toDouble(); refresh()
            }
            SliderRow("Контраст", block.contrast.toFloat(), -100f..100f) {
                block.contrast = it.toDouble(); refresh()
            }
            SliderRow("Насыщенность", block.saturation.toFloat(), 0f..200f) {
                block.saturation = it.toDouble(); refresh()
            }
            // Порог действует только в режимах «Порог» и «Случайный» —
            // в остальных он зашит (128), слайдер гасим как на десктопе.
            val thresholdOn = block.dither == DitherMode.THRESHOLD.ordinal ||
                block.dither == DitherMode.RANDOM.ordinal
            SliderRow("Порог", block.threshold.toFloat(), 1f..254f, enabled = thresholdOn) {
                block.threshold = it.toInt(); refresh()
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = {
                    block.rotation = (block.rotation + 90) % 360
                    refresh()
                }) { Text("⟳ ${block.rotation}°") }
                FilterChip(
                    selected = block.mirror,
                    onClick = { block.mirror = !block.mirror; refresh() },
                    label = { Text("⇋ Зеркало") }
                )
                // Обрезка: первое нажатие — выделить область пальцем
                // на картинке ниже, второе — применить.
                OutlinedButton(onClick = {
                    if (cropMode) {
                        cropSel?.let { r ->
                            if (r.width >= 0.05f && r.height >= 0.05f) {
                                block.cropL = r.left.toDouble()
                                block.cropT = r.top.toDouble()
                                block.cropR = (1f - r.right).toDouble()
                                block.cropB = (1f - r.bottom).toDouble()
                                refresh()
                            }
                        }
                        cropSel = null
                        cropMode = false
                    } else {
                        cropSel = null
                        cropAnchor = Offset.Zero
                        cropMode = true
                    }
                }) { Text(if (cropMode) "✓ Обрезать" else "✂ Обрезать") }
            }
            if (cropMode) {
                Text(
                    "Веди пальцем по картинке, затем нажми «✓ Обрезать»",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                val ob = orientedColor(block)
                if (ob != null) {
                    val obImg = remember(ob) { ob.asImageBitmap() }
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ob.width.toFloat() / ob.height.coerceAtLeast(1))
                    ) {
                        val bwPx = with(LocalDensity.current) { maxWidth.toPx() }
                        val bhPx = with(LocalDensity.current) { maxHeight.toPx() }
                        Image(
                            bitmap = obImg,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(bwPx, bhPx) {
                                    detectDragGestures(
                                        onDragStart = { off ->
                                            val f = Offset(
                                                (off.x / bwPx).coerceIn(0f, 1f),
                                                (off.y / bhPx).coerceIn(0f, 1f)
                                            )
                                            cropAnchor = f
                                            cropSel = Rect(f.x, f.y, f.x, f.y)
                                        },
                                        onDrag = { change, _ ->
                                            val f = Offset(
                                                (change.position.x / bwPx).coerceIn(0f, 1f),
                                                (change.position.y / bhPx).coerceIn(0f, 1f)
                                            )
                                            val a = cropAnchor
                                            cropSel = Rect(
                                                a.x.coerceAtMost(f.x),
                                                a.y.coerceAtMost(f.y),
                                                a.x.coerceAtLeast(f.x),
                                                a.y.coerceAtLeast(f.y)
                                            )
                                        }
                                    )
                                },
                            contentScale = ContentScale.FillBounds
                        )
                        cropSel?.let { r ->
                            Canvas(Modifier.matchParentSize()) {
                                val l = r.left * size.width
                                val t = r.top * size.height
                                val w = r.width * size.width
                                val h = r.height * size.height
                                val dim = Color.Black.copy(alpha = 0.5f)
                                drawRect(dim, Offset(0f, 0f), Size(size.width, t))
                                drawRect(
                                    dim, Offset(0f, t + h),
                                    Size(size.width, size.height - t - h)
                                )
                                drawRect(dim, Offset(0f, t), Size(l, h))
                                drawRect(
                                    dim, Offset(l + w, t),
                                    Size(size.width - l - w, h)
                                )
                                drawRect(
                                    Color.White, Offset(l, t), Size(w, h),
                                    style = Stroke(width = 3f)
                                )
                            }
                        }
                    }
                }
            }
            SliderRow("Гамма", block.gamma.toFloat(), 0.2f..3f) {
                val v = (it * 100).toInt() / 100.0
                block.gamma = v; refresh()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Инверсия")
                Spacer(Modifier.width(8.dp))
                AppSwitch(checked = block.invert, onCheckedChange = {
                    block.invert = it; refresh()
                })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    // 📷 Фото: Флойд + автоуровни (гистограмма — в фоне)
                    scope.launch {
                        val auto = withContext(Dispatchers.Default) { autoLevels(block) }
                        block.dither = DitherMode.FLOYD_STEINBERG.ordinal
                        block.brightness = auto.first
                        block.contrast = auto.second
                        block.gamma = 1.0
                        block.invert = false
                        refresh()
                    }
                }) { Text("📷 Фото") }
                OutlinedButton(onClick = {
                    block.dither = DitherMode.THRESHOLD.ordinal
                    block.brightness = 0.0
                    block.contrast = 0.0
                    block.threshold = 128
                    block.gamma = 1.0
                    block.invert = false
                    refresh()
                }) { Text("✒️ Графика") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val auto = withContext(Dispatchers.Default) { autoLevels(block) }
                        block.brightness = auto.first
                        block.contrast = auto.second
                        refresh()
                    }
                }) { Text("✨ Авто") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiSheet(
    vm: AppViewModel,
    onPick: (String) -> Unit,
    onClose: () -> Unit
) {
    var cat by remember { mutableStateOf(EmojiData.categories.size - 1) }
    // Ч/б глифы как на бумаге (API 29+, иначе системные цветные)
    val bw = remember { emojiPickerFamily() }
    val recents = remember(vm.settings.recentEmojis, vm.emojiTick) {
        vm.settings.recentEmojis
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            Text("Эмодзи", style = MaterialTheme.typography.titleMedium)
            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Недавние", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    recents.forEach { e ->
                        TextButton(onClick = { onPick(e) }) {
                            Text(e, fontSize = 24.sp, fontFamily = bw)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EmojiData.categories.forEachIndexed { i, c ->
                    FilterChip(
                        selected = cat == i,
                        onClick = { cat = i },
                        label = { Text(c.title, maxLines = 1) }
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                items(EmojiData.categories[cat].items) { e ->
                    TextButton(onClick = { onPick(e) }) {
                        Text(e, fontSize = 24.sp, maxLines = 1, fontFamily = bw)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Лупа: полноэкранный просмотр картинки с pinch-зумом. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomDialog(bmp: Bitmap, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 6f)
        offset += panChange
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val img = remember(bmp) { bmp.asImageBitmap() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
        ) {
            Image(
                bitmap = img,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
            TextButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) { Text("✕", fontSize = 24.sp, color = Color.White) }
        }
    }
}

/** Пасхалка по тапу на кота в шапке — порт десктопного EggWindow. */
@Composable
private fun EggDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.logo256),
                    contentDescription = null,
                    modifier = Modifier.size(150.dp)
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    "Это приложение писалось для моей любимой жены",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Зайка, я тебя люблю!",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Это для тебя ❤",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text("🐾 ❤ 🐾", fontSize = 20.sp, textAlign = TextAlign.Center)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Закрыть") }
        }
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit
) {
    Column {
        Text(
            "$label: ${if (range.endInclusive > 10) value.toInt() else "%.2f".format(value)}",
            color = if (enabled) Color.Unspecified else Color.Gray
        )
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled)
    }
}

/** Цветной оригинал блока (фон, пока считается ч/б). */
private fun decodeColorBitmap(pngBase64: String): Bitmap? {
    return try {
        if (pngBase64.isBlank()) null
        else {
            val bytes = Base64.decode(pngBase64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Цветной оригинал с применёнными поворотом/зеркалом — поле для
 * выделения обрезки («режешь то, что видишь»). Только для показа,
 * в печать идёт обычный пайплайн через DocRender.
 */
@Composable
private fun orientedColor(block: DocBlock.Image): Bitmap? {
    val bmp = produceState<Bitmap?>(initialValue = null,
        block.pngBase64, block.rotation, block.mirror
    ) {
        value = withContext(Dispatchers.Default) {
            try {
                val bytes = Base64.decode(block.pngBase64, Base64.NO_WRAP)
                val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                if (block.rotation == 0 && !block.mirror) return@withContext src
                val m = android.graphics.Matrix()
                if (block.mirror) m.preScale(-1f, 1f)
                if (block.rotation != 0) m.postRotate(block.rotation.toFloat())
                val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
                if (out !== src && !src.isRecycled) src.recycle()
                out
            } catch (e: Exception) {
                null
            }
        }
    }
    val cur = bmp.value
    DisposableEffect(block.pngBase64, block.rotation, block.mirror) {
        onDispose {
            cur?.let { if (!it.isRecycled) it.recycle() }
        }
    }
    return cur
}

/**
 * Слот превью: битмап переиспользуется между тиками (ноль аллокаций),
 * а ver растёт на каждое обновление. Версия обязательна: Compose сравнивает
 * состояние и тот же экземпляр Bitmap посчитал бы «без изменений» —
 * картинка бы не перерисовывалась без посторонней рекомпозиции (скролл).
 */
private data class PreviewSlot(val bmp: Bitmap?, val ver: Long)

/**
 * Превью картинки: сырые битмапы + готовые обёртки для отрисовки.
 * Обёртки кэшируем remember'ом, иначе каждая рекомпозиция создаёт новую
 * и GPU перезаливает текстуру (22МБ+ перезаливок, рваные кадры).
 */
private data class BlockPreview(
    val color: Bitmap?,
    val preview: Bitmap?,
    val colorImg: androidx.compose.ui.graphics.ImageBitmap?,
    val previewImg: androidx.compose.ui.graphics.ImageBitmap?
)
@Composable
private fun imagePreview(
    block: DocBlock.Image,
    vm: AppViewModel
): BlockPreview {
    val color = produceState<Bitmap?>(initialValue = null, block.pngBase64) {
        value = withContext(Dispatchers.Default) { decodeColorBitmap(block.pngBase64) }
    }
    val tick = if (vm.previewUid == block.uid) vm.previewTick else -1
    val preview = produceState<PreviewSlot?>(initialValue = null,
        block.pngBase64, block.dither, block.threshold,
        block.brightness, block.contrast, block.saturation, block.gamma, block.invert,
        block.rotation, block.mirror, block.frame,
        block.cropL, block.cropT, block.cropR, block.cropB, tick
    ) {
        delay(120)
        val bmp = withContext(Dispatchers.Default) {
            DocRender.previewInto(block, value?.bmp?.takeIf { !it.isRecycled })
        }
        if (isActive) value = PreviewSlot(bmp, (value?.ver ?: 0L) + 1L)
    }
    // Обёртки — только при смене слота (версия гарантирует неравенство),
    // иначе перезаливка текстур на каждой рекомпозиции.
    val colorImg = remember(color.value) { color.value?.asImageBitmap() }
    val previewImg = remember(preview.value) { preview.value?.bmp?.asImageBitmap() }
    return BlockPreview(color.value, preview.value?.bmp, colorImg, previewImg)
}

/** Автоуровни по гистограмме (порт десктопа). Возвращает (яркость, контраст). */
private fun autoLevels(block: DocBlock.Image): Pair<Double, Double> {
    return try {
        val bytes = Base64.decode(block.pngBase64, Base64.NO_WRAP)
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return 0.0 to 0.0
        if (bmp.width > 384) {
            val k = 384.0 / bmp.width
            bmp = Bitmap.createScaledBitmap(
                bmp, 384, (bmp.height * k).toInt(), true
            )
        }
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val hist = IntArray(256)
        for (p in px) {
            val lum = (0.299 * ((p shr 16) and 0xFF) +
                0.587 * ((p shr 8) and 0xFF) +
                0.114 * (p and 0xFF)).toInt().coerceIn(0, 255)
            hist[lum]++
        }
        val total = w * h
        var p1 = 0
        var acc = 0
        for (v in 0..255) {
            acc += hist[v]
            if (acc >= total * 0.01) {
                p1 = v
                break
            }
        }
        var p99 = 255
        acc = 0
        for (v in 255 downTo 0) {
            acc += hist[v]
            if (acc >= total * 0.01) {
                p99 = v
                break
            }
        }
        if (p99 - p1 < 8) return 0.0 to 0.0
        val cf = 239.0 / (p99 - p1)
        val a = cf * 255.0
        val c = 259.0 * (a - 255.0) / (259.0 + a)
        val off = 8.0 - ((p1 - 128.0) * cf + 128.0)
        (off / 2.55).coerceIn(-100.0, 100.0) to (c / 2.55).coerceIn(-100.0, 100.0)
    } catch (e: Exception) {
        0.0 to 0.0
    }
}
