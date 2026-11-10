package com.radley.homefit.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

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
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = HomeFitDatabase.DropCoachChat::class)],
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

    /** Version 2: the coach moved to the Claude app, so its in-app chat history went with it. */
    @DeleteTable(tableName = "coach_messages")
    class DropCoachChat : AutoMigrationSpec

    companion object {
        const val NAME = "homefit.db"

        fun create(context: Context): HomeFitDatabase =
            Room.databaseBuilder(context, HomeFitDatabase::class.java, NAME).build()
    }
}
