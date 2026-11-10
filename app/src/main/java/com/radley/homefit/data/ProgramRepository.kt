package com.radley.homefit.data

import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.Program
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.data.db.ProgramProgressEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ProgramStatus(
    val program: Program,
    val level: Level,
    val completedDays: Int,
    val started: Boolean,
    val timesCompleted: Int,
) {
    /** The next day to do; one past the end once the program is finished. */
    val currentDay: Int get() = completedDays + 1
    val finished: Boolean get() = completedDays >= program.days
    val percent: Int get() = completedDays * 100 / program.days
}

/** Challenge and recovery-plan progress. Days are unlocked strictly in order. */
class ProgramRepository(private val db: HomeFitDatabase) {

    val progress: Flow<Map<String, ProgramProgressEntity>> =
        db.programProgress().observeAll().map { rows -> rows.associateBy { it.programId } }

    fun status(program: Program, row: ProgramProgressEntity?) = ProgramStatus(
        program = program,
        level = row?.level?.let { runCatching { Level.valueOf(it) }.getOrNull() } ?: program.availableLevels.first(),
        completedDays = row?.completedDays ?: 0,
        started = row != null,
        timesCompleted = row?.timesCompleted ?: 0,
    )

    suspend fun setLevel(programId: String, level: Level) {
        val existing = db.programProgress().byId(programId)
        db.programProgress().upsert(
            existing?.copy(level = level.name)
                ?: ProgramProgressEntity(programId, level.name, completedDays = 0, startedAt = System.currentTimeMillis()),
        )
    }

    /**
     * Marks [day] done if it's the next one due. Replaying an earlier day (allowed, for practice)
     * doesn't move progress, and completing the final day bumps the completion count so a
     * restart doesn't erase the achievement.
     */
    suspend fun completeDay(program: Program, day: Int) {
        val now = System.currentTimeMillis()
        val existing = db.programProgress().byId(program.id)
            ?: ProgramProgressEntity(program.id, program.availableLevels.first().name, 0, now)
        if (day != existing.completedDays + 1) return
        val finishing = day == program.days
        db.programProgress().upsert(
            existing.copy(
                completedDays = day,
                lastCompletedAt = now,
                timesCompleted = existing.timesCompleted + if (finishing) 1 else 0,
            ),
        )
    }

    suspend fun restart(programId: String) {
        val existing = db.programProgress().byId(programId) ?: return
        db.programProgress().upsert(
            existing.copy(completedDays = 0, startedAt = System.currentTimeMillis(), lastCompletedAt = null),
        )
    }
}
