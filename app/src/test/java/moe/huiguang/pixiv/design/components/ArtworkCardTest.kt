package moe.huiguang.pixiv.design.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkCardTest {

    @Test
    fun `downgradeWaterfallThumbUrl lowers heavy CDN presets and keeps compact or original suffixes intact`() {
        assertEquals(
            "https://i.pximg.net/c/240x480/img-master/img/301_p0_master1200.jpg",
            downgradeWaterfallThumbUrl("https://i.pximg.net/c/480x960/img-master/img/301_p0_master1200.jpg"),
        )
        assertEquals(
            "https://i.pximg.net/c/360x360_70/img-master/img/tag.jpg",
            downgradeWaterfallThumbUrl("https://i.pximg.net/c/540x540_70/img-master/img/tag.jpg"),
        )
        assertEquals(
            "https://i.pximg.net/c/360x360_70/custom-thumb/img/401_p0_custom1200.jpg",
            downgradeWaterfallThumbUrl("https://i.pximg.net/c/600x600/custom-thumb/img/401_p0_custom1200.jpg"),
        )
        assertEquals(
            "https://i.pximg.net/c/250x250_80_a2/img-master/img/200_p0_square1200.jpg",
            downgradeWaterfallThumbUrl("https://i.pximg.net/c/250x250_80_a2/img-master/img/200_p0_square1200.jpg"),
        )
    }
}
