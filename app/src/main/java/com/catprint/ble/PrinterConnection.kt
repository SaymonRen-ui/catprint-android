package com.catprint.ble

import android.content.Context
import com.catprint.core.printer.Mxw01Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

enum class PrinterState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR }

/**
 * Подключение принтера: состояние, скан, коннект, батарея, статус A1.
 */
class PrinterConnection(context: Context) {

    private val ble = BleConnection(context.applicationContext)

    private val _state = MutableStateFlow(PrinterState.DISCONNECTED)
    val state: StateFlow<PrinterState> = _state

    private val _lastBattery = MutableStateFlow<Int?>(null)
    val lastBattery: StateFlow<Int?> = _lastBattery

    val isConnected: Boolean
        get() = _state.value == PrinterState.CONNECTED && ble.isConnected

    val deviceName: String get() = ble.deviceName
    val hasBattery: Boolean get() = ble.hasBattery
    val serviceUuids: List<UUID> get() = ble.serviceUuids.toList()
    val mtu: Int get() = ble.mtu

    suspend fun scan(timeoutMs: Long = 6000): List<BleDeviceInfo> {
        _state.value = PrinterState.SCANNING
        try {
            val list = ble.scan(timeoutMs)
            _state.value = PrinterState.DISCONNECTED
            return list
        } catch (e: Exception) {
            _state.value = PrinterState.DISCONNECTED
            throw e
        }
    }

    suspend fun connect(device: BleDeviceInfo) {
        if (isConnected) return
        _state.value = PrinterState.CONNECTING
        try {
            ble.connect(device.address)
            _state.value = PrinterState.CONNECTED
            queryBattery()
        } catch (e: Exception) {
            _state.value = PrinterState.ERROR
            throw e
        }
    }

    suspend fun connectMxw01() {
        if (isConnected) return
        _state.value = PrinterState.CONNECTING
        try {
            val found = ble.scan(10_000).firstOrNull {
                it.name.equals("MXW01", ignoreCase = true)
            } ?: throw IllegalStateException("Принтер MXW01 не найден")
            ble.connect(found.address)
            _state.value = PrinterState.CONNECTED
            queryBattery()
        } catch (e: Exception) {
            _state.value = PrinterState.ERROR
            throw e
        }
    }

    suspend fun sendCommand(data: ByteArray) {
        checkConnected()
        ble.writeCommand(data)
    }

    suspend fun sendRaster(data: ByteArray) {
        checkConnected()
        ble.writeData(data)
    }

    /** Быстрый интервал перед печатью (best-effort). */
    fun requestFastLink() = ble.requestFastLink()

    /** Батарея: стандартный BAS, если принтер отдал. */
    suspend fun queryBattery(): Int? {
        if (!isConnected) return null
        return try {
            val level = ble.readBattery()
            _lastBattery.value = level
            level
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Статус A1: шлём запрос, 4 секунды слушаем AE02, ищем батарею в байте 9.
     * onPacket — сырые пакеты для журнала.
     */
    suspend fun requestBattery(
        onPacket: ((ByteArray) -> Unit)? = null
    ): Int? {
        checkConnected()
        var found: Int? = null
        val listen = CoroutineScope(Dispatchers.IO)
            .async {
                ble.listenNotify(4000) { data ->
                    onPacket?.invoke(data)
                    Mxw01Protocol.parseStatusBattery(data)?.let { found = it }
                }
            }
        ble.writeCommand(Mxw01Protocol.statusRequest())
        listen.await()
        if (found != null) _lastBattery.value = found
        return found
    }

    suspend fun exploreService(serviceUuid: UUID) =
        ble.exploreService(serviceUuid)

    suspend fun readCharacteristic(serviceUuid: UUID, charUuid: UUID) =
        ble.readCharacteristic(serviceUuid, charUuid)

    private fun checkConnected() {
        if (!isConnected) throw IllegalStateException("Принтер не подключён")
    }

    fun disconnect() {
        ble.disconnect()
        _lastBattery.value = null
        _state.value = PrinterState.DISCONNECTED
    }
}
