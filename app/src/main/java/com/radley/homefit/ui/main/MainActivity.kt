package com.radley.homefit.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.PlanDayResult
import com.radley.homefit.domain.PlanGenerator
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.platform.Notifications
import com.radley.homefit.ui.navigation.AppNavHost
import com.radley.homefit.ui.navigation.PlayerRoute
import com.radley.homefit.ui.navigation.WorkoutDetailRoute
import com.radley.homefit.ui.theme.HomeFitTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Deep links from notifications and the widget, delivered once the nav graph exists. */
    private val deepLinks = Channel<Intent>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) deepLinks.trySend(intent)

        // Null until DataStore's first read, so the wrong start screen never flashes.
        val prefsFlow = ServiceLocator.settings.appPrefs.map<AppPrefs, AppPrefs?> { it }
        setContent {
            val prefs by prefsFlow.collectAsState(initial = null)
            val current = prefs
            HomeFitTheme {
                if (current == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    val nav = rememberNavController()
                    // Captured once: finishing onboarding mid-session shouldn't rebuild the graph.
                    val startOnboarding = androidx.compose.runtime.remember { !current.onboardingComplete }
                    AppNavHost(nav, startOnboarding)
                    LaunchedEffect(nav) {
                        deepLinks.receiveAsFlow().collect { handle(it, nav) }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLinks.trySend(intent)
    }

    private fun handle(intent: Intent, nav: NavHostController) {
        when {
            intent.getBooleanExtra(Notifications.EXTRA_OPEN_PLAYER, false) -> {
                if (ServiceLocator.session.active.value != null) nav.navigate(PlayerRoute) { launchSingleTop = true }
            }
            intent.getBooleanExtra(Notifications.EXTRA_OPEN_TODAY, false) -> lifecycleScope.launch {
                todaysWorkout()?.let { nav.navigate(WorkoutDetailRoute(it.encode())) }
            }
        }
    }

    /** Today's plan session if there is one, else the next day of the challenge in progress. */
    private suspend fun todaysWorkout(): WorkoutRef? {
        val settings = ServiceLocator.settings
        val app = settings.currentAppPrefs()
        val today = ServiceLocator.history.today().toEpochDay()
        val content = ServiceLocator.content.get()
        app.planStartEpochDay?.let { start ->
            val day = PlanGenerator(content).dayFor(settings.currentProfile(), start, app.planSeed, today, settings.currentWorkoutPrefs().restSeconds)
            if (day is PlanDayResult.Train) return WorkoutRef.PlanDay(today)
        }
        val progress = ServiceLocator.database.programProgress().all()
            .filter { row -> content.programs[row.programId]?.let { row.completedDays < it.days } == true }
            .maxByOrNull { it.lastCompletedAt ?: it.startedAt }
            ?: return null
        val program = content.programs.getValue(progress.programId)
        val next = progress.completedDays + 1
        return if (next in program.restDays) null else WorkoutRef.ProgramDay(program.id, next)
    }
}
