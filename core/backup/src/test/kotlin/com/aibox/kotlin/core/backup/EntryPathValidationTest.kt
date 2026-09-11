package com.aibox.kotlin.core.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** ZIP 条目路径安全校验测试（防目录穿越）。 */
class EntryPathValidationTest {

    @Test
    fun `normal relative paths pass`() {
        assertTrue(validateEntryPath("manifest.json"))
        assertTrue(validateEntryPath("sessions/abc/session.json"))
        assertTrue(validateEntryPath("resources/resource-000001.png"))
    }

    @Test
    fun `absolute paths rejected`() {
        assertFalse(validateEntryPath("/etc/passwd"))
        assertFalse(validateEntryPath("\\windows\\system32"))
    }

    @Test
    fun `traversal rejected`() {
        assertFalse(validateEntryPath("../escape"))
        assertFalse(validateEntryPath("sessions/../../escape"))
        assertFalse(validateEntryPath("sessions/./weird"))
    }

    @Test
    fun `drive letters and schemes rejected`() {
        assertFalse(validateEntryPath("C:/evil"))
        assertFalse(validateEntryPath("http://evil"))
    }

    @Test
    fun `blank and empty segments rejected`() {
        assertFalse(validateEntryPath(""))
        assertFalse(validateEntryPath("a//b"))
    }
}
