package moe.huiguang.pixiv.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkLabelsTest {

    @Test
    fun `formatWorkDate normalizes iso and fallback dates to dotted yyyy MM dd`() {
        assertEquals("2026.11.24", formatWorkDate("2026-11-24T12:34:56+09:00"))
        assertEquals("2026.01.05", formatWorkDate("2026-01-05T00:00:00Z"))
        assertEquals("2026.03.09", formatWorkDate("2026/3/9"))
        assertEquals("", formatWorkDate(""))
        assertEquals("", formatWorkDate("invalid"))
    }
}
