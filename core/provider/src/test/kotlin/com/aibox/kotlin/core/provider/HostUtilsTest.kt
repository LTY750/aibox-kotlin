package com.aibox.kotlin.core.provider

import org.junit.Assert.assertEquals
import org.junit.Test

/** 主机归一化契约测试（对齐旧版 llm_utils 行为）。 */
class HostUtilsTest {

    @Test
    fun `openai bare host gets v1 path`() {
        val (base, path) = HostUtils.normalizeOpenAi("api.example.com")
        assertEquals("https://api.example.com", base)
        assertEquals("/v1", path)
    }

    @Test
    fun `openai host with v1 path preserved`() {
        val (base, path) = HostUtils.normalizeOpenAi("https://api.example.com/v1")
        assertEquals("https://api.example.com", base)
        assertEquals("/v1", path)
    }

    @Test
    fun `openai versioned path v3 not rewritten`() {
        val (base, path) = HostUtils.normalizeOpenAi("https://ark.cn-beijing.volces.com/api/v3")
        assertEquals("https://ark.cn-beijing.volces.com", base)
        assertEquals("/api/v3", path)
    }

    @Test
    fun `openai null host defaults to openai`() {
        val (base, path) = HostUtils.normalizeOpenAi(null)
        assertEquals("https://api.openai.com", base)
        assertEquals("/v1", path)
    }

    @Test
    fun `openai api openai com bare gets v1`() {
        val (base, path) = HostUtils.normalizeOpenAi("https://api.openai.com")
        assertEquals("https://api.openai.com", base)
        assertEquals("/v1", path)
    }

    @Test
    fun `anthropic default appends v1`() {
        assertEquals("https://api.anthropic.com/v1", HostUtils.normalizeAnthropic(null))
        assertEquals("https://api.anthropic.com/v1", HostUtils.normalizeAnthropic("https://api.anthropic.com"))
    }

    @Test
    fun `anthropic existing v1 not doubled`() {
        assertEquals("https://api.anthropic.com/v1", HostUtils.normalizeAnthropic("https://api.anthropic.com/v1"))
    }

    @Test
    fun `gemini default appends v1beta`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            HostUtils.normalizeGemini(null),
        )
    }

    @Test
    fun `gemini existing v1beta not doubled`() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta",
            HostUtils.normalizeGemini("https://generativelanguage.googleapis.com/v1beta"),
        )
    }

    @Test
    fun `models list base strips chat completions`() {
        assertEquals("https://api.example.com/v1/models", HostUtils.modelsListBase("https://api.example.com", "/v1/chat/completions"))
        assertEquals("https://api.example.com/v1/models", HostUtils.modelsListBase("https://api.example.com", "/v1"))
    }
}
