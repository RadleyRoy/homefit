package com.radley.homefit.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "homefit")

/**
 * Profile and preferences: a few dozen scalars, so DataStore rather than Room. Everything that
 * grows over time (sessions, weights, reminders…) lives in [com.radley.homefit.data.db.HomeFitDatabase].
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val SEX = stringPreferencesKey("sex")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val HEIGHT_CM = doublePreferencesKey("height_cm")
        val GOAL = stringPreferencesKey("goal")
        val LEVEL = stringPreferencesKey("level")
        val FOCUS = stringSetPreferencesKey("focus_areas")
        val WEEKLY_GOAL = intPreferencesKey("weekly_goal")
        val TRAINING_DAYS = stringSetPreferencesKey("training_days")
        val MINUTES = intPreferencesKey("minutes_per_session")
        val TARGET_WEIGHT = doublePreferencesKey("target_weight_kg")
        val QUIET = booleanPreferencesKey("quiet_mode")

        val REST = intPreferencesKey("rest_seconds")
        val COUNTDOWN = intPreferencesKey("countdown_seconds")
        val VOICE = booleanPreferencesKey("voice_enabled")
        val TIPS = booleanPreferencesKey("coach_tips")
        val SFX = booleanPreferencesKey("sound_effects")
        val SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        val UNITS = stringPreferencesKey("units")
        val WEEK_START = stringPreferencesKey("week_start")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val PLAN_START = longPreferencesKey("plan_start_epoch_day")
        val PLAN_SEED = longPreferencesKey("plan_seed")
        val COACH_MODEL = stringPreferencesKey("coach_model")
        val HEALTH_SYNC = booleanPreferencesKey("health_connect_sync")
    }

    val profile: Flow<Profile> = context.dataStore.data.map { it.toProfile() }
    val workoutPrefs: Flow<WorkoutPrefs> = context.dataStore.data.map { it.toWorkoutPrefs() }
    val appPrefs: Flow<AppPrefs> = context.dataStore.data.map { it.toAppPrefs() }

    suspend fun currentProfile(): Profile = profile.first()
    suspend fun currentWorkoutPrefs(): WorkoutPrefs = workoutPrefs.first()
    suspend fun currentAppPrefs(): AppPrefs = appPrefs.first()

    suspend fun setProfile(profile: Profile) {
        context.dataStore.edit { it.write(profile) }
    }

    suspend fun updateProfile(transform: (Profile) -> Profile) {
        context.dataStore.edit { prefs -> prefs.write(transform(prefs.toProfile())) }
    }

    suspend fun updateWorkoutPrefs(transform: (WorkoutPrefs) -> WorkoutPrefs) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toWorkoutPrefs())
            prefs[Keys.REST] = next.restSeconds.coerceIn(WorkoutPrefs.REST_RANGE)
            prefs[Keys.COUNTDOWN] = next.countdownSeconds.coerceIn(WorkoutPrefs.COUNTDOWN_RANGE)
            prefs[Keys.VOICE] = next.voiceEnabled
            prefs[Keys.TIPS] = next.coachTips
            prefs[Keys.SFX] = next.soundEffects
            prefs[Keys.SCREEN_ON] = next.keepScreenOn
        }
    }

    suspend fun updateAppPrefs(transform: (AppPrefs) -> AppPrefs) {
        context.dataStore.edit { prefs ->
            val next = transform(prefs.toAppPrefs())
            prefs[Keys.UNITS] = next.units.name
            prefs[Keys.WEEK_START] = next.weekStart.name
            prefs[Keys.ONBOARDED] = next.onboardingComplete
            next.planStartEpochDay?.let { prefs[Keys.PLAN_START] = it } ?: prefs.remove(Keys.PLAN_START)
            prefs[Keys.PLAN_SEED] = next.planSeed
            prefs[Keys.COACH_MODEL] = next.coachModel
            prefs[Keys.HEALTH_SYNC] = next.healthConnectSync
        }
    }

    /** Wipes everything, including onboarding, for "Delete all data". */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    private fun MutablePreferences.write(profile: Profile) {
        this[Keys.SEX] = profile.sex.name
        profile.birthYear?.let { this[Keys.BIRTH_YEAR] = it } ?: remove(Keys.BIRTH_YEAR)
        profile.heightCm?.let { this[Keys.HEIGHT_CM] = it } ?: remove(Keys.HEIGHT_CM)
        this[Keys.GOAL] = profile.goal.name
        this[Keys.LEVEL] = profile.level.name
        this[Keys.FOCUS] = profile.focusAreas.map { it.name }.toSet()
        this[Keys.WEEKLY_GOAL] = profile.weeklyGoal.coerceIn(1, 7)
        this[Keys.TRAINING_DAYS] = profile.trainingDays.map { it.name }.toSet()
        this[Keys.MINUTES] = profile.minutesPerSession
        profile.targetWeightKg?.let { this[Keys.TARGET_WEIGHT] = it } ?: remove(Keys.TARGET_WEIGHT)
        this[Keys.QUIET] = profile.quietMode
    }

    private fun Preferences.toProfile(): Profile {
        val defaults = Profile()
        return Profile(
            sex = enumOr(this[Keys.SEX], defaults.sex),
            birthYear = this[Keys.BIRTH_YEAR],
            heightCm = this[Keys.HEIGHT_CM],
            goal = enumOr(this[Keys.GOAL], defaults.goal),
            level = enumOr(this[Keys.LEVEL], defaults.level),
            focusAreas = this[Keys.FOCUS]
                ?.mapNotNull { runCatching { FocusArea.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?.ifEmpty { null }
                ?: defaults.focusAreas,
            weeklyGoal = this[Keys.WEEKLY_GOAL] ?: defaults.weeklyGoal,
            trainingDays = this[Keys.TRAINING_DAYS]
                ?.mapNotNull { runCatching { DayOfWeek.valueOf(it) }.getOrNull() }
                ?.toSet()
                ?: defaults.trainingDays,
            minutesPerSession = this[Keys.MINUTES] ?: defaults.minutesPerSession,
            targetWeightKg = this[Keys.TARGET_WEIGHT],
            quietMode = this[Keys.QUIET] ?: defaults.quietMode,
        )
    }

    private fun Preferences.toWorkoutPrefs(): WorkoutPrefs {
        val d = WorkoutPrefs()
        return WorkoutPrefs(
            restSeconds = this[Keys.REST] ?: d.restSeconds,
            countdownSeconds = this[Keys.COUNTDOWN] ?: d.countdownSeconds,
            voiceEnabled = this[Keys.VOICE] ?: d.voiceEnabled,
            coachTips = this[Keys.TIPS] ?: d.coachTips,
            soundEffects = this[Keys.SFX] ?: d.soundEffects,
            keepScreenOn = this[Keys.SCREEN_ON] ?: d.keepScreenOn,
        )
    }

    private fun Preferences.toAppPrefs(): AppPrefs {
        val d = AppPrefs()
        return AppPrefs(
            units = enumOr(this[Keys.UNITS], d.units),
            weekStart = enumOr(this[Keys.WEEK_START], d.weekStart),
            onboardingComplete = this[Keys.ONBOARDED] ?: d.onboardingComplete,
            planStartEpochDay = this[Keys.PLAN_START],
            planSeed = this[Keys.PLAN_SEED] ?: d.planSeed,
            coachModel = this[Keys.COACH_MODEL] ?: d.coachModel,
            healthConnectSync = this[Keys.HEALTH_SYNC] ?: d.healthConnectSync,
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback
}
