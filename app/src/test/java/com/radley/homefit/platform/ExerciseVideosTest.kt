package com.radley.homefit.platform

import kotlin.test.assertEquals
import org.junit.Test

class ExerciseVideosTest {

    @Test
    fun `search link holds only the encoded exercise name`() {
        assertEquals(
            "https://www.youtube.com/results?search_query=how+to+do+Push-Ups+%26+Hold+exercise+proper+form",
            ExerciseVideos.searchUrl("Push-Ups & Hold"),
        )
    }
}
