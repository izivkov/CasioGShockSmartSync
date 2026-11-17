package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.h5600.dropIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDropIndexTest {
    private val h = listOf(200, 200, 200, 200)
    private val gap = 20f

    @Test fun smallDragStaysPut() = assertEquals(1, dropIndex(1, 80f, h, gap))
    @Test fun pastHalfOfNextMovesDownOne() = assertEquals(2, dropIndex(1, 120f, h, gap))
    @Test fun twoCardsDown() = assertEquals(3, dropIndex(1, 360f, h, gap))
    @Test fun clampedAtBottom() = assertEquals(3, dropIndex(1, 5000f, h, gap))
    @Test fun upOne() = assertEquals(1, dropIndex(2, -120f, h, gap))
    @Test fun clampedAtTop() = assertEquals(0, dropIndex(2, -5000f, h, gap))
}
