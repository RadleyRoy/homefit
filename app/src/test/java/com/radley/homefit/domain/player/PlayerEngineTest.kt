package com.radley.homefit.domain.player

import com.radley.homefit.data.content.Measure
import com.radley.homefit.domain.ResolvedStep
import com.radley.homefit.testing.exercise
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class PlayerEngineTest {

    private val plank = ResolvedStep(exercise("plank"), Measure.Timed(30))
    private val squats = ResolvedStep(exercise("squats"), Measure.Reps(12))
    private val sidePlank = ResolvedStep(exercise("side_plank", perSide = true), Measure.Timed(10))

    private fun engine(vararg steps: ResolvedStep) = PlayerEngine(steps.toList(), readyMs = 10_000, restMs = 20_000)

    @Test
    fun `starts with get-ready and announces the first exercise`() {
        val e = engine(plank, squats)
        assertEquals(Phase.Ready(0, 10_000, 10_000), e.snapshot.phase)
        assertEquals(listOf<Cue>(Cue.GetReady(plank)), e.drainCues())
    }

    @Test
    fun `timed exercise runs to completion then rests before the next`() {
        val e = engine(plank, squats)
        e.advance(10_000)
        assertEquals(Phase.Work(0, 0), e.snapshot.phase)
        e.advance(30_000)
        assertEquals(Phase.Rest(1, 20_000, 20_000), e.snapshot.phase)
        assertEquals(setOf(0), e.snapshot.completed)
        assertEquals(30_000, e.snapshot.activeMs)
    }

    @Test
    fun `rep exercise waits for Done`() {
        val e = engine(squats, plank)
        e.advance(10_000)
        e.advance(120_000)
        assertEquals(Phase.Work(0, 120_000), e.snapshot.phase)
        e.complete()
        assertIs<Phase.Rest>(e.snapshot.phase)
        assertEquals(setOf(0), e.snapshot.completed)
    }

    @Test
    fun `one large delta walks through every boundary it covers`() {
        val e = engine(plank, plank, plank)
        e.drainCues()
        // ready 10 + work 30 + rest 20 + work 30 + rest 20 + work 30
        e.advance(140_000)
        assertEquals(Phase.Finished, e.snapshot.phase)
        assertEquals(setOf(0, 1, 2), e.snapshot.completed)
        assertEquals(140_000, e.snapshot.elapsedMs)
        val cues = e.drainCues()
        assertEquals(3, cues.count { it is Cue.Start })
        assertEquals(2, cues.count { it is Cue.RestStarted })
        assertEquals(Cue.Finished, cues.last())
    }

    @Test
    fun `paused time is ignored`() {
        val e = engine(plank, squats)
        e.advance(10_000)
        e.pause()
        e.advance(60_000)
        assertEquals(Phase.Work(0, 0), e.snapshot.phase)
        assertEquals(10_000, e.snapshot.elapsedMs)
        e.resume()
        e.advance(5_000)
        assertEquals(Phase.Work(0, 5_000), e.snapshot.phase)
    }

    @Test
    fun `skip leaves an exercise uncounted and cuts rest short`() {
        val e = engine(plank, squats)
        e.skip() // get-ready
        e.skip() // plank
        assertEquals(Phase.Rest(1, 20_000, 20_000), e.snapshot.phase)
        assertTrue(e.snapshot.completed.isEmpty())
        e.skip()
        assertEquals(Phase.Work(1, 0), e.snapshot.phase)
    }

    @Test
    fun `extend rest adds time and previous replays the last exercise`() {
        val e = engine(plank, squats)
        e.advance(40_000)
        e.extendRest(20_000)
        assertEquals(Phase.Rest(1, 40_000, 40_000), e.snapshot.phase)
        e.previous()
        assertEquals(Phase.Work(0, 0), e.snapshot.phase)
    }

    @Test
    fun `countdown cues fire for the last three seconds of a rest`() {
        val e = engine(plank, squats)
        e.advance(40_000)
        e.drainCues()
        repeat(200) { e.advance(100) }
        val numbers = e.drainCues().filterIsInstance<Cue.Countdown>().map { it.number }
        assertEquals(listOf(3, 2, 1), numbers)
    }

    @Test
    fun `per-side holds run twice as long and call the switch`() {
        val e = engine(sidePlank, squats)
        e.advance(10_000)
        e.drainCues()
        e.advance(10_000)
        assertTrue(Cue.SwitchSides in e.drainCues())
        assertEquals(Phase.Work(0, 10_000), e.snapshot.phase)
        e.advance(10_000)
        assertIs<Phase.Rest>(e.snapshot.phase)
    }

    @Test
    fun `halfway is called only on holds long enough for it`() {
        val e = engine(plank, ResolvedStep(exercise("short"), Measure.Timed(10)))
        e.advance(10_000)
        e.drainCues()
        e.advance(15_000)
        assertTrue(Cue.Halfway in e.drainCues())
        e.advance(15_000 + 20_000)
        e.drainCues()
        e.advance(5_000)
        assertTrue(Cue.Halfway !in e.drainCues())
    }

    @Test
    fun `zero rest flows straight into the next interval and names it`() {
        val walk = ResolvedStep(exercise("walk"), Measure.Timed(60))
        val run = ResolvedStep(exercise("run"), Measure.Timed(30))
        val e = PlayerEngine(listOf(walk, run), readyMs = 5_000, restMs = 0)
        e.advance(5_000)
        e.drainCues()
        e.advance(60_000)
        assertEquals(Phase.Work(1, 0), e.snapshot.phase)
        val cues = e.drainCues()
        assertTrue(cues.none { it is Cue.RestStarted })
        assertTrue(Cue.Start(run, announce = true) in cues)
    }

    @Test
    fun `finishing the last exercise ends the workout`() {
        val e = engine(squats)
        e.skip()
        e.complete()
        assertEquals(Phase.Finished, e.snapshot.phase)
        assertTrue(Cue.Finished in e.drainCues())
    }
}
