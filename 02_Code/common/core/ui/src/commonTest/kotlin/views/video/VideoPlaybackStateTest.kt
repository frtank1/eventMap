package com.eventmap.views.video

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlaybackStateTest {
    @Test fun unknownDurationHasNoInventedProgress() {
        assertEquals(0f, VideoPlaybackState(10_000, 0).progress)
        assertEquals(0f, VideoPlaybackState(10_000, -1).progress)
    }
    @Test fun progressIsClampedToMediaBounds() {
        assertEquals(.5f, VideoPlaybackState(5_000, 10_000).progress)
        assertEquals(1f, VideoPlaybackState(20_000, 10_000).progress)
        assertEquals(0f, VideoPlaybackState(-1, 10_000).progress)
    }
    @Test fun timeLabelsUseTheActualDuration() {
        assertEquals("00:00", videoTimeLabel(-1))
        assertEquals("00:10", videoTimeLabel(10_900))
        assertEquals("01:05", videoTimeLabel(65_000))
    }
}
