package com.aibox.kotlin.core.provider

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * AWS Event Stream 二进制分帧解析（application/vnd.amazon.eventstream）。
 *
 * 帧结构（大端）：
 * - Total Length (4B)：整帧长度
 * - Headers Length (4B)
 * - Prelude CRC (4B，跳过)
 * - Headers：[nameLen(1B)][name][valueType(1B，7=字符串)][valueLen(2B)][value]
 * - Payload：Total - 12 - HeadersLen - 4 字节
 * - Message CRC (4B，跳过)
 *
 * 纯函数：从缓冲区解析尽可能多的完整帧；不校验 CRC（Bedrock 实测无坏帧，
 * 且流式场景下失败抛异常会毁掉整条已计费回复）。
 */
object AwsEventStreamParser {

    data class AwsEvent(
        val headers: Map<String, String>,
        val payload: ByteArray,
    ) {
        val payloadText: String get() = String(payload, StandardCharsets.UTF_8)
    }

    data class ParseResult(
        val events: List<AwsEvent>,
        /** 已消费的字节数；剩余字节为不完整帧，需保留给下一批数据。 */
        val consumedBytes: Int,
    )

    fun parse(buffer: ByteArray, length: Int = buffer.size): ParseResult {
        val events = mutableListOf<AwsEvent>()
        var offset = 0
        while (offset + 12 <= length) {
            val totalLength = readInt32(buffer, offset)
            if (totalLength < 16) break // 损坏帧，放弃解析
            if (offset + totalLength > length) break // 不完整帧
            val headersLength = readInt32(buffer, offset + 4)
            val headersEnd = offset + 12 + headersLength
            if (headersEnd > offset + totalLength - 4) break // 损坏帧
            val headers = parseHeaders(buffer, offset + 12, headersLength)
            val payloadStart = headersEnd
            val payloadEnd = offset + totalLength - 4 // 去掉尾部 Message CRC
            val payload = buffer.copyOfRange(payloadStart, payloadEnd)
            events.add(AwsEvent(headers = headers, payload = payload))
            offset += totalLength
        }
        return ParseResult(events = events, consumedBytes = offset)
    }

    private fun parseHeaders(buffer: ByteArray, start: Int, length: Int): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        var offset = start
        val end = start + length
        while (offset + 2 <= end) {
            val nameLen = buffer[offset].toInt() and 0xff
            if (offset + 1 + nameLen >= end) break
            val name = String(buffer, offset + 1, nameLen, StandardCharsets.UTF_8)
            offset += 1 + nameLen
            val valueType = buffer[offset].toInt() and 0xff
            offset += 1
            when (valueType) {
                7 -> { // 字符串
                    if (offset + 2 > end) break
                    val valueLen = ((buffer[offset].toInt() and 0xff) shl 8) or (buffer[offset + 1].toInt() and 0xff)
                    offset += 2
                    if (offset + valueLen > end) break
                    headers[name] = String(buffer, offset, valueLen, StandardCharsets.UTF_8)
                    offset += valueLen
                }
                0 -> { // 布尔（1 字节值）
                    if (offset >= end) break
                    offset += 1
                }
                5 -> { // 字节（2 字节长度前缀）
                    if (offset + 2 > end) break
                    val valueLen = ((buffer[offset].toInt() and 0xff) shl 8) or (buffer[offset + 1].toInt() and 0xff)
                    offset += 2 + valueLen
                }
                else -> break // 未知类型：放弃剩余头
            }
        }
        return headers
    }

    private fun readInt32(buffer: ByteArray, offset: Int): Int =
        ((buffer[offset].toInt() and 0xff) shl 24) or
            ((buffer[offset + 1].toInt() and 0xff) shl 16) or
            ((buffer[offset + 2].toInt() and 0xff) shl 8) or
            (buffer[offset + 3].toInt() and 0xff)

    /** 增量解析辅助：持有未消费残余的缓冲区。 */
    class IncrementalParser {
        private var pending = ByteArray(0)

        fun feed(chunk: ByteArray, length: Int): List<AwsEvent> {
            val merged = ByteArrayOutputStream(pending.size + length)
            merged.write(pending, 0, pending.size)
            merged.write(chunk, 0, length)
            val buffer = merged.toByteArray()
            val result = parse(buffer)
            pending = buffer.copyOfRange(result.consumedBytes, buffer.size)
            return result.events
        }

        fun hasPending(): Boolean = pending.isNotEmpty()
    }
}
