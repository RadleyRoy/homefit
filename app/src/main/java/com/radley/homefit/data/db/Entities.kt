package com.radley.homefit.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * One finished (or abandoned-with-progress) workout. [refKey] is a [com.radley.homefit.domain.WorkoutRef]
 * encoding, so a session can always be traced back to what was played and replayed from history.
 */
@Serializable
@Entity(tableName = "sessions", indices = [Index("startedAt")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long,
    /** Seconds spent in exercises; what calories are computed from. */
    val activeSeconds: Int,
    /** Wall time excluding pauses, rests included. What the user thinks of as "duration". */
    val totalSeconds: Int,
    val kcal: Double,
    val refKey: String,
    val title: String,
    val category: String,
    val focus: String?,
    val exercisesCompleted: Int,
    val exercisesTotal: Int,
    val feedback: String? = null,
)

@Serializable
@Entity(tableName = "weights")
data class WeightEntity(
    /** One entry per day; logging twice in a day replaces the earlier value. */
    @PrimaryKey val epochDay: Long,
    val kg: Double,
)

@Serializable
@Entity(tableName = "program_progress")
data class ProgramProgressEntity(
    @PrimaryKey val programId: String,
    val level: String,
    /** Days are done strictly in order, so a count is enough: day N is unlocked when N-1 is done. */
    val completedDays: Int,
    val startedAt: Long,
    val lastCompletedAt: Long? = null,
    /** How many times the whole program has been finished; drives the "repeat" badge. */
    val timesCompleted: Int = 0,
)

@Serializable
@Entity(tableName = "custom_workouts")
data class CustomWorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** [com.radley.homefit.data.content.ContentParser.encodeItems] format. */
    val items: String,
    val restSeconds: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    /** "coach" for LLM-generated workouts the user chose to keep; null for hand-built ones. */
    val origin: String? = null,
)

/** The user's edits to a built-in workout or a program day, keyed by the ref they apply to. */
@Serializable
@Entity(tableName = "workout_overrides")
data class WorkoutOverrideEntity(
    @PrimaryKey val refKey: String,
    val items: String,
    val updatedAt: Long,
)

/** Adaptive difficulty state, keyed by [com.radley.homefit.domain.WorkoutRef.difficultyKey]. */
@Serializable
@Entity(tableName = "difficulty")
data class DifficultyEntity(
    @PrimaryKey val key: String,
    val multiplier: Double,
    val hardStreak: Int,
    val easyStreak: Int,
    /**
     * Exercise substitutions made after repeated feedback, as `from>to;from>to`. Stored here
     * rather than as an edit so they follow a whole program, not just the day that triggered them.
     */
    val swaps: String = "",
)

@Serializable
@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val refKey: String,
    val addedAt: Long,
)

@Serializable
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Bit 0 = Monday … bit 6 = Sunday, matching [java.time.DayOfWeek.ordinal]. */
    val daysMask: Int,
    val enabled: Boolean,
)

@Serializable
@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Long,
)
