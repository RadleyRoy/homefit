package com.radley.homefit.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.db.AchievementEntity
import com.radley.homefit.data.db.CoachMessageEntity
import com.radley.homefit.data.db.CustomWorkoutEntity
import com.radley.homefit.data.db.DifficultyEntity
import com.radley.homefit.data.db.FavoriteEntity
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.data.db.ProgramProgressEntity
import com.radley.homefit.data.db.ReminderEntity
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.data.db.WeightEntity
import com.radley.homefit.data.db.WorkoutOverrideEntity
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A single JSON file holding every table and setting, written to wherever the user picks in the
 * system file picker (Google Drive included). That replaces the original app's sign-in-to-sync:
 * no account, and the user can see exactly what's in it.
 *
 * The coach API key is deliberately not included; it's encrypted with a key that can't leave
 * this device, and a backup file is the last place it should end up in plain text.
 */
class BackupManager(
    private val context: Context,
    private val db: HomeFitDatabase,
    private val settings: SettingsStore,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            profile = ProfileDto.from(settings.currentProfile()),
            workoutPrefs = settings.currentWorkoutPrefs().let { WorkoutPrefsDto(it.restSeconds, it.countdownSeconds, it.voiceEnabled, it.coachTips, it.soundEffects, it.keepScreenOn) },
            appPrefs = settings.currentAppPrefs().let { AppPrefsDto(it.units.name, it.weekStart.name, it.planStartEpochDay, it.planSeed, it.coachModel) },
            sessions = db.sessions().all(),
            weights = db.weights().all(),
            programProgress = db.programProgress().all(),
            customWorkouts = db.customWorkouts().all(),
            overrides = db.overrides().all(),
            difficulty = db.difficulty().all(),
            favorites = db.favorites().all(),
            reminders = db.reminders().all(),
            achievements = db.achievements().all(),
            coachMessages = db.coachMessages().all(),
        )
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
            it.write(json.encodeToString(BackupFile.serializer(), file))
        } ?: error("Couldn't open the file for writing")
    }

    /** Replaces everything with the backup's contents. Returns the number of workouts restored. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Couldn't open the file")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.format == FORMAT) { "This isn't a HomeFit backup" }

        db.withTransaction {
            clearTables()
            db.sessions().insertAll(file.sessions)
            db.weights().insertAll(file.weights)
            db.programProgress().insertAll(file.programProgress)
            db.customWorkouts().insertAll(file.customWorkouts)
            db.overrides().insertAll(file.overrides)
            db.difficulty().insertAll(file.difficulty)
            db.favorites().insertAll(file.favorites)
            db.reminders().insertAll(file.reminders)
            db.achievements().insertAll(file.achievements)
            file.coachMessages.forEach { db.coachMessages().insert(it) }
        }
        settings.setProfile(file.profile.toModel())
        settings.updateWorkoutPrefs {
            WorkoutPrefs(file.workoutPrefs.restSeconds, file.workoutPrefs.countdownSeconds, file.workoutPrefs.voiceEnabled, file.workoutPrefs.coachTips, file.workoutPrefs.soundEffects, file.workoutPrefs.keepScreenOn)
        }
        settings.updateAppPrefs { current ->
            current.copy(
                units = enumOr(file.appPrefs.units, current.units),
                weekStart = enumOr(file.appPrefs.weekStart, current.weekStart),
                onboardingComplete = true,
                planStartEpochDay = file.appPrefs.planStartEpochDay,
                planSeed = file.appPrefs.planSeed,
                coachModel = file.appPrefs.coachModel,
            )
        }
        file.sessions.size
    }

    /** Training progress only: history, programs, badges, adaptive difficulty. Workouts and weights stay. */
    suspend fun resetProgress() = db.withTransaction {
        db.sessions().clear()
        db.programProgress().clear()
        db.achievements().clear()
        db.difficulty().clear()
    }

    suspend fun deleteEverything() {
        db.withTransaction { clearTables() }
        settings.clear()
    }

    private suspend fun clearTables() {
        db.sessions().clear()
        db.weights().clear()
        db.programProgress().clear()
        db.customWorkouts().clear()
        db.overrides().clear()
        db.difficulty().clear()
        db.favorites().clear()
        db.reminders().clear()
        db.achievements().clear()
        db.coachMessages().clear()
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String, fallback: E): E =
        runCatching { enumValueOf<E>(name) }.getOrDefault(fallback)

    companion object {
        const val FORMAT = "homefit-backup-1"
        const val MIME = "application/json"

        fun suggestedName(): String = "homefit-backup-${java.time.LocalDate.now()}.json"
    }
}

@Serializable
private data class BackupFile(
    val format: String = BackupManager.FORMAT,
    val exportedAt: Long,
    val profile: ProfileDto,
    val workoutPrefs: WorkoutPrefsDto,
    val appPrefs: AppPrefsDto,
    val sessions: List<SessionEntity>,
    val weights: List<WeightEntity>,
    val programProgress: List<ProgramProgressEntity>,
    val customWorkouts: List<CustomWorkoutEntity>,
    val overrides: List<WorkoutOverrideEntity>,
    val difficulty: List<DifficultyEntity>,
    val favorites: List<FavoriteEntity>,
    val reminders: List<ReminderEntity>,
    val achievements: List<AchievementEntity>,
    val coachMessages: List<CoachMessageEntity> = emptyList(),
)

@Serializable
private data class ProfileDto(
    val sex: String,
    val birthYear: Int?,
    val heightCm: Double?,
    val goal: String,
    val level: String,
    val focusAreas: List<String>,
    val weeklyGoal: Int,
    val trainingDays: List<String>,
    val minutesPerSession: Int,
    val targetWeightKg: Double?,
    val quietMode: Boolean,
) {
    fun toModel(): Profile {
        val d = Profile()
        return Profile(
            sex = runCatching { Sex.valueOf(sex) }.getOrDefault(d.sex),
            birthYear = birthYear,
            heightCm = heightCm,
            goal = runCatching { Goal.valueOf(goal) }.getOrDefault(d.goal),
            level = runCatching { Level.valueOf(level) }.getOrDefault(d.level),
            focusAreas = focusAreas.mapNotNull { runCatching { FocusArea.valueOf(it) }.getOrNull() }.toSet().ifEmpty { d.focusAreas },
            weeklyGoal = weeklyGoal,
            trainingDays = trainingDays.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }.toSet(),
            minutesPerSession = minutesPerSession,
            targetWeightKg = targetWeightKg,
            quietMode = quietMode,
        )
    }

    companion object {
        fun from(p: Profile) = ProfileDto(
            p.sex.name, p.birthYear, p.heightCm, p.goal.name, p.level.name, p.focusAreas.map { it.name },
            p.weeklyGoal, p.trainingDays.map { it.name }, p.minutesPerSession, p.targetWeightKg, p.quietMode,
        )
    }
}

@Serializable
private data class WorkoutPrefsDto(
    val restSeconds: Int,
    val countdownSeconds: Int,
    val voiceEnabled: Boolean,
    val coachTips: Boolean,
    val soundEffects: Boolean,
    val keepScreenOn: Boolean,
)

@Serializable
private data class AppPrefsDto(
    val units: String,
    val weekStart: String,
    val planStartEpochDay: Long?,
    val planSeed: Long,
    val coachModel: String,
)
