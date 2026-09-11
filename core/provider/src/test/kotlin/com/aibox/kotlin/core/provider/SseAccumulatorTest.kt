package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.network.ApiError
import com.aibox.kotlin.core.network.MidStreamApiError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 三大协议族 SSE 帧解析的契约测试。 */
class SseAccumulatorTest {

    private fun parse(json: String) = Json.parseToJsonElement(json).jsonObject

    // ---------- OpenAI ----------

    @Test
    fun `openai text delta`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(
            parse("""{"choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}"""),
        )
        assertEquals(1, events.size)
        assertEquals("Hello", (events[0] as StreamEvent.TextDelta).text)
    }

    @Test
    fun `openai reasoning delta from reasoning_content`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(
            parse("""{"choices":[{"delta":{"reasoning_content":"thinking..."}}]}"""),
        )
        assertEquals("thinking...", (events[0] as StreamEvent.ReasoningDelta).text)
    }

    @Test
    fun `openai tool call accumulates across chunks`() {
        val acc = OpenAiChunkAccumulator()
        val start = acc.onFrame(
            parse(
                """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"get_weather","arguments":""}}]}}]}""",
            ),
        )
        assertEquals(StreamEvent.ToolCallStart(0, "call_1", "get_weather"), start[0])

        val args1 = acc.onFrame(
            parse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"city\":"}}]}}]}"""),
        )
        assertEquals(
            StreamEvent.ToolCallArgsDelta(0, "{\"city\":"),
            args1[0],
        )

        val done = acc.onDone()
        // 参数分片 + ToolCallEnd + Finish
        assertTrue(done.contains(StreamEvent.ToolCallEnd(0)))
        assertTrue(done.any { it is StreamEvent.Finish })
    }

    @Test
    fun `openai usage captured`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(
            parse("""{"choices":[],"usage":{"prompt_tokens":10,"completion_tokens":20,"total_tokens":30}}"""),
        )
        val usage = events.filterIsInstance<StreamEvent.Usage>().single()
        assertEquals(10L, usage.usage.inputTokens)
        assertEquals(20L, usage.usage.outputTokens)
        assertEquals(30L, usage.usage.totalTokens)
    }

    @Test
    fun `openai error frame yields ApiError event`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(parse("""{"error":{"message":"rate limited","code":429}}"""))
        val error = events.filterIsInstance<StreamEvent.Error>().single()
        assertTrue(error.error is ApiError)
        assertEquals(429, (error.error as ApiError).statusCode)
    }

    // ---------- 真实线上帧形状回归（DeepSeek 等）：null 字段绝不抛异常 ----------

    @Test
    fun `deepseek usage frame with null choices does not crash`() {
        val acc = OpenAiChunkAccumulator()
        // DeepSeek stream_options include_usage 的末帧："choices": null + usage
        val events = acc.onFrame(
            parse("""{"id":"x","object":"chat.completion.chunk","choices":null,"usage":{"prompt_tokens":9,"completion_tokens":7,"total_tokens":16}}"""),
        )
        val usage = events.filterIsInstance<StreamEvent.Usage>().single()
        assertEquals(9L, usage.usage.inputTokens)
        assertEquals(16L, usage.usage.totalTokens)
    }

    @Test
    fun `null delta element does not crash`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(parse("""{"choices":[null]}"""))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `null delta object does not crash`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(parse("""{"choices":[{"index":0,"delta":null,"finish_reason":null}]}"""))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `null content and reasoning fields do not crash`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(
            parse("""{"choices":[{"delta":{"role":"assistant","content":null,"reasoning_content":null}}]}"""),
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun `empty content string emits no event`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(parse("""{"choices":[{"delta":{"content":""}}]}"""))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `null error field is not treated as error frame`() {
        val acc = OpenAiChunkAccumulator()
        val events = acc.onFrame(parse("""{"error":null,"choices":[{"delta":{"content":"ok"}}]}"""))
        assertEquals("ok", (events.single() as StreamEvent.TextDelta).text)
    }

    // ---------- Anthropic ----------

    @Test
    fun `anthropic named events sequence`() {
        val acc = AnthropicEventAccumulator()
        val start = acc.onEvent(
            "message_start",
            parse("""{"message":{"usage":{"input_tokens":15}}}"""),
        )
        assertEquals(15L, (start as StreamEvent.Usage).usage.inputTokens)

        val blockStart = acc.onEvent(
            "content_block_start",
            parse("""{"index":0,"content_block":{"type":"tool_use","id":"tool_1","name":"search"}}"""),
        )
        assertEquals(StreamEvent.ToolCallStart(0, "tool_1", "search"), blockStart)

        val text = acc.onEvent(
            "content_block_delta",
            parse("""{"index":1,"delta":{"type":"text_delta","text":"hi"}}"""),
        )
        assertEquals("hi", (text as StreamEvent.TextDelta).text)

        val thinking = acc.onEvent(
            "content_block_delta",
            parse("""{"index":2,"delta":{"type":"thinking_delta","thinking":"hm"}}"""),
        )
        assertEquals("hm", (thinking as StreamEvent.ReasoningDelta).text)

        val finish = acc.onEvent(
            "message_delta",
            parse("""{"delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":7}}"""),
        )
        assertEquals("stop", (finish as StreamEvent.Finish).reason)
    }

    // ---------- Gemini ----------

    @Test
    fun `gemini text and thought parts`() {
        val acc = GeminiChunkAccumulator()
        val events = acc.onFrame(
            parse(
                """{"candidates":[{"content":{"parts":[{"text":"wait","thought":true},{"text":"Hello"}]}}]}""",
            ),
        )
        assertEquals(
            listOf(
                StreamEvent.ReasoningDelta("wait"),
                StreamEvent.TextDelta("Hello"),
            ),
            events,
        )
    }

    @Test
    fun `gemini function call arrives complete`() {
        val acc = GeminiChunkAccumulator()
        val events = acc.onFrame(
            parse(
                """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"f","args":{"a":1}}}]}}]}""",
            ),
        )
        assertEquals(StreamEvent.ToolCallStart(0, "call_0", "f"), events[0])
        assertTrue(events[1] is StreamEvent.ToolCallArgsDelta)
        assertEquals(StreamEvent.ToolCallEnd(0), events[2])
    }

    @Test
    fun `gemini usage metadata maps reasoning tokens`() {
        val acc = GeminiChunkAccumulator()
        val events = acc.onFrame(
            parse(
                """{"usageMetadata":{"promptTokenCount":5,"candidatesTokenCount":10,"thoughtsTokenCount":4,"totalTokenCount":19}}""",
            ),
        )
        val usage = events.filterIsInstance<StreamEvent.Usage>().single()
        assertEquals(5L, usage.usage.inputTokens)
        assertEquals(14L, usage.usage.outputTokens) // candidates + thoughts
        assertEquals(19L, usage.usage.totalTokens)
        assertEquals(4L, usage.usage.reasoningTokens)
    }

    @Test
    fun `gemini mid-stream error before content is retryable ApiError`() {
        val acc = GeminiChunkAccumulator()
        val events = acc.onFrame(
            parse("""{"error":{"code":503,"message":"UNAVAILABLE","status":"UNAVAILABLE"}}"""),
        )
        val error = events.filterIsInstance<StreamEvent.Error>().single()
        assertTrue(error.error is ApiError)
    }

    @Test
    fun `gemini mid-stream error after content is MidStreamApiError`() {
        val acc = GeminiChunkAccumulator()
        acc.onFrame(parse("""{"candidates":[{"content":{"parts":[{"text":"partial"}]}}]}"""))
        val events = acc.onFrame(
            parse("""{"error":{"code":503,"message":"UNAVAILABLE","status":"UNAVAILABLE"}}"""),
        )
        val error = events.filterIsInstance<StreamEvent.Error>().single()
        assertTrue(error.error is MidStreamApiError)
    }

    @Test
    fun `gemini finish reason STOP maps to stop`() {
        val acc = GeminiChunkAccumulator()
        val events = acc.onFrame(
            parse("""{"candidates":[{"finishReason":"STOP"}]}"""),
        )
        assertEquals("stop", (events[0] as StreamEvent.Finish).reason)
    }
}
