package com.radley.homefit.domain

import kotlin.test.assertEquals
import org.junit.Test

class WorkoutRefTest {

    @Test
    fun `every ref round-trips through its encoding`() {
        val refs = listOf(
            WorkoutRef.Builtin("abs_beginner"),
            WorkoutRef.Custom("3f2a-11"),
            WorkoutRef.ProgramDay("full_body_28", 12),
            WorkoutRef.PlanDay(20_400),
            WorkoutRef.CoachProposal(7),
        )
        for (ref in refs) assertEquals(ref, WorkoutRef.decode(ref.encode()))
    }

    @Test
    fun `program days share one difficulty key`() {
        assertEquals(WorkoutRef.ProgramDay("x", 1).difficultyKey, WorkoutRef.ProgramDay("x", 9).difficultyKey)
    }
}
