package com.radley.homefit.data

import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.db.AchievementEntity
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.data.db.WeightEntity
import com.radley.homefit.domain.Achievement
import com.radley.homefit.domain.AchievementStats
import com.radley.homefit.domain.Achievements
import com.radley.homefit.domain.Calories
import com.radley.homefit.domain.Streaks
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Completed sessions, weigh-ins, and everything derived from them (streaks, badges). */
class HistoryRepository(
    private val db: HomeFitDatabase,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /** Told about weigh-ins the user enters, so Health Connect can mirror them. */
    private val onWeightLogged: suspend (epochDay: Long, kg: Double) -> Unit = { _, _ -> },
) {

    val sessions: Flow<List<SessionEntity>> = db.sessions().observeAll()
    val weights: Flow<List<WeightEntity>> = db.weights().observeAll()
    val unlockedAchievements: Flow<Map<String, Long>> =
        db.achievements().observeAll().map { rows -> rows.associate { it.id to it.unlockedAt } }

    /** Local calendar days with at least one workout, as epoch days. */
    val workoutDays: Flow<Set<Long>> = sessions.map { list -> list.map { localDay(it.startedAt) }.toSet() }

    fun localDay(epochMillis: Long): Long =
        Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate().toEpochDay()

    fun today(): LocalDate = LocalDate.now(zone())

    suspend fun record(session: SessionEntity): Long = db.sessions().insert(session)

    suspend fun session(id: Long): SessionEntity? = db.sessions().byId(id)

    suspend fun setFeedback(sessionId: Long, feedback: String) = db.sessions().setFeedback(sessionId, feedback)

    suspend fun deleteSession(id: Long) = db.sessions().delete(id)

    suspend fun logWeight(epochDay: Long, kg: Double) {
        storeWeight(epochDay, kg)
        runCatching { onWeightLogged(epochDay, kg) }
    }

    /** Stores without notifying; for values that came from Health Connect in the first place. */
    suspend fun storeWeight(epochDay: Long, kg: Double) = db.weights().upsert(WeightEntity(epochDay, kg))

    suspend fun deleteWeight(epochDay: Long) = db.weights().delete(epochDay)

    suspend fun latestWeightKg(): Double = db.weights().latest()?.kg ?: Calories.DEFAULT_WEIGHT_KG

    suspend fun hasWeight(): Boolean = db.weights().latest() != null

    suspend fun stats(): AchievementStats {
        val sessions = db.sessions().all()
        val days = sessions.map { localDay(it.startedAt) }.toSet()
        val hours = sessions.map { Instant.ofEpochMilli(it.startedAt).atZone(zone()).hour }
        return AchievementStats(
            totalWorkouts = sessions.size,
            currentStreak = Streaks.current(days, today().toEpochDay()),
            bestStreak = Streaks.best(days),
            totalKcal = sessions.sumOf { it.kcal },
            totalActiveMinutes = sessions.sumOf { it.activeSeconds } / 60,
            programsCompleted = db.programProgress().all().sumOf { it.timesCompleted },
            weightEntries = db.weights().all().size,
            focusAreasTrained = sessions.mapNotNull { s -> s.focus?.let { runCatching { FocusArea.valueOf(it) }.getOrNull() } }.toSet(),
            earlyBird = hours.any { it < 7 },
            nightOwl = hours.any { it >= 22 },
        )
    }

    /** Unlocks whatever the latest activity earned, and returns it for a celebration toast. */
    suspend fun evaluateAchievements(): List<Achievement> {
        val unlocked = db.achievements().all().map { it.id }.toSet()
        val earned = Achievements.newlyEarned(stats(), unlocked)
        if (earned.isNotEmpty()) {
            val now = System.currentTimeMillis()
            db.achievements().insertAll(earned.map { AchievementEntity(it.id, now) })
        }
        return earned
    }
}
