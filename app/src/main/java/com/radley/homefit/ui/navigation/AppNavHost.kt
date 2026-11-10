package com.radley.homefit.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.coach.CoachScreen
import com.radley.homefit.ui.coach.CoachSettingsScreen
import com.radley.homefit.ui.complete.CompleteScreen
import com.radley.homefit.ui.discover.DiscoverScreen
import com.radley.homefit.ui.discover.LibraryScreen
import com.radley.homefit.ui.home.HomeScreen
import com.radley.homefit.ui.me.AboutScreen
import com.radley.homefit.ui.me.DataScreen
import com.radley.homefit.ui.me.MeScreen
import com.radley.homefit.ui.me.ProfileScreen
import com.radley.homefit.ui.me.RemindersScreen
import com.radley.homefit.ui.me.WorkoutSettingsScreen
import com.radley.homefit.ui.onboarding.OnboardingScreen
import com.radley.homefit.ui.player.PlayerScreen
import com.radley.homefit.ui.program.ProgramScreen
import com.radley.homefit.ui.report.AchievementsScreen
import com.radley.homefit.ui.report.HistoryScreen
import com.radley.homefit.ui.report.ReportScreen
import com.radley.homefit.ui.workout.EditorScreen
import com.radley.homefit.ui.workout.WorkoutDetailScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    TRAINING("Training", Icons.Rounded.FitnessCenter),
    DISCOVER("Discover", Icons.Rounded.Explore),
    REPORT("Report", Icons.Rounded.BarChart),
    ME("Me", Icons.Rounded.Person),
}

@Composable
fun AppNavHost(nav: NavHostController, startOnboarding: Boolean) {
    fun openWorkout(ref: WorkoutRef) = nav.navigate(WorkoutDetailRoute(ref.encode()))
    fun backToTabs() {
        if (!nav.popBackStack(TabsRoute, inclusive = false)) {
            nav.navigate(TabsRoute) { popUpTo(0) }
        }
    }

    NavHost(nav, startDestination = if (startOnboarding) OnboardingRoute else TabsRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(editing = false, onDone = { nav.navigate(TabsRoute) { popUpTo(OnboardingRoute) { inclusive = true } } })
        }
        composable<PlanRoute> {
            OnboardingScreen(editing = true, onDone = { nav.popBackStack() })
        }
        composable<TabsRoute> {
            Tabs(nav, ::openWorkout)
        }
        composable<WorkoutDetailRoute> { entry ->
            val ref = WorkoutRef.decode(entry.toRoute<WorkoutDetailRoute>().ref)
            WorkoutDetailScreen(
                ref = ref,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(EditorRoute(it.encode())) },
                onStarted = { nav.navigate(PlayerRoute) },
            )
        }
        composable<PlayerRoute> {
            PlayerScreen(
                onFinished = { id -> nav.navigate(CompleteRoute(id)) { popUpTo<PlayerRoute> { inclusive = true } } },
                onExit = { nav.popBackStack() },
            )
        }
        composable<CompleteRoute> { entry ->
            CompleteScreen(entry.toRoute<CompleteRoute>().sessionId, onDone = ::backToTabs)
        }
        composable<ProgramRoute> { entry ->
            ProgramScreen(entry.toRoute<ProgramRoute>().programId, onBack = { nav.popBackStack() }, onOpenDay = ::openWorkout)
        }
        composable<EditorRoute> { entry ->
            val ref = entry.toRoute<EditorRoute>().ref?.let(WorkoutRef::decode)
            EditorScreen(
                ref = ref,
                onBack = { nav.popBackStack() },
                onSaved = { saved, isNew ->
                    if (isNew) {
                        nav.navigate(WorkoutDetailRoute(saved.encode())) { popUpTo<EditorRoute> { inclusive = true } }
                    } else {
                        nav.popBackStack()
                    }
                },
                // Deleting from the editor also leaves the deleted workout's detail screen.
                onDeleted = { backToTabs() },
            )
        }
        composable<LibraryRoute> { entry ->
            val route = entry.toRoute<LibraryRoute>()
            val category = route.category?.let { runCatching { WorkoutCategory.valueOf(it) }.getOrNull() }
            LibraryScreen(
                category = category,
                favorites = route.favorites,
                title = route.title ?: category?.label ?: "Workouts",
                onBack = { nav.popBackStack() },
                onOpenWorkout = ::openWorkout,
            )
        }
        composable<HistoryRoute> { HistoryScreen(onBack = { nav.popBackStack() }, onReplay = ::openWorkout) }
        composable<AchievementsRoute> { AchievementsScreen(onBack = { nav.popBackStack() }) }
        composable<ProfileRoute> { ProfileScreen(onBack = { nav.popBackStack() }) }
        composable<WorkoutSettingsRoute> { WorkoutSettingsScreen(onBack = { nav.popBackStack() }) }
        composable<RemindersRoute> { RemindersScreen(onBack = { nav.popBackStack() }) }
        composable<DataRoute> {
            DataScreen(onBack = { nav.popBackStack() }, onDeletedAll = { nav.navigate(OnboardingRoute) { popUpTo(0) } })
        }
        composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        composable<CoachRoute> {
            CoachScreen(
                onBack = { nav.popBackStack() },
                onSettings = { nav.navigate(CoachSettingsRoute) },
                onOpenWorkout = ::openWorkout,
            )
        }
        composable<CoachSettingsRoute> { CoachSettingsScreen(onBack = { nav.popBackStack() }) }
    }
}

@Composable
private fun Tabs(nav: NavHostController, openWorkout: (WorkoutRef) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        // The top bars inside each tab handle the status-bar inset; only the bottom bar's is passed down.
        val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
        val modifier = Modifier.padding(top = padding.calculateTopPadding())
        androidx.compose.foundation.layout.Box(modifier) {
            when (Tab.entries[tab]) {
                Tab.TRAINING -> HomeScreen(
                    onOpenWorkout = openWorkout,
                    onOpenProgram = { nav.navigate(ProgramRoute(it)) },
                    onSetUpPlan = { nav.navigate(PlanRoute) },
                    contentPadding = bottom,
                )
                Tab.DISCOVER -> DiscoverScreen(
                    onOpenWorkout = openWorkout,
                    onOpenProgram = { nav.navigate(ProgramRoute(it)) },
                    onSeeAll = { nav.navigate(LibraryRoute(category = it.name)) },
                    onFavorites = { nav.navigate(LibraryRoute(title = "Favourites", favorites = true)) },
                    onCreate = { nav.navigate(EditorRoute()) },
                    contentPadding = bottom,
                )
                Tab.REPORT -> ReportScreen(
                    onHistory = { nav.navigate(HistoryRoute) },
                    onAchievements = { nav.navigate(AchievementsRoute) },
                    contentPadding = bottom,
                )
                Tab.ME -> MeScreen(
                    onProfile = { nav.navigate(ProfileRoute) },
                    onPlan = { nav.navigate(PlanRoute) },
                    onCoach = { nav.navigate(CoachRoute) },
                    onWorkoutSettings = { nav.navigate(WorkoutSettingsRoute) },
                    onReminders = { nav.navigate(RemindersRoute) },
                    onData = { nav.navigate(DataRoute) },
                    onAbout = { nav.navigate(AboutRoute) },
                    contentPadding = bottom,
                )
            }
        }
    }
}
