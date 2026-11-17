package org.avmedia.gshockGoogleSync

import org.avmedia.gshockGoogleSync.ui.settings.RideEmoji
import org.junit.Assert.assertEquals
import org.junit.Test

class RideEmojiTest {
    @Test fun emojiCountsAsOne() = assertEquals(3, RideEmoji.graphemes("🚲🦄🏍️").size)
    @Test fun familyEmojiCountsAsOne() = assertEquals(1, RideEmoji.graphemes("👨‍👩‍👧").size)
    @Test fun limitsToFourteen() = assertEquals("abcdefghijklmn", RideEmoji.limit("abcdefghijklmnop"))
    @Test fun neverCutsAnEmojiInHalf() = assertEquals("🚲".repeat(14), RideEmoji.limit("🚲".repeat(20)))
}
