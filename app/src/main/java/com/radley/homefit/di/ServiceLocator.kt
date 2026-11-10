package com.radley.homefit.di

import android.content.Context
import com.radley.homefit.data.BackupManager
import com.radley.homefit.data.HistoryRepository
import com.radley.homefit.data.ProgramRepository
import com.radley.homefit.data.SettingsStore
import com.radley.homefit.data.WorkoutRepository
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.platform.HealthConnectSync
import com.radley.homefit.platform.ReminderRepository
import com.radley.homefit.platform.ReminderScheduler
import com.radley.homefit.platform.VoiceCoach
import com.radley.homefit.session.WorkoutSessionController
import com.radley.homefit.widget.WeeklyGoalWidget
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Hand-rolled DI, as in App Lock: a handful of process singletons. The awkward consumers here
 * are a foreground service, broadcast receivers and a widget, none of which Hilt makes simpler.
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val database: HomeFitDatabase by lazy { HomeFitDatabase.create(appContext) }
    val content: ContentRepository by lazy { ContentRepository(appContext) }
    val workouts: WorkoutRepository by lazy { WorkoutRepository(content, database, settings) }
    val history: HistoryRepository by lazy {
        HistoryRepository(database, onWeightLogged = { day, kg -> health.writeWeight(day, kg) })
    }
    val programs: ProgramRepository by lazy { ProgramRepository(database) }
    val health: HealthConnectSync by lazy { HealthConnectSync(appContext, history, settings) }
    val backup: BackupManager by lazy { BackupManager(appContext, database, settings) }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(appContext) }
    val reminders: ReminderRepository by lazy { ReminderRepository(database, reminderScheduler) }

    /**
     * Text shared into HomeFit (Claude's reply, via Share → HomeFit), waiting for the coach
     * screen to offer it for import.
     */
    val coachInbox = MutableStateFlow<String?>(null)

    /** Created on first use: binding the TTS engine takes a moment and isn't free. */
    val voice: VoiceCoach by lazy { VoiceCoach(appContext) }

    val session: WorkoutSessionController by lazy {
        WorkoutSessionController(
            context = appContext,
            voice = { voice },
            history = history,
            programs = programs,
            workouts = workouts,
            onRecorded = {
                WeeklyGoalWidget.refresh(appContext)
                health.writeSession(it)
            },
        )
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }
}
