package com.radley.homefit.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.domain.Feedback
import com.radley.homefit.domain.SwapDirection
import com.radley.homefit.domain.WorkoutRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The repositories against a real (in-memory) Room database and the real bundled content.
 * Settings are only read, never written, so running this on a personal phone leaves the
 * installed app's profile alone.
 */
@RunWith(AndroidJUnit4::class)
class RepositoriesTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: HomeFitDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var programs: ProgramRepository
    private lateinit var history: HistoryRepository
    private val content = ContentRepository(context)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HomeFitDatabase::class.java).build()
        workouts = WorkoutRepository(content, db, SettingsStore(context))
        programs = ProgramRepository(db)
        history = HistoryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun programDaysScaleWithTheWeek() = runBlocking {
        val day1 = workouts.resolve(WorkoutRef.ProgramDay("full_body_28", 1))!!
        val day9 = workouts.resolve(WorkoutRef.ProgramDay("full_body_28", 9))!!
        val squats1 = day1.steps.first { it.exercise.id == "squats" }.measure as Measure.Reps
        val template = content.get().workouts.getValue("fb28_beginner_a")
        val base = template.items.first { it.exerciseId == "squats" }.measure as Measure.Reps
        assertEquals(base.count, squats1.count)
        // Day 9 is in week two (×1.15) and, with rest on day 4 and 8, the seventh training day: template A again.
        val squats9 = day9.steps.first { it.exercise.id == "squats" }.measure as Measure.Reps
        assertEquals(Measure.Reps(base.count).scaled(1.15), squats9)
    }

    @Test
    fun restDaysDoNotResolve() = runBlocking {
        assertEquals(null, workouts.resolve(WorkoutRef.ProgramDay("full_body_28", 4)))
    }

    @Test
    fun twoTooHardsSwapInEasierMoves() = runBlocking {
        val ref = WorkoutRef.Builtin("chest_intermediate")
        val before = workouts.resolve(ref)!!.steps.map { it.exercise.id }
        val pushUpAt = before.indexOf("push_ups")
        assertTrue(pushUpAt >= 0)
        assertEquals(null, workouts.applyFeedback(ref, Feedback.TOO_HARD))
        assertEquals(SwapDirection.EASIER, workouts.applyFeedback(ref, Feedback.TOO_HARD))
        val after = workouts.resolve(ref)!!
        // Each move steps down one variant: push-ups become knee push-ups (and decline push-ups
        // become plain push-ups, so compare position by position rather than by presence).
        assertEquals("knee_push_ups", after.steps[pushUpAt].exercise.id)
        assertEquals("push_ups", after.steps[before.indexOf("decline_push_ups")].exercise.id)
        assertTrue(after.isEdited)
        assertTrue(after.difficultyMultiplier < 1.0)

        workouts.resetEdits(ref)
        val reset = workouts.resolve(ref)!!
        assertTrue(reset.steps.any { it.exercise.id == "push_ups" })
        assertEquals(1.0, reset.difficultyMultiplier, 0.0)
    }

    @Test
    fun editsPersistAndReset() = runBlocking {
        val ref = WorkoutRef.Builtin("abs_beginner")
        val edited = listOf(WorkoutItem("plank", Measure.Timed(45)), WorkoutItem("crunches", Measure.Reps(20)))
        workouts.saveEdits(ref, edited)
        val resolved = workouts.resolve(ref)!!
        assertEquals(listOf("plank", "crunches"), resolved.steps.map { it.exercise.id })
        assertTrue(resolved.isEdited)
        workouts.resetEdits(ref)
        assertFalse(workouts.resolve(ref)!!.isEdited)
    }

    @Test
    fun customWorkoutsRoundTrip() = runBlocking {
        val id = workouts.createCustom("Mine", listOf(WorkoutItem("squats", Measure.Reps(10))))
        val resolved = workouts.resolve(WorkoutRef.Custom(id))
        assertNotNull(resolved)
        assertEquals("Mine", resolved!!.title)
        workouts.deleteCustom(id)
        assertEquals(null, workouts.resolve(WorkoutRef.Custom(id)))
    }

    @Test
    fun programDaysUnlockInOrderAndCountCompletions() = runBlocking {
        val program = content.get().programs.getValue("plank_30")
        programs.completeDay(program, 2)
        assertEquals(null, db.programProgress().byId(program.id))
        for (day in 1..program.days) programs.completeDay(program, day)
        val row = db.programProgress().byId(program.id)!!
        assertEquals(program.days, row.completedDays)
        assertEquals(1, row.timesCompleted)
        programs.restart(program.id)
        assertEquals(0, db.programProgress().byId(program.id)!!.completedDays)
        assertEquals(1, db.programProgress().byId(program.id)!!.timesCompleted)
    }

    @Test
    fun firstSessionEarnsTheFirstBadge() = runBlocking {
        history.record(
            SessionEntity(
                startedAt = System.currentTimeMillis(), endedAt = System.currentTimeMillis() + 600_000,
                activeSeconds = 400, totalSeconds = 600, kcal = 55.0, refKey = "w:abs_beginner",
                title = "Abs Beginner", category = "FOCUS", focus = "ABS", exercisesCompleted = 10, exercisesTotal = 11,
            ),
        )
        val earned = history.evaluateAchievements().map { it.id }
        assertTrue("first_workout" in earned)
        assertTrue(history.evaluateAchievements().isEmpty())
        assertEquals(1, history.workoutDays.first().size)
    }
}
