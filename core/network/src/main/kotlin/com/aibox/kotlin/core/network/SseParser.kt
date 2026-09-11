package com.aibox.kotlin.core.network

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/** 一个完整的 SSE 帧：若干行字段聚合后的结果。 */
data class SseFrame(
    /** event: 字段；未声明时为 null（OpenAI 风格仅 data）。 */
    val event: String? = null,
    /** data: 字段聚合（多行 data 以 \n 连接）。 */
    val data: String = "",
)

/**
 * 逐行读取响应体并聚合 SSE 帧。
 * 空行分帧；忽略注释（: 开头）与 retry/id 等非数据字段。
 */
fun ByteReadChannel.toSseFlow(): Flow<SseFrame> = callbackFlow {
    try {
        while (true) {
            var event: String? = null
            val dataLines = mutableListOf<String>()
            var sawAny = false
            while (true) {
                val line = readUTF8Line() ?: break
                when {
                    line.isEmpty() -> {
                        // 帧结束
                        break
                    }
                    line.startsWith(":") -> continue // 注释/keepalive
                    line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        sawAny = true
                        dataLines.add(line.removePrefix("data:").removePrefix(" "))
                    }
                    // id:/retry: 忽略
                }
                if (line.isNotEmpty() && !line.startsWith(":")) sawAny = true
            }
            if (sawAny || event != null) {
                send(SseFrame(event = event, data = dataLines.joinToString("\n")))
            } else {
                // 流结束（读到 null 且没有任何内容）
                break
            }
        }
        close()
    } catch (t: Throwable) {
        close(t)
    }
    awaitClose { }
}
