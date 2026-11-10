package com.radley.homefit.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startedAt >= :from AND startedAt < :until ORDER BY startedAt DESC")
    fun observeBetween(from: Long, until: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY startedAt ASC")
    suspend fun all(): List<SessionEntity>

    @Query("UPDATE sessions SET feedback = :feedback WHERE id = :id")
    suspend fun setFeedback(id: Long, feedback: String)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("DELETE FROM sessions")
    suspend fun clear()
}

@Dao
interface WeightDao {
    @Upsert
    suspend fun upsert(entry: WeightEntity)

    @Query("SELECT * FROM weights ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY epochDay DESC LIMIT 1")
    suspend fun latest(): WeightEntity?

    @Query("SELECT * FROM weights ORDER BY epochDay ASC")
    suspend fun all(): List<WeightEntity>

    @Query("DELETE FROM weights WHERE epochDay = :epochDay")
    suspend fun delete(epochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<WeightEntity>)

    @Query("DELETE FROM weights")
    suspend fun clear()
}

@Dao
interface ProgramProgressDao {
    @Upsert
    suspend fun upsert(progress: ProgramProgressEntity)

    @Query("SELECT * FROM program_progress")
    fun observeAll(): Flow<List<ProgramProgressEntity>>

    @Query("SELECT * FROM program_progress WHERE programId = :programId")
    suspend fun byId(programId: String): ProgramProgressEntity?

    @Query("SELECT * FROM program_progress")
    suspend fun all(): List<ProgramProgressEntity>

    @Query("DELETE FROM program_progress WHERE programId = :programId")
    suspend fun delete(programId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ProgramProgressEntity>)

    @Query("DELETE FROM program_progress")
    suspend fun clear()
}

@Dao
interface CustomWorkoutDao {
    @Upsert
    suspend fun upsert(workout: CustomWorkoutEntity)

    @Query("SELECT * FROM custom_workouts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CustomWorkoutEntity>>

    @Query("SELECT * FROM custom_workouts WHERE id = :id")
    suspend fun byId(id: String): CustomWorkoutEntity?

    @Query("SELECT * FROM custom_workouts")
    suspend fun all(): List<CustomWorkoutEntity>

    @Query("DELETE FROM custom_workouts WHERE id = :id")
    suspend fun delete(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<CustomWorkoutEntity>)

    @Query("DELETE FROM custom_workouts")
    suspend fun clear()
}

@Dao
interface WorkoutOverrideDao {
    @Upsert
    suspend fun upsert(override: WorkoutOverrideEntity)

    @Query("SELECT * FROM workout_overrides WHERE refKey = :refKey")
    suspend fun byKey(refKey: String): WorkoutOverrideEntity?

    @Query("SELECT * FROM workout_overrides WHERE refKey = :refKey")
    fun observe(refKey: String): Flow<WorkoutOverrideEntity?>

    @Query("SELECT * FROM workout_overrides")
    suspend fun all(): List<WorkoutOverrideEntity>

    @Query("DELETE FROM workout_overrides WHERE refKey = :refKey")
    suspend fun delete(refKey: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<WorkoutOverrideEntity>)

    @Query("DELETE FROM workout_overrides")
    suspend fun clear()
}

@Dao
interface DifficultyDao {
    @Upsert
    suspend fun upsert(state: DifficultyEntity)

    @Query("SELECT * FROM difficulty WHERE `key` = :key")
    suspend fun byKey(key: String): DifficultyEntity?

    @Query("SELECT * FROM difficulty WHERE `key` = :key")
    fun observe(key: String): Flow<DifficultyEntity?>

    @Query("SELECT * FROM difficulty")
    suspend fun all(): List<DifficultyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<DifficultyEntity>)

    @Query("DELETE FROM difficulty WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM difficulty")
    suspend fun clear()
}

@Dao
interface FavoriteDao {
    @Upsert
    suspend fun upsert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE refKey = :refKey")
    suspend fun delete(refKey: String)

    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites")
    suspend fun all(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<FavoriteEntity>)

    @Query("DELETE FROM favorites")
    suspend fun clear()
}

@Dao
interface ReminderDao {
    @Upsert
    suspend fun upsert(reminder: ReminderEntity): Long

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders ORDER BY hour, minute")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun all(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ReminderEntity>)

    @Query("DELETE FROM reminders")
    suspend fun clear()
}

@Dao
interface AchievementDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rows: List<AchievementEntity>)

    @Query("SELECT * FROM achievements")
    fun observeAll(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun all(): List<AchievementEntity>

    @Query("DELETE FROM achievements")
    suspend fun clear()
}
