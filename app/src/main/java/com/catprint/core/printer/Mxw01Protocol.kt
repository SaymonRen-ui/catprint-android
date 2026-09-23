package com.catprint.core.printer

/**
 * Протокол MXW01 (семейство V5X). Побайтовый порт десктопного Mxw01Protocol.
 * Формат команды: 22 21 CMD 00 LEN_LO LEN_HI PAYLOAD CRC|00 00.
 * A2 — с CRC8 (poly 0x07), A9/AD — хвост 00 00.
 */
object Mxw01Protocol {
    const val PRINTER_WIDTH = 384
    const val BYTES_PER_LINE = 48

    private const val HEADER_1: Byte = 0x22
    private const val HEADER_2: Byte = 0x21
    private const val TERMINATOR: Byte = 0xFF.toByte()

    private const val CMD_SET_INTENSITY: Byte = 0xA2.toByte()
    const val CMD_STATUS_REQUEST: Byte = 0xA1.toByte()
    private const val CMD_PRINT_REQUEST: Byte = 0xA9.toByte()
    private const val CMD_FLUSH: Byte = 0xAD.toByte()

    fun intensity(level: Byte): ByteArray =
        createCommandWithCrc(CMD_SET_INTENSITY, byteArrayOf(level))

    /** Запрос статуса A1 — ответ приходит в AE02, батарея в байте 9. */
    fun statusRequest(): ByteArray =
        createSimpleCommand(CMD_STATUS_REQUEST, byteArrayOf(0x00))

    /**
     * Батарея из ответа на A1: байт 9 (проверено на железе: 85% -> 0x55).
     */
    fun parseStatusBattery(packet: ByteArray): Int? {
        if (packet.size < 10) return null
        if (packet[0] != HEADER_1 || packet[1] != HEADER_2) return null
        if (packet[2] != CMD_STATUS_REQUEST) return null
        val v = packet[9].toInt() and 0xFF
        if (v !in 0..100) return null
        return v
    }

    fun printRequest(height: Int): ByteArray =
        createSimpleCommand(
            CMD_PRINT_REQUEST,
            byteArrayOf(
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte(),
                (BYTES_PER_LINE and 0xFF).toByte(),
                ((BYTES_PER_LINE shr 8) and 0xFF).toByte()
            )
        )

    fun flush(): ByteArray =
        createSimpleCommand(CMD_FLUSH, byteArrayOf(0x00))

    private fun createCommandWithCrc(command: Byte, payload: ByteArray): ByteArray {
        val result = ByteArray(8 + payload.size)
        result[0] = HEADER_1
        result[1] = HEADER_2
        result[2] = command
        result[3] = 0x00
        result[4] = (payload.size and 0xFF).toByte()
        result[5] = ((payload.size shr 8) and 0xFF).toByte()
        payload.copyInto(result, 6)
        result[6 + payload.size] = calculateCrc8(payload)
        result[7 + payload.size] = TERMINATOR
        return result
    }

    private fun createSimpleCommand(command: Byte, payload: ByteArray): ByteArray {
        val result = ByteArray(8 + payload.size)
        result[0] = HEADER_1
        result[1] = HEADER_2
        result[2] = command
        result[3] = 0x00
        result[4] = (payload.size and 0xFF).toByte()
        result[5] = ((payload.size shr 8) and 0xFF).toByte()
        payload.copyInto(result, 6)
        // A1/A9/AD — рабочий формат без CRC
        result[6 + payload.size] = 0x00
        result[7 + payload.size] = 0x00
        return result
    }

    fun calculateCrc8(data: ByteArray): Byte {
        var crc = 0
        for (value in data) {
            crc = crc xor (value.toInt() and 0xFF)
            repeat(8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc.toByte()
    }
}
