package com.radley.homefit.domain

import com.radley.homefit.data.content.FocusArea
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class AchievementsTest {

    private val none = AchievementStats(0, 0, 0, 0.0, 0, 0, 0, emptySet(), earlyBird = false, nightOwl = false)

    @Test
    fun `first workout unlocks once`() {
        val stats = none.copy(totalWorkouts = 1)
        assertEquals(listOf("first_workout"), Achievements.newlyEarned(stats, emptySet()).map { it.id })
        assertTrue(Achievements.newlyEarned(stats, setOf("first_workout")).isEmpty())
    }

    @Test
    fun `all-rounder needs every focus area`() {
        val almost = none.copy(focusAreasTrained = FocusArea.entries.drop(1).toSet())
        assertTrue(Achievements.newlyEarned(almost, emptySet()).none { it.id == "all_focus" })
        val all = none.copy(focusAreasTrained = FocusArea.entries.toSet())
        assertTrue(Achievements.newlyEarned(all, emptySet()).any { it.id == "all_focus" })
    }

    @Test
    fun `ids are unique`() {
        assertEquals(Achievements.all.size, Achievements.all.map { it.id }.toSet().size)
    }
}
