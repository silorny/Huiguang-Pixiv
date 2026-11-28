package moe.huiguang.pixiv.design

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {

    @Test
    fun `computeAdaptiveGridColumns scales across phone and tablet breakpoints`() {
        assertEquals(2, computeAdaptiveGridColumns(360.dp))
        assertEquals(2, computeAdaptiveGridColumns(412.dp))
        assertEquals(3, computeAdaptiveGridColumns(800.dp))
        assertEquals(3, computeAdaptiveGridColumns(920.dp))
        assertEquals(5, computeAdaptiveGridColumns(1280.dp))
        assertEquals(5, computeAdaptiveGridColumns(1600.dp))
    }
}
