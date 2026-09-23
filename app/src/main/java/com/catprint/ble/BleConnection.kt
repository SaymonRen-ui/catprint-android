package com.catprint.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import com.catprint.core.printer.Mxw01Protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class BleDeviceInfo(
    val name: String,
    val address: String,
    var rssi: Int
)

/**
 * BLE-соединение с MXW01. Повторяет рабочий десктопный протокол:
 * сервис AE30, AE01 — команды, AE02 — notify, AE03 — растр.
 * Строка (48 байт) пишется ОДНИМ вызовом, дробить нельзя.
 */
class BleConnection(private val context: Context) {

    companion object {
        val SERVICE_V5X: UUID = UUID.fromString("0000ae30-0000-1000-8000-00805f9b34fb")
        val CHAR_COMMAND: UUID = UUID.fromString("0000ae01-0000-1000-8000-00805f9b34fb")
        val CHAR_NOTIFY: UUID = UUID.fromString("0000ae02-0000-1000-8000-00805f9b34fb")
        val CHAR_DATA: UUID = UUID.fromString("0000ae03-0000-1000-8000-00805f9b34fb")
        val SERVICE_BATTERY: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val CHAR_BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        val DESC_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val WRITE_RETRIES = 3
        private const val OP_TIMEOUT_MS = 15_000L
        // Строки без ответа: потерянный колбэк нельзя ждать долго —
        // принтер за это время закроет задание по своему вотчдогу и
        // на бумаге останется полоса. NO_RESPONSE не подтверждает
        // доставку, поэтому потеря — штатно, лечится быстрым ретраем
        // (дубль одной строки на фото незаметен, дыра — очень).
        private const val WRITE_TIMEOUT_MS = 500L
    }

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter

    private val io = Mutex()
    private var gatt: BluetoothGatt? = null
    private var cmdChar: BluetoothGattCharacteristic? = null
    private var notifyChar: BluetoothGattCharacteristic? = null
    private var dataChar: BluetoothGattCharacteristic? = null
    private var batteryChar: BluetoothGattCharacteristic? = null
    private var notifyEnabled = false

    val serviceUuids = mutableListOf<UUID>()

    @Volatile
    private var writeResult: CompletableDeferred<Boolean>? = null

    @Volatile
    private var readResult: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var notifyListener: ((ByteArray) -> Unit)? = null

    @Volatile
    private var connectResult: CompletableDeferred<Boolean>? = null

    @Volatile
    private var mtuResult: CompletableDeferred<Int>? = null

    /** Фактический ATT MTU соединения (для диагностики). */
    @Volatile
    var mtu: Int = 23
        private set

    val isConnected: Boolean
        get() = gatt != null && cmdChar != null &&
            notifyChar != null && dataChar != null && notifyEnabled

    val deviceName: String
        get() = try {
            gatt?.device?.name ?: ""
        } catch (e: SecurityException) {
            ""
        }

    val hasBattery: Boolean get() = batteryChar != null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt, status: Int, newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectResult?.complete(true)
            } else {
                connectResult?.complete(false)
                closeQuietly()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {}

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            writeResult?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // 4-аргументного варианта для записи нет даже в API 36 —
        // система всегда зовёт 3-аргументный. Значение мы и так знаем.
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readResult?.complete(characteristic.value?.copyOf())
            } else {
                readResult?.complete(null)
            }
        }

        // API 33+: значение приходит параметром
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                readResult?.complete(value.copyOf())
            } else {
                readResult?.complete(null)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value?.copyOf() ?: return
            if (data.isNotEmpty()) notifyListener?.invoke(data)
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (value.isNotEmpty()) notifyListener?.invoke(value.copyOf())
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            @Suppress("DEPRECATION")
            writeResult?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                this@BleConnection.mtu = mtu
            }
            mtuResult?.complete(mtu)
        }

        // 4-аргументного onDescriptorWrite нет — всегда 3-аргументный.

        private fun closeQuietly() {
            notifyEnabled = false
            cmdChar = null
            notifyChar = null
            dataChar = null
            batteryChar = null
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: SecurityException) {
            }
            gatt = null
        }
    }

    // ----------------------------------------------------------
    // СКАН
    // ----------------------------------------------------------

    @SuppressLint("MissingPermission")
    suspend fun scan(timeoutMs: Long = 6000): List<BleDeviceInfo> {
        val scanner = adapter?.bluetoothLeScanner
            ?: throw IllegalStateException("Нет BLE-адаптера")
        val found = LinkedHashMap<String, BleDeviceInfo>()
        val done = CompletableDeferred<Unit>()

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                try {
                    val name = result.device.name ?: return
                    if (name.isBlank()) return
                    val addr = result.device.address
                    val prev = found[addr]
                    if (prev == null) {
                        found[addr] = BleDeviceInfo(name, addr, result.rssi)
                    } else {
                        prev.rssi = result.rssi
                    }
                } catch (e: SecurityException) {
                    // нет разрешения — пропускаем
                }
            }
        }

        try {
            scanner.startScan(null, ScanSettings.Builder().build(), cb)
        } catch (e: SecurityException) {
            throw IllegalStateException("Нет разрешения BLUETOOTH_SCAN")
        }

        try {
            kotlinx.coroutines.delay(timeoutMs)
        } finally {
            try {
                scanner.stopScan(cb)
            } catch (e: SecurityException) {
            }
            done.complete(Unit)
        }
        done.await()

        return found.values
            .sortedWith(
                compareByDescending<BleDeviceInfo> {
                    it.name.contains("MXW01", ignoreCase = true)
                }.thenByDescending { it.rssi }
            )
    }

    // ----------------------------------------------------------
    // КОННЕКТ
    // ----------------------------------------------------------

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String) {
        disconnect()
        val device = adapter?.getRemoteDevice(address)
            ?: throw IllegalStateException("Нет BLE-адаптера")

        val connected = CompletableDeferred<Boolean>()
        connectResult = connected

        val g = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
        } catch (e: SecurityException) {
            connectResult = null
            throw IllegalStateException("Нет разрешения BLUETOOTH_CONNECT")
        } ?: throw IllegalStateException("connectGatt вернул null")

        gatt = g
        try {
            val ok = withTimeout(OP_TIMEOUT_MS) { connected.await() }
            if (!ok) throw IllegalStateException("Принтер отклонил коннект")
            // Быстрый интервал соединения — иначе строки ползут по 150+мс.
            // Принтер вправе отказать, тогда останемся на текущем.
            try {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            } catch (e: SecurityException) {
                throw IllegalStateException("Нет разрешения BLUETOOTH_CONNECT")
            }
            // Маленькая пауза перед discovery — так стабильнее на части стеков
            kotlinx.coroutines.delay(500)
        } catch (e: TimeoutCancellationException) {
            disconnect()
            throw IllegalStateException("Принтер не отвечает на коннект")
        } finally {
            connectResult = null
        }

        discover()
    }

    @SuppressLint("MissingPermission")
    private suspend fun discover() {
        val g = gatt ?: throw IllegalStateException("Нет соединения")
        serviceUuids.clear()

        try {
            if (!g.discoverServices()) {
                throw IllegalStateException("discoverServices отклонён")
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Нет разрешения BLUETOOTH_CONNECT")
        }

        // Ждём появления сервисов опросом (колбэк onServicesDiscovered
        // в стеке тоже отработает, но список берём напрямую).
        var waited = 0
        while ((g.services?.isEmpty() != false) && waited < OP_TIMEOUT_MS) {
            kotlinx.coroutines.delay(100)
            waited += 100
        }

        val services = g.services
        if (services.isNullOrEmpty()) {
            disconnect()
            throw IllegalStateException("Принтер не вернул GATT-сервисы")
        }
        for (s in services) serviceUuids.add(s.uuid)

        val v5x = services.firstOrNull { it.uuid == SERVICE_V5X }
            ?: throw IllegalStateException("Сервис V5X AE30 не найден").also {
                disconnect()
            }

        cmdChar = v5x.characteristics.firstOrNull { it.uuid == CHAR_COMMAND }
            ?: throw IllegalStateException("AE01 не найдена").also { disconnect() }
        notifyChar = v5x.characteristics.firstOrNull { it.uuid == CHAR_NOTIFY }
            ?: throw IllegalStateException("AE02 не найдена").also { disconnect() }
        dataChar = v5x.characteristics.firstOrNull { it.uuid == CHAR_DATA }
            ?: throw IllegalStateException("AE03 не найдена").also { disconnect() }

        // Батарея (необязательно)
        batteryChar = services
            .firstOrNull { it.uuid == SERVICE_BATTERY }
            ?.characteristics
            ?.firstOrNull { it.uuid == CHAR_BATTERY_LEVEL }

        requestLargeMtu()
        enableNotify()
    }

    /**
     * Просим MTU 247: строка растра 48 байт должна уходить ОДНИМ
     * ATT-пакетом, иначе прошивка режет строку и рассинхронизируется.
     * Неудача не фатальна — принтер мог сам запросить MTU.
     */
    @SuppressLint("MissingPermission")
    private suspend fun requestLargeMtu() {
        val g = gatt ?: return
        try {
            val d = CompletableDeferred<Int>()
            mtuResult = d
            if (!g.requestMtu(247)) {
                mtuResult = null
                return
            }
            try {
                withTimeout(5000) { d.await() }
            } finally {
                mtuResult = null
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Нет разрешения BLUETOOTH_CONNECT")
        } catch (e: Exception) {
            // таймаут MTU — продолжаем с текущим
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotify() {
        val g = gatt ?: throw IllegalStateException("Нет соединения")
        val ch = notifyChar ?: throw IllegalStateException("Нет AE02")
        try {
            if (!g.setCharacteristicNotification(ch, true)) {
                throw IllegalStateException("Notify не включился")
            }
            val cccd = ch.getDescriptor(DESC_CCCD)
                ?: throw IllegalStateException("Нет CCCD у AE02")
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!g.writeDescriptor(cccd)) {
                throw IllegalStateException("Запись CCCD отклонена")
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Нет разрешения BLUETOOTH_CONNECT")
        }

        val ok = waitWriteResult()
        if (!ok) throw IllegalStateException("AE02 Notify не включился")
        notifyEnabled = true
    }

    // ----------------------------------------------------------
    // ЗАПИСЬ — строго целиком за один вызов
    // ----------------------------------------------------------

    @SuppressLint("MissingPermission")
    private suspend fun writeWhole(
        char: BluetoothGattCharacteristic,
        data: ByteArray,
        tag: String
    ) {
        if (data.isEmpty()) return
        val g = gatt ?: throw IllegalStateException("Нет соединения")

        var lastError: Exception? = null
        repeat(WRITE_RETRIES) {
            try {
                val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = g.writeCharacteristic(
                        char, data,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    )
                    status == BluetoothGatt.GATT_SUCCESS &&
                        waitWriteResult(WRITE_TIMEOUT_MS)
                } else {
                    @Suppress("DEPRECATION")
                    char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    @Suppress("DEPRECATION")
                    char.value = data
                    g.writeCharacteristic(char) && waitWriteResult(WRITE_TIMEOUT_MS)
                }
                if (ok) return
            } catch (e: SecurityException) {
                throw IllegalStateException("Нет разрешения BLUETOOTH_CONNECT")
            } catch (e: Exception) {
                lastError = e
            }
            kotlinx.coroutines.delay(30)
        }
        throw IllegalStateException(
            "Принтер перестал отвечать ($tag, ${data.size} байт): " +
                (lastError?.message ?: "статус != SUCCESS")
        )
    }

    private suspend fun waitWriteResult(timeoutMs: Long = OP_TIMEOUT_MS): Boolean {
        val d = CompletableDeferred<Boolean>()
        writeResult = d
        return try {
            withTimeout(timeoutMs) { d.await() }
        } catch (e: TimeoutCancellationException) {
            false
        } finally {
            if (writeResult === d) writeResult = null
        }
    }

    suspend fun writeCommand(data: ByteArray) {
        val ch = cmdChar ?: throw IllegalStateException("AE01 недоступна")
        io.withLock { writeWhole(ch, data, "AE01") }
    }

    suspend fun writeData(data: ByteArray) {
        val ch = dataChar ?: throw IllegalStateException("AE03 недоступна")
        io.withLock { writeWhole(ch, data, "AE03") }
    }

    /**
     * Просьба быстрого интервала соединения перед печатью.
     * Стек после простоя часто скатывается на медленный интервал
     * (отсюда 70мс/строка); принтер вправе отказать — не фатально.
     */
    @SuppressLint("MissingPermission")
    fun requestFastLink() {
        try {
            gatt?.requestConnectionPriority(
                BluetoothGatt.CONNECTION_PRIORITY_HIGH
            )
        } catch (e: SecurityException) {
            // молча: печать продолжится на текущем интервале
        } catch (e: Exception) {
        }
    }

    // ----------------------------------------------------------
    // ЧТЕНИЕ
    // ----------------------------------------------------------

    @SuppressLint("MissingPermission")
    suspend fun readBattery(): Int? {
        val ch = batteryChar ?: return null
        if (!isConnected) return null
        return io.withLock {
            val g = gatt ?: return null
            try {
                val d = CompletableDeferred<ByteArray?>()
                readResult = d
                @Suppress("DEPRECATION")
                val started = g.readCharacteristic(ch)
                if (!started) {
                    readResult = null
                    return null
                }
                val bytes = try {
                    withTimeout(OP_TIMEOUT_MS) { d.await() }
                } finally {
                    readResult = null
                } ?: return null
                if (bytes.isEmpty()) return null
                (bytes[0].toInt() and 0xFF).coerceIn(0, 100)
            } catch (e: SecurityException) {
                null
            }
        }
    }

    // ----------------------------------------------------------
    // ПРОСЛУШКА AE02
    // ----------------------------------------------------------

    suspend fun listenNotify(
        durationMs: Long,
        onPacket: (ByteArray) -> Unit
    ) {
        if (!isConnected) throw IllegalStateException("AE02 недоступна")
        notifyListener = onPacket
        try {
            kotlinx.coroutines.delay(durationMs)
        } finally {
            notifyListener = null
        }
    }

    // ----------------------------------------------------------
    // СЕРВИСЫ ДЛЯ РАЗВЕДКИ
    // ----------------------------------------------------------

    @SuppressLint("MissingPermission")
    suspend fun exploreService(serviceUuid: UUID): List<Pair<UUID, Int>> {
        val g = gatt ?: throw IllegalStateException("Нет соединения")
        val svc = g.services?.firstOrNull { it.uuid == serviceUuid }
            ?: throw IllegalStateException("Сервис не найден")
        return svc.characteristics.map { it.uuid to it.properties }
    }

    @SuppressLint("MissingPermission")
    suspend fun readCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID
    ): ByteArray? {
        val g = gatt ?: return null
        val ch = g.services
            ?.firstOrNull { it.uuid == serviceUuid }
            ?.characteristics
            ?.firstOrNull { it.uuid == charUuid }
            ?: return null
        if ((ch.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            return null
        }
        return io.withLock {
            try {
                val d = CompletableDeferred<ByteArray?>()
                readResult = d
                @Suppress("DEPRECATION")
                if (!g.readCharacteristic(ch)) {
                    readResult = null
                    return null
                }
                try {
                    withTimeout(OP_TIMEOUT_MS) { d.await() }
                } finally {
                    readResult = null
                }
            } catch (e: SecurityException) {
                null
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        notifyEnabled = false
        cmdChar = null
        notifyChar = null
        dataChar = null
        batteryChar = null
        serviceUuids.clear()
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
        }
        gatt = null
    }
}
