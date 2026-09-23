package com.catprint.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catprint.ble.BleDeviceInfo
import com.catprint.ble.PrinterConnection
import com.catprint.ble.PrinterState
import com.catprint.core.doc.DocBlock
import com.catprint.core.doc.DocRender
import com.catprint.core.doc.DocSerializer
import com.catprint.core.doc.asRun
import com.catprint.core.doc.getAttr
import com.catprint.core.doc.setAttr
import com.catprint.core.doc.styleAt
import com.catprint.data.SettingsStore
import com.catprint.print.PrintService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Лог приложения (кольцо, 300 строк) + зеркало в logcat и файл. */
class LogBus(private val dir: java.io.File? = null) {
    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    @Synchronized
    fun log(text: String) {
        val stamped =
            "[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(java.util.Date())}] $text"
        _lines.value = (_lines.value + stamped).takeLast(300)
        // INFO, не DEBUG: часть прошивок режет DEBUG-логи напрочь.
        Log.i("CatPrint", text)
        try {
            val d = dir ?: return
            val f = java.io.File(d, "catprint.log")
            if (f.length() > 200 * 1024) f.writeText("")
            f.appendText("$stamped\n")
        } catch (e: Exception) {
            // файловый лог не критичен
        }
    }

    fun clear() {
        _lines.value = emptyList()
    }
}

/** Общее состояние: принтер, документ, печать, журнал. */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)
    var themeMode by mutableStateOf(settings.darkTheme)
    val log = LogBus(app.cacheDir)
    val printer = PrinterConnection(app)
    private val printService = PrintService(printer)

    val blocks = mutableStateListOf<DocBlock>()
    var docName by mutableStateOf("Новый документ")
    var docPath: String? = null

    /** Счётчик изменений документа — дёргает рекомпозицию карточек. */
    var revision by mutableStateOf(0)

    /**
     * Лёгкий тик превью: дёргает пересчёт превью картинок БЕЗ снапшота Undo.
     * Для слайдеров шторки, чтобы карточка и шторка не расходились.
     * uid ограничивает пересчёт одним блоком: остальные карточки тик игнорят.
     */
    var previewTick by mutableStateOf(0)
    var previewUid by mutableStateOf("")
        private set

    fun bumpPreview(uid: String = "") {
        previewUid = uid
        previewTick++
        refreshPreview()
    }

    /** Отмена: стек снапшотов .catdoc (как десктопный Undo, макс 30). */
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    private val undoStack = ArrayDeque<String>()
    private val redoStack = ArrayDeque<String>()
    private var lastSnap = 0L

    /**
     * Сериализатор undo — один за раз (мьютекс), чтобы быстрые undo/redo
     * не перемешивали стек. Порядок взятия: mutex -> undoLock, не наоборот.
     * ОБЪЯВЛЕНЫ ЗДЕСЬ (до init): init делает стартовый snapshot(force).
     */
    private val undoMutex = kotlinx.coroutines.sync.Mutex()
    private val undoLock = Any()

    /** Вставка эмодзи в поле: uid блока -> очередь вставки. */
    var emojiTick by mutableStateOf(0)
    private val emojiPending = mutableMapOf<String, StringBuilder>()

    var isPrinting by mutableStateOf(false)
    var printProgress by mutableStateOf(0.0)
    private var printJob: Job? = null

    var devices by mutableStateOf<List<BleDeviceInfo>>(emptyList())
    var isScanning by mutableStateOf(false)

    /** Фон опроса батареи раз в 10 минут, пока принтер на связи. */
    private var batteryJob: Job? = null

    private fun startBatteryLoop() {
        batteryJob?.cancel()
        batteryJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10 * 60 * 1000)
                if (!printer.isConnected) break
                queryBattery()
            }
        }
    }

    /** Текущий 1-битный предпросмотр всего документа. */
    var bitPreview: Bitmap? by mutableStateOf(null)

    /** Плотность, с которой посчитано превью (учёт автоплотности). */
    var previewIntensity by mutableStateOf(0x5D)
        private set

    init {
        // Разовая миграция: деление рвало ленту протяжками — выключаем.
        if (!settings.splitFixApplied) {
            settings.splitEnabled = false
            settings.splitFixApplied = true
        }
        // Шрифты греем в фоне — иначе первый кадр висит секунды
        viewModelScope.launch(Dispatchers.IO) {
            com.catprint.core.printer.AppFonts.warmup()
        }
        // Пустой холст на старте
        blocks.add(DocBlock.Text("", fontSize = 24f, alignment = 1))
        snapshot(force = true)
        log.log("Готов. Подключите принтер на вкладке «Принтер».")
        // Автоподключение
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            autoConnect()
        }
    }

    // ----------------------------------------------------------
    // ПРИНТЕР
    // ----------------------------------------------------------

    fun scan() {
        viewModelScope.launch {
            isScanning = true
            try {
                devices = printer.scan()
                log.log(if (devices.isEmpty()) "Устройства не найдены"
                else "Найдено: ${devices.size}")
            } catch (e: Exception) {
                log.log("Ошибка сканирования: ${e.message}")
            } finally {
                isScanning = false
            }
        }
    }

    fun connect(device: BleDeviceInfo) {
        viewModelScope.launch {
            try {
                log.log("Подключение...")
                printer.connect(device)
                settings.lastDeviceAddress = device.address
                settings.lastDeviceName = device.name
                log.log("Подключено: ${printer.deviceName}")
                queryBattery()
                startBatteryLoop()
                log.log("MTU: ${printer.mtu}")
                log.log(
                    "Сервисы: " + printer.serviceUuids
                        .joinToString(" ") { it.toString().takeLast(12).take(4) }
                )
            } catch (e: Exception) {
                log.log("Ошибка подключения: ${e.message}")
            }
        }
    }

    fun quickConnect() {
        viewModelScope.launch {
            try {
                log.log("Поиск MXW01...")
                printer.connectMxw01()
                log.log("Подключено: ${printer.deviceName}")
                queryBattery()
                startBatteryLoop()
            } catch (e: Exception) {
                log.log("Ошибка подключения: ${e.message}")
            }
        }
    }

    private suspend fun autoConnect() {
        val addr = settings.lastDeviceAddress ?: return
        try {
            log.log("Автоподключение к ${settings.lastDeviceName}...")
            printer.connect(BleDeviceInfo(settings.lastDeviceName, addr, 0))
            log.log("Подключено: ${printer.deviceName}")
            queryBattery()
            startBatteryLoop()
        } catch (e: Exception) {
            log.log("Автоподключение не удалось: ${e.message}")
        }
    }

    fun disconnect() {
        batteryJob?.cancel()
        lastBatteryQuery = 0L
        printer.disconnect()
        log.log("Отключено.")
    }

    private var lastBatteryQuery = 0L

    fun queryBattery() {
        // Троттлинг 30с: опрос — это A1 + 4с прослушки, тапами не спамим.
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBatteryQuery < 30_000) return
        lastBatteryQuery = now
        viewModelScope.launch {
            // BAS у MXW01 нет — батарею отдаёт только ответ A1 (байт 9).
            try {
                val level = printer.requestBattery()
                if (level != null) log.log("🔋 Батарея: $level%.")
            } catch (e: Exception) {
                // не критично
            }
        }
    }

    fun requestStatus() {
        viewModelScope.launch {
            try {
                log.log("Запрос статуса A1, слушаю AE02...")
                var count = 0
                val battery = printer.requestBattery { data ->
                    count++
                    log.log(
                        "AE02 [${data.size}]: " +
                            data.joinToString(" ") { "%02X".format(it) }
                    )
                }
                log.log(
                    if (count == 0) "AE02 молчит — на A1 не отвечает."
                    else if (battery != null) "Батарея: $battery%."
                    else "Батарея не распознана."
                )
            } catch (e: Exception) {
                log.log("Статус не удался: ${e.message}")
            }
        }
    }

    // ----------------------------------------------------------
    // ДОКУМЕНТ
    // ----------------------------------------------------------

    fun newDocument() {
        blocks.clear()
        pendingStyles.clear()
        fmtSel = null
        resetCaretSync()
        blocks.add(DocBlock.Text("", fontSize = 24f, alignment = 1))
        docName = "Новый документ"
        docPath = null
        undoStack.clear()
        redoStack.clear()
        canRedo = false
        snapshot(force = true)
        revision++
        refreshPreview()
    }

    fun addText(after: Int = blocks.size) {
        blocks.add(after.coerceIn(0, blocks.size),
            DocBlock.Text("Новая строка", fontSize = 24f, alignment = 1))
        refreshPreview()
    }

    fun addDivider() {
        blocks.add(DocBlock.Divider(0))
        refreshPreview()
    }

    fun addGap() {
        blocks.add(DocBlock.Gap(48))
        refreshPreview()
    }

    fun addQr(after: Int = blocks.size) {
        blocks.add(after.coerceIn(0, blocks.size), DocBlock.Qr("", 1))
        touch()
    }

    /** Если документ — пустой холст по умолчанию, начать с чистого. */
    private fun replaceIfBlankDefault() {
        if (blocks.size == 1) {
            val b = blocks[0]
            if (b is DocBlock.Text && b.text.isBlank()) blocks.clear()
        }
    }

    fun templateChecklist() {
        replaceIfBlankDefault()
        blocks.add(DocBlock.Text("Покупки", fontSize = 30f, bold = true, alignment = 1))
        blocks.add(DocBlock.Divider(0))
        repeat(5) {
            blocks.add(DocBlock.Text("☐ ", fontSize = 24f, alignment = 0))
        }
        docName = "Список"
        touch()
        log.log("Шаблон: список.")
    }

    fun templateNote() {
        replaceIfBlankDefault()
        blocks.add(DocBlock.Text("Заметка", fontSize = 30f, bold = true, alignment = 1))
        blocks.add(DocBlock.Divider(1))
        blocks.add(DocBlock.Text("Текст...", fontSize = 24f, alignment = 0))
        docName = "Заметка"
        touch()
        log.log("Шаблон: заметка.")
    }

    fun templateHeader() {
        replaceIfBlankDefault()
        blocks.add(DocBlock.Text("Заголовок", fontSize = 48f, bold = true, alignment = 1))
        docName = "Заголовок"
        touch()
        log.log("Шаблон: заголовок.")
    }

    fun moveBlock(from: Int, to: Int) {
        if (from !in blocks.indices || to !in 0..blocks.size) return
        val b = blocks.removeAt(from)
        blocks.add(to.coerceIn(0, blocks.size), b)
        touch()
    }

    fun duplicateBlock(index: Int) {
        if (index !in blocks.indices) return
        val copy = when (val b = blocks[index]) {
            is DocBlock.Text -> b.copy(
                runs = b.runs.map { it.copy() }.toMutableList()
            )
            is DocBlock.Image -> b.copy()
            is DocBlock.Gap -> b.copy()
            is DocBlock.Divider -> b.copy()
            is DocBlock.Qr -> b.copy()
        }.withFreshUid()
        blocks.add(index + 1, copy)
        touch()
    }

    fun deleteBlock(index: Int) {
        if (index !in blocks.indices) return
        blocks.removeAt(index)
        touch()
    }

    fun touch() {
        revision++
        snapshot()
        refreshPreview()
    }

    /** Снапшот для Undo. Троттлинг 1.5с против спама слайдерами. */
    private fun snapshot(force: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        synchronized(undoLock) {
            if (!force && now - lastSnap < 1500 && undoStack.isNotEmpty()) return
            lastSnap = now
        }
        // Сериализация (мегабайты base64) — в фоне, иначе фризы до секунд.
        viewModelScope.launch(Dispatchers.Default) {
            val json = try {
                DocSerializer.save(blocks.toList())
            } catch (e: Exception) {
                null
            } ?: return@launch
            undoMutex.withLock {
                val size = synchronized(undoLock) {
                    undoStack.addLast(json)
                    while (undoStack.size > 30) undoStack.removeFirst()
                    redoStack.clear()
                    undoStack.size
                }
                withContext(Dispatchers.Main) {
                    canUndo = size > 1
                    canRedo = false
                }
            }
        }
    }

    private fun applyDocList(list: List<DocBlock>, label: String?) {
        blocks.clear()
        blocks.addAll(list)
        pendingStyles.clear()
        fmtSel = null
        resetCaretSync()
        revision++
        emojiTick++ // поля пересозданы — сбросить локальные состояния
        refreshPreview()
        if (label != null) log.log(label)
    }

    private fun restore(json: String, label: String) {
        viewModelScope.launch(Dispatchers.Default) {
            undoMutex.withLock {
                val list = try {
                    DocSerializer.load(json)
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (list == null) {
                        log.log("Не смог: неверный снимок")
                    } else {
                        applyDocList(list, label)
                    }
                }
            }
        }
    }

    fun undo() {
        val cur = blocks.toList()
        val pop = synchronized(undoLock) {
            if (undoStack.size < 2) return
            undoStack.removeLast()
            undoStack.last()
        }
        viewModelScope.launch(Dispatchers.Default) {
            undoMutex.withLock {
                try {
                    val curJson = DocSerializer.save(cur)
                    synchronized(undoLock) {
                        redoStack.addLast(curJson)
                        while (redoStack.size > 30) redoStack.removeFirst()
                    }
                } catch (e: Exception) {
                }
                val list = try {
                    DocSerializer.load(pop)
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (list == null) {
                        log.log("Не смог: неверный снимок")
                    } else {
                        applyDocList(list, "Отменено.")
                    }
                    val (u, r) = synchronized(undoLock) {
                        (undoStack.size > 1) to redoStack.isNotEmpty()
                    }
                    canUndo = u
                    canRedo = r
                    synchronized(undoLock) { lastSnap = 0L }
                }
            }
        }
    }

    fun redo() {
        val json = synchronized(undoLock) {
            if (redoStack.isEmpty()) return
            redoStack.removeLast()
        }
        viewModelScope.launch(Dispatchers.Default) {
            undoMutex.withLock {
                val list = try {
                    DocSerializer.load(json)
                } catch (e: Exception) {
                    null
                }
                if (list == null) {
                    withContext(Dispatchers.Main) { log.log("Не смог: неверный снимок") }
                    return@withLock
                }
                synchronized(undoLock) {
                    undoStack.addLast(json)
                    while (undoStack.size > 30) undoStack.removeFirst()
                }
                withContext(Dispatchers.Main) {
                    applyDocList(list, "Возвращено.")
                    val (u, r) = synchronized(undoLock) {
                        (undoStack.size > 1) to redoStack.isNotEmpty()
                    }
                    canUndo = u
                    canRedo = r
                    synchronized(undoLock) { lastSnap = 0L }
                }
            }
        }
    }

    /** Поставить эмодзи в очередь вставки для блока. */
    fun queueEmoji(uid: String, emoji: String) {
        queueInsert(uid, emoji)
        pushRecentEmoji(emoji)
    }

    /** Вставка произвольного текста в позицию каретки (без «недавних»). */
    fun queueInsert(uid: String, text: String) {
        emojiPending.getOrPut(uid) { StringBuilder() }.append(text)
        emojiTick++
    }

    /**
     * Программная установка выделения (кнопки −/+ границы).
     * Тач-драг ручек на части прошивок/IME рвётся, а прямая установка
     * через состояние поля работает всегда. Едет тем же надёжным
     * каналом, что и вставка эмодзи (emojiTick).
     */
    private val selPending = mutableMapOf<String, androidx.compose.ui.text.TextRange>()

    fun queueSel(uid: String, range: androidx.compose.ui.text.TextRange) {
        selPending[uid] = range
        emojiTick++
    }

    fun takeSel(uid: String): androidx.compose.ui.text.TextRange? =
        selPending.remove(uid)

    /**
     * Обновить текстовый блок ЗАМЕНОЙ объекта с тем же uid.
     * Мутации plain-var полей (bold/...) Compose не видит: ссылка та же —
     * рекомпозиция пропускается и чипы мёртвые. А замена элемента
     * в SnapshotStateList видна всем подписчикам списка.
     * uid сохраняем — поле ввода не пересоздаётся, фокус и каретка живут.
     */
    fun updateText(uid: String, change: (DocBlock.Text) -> Unit): DocBlock.Text? {
        val i = blocks.indexOfFirst { it is DocBlock.Text && it.uid == uid }
        if (i < 0) return null
        val nb = (blocks[i] as DocBlock.Text).copy()
        change(nb)
        nb.uid = uid
        blocks[i] = nb
        touch()
        return nb
    }

    /**
     * Тоггл Ж/К/Ч на диапазоне [a,b) (null = весь абзац).
     * Возвращает новый объект (uid сохранён) или null.
     */
    fun toggleStyle(
        uid: String, a: Int?, b: Int?, attr: com.catprint.core.doc.StyleAttr
    ): DocBlock.Text? = replaceText(uid) {
        com.catprint.core.doc.toggleStyleIn(it, a, b, attr)
    }

    /**
     * Вооружённый стиль печати (как в Word: тап Ж без выделения не красит
     * текст, а вооружает жирность для нового; повторный тап снимает).
     * uid -> стиль. Не сериализуется (намерение, а не документ).
     * SnapshotStateMap — панель подписывается и подсвечивает чипы.
     */
    val pendingStyles = androidx.compose.runtime.mutableStateMapOf<String, com.catprint.core.doc.TextRun>()

    /**
     * Выделение в активном поле (uid, start, end). Живёт здесь, а не в
     * EditorScreen: иначе каждое движение ручки выделения дёргало
     * рекомпозицию всего списка и жест обрывался на первом символе.
     * Читает только FormatBar, пишет только карточка поля.
     */
    var fmtSel by mutableStateOf<Triple<String, Int, Int>?>(null)

    /** Приём выделения из поля: диапазон + pending вслед за кареткой. */
    fun updateSel(uid: String, start: Int, end: Int) {
        fmtSel = Triple(uid, start, end)
        if (start == end) syncPendingToCaret(uid, start)
    }

    /** Стиль позиции каретки: ран влево, иначе база. */
    private fun caretStyleOf(
        b: DocBlock.Text, caret: Int
    ): com.catprint.core.doc.TextRun {
        if (b.runs.isEmpty()) return b.asRun()
        return com.catprint.core.doc.styleAt(
            b.runs, caret.coerceIn(0, b.text.length)
        )?.copy() ?: b.asRun()
    }

    /** Короткий тап Ж/К/Ч без выделения: вооружить/снять атрибут. */
    fun armAttr(uid: String, caret: Int, attr: com.catprint.core.doc.StyleAttr) {
        val b = blocks.filterIsInstance<DocBlock.Text>().firstOrNull { it.uid == uid }
            ?: return
        val cur = pendingStyles[uid]?.copy() ?: caretStyleOf(b, caret)
        cur.setAttr(attr, !cur.getAttr(attr))
        pendingStyles[uid] = cur
    }

    /** Размер для нового текста (без выделения). */
    fun setPendingSize(uid: String, caret: Int, size: Float) {
        val b = blocks.filterIsInstance<DocBlock.Text>().firstOrNull { it.uid == uid }
            ?: return
        val cur = pendingStyles[uid]?.copy() ?: caretStyleOf(b, caret)
        cur.fontSize = size
        pendingStyles[uid] = cur
    }

    /** Шрифт для нового текста (без выделения). */
    fun setPendingFont(uid: String, caret: Int, font: String) {
        val b = blocks.filterIsInstance<DocBlock.Text>().firstOrNull { it.uid == uid }
            ?: return
        val cur = pendingStyles[uid]?.copy() ?: caretStyleOf(b, caret)
        cur.fontFamily = font
        pendingStyles[uid] = cur
    }

    /**
     * Каретка переехала: pending = стиль новой позиции (тулбар следует
     * за кареткой, как в Word). Вызывать только на схлопнутом caret.
     */
    private var lastSyncUid: String? = null
    private var lastSyncPos: Int = -1

    fun syncPendingToCaret(uid: String, pos: Int) {
        // Дедуп: onCaret прилетает на каждый кадр драга выделения,
        // повторная запись того же стиля — лишняя рекомпозиция панели.
        if (uid == lastSyncUid && pos == lastSyncPos) return
        val b = blocks.filterIsInstance<DocBlock.Text>().firstOrNull { it.uid == uid }
            ?: return
        pendingStyles[uid] = caretStyleOf(b, pos)
        lastSyncUid = uid
        lastSyncPos = pos
    }

    private fun resetCaretSync() {
        lastSyncUid = null
        lastSyncPos = -1
    }

    /** Долгий тап: весь абзац + pending вровень с новой базой. */
    fun wholeStyle(uid: String, attr: com.catprint.core.doc.StyleAttr): DocBlock.Text? {
        val nb = toggleStyle(uid, null, null, attr) ?: return null
        pendingStyles[uid] = nb.asRun()
        return nb
    }

    /** Кегль на диапазоне (null = весь абзац). */
    fun setSize(uid: String, a: Int?, b: Int?, size: Float): DocBlock.Text? =
        replaceText(uid) {
            com.catprint.core.doc.setStyleIn(
                it, a, b,
                { t -> t.fontSize = size },
                { r -> r.fontSize = size }
            )
        }

    /** Шрифт на диапазоне (null = весь абзац). */
    fun setFont(uid: String, a: Int?, b: Int?, font: String): DocBlock.Text? =
        replaceText(uid) {
            com.catprint.core.doc.setStyleIn(
                it, a, b,
                { t -> t.fontFamily = font },
                { r -> r.fontFamily = font }
            )
        }

    /** Замена с сохранением uid + touch (общее для стилевых опов). */
    private fun replaceText(
        uid: String, op: (DocBlock.Text) -> DocBlock.Text
    ): DocBlock.Text? {
        val i = blocks.indexOfFirst { it is DocBlock.Text && it.uid == uid }
        if (i < 0) return null
        val nb = op((blocks[i] as DocBlock.Text))
        nb.uid = uid
        // retype/toggle возвращают новый список ранов; подстрахуемся
        // от shared-ссылки после copy().
        nb.runs = nb.runs.map { it.copy() }.toMutableList()
        blocks[i] = nb
        touch()
        return nb
    }

    /**
     * Печать/удаление/вставка текста с подгонкой ранов.
     * Вооружённый стиль (если есть) — вставляемому; иначе наследование влево.
     * Без снапшота на каждый символ (как раньше): только тихое превью.
     */
    fun applyTextEdit(uid: String, newText: String): DocBlock.Text? {
        val i = blocks.indexOfFirst { it is DocBlock.Text && it.uid == uid }
        if (i < 0) return null
        val old = blocks[i] as DocBlock.Text
        if (newText == old.text) return old
        if (old.runs.isEmpty()) {
            val p = pendingStyles[uid]
            if (p == null || newText.isEmpty()) {
                old.text = newText
                refreshPreview()
                return old
            }
            // Вооружён: первый символ начинает раны в стиле pending.
            val nb = old.copy(
                text = newText,
                runs = mutableListOf(p.copy(text = newText))
            )
            nb.uid = uid
            com.catprint.core.doc.collapseRuns(nb)
            blocks[i] = nb
            refreshPreview()
            return nb
        }
        val nb = com.catprint.core.doc.retypeIn(old, newText, pendingStyles[uid])
        nb.uid = uid
        nb.runs = nb.runs.map { it.copy() }.toMutableList()
        blocks[i] = nb
        refreshPreview()
        return nb
    }

    /** То же для QR (размер). */
    fun updateQr(uid: String, change: (DocBlock.Qr) -> Unit): DocBlock.Qr? {
        val i = blocks.indexOfFirst { it is DocBlock.Qr && it.uid == uid }
        if (i < 0) return null
        val nb = (blocks[i] as DocBlock.Qr).copy()
        change(nb)
        nb.uid = uid
        blocks[i] = nb
        touch()
        return nb
    }

    /** То же для отступа (высота). */
    fun updateGap(uid: String, change: (DocBlock.Gap) -> Unit): DocBlock.Gap? {
        val i = blocks.indexOfFirst { it is DocBlock.Gap && it.uid == uid }
        if (i < 0) return null
        val nb = (blocks[i] as DocBlock.Gap).copy()
        change(nb)
        nb.uid = uid
        blocks[i] = nb
        touch()
        return nb
    }

    /** То же для разделителя (стиль). */
    fun updateDivider(uid: String, change: (DocBlock.Divider) -> Unit): DocBlock.Divider? {
        val i = blocks.indexOfFirst { it is DocBlock.Divider && it.uid == uid }
        if (i < 0) return null
        val nb = (blocks[i] as DocBlock.Divider).copy()
        change(nb)
        nb.uid = uid
        blocks[i] = nb
        touch()
        return nb
    }

    /** Забрать очередь вставки для блока (вызывает карточка поля). */
    fun takeEmoji(uid: String): String? =
        emojiPending.remove(uid)?.toString()?.ifEmpty { null }

    fun saveTo(path: String) {
        val cur = blocks.toList()
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val text = DocSerializer.save(cur)
                java.io.File(path).writeText(text)
                withContext(Dispatchers.Main) {
                    docPath = path
                    docName = java.io.File(path).name
                    log.log("Документ сохранён.")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    log.log("Ошибка сохранения: ${e.message}")
                }
            }
        }
    }

    fun loadFrom(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            undoMutex.withLock {
                val list = try {
                    DocSerializer.load(java.io.File(path).readText())
                } catch (e: Exception) {
                    null
                }
                withContext(Dispatchers.Main) {
                    if (list == null) {
                        log.log("Ошибка открытия.")
                        return@withContext
                    }
                    blocks.clear()
                    blocks.addAll(list)
                    pendingStyles.clear()
                    fmtSel = null
                    resetCaretSync()
                    docPath = path
                    docName = java.io.File(path).name
                    log.log("Открыто: $docName")
                    synchronized(undoLock) {
                        undoStack.clear()
                        redoStack.clear()
                    }
                    canRedo = false
                    snapshot(force = true)
                    revision++
                    refreshPreview()
                }
            }
        }
    }

    /** Картинка из галереи -> сжатый оригинал (макс 768) -> блок. */
    suspend fun importImage(uri: Uri): DocBlock.Image? =
        withContext(Dispatchers.IO) {
            try {
                val resolver = getApplication<Application>().contentResolver
                resolver.openInputStream(uri)?.use { stream ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, bounds)
                    // Только до 1536: дальше ужмёт пошаговый билинейный
                    // пайплайна (усредняет). Грубое прореживание до 768
                    // (как было) выкидывало мелкую текстуру — лицо
                    // выбеливалось, тёмное давилось. На ПК даунскейл
                    // качественный сразу из полного разрешения.
                    var sample = 1
                    while (bounds.outWidth / sample > 1536) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    resolver.openInputStream(uri)?.use { s2 ->
                        val bmp = BitmapFactory.decodeStream(s2, null, opts)
                            ?: return@withContext null
                        val out = ByteArrayOutputStream()
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                        if (!bmp.isRecycled) bmp.recycle()
                        DocBlock.Image(
                            pngBase64 = Base64.encodeToString(
                                out.toByteArray(), Base64.NO_WRAP
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                log.log("Ошибка картинки: ${e.message}")
                null
            }
        }

    fun importAndAdd(uri: Uri) {
        viewModelScope.launch {
            val img = importImage(uri) ?: return@launch
            blocks.add(img)
            touch()
            log.log("Картинка добавлена.")
        }
    }

    fun splitBlock(index: Int) {
        if (index !in blocks.indices) return
        val b = blocks[index]
        if (b is DocBlock.Text) {
            blocks.add(
                index + 1,
                DocBlock.Text(
                    "",
                    fontFamily = b.fontFamily,
                    fontSize = b.fontSize,
                    bold = b.bold,
                    italic = b.italic,
                    underline = b.underline,
                    alignment = b.alignment,
                    lineHeight = b.lineHeight,
                    invert = b.invert,
                    frame = b.frame
                )
            )
            touch()
        }
    }

    // ----------------------------------------------------------
    // ПРЕДПРОСМОТР 1-БИТ
    // ----------------------------------------------------------

    private var previewJob: Job? = null

    fun refreshPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            kotlinx.coroutines.delay(400)
            try {
                if (blocks.isEmpty()) {
                    bitPreview = null
                    return@launch
                }
                val rendered = DocRender.render(blocks.toList())
                // Превью — идеальные биты как пойдут в принтер + чип жара.
                // Тепловую симуляцию пробовали: на мелких масштабах даёт
                // кашу вместо градиента, откатили.
                val eff = if (settings.autoDensity) {
                    PrintService.autoIntensity(
                        settings.intensity,
                        DocRender.coverageOf(rendered)
                    )
                } else {
                    settings.intensity
                }
                previewIntensity = eff
                // Показываем с учётом пропекания одиночных точек:
                // принтер их не берёт, в глазике их быть не должно.
                val shown = DocRender.dropUnfired(rendered)
                val bmp = Bitmap.createBitmap(
                    rendered.width, rendered.height, Bitmap.Config.ARGB_8888
                )
                val px = IntArray(shown.size) { i ->
                    if (shown[i]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
                bmp.setPixels(px, 0, rendered.width, 0, 0,
                    rendered.width, rendered.height)
                bitPreview = bmp
            } catch (e: Exception) {
                // предпросмотр не критичен
            }
        }
    }

    fun renderForPrint(): DocRender.Rendered =
        DocRender.render(blocks.toList())

    // ----------------------------------------------------------
    // ПЕЧАТЬ
    // ----------------------------------------------------------

    fun print() {
        if (isPrinting) return
        printJob = viewModelScope.launch {
            isPrinting = true
            printProgress = 0.0
            try {
                if (!printer.isConnected) {
                    log.log("Принтер не подключён.")
                    return@launch
                }
                if (blocks.isEmpty()) {
                    log.log("Документ пуст.")
                    return@launch
                }
                val rendered = withContext(Dispatchers.Default) { renderForPrint() }
                // Диагностика растра: сколько чёрного и где (пустой ли документ)
                val blackCount = rendered.black.count { it }
                var first = -1
                var last = -1
                for (y in 0 until rendered.height) {
                    var any = false
                    for (x in 0 until rendered.width) {
                        if (rendered.black[y * rendered.width + x]) {
                            any = true
                            break
                        }
                    }
                    if (any) {
                        if (first < 0) first = y
                        last = y
                    }
                }
                log.log(
                    "Растр: 384×${rendered.height}, чёрных точек=$blackCount, " +
                        "непустые строки=$first..$last"
                )
                // Диагностика стилей: какие флаги реально доехали до рендера.
                blocks.filterIsInstance<DocBlock.Text>().forEachIndexed { i, t ->
                    if (t.bold || t.italic || t.underline) {
                        log.log(
                            "Стиль[$i]: Ж=${t.bold} К=${t.italic} Ч=${t.underline} " +
                                "«${t.text.take(24)}»"
                        )
                    }
                }
                // Высокое плотное задание одним куском принтер может не вывезти
                // (перегрев): жар уже снижен автоплотностью, плюс идут
                // дыхательные паузы без разрывов (см. лог ниже).
                if (rendered.height > 256 &&
                    blackCount * 100L / (384L * rendered.height) > 40 &&
                    !settings.splitEnabled
                ) {
                    log.log("Высокое плотное задание: работают автопаузы.")
                }
                // Сохраняем картинку растра для съёма по ADB
                try {
                    val bmp = Bitmap.createBitmap(
                        rendered.width, rendered.height, Bitmap.Config.ARGB_8888
                    )
                    val px = IntArray(rendered.black.size) { i ->
                        if (rendered.black[i]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    }
                    bmp.setPixels(px, 0, rendered.width, 0, 0,
                        rendered.width, rendered.height)
                    java.io.File(
                        getApplication<Application>().cacheDir, "print_last.png"
                    ).outputStream().use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (e: Exception) {
                    log.log("Не смог сохранить растр: ${e.message}")
                }
                log.log(
                    "Печать: 384×${rendered.height} " +
                        "(~${rendered.heightMm.toInt()} мм)..."
                )
                val coverage = blackCount.toDouble() / (384.0 * rendered.height)
                var intensity = settings.intensity
                // Адаптив держит базовый жар сам (модуляция по группам),
                // глобальное снижение тут не нужно.
                if (!settings.adaptiveHeat && settings.autoDensity) {
                    val auto = PrintService.autoIntensity(intensity, coverage)
                    if (auto != intensity) {
                        log.log(
                            "Автоплотность: 0x${intensity.toString(16).uppercase()} → " +
                                "0x${auto.toString(16).uppercase()} " +
                                "(заливка ${(coverage * 100).toInt()}%)"
                        )
                    }
                    intensity = auto
                }
                val timing = PrintService.Timing(
                    intensity = intensity.toByte(),
                    lineDelayMs = settings.lineDelayMs,
                    blockLines = settings.blockLines,
                    blockPauseMs = settings.blockPauseMs,
                    splitEnabled = settings.splitEnabled,
                    splitLines = settings.splitLines,
                    splitPauseMs = settings.splitPauseMs,
                    adaptiveHeat = settings.adaptiveHeat
                )
                val copies = settings.copies.coerceIn(1, 10)
                val packed = rendered.packed(settings.bitOrder)
                // Быстрый интервал: после простоя стек скатывается на медленный
                printer.requestFastLink()
                log.log("MTU: ${printer.mtu}")
                repeat(copies) { c ->
                    if (c > 0) {
                        log.log("Копия ${c + 1}/$copies...")
                        kotlinx.coroutines.delay(1500)
                    }
                    withContext(Dispatchers.IO) {
                        printService.print(
                            packed,
                            rendered.width, rendered.height, timing,
                            onProgress = { printProgress = (c + it) / copies },
                            onLog = { log.log(it) }
                        )
                    }
                }
                log.log(if (copies > 1) "Напечатано: $copies." else "Напечатано.")
                queryBattery()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    log.log("Печать отменена.")
                } else {
                    log.log("Ошибка печати: ${e.message}")
                }
            } finally {
                isPrinting = false
            }
        }
    }

    fun cancelPrint() {
        printJob?.cancel()
    }

    /**
     * Диагностический тест транспорта: рамка + диагональ + горизонтали.
     * Обходит рендер документа — чистый тест BLE-канала.
     * Диагональ покажет схлопывание строк, рамка — обрезку краёв.
     */
    fun printTestPattern() {
        if (isPrinting) return
        printJob = viewModelScope.launch {
            isPrinting = true
            printProgress = 0.0
            try {
                if (!printer.isConnected) {
                    log.log("Принтер не подключён.")
                    return@launch
                }
                val w = 384
                val h = 144
                val black = BooleanArray(w * h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val frame = y == 0 || y == h - 1 || x == 0 || x == w - 1
                        val horiz = y % 16 == 0
                        val vert = x == w / 2
                        val diag = x == (y * w / h)
                        if (frame || horiz || vert || diag) black[y * w + x] = true
                    }
                }
                val packed = com.catprint.core.printer.Raster.pack(
                    black, w, h, settings.bitOrder
                )
                log.log("Тест: рамка+диагональ 384×$h...")
                val timing = PrintService.Timing(
                    intensity = settings.intensity.toByte(),
                    lineDelayMs = settings.lineDelayMs,
                    blockLines = settings.blockLines,
                    blockPauseMs = settings.blockPauseMs,
                    splitEnabled = false
                )
                withContext(Dispatchers.IO) {
                    printService.print(
                        packed, w, h, timing,
                        onProgress = { printProgress = it },
                        onLog = { log.log(it) }
                    )
                }
                log.log("Тест напечатан.")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    log.log("Печать отменена.")
                } else {
                    log.log("Ошибка печати: ${e.message}")
                }
            } finally {
                isPrinting = false
            }
        }
    }

    // ----------------------------------------------------------
    // ШРИФТЫ / ЭМОДЗИ RECENTS
    // ----------------------------------------------------------

    fun pushRecentFont(font: String) {
        val list = (listOf(font) + settings.recentFonts)
            .distinct().take(5)
        settings.recentFonts = list
    }

    fun pushRecentEmoji(emoji: String) {
        val list = (listOf(emoji) + settings.recentEmojis)
            .distinct().take(16)
        settings.recentEmojis = list
    }

    // ----------------------------------------------------------
    // НАСТРОЙКИ-ПРЕСЕТЫ
    // ----------------------------------------------------------

    fun applyPreset(kind: String) {
        when (kind) {
            "doc" -> {
                settings.intensity = 0x5D
                settings.lineDelayMs = 15
                settings.blockLines = 40
                settings.blockPauseMs = 0
                settings.splitEnabled = false
            }
            "photo" -> {
                settings.intensity = 0x55
                settings.lineDelayMs = 15
                settings.blockLines = 40
                settings.blockPauseMs = 0
                // Деление НЕ включаем: каждый кусок закрывается AD,
                // а AD принтер понимает как конец печати и тянет бумагу.
                // Высоким заданиям хватает равномерной подачи без остановок.
                settings.splitEnabled = false
                settings.splitLines = 96
                settings.splitPauseMs = 1500
            }
            "fast" -> {
                settings.intensity = 0x40
                settings.lineDelayMs = 8
                settings.blockLines = 40
                settings.blockPauseMs = 0
                settings.splitEnabled = false
            }
            "safe" -> {
                settings.intensity = 0x5D
                settings.lineDelayMs = 30
                settings.blockLines = 40
                settings.blockPauseMs = 500
                settings.splitEnabled = false
                settings.splitLines = 96
                settings.splitPauseMs = 2000
            }
        }
        log.log("Пресет применён.")
    }

    fun powerDisplay(): String {
        val pct = ((settings.intensity - 0x30) / 0.74).toInt().coerceIn(0, 100)
        val word = when {
            pct <= 30 -> "Эконом"
            pct <= 70 -> "Стандарт"
            else -> "Максимум"
        }
        return "$pct% · $word"
    }
}
