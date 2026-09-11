package com.aibox.kotlin.feature.chat

import com.aibox.kotlin.core.common.AppLanguage
import com.aibox.kotlin.core.common.I18n
import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageRole
import com.aibox.kotlin.core.model.Settings
import com.aibox.kotlin.core.model.TokenUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [MessageDisplayOptions] / [MessageMetadata] 纯逻辑单测（JVM）。
 *
 * 验证「按 Settings 开关独立显示元信息」与缺省语义（Markdown/头像默认开，
 * 其余默认关；空设置下不产生任何元信息）。断言固定使用英文词条，避免测试机 Locale 影响。
 */
class MessageDisplayOptionsTest {

    @Before
    fun pinEnglish() {
        I18n.language = AppLanguage.ENGLISH
    }

    @Test
    fun `defaults enable markdown and avatar only`() {
        val options = MessageDisplayOptions.from(Settings())

        assertTrue(options.markdownEnabled)
        assertTrue(options.showAvatar)
        assertFalse(options.showWordCount)
        assertFalse(options.showTokenCount)
        assertFalse(options.showUsedToken)
        assertFalse(options.showModelName)
        assertFalse(options.showMessageTimestamp)
        assertFalse(options.showFirstTokenLatency)
        assertFalse(options.showAnyMeta)
    }

    @Test
    fun `settings toggles are reflected independently`() {
        val settings = Settings(
            enableMarkdownRendering = false,
            showWordCount = true,
            showModelName = true,
            showFirstTokenLatency = true,
            showAvatar = false,
        )
        val options = MessageDisplayOptions.from(settings)

        assertFalse(options.markdownEnabled)
        assertFalse(options.showAvatar)
        assertTrue(options.showWordCount)
        assertTrue(options.showModelName)
        assertTrue(options.showFirstTokenLatency)
        assertFalse(options.showTokenCount)
        assertTrue(options.showAnyMeta)
    }

    @Test
    fun `meta fields are empty when nothing enabled`() {
        val message = Message(
            id = "m1",
            role = MessageRole.ASSISTANT,
            model = "gpt-4o",
            wordCount = 12,
            tokenCount = 8,
            timestamp = 0,
        )
        assertTrue(MessageMetadata.fields(message, MessageDisplayOptions.Default).isEmpty())
    }

    @Test
    fun `meta fields respect each toggle`() {
        val message = Message(
            id = "m1",
            role = MessageRole.ASSISTANT,
            model = "gpt-4o",
            wordCount = 12,
            tokenCount = 8,
            usage = TokenUsage(totalTokens = 100),
            firstTokenLatency = 350,
        )
        val fields = MessageMetadata.fields(
            message,
            MessageDisplayOptions(
                showWordCount = true,
                showTokenCount = true,
                showUsedToken = true,
                showModelName = true,
                showFirstTokenLatency = true,
            ),
        )

        assertEquals(
            listOf("gpt-4o", "12 words", "8 tokens", "100 used", "350 ms"),
            fields,
        )
    }

    @Test
    fun `token count falls back to usage output tokens`() {
        val message = Message(
            id = "m2",
            role = MessageRole.ASSISTANT,
            usage = TokenUsage(outputTokens = 42),
        )
        val fields = MessageMetadata.fields(message, MessageDisplayOptions(showTokenCount = true))
        assertEquals(listOf("42 tokens"), fields)
    }

    @Test
    fun `timestamp formats as hours and minutes`() {
        val formatted = MessageMetadata.formatTimestamp(0L)
        assertTrue("unexpected timestamp format: $formatted", Regex("""\d{2}:\d{2}""").matches(formatted))
    }

    @Test
    fun `unknown model name is skipped`() {
        val message = Message(id = "m3", role = MessageRole.ASSISTANT, model = "   ")
        val fields = MessageMetadata.fields(message, MessageDisplayOptions(showModelName = true))
        assertTrue(fields.isEmpty())
    }

    @Test
    fun `image content part is excluded from text parts`() {
        val message = Message(
            id = "m4",
            role = MessageRole.USER,
            contentParts = listOf(
                MessageContentPart.Text("hi"),
                MessageContentPart.Image(storageKey = "a.png"),
            ),
        )
        assertEquals(
            listOf(MessageContentPart.Text("hi")),
            message.contentParts.filterIsInstance<MessageContentPart.Text>(),
        )
        assertNull(message.usage)
    }
}
