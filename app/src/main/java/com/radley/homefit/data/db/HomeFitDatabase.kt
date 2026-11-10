package com.radley.homefit.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        WeightEntity::class,
        ProgramProgressEntity::class,
        CustomWorkoutEntity::class,
        WorkoutOverrideEntity::class,
        DifficultyEntity::class,
        FavoriteEntity::class,
        ReminderEntity::class,
        AchievementEntity::class,
        CoachMessageEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HomeFitDatabase : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun weights(): WeightDao
    abstract fun programProgress(): ProgramProgressDao
    abstract fun customWorkouts(): CustomWorkoutDao
    abstract fun overrides(): WorkoutOverrideDao
    abstract fun difficulty(): DifficultyDao
    abstract fun favorites(): FavoriteDao
    abstract fun reminders(): ReminderDao
    abstract fun achievements(): AchievementDao
    abstract fun coachMessages(): CoachMessageDao

    companion object {
        const val NAME = "homefit.db"

        fun create(context: Context): HomeFitDatabase =
            Room.databaseBuilder(context, HomeFitDatabase::class.java, NAME).build()
    }
}
