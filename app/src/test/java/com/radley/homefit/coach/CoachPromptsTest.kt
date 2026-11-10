package com.radley.homefit.coach

import com.radley.homefit.data.Profile
import com.radley.homefit.data.Sex
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.domain.Feedback
import com.radley.homefit.testing.bundledContent
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class CoachPromptsTest {

    private val content = bundledContent
    private val today = LocalDate.of(2026, 11, 10)

    private fun context(profile: Profile = Profile(sex = Sex.FEMALE, birthYear = 1990, heightCm = 165.0)) = CoachContext(
        profile = profile,
        units = UnitSystem.METRIC,
        today = today,
        latestWeightKg = 62.5,
        restSeconds = 25,
        workoutsThisWeek = 1,
        streakDays = 2,
        recent = listOf(RecentSession(today.minusDays(1), "Abs Beginner", 12, Feedback.TOO_EASY)),
    )

    @Test
    fun `workout prompt carries the profile, history and the block format`() {
        val prompt = CoachPrompts.workout(context(), content, WorkoutRequest(20, FocusArea.LEGS, emptySet(), "sore shoulder"))
        assertTrue("36 years old" in prompt)
        assertTrue("62.5 kg" in prompt)
        assertTrue("Abs Beginner, 12 min, felt too easy" in prompt)
        assertTrue("About 20 minutes" in prompt)
        assertTrue("sore shoulder" in prompt)
        assertTrue("```${CoachPrompts.BLOCK_TAG}" in prompt)
        assertTrue("rest: 25" in prompt)
    }

    @Test
    fun `the catalog lists only moves for the equipment on hand`() {
        val bodyweight = CoachPrompts.workout(context(), content, WorkoutRequest(15, null, emptySet(), ""))
        assertTrue("\nsquats: Squats, reps" in bodyweight)
        assertFalse("\nwall_sit:" in bodyweight)
        val withWall = CoachPrompts.workout(context(), content, WorkoutRequest(15, null, setOf(Equipment.WALL), ""))
        assertTrue("\nwall_sit: Wall Sit, timed, wall" in withWall)
    }

    @Test
    fun `the format example in the prompt imports cleanly`() {
        val example = CoachImport.parse(CoachPrompts.workout(context(), content, WorkoutRequest(15, null, emptySet(), "")), content)
        assertEquals(1, example.size)
        assertTrue(example.single().skipped.isEmpty())
    }

    @Test
    fun `quiet mode asks for no jumping`() {
        val quiet = CoachPrompts.week(context(Profile(quietMode = true)), content)
        assertTrue("No jumping" in quiet)
        assertTrue("skip anything marked jumping" in quiet)
        assertFalse("No jumping" in CoachPrompts.week(context(), content))
    }

    @Test
    fun `a plain question gets no catalog`() {
        val prompt = CoachPrompts.ask(context(), "Should I train when sore?")
        assertTrue("Should I train when sore?" in prompt)
        assertFalse("Exercise catalog" in prompt)
    }
}
