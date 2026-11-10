package com.radley.homefit.ui.me

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.data.Profile
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.WeekStart
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.IconBadge
import com.radley.homefit.ui.components.ListRow
import com.radley.homefit.ui.components.SectionHeader
import com.radley.homefit.ui.components.TabTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MeViewModel : ViewModel() {
    private val settings = ServiceLocator.settings
    val profile: StateFlow<Profile> = settings.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile())
    val app: StateFlow<AppPrefs> = settings.appPrefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())

    fun setUnits(units: UnitSystem) = viewModelScope.launch { settings.updateAppPrefs { it.copy(units = units) } }
    fun setWeekStart(start: WeekStart) = viewModelScope.launch { settings.updateAppPrefs { it.copy(weekStart = start) } }
    fun stopPlan() = viewModelScope.launch { settings.updateAppPrefs { it.copy(planStartEpochDay = null) } }
}

@Composable
fun MeScreen(
    onProfile: () -> Unit,
    onPlan: () -> Unit,
    onCoach: () -> Unit,
    onWorkoutSettings: () -> Unit,
    onReminders: () -> Unit,
    onData: () -> Unit,
    onAbout: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: MeViewModel = viewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val app by viewModel.app.collectAsStateWithLifecycle()
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmStop by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Me")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth(), onClick = onProfile) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(Icons.Rounded.Person, MaterialTheme.colorScheme.primary, size = 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("My profile", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${profile.goal.label} · ${profile.level.label} · ${profile.weeklyGoal} days a week",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Training") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        ListRow(
                            "My plan",
                            subtitle = if (app.planStartEpochDay != null) "Active · tap to change goal, focus or schedule" else "Not set up",
                            icon = Icons.Rounded.CalendarMonth,
                            onClick = onPlan,
                        )
                        if (app.planStartEpochDay != null) {
                            ListRow("Stop my plan", subtitle = "Keep training freely without a schedule", icon = Icons.Rounded.Today, onClick = { confirmStop = true })
                        }
                        ListRow("AI coach", subtitle = "Ask Claude, get workouts and reviews on your Claude plan", icon = Icons.Rounded.AutoAwesome, onClick = onCoach)
                        ListRow("Workout settings", subtitle = "Rest, countdown, voice and sounds", icon = Icons.Rounded.Timer, onClick = onWorkoutSettings)
                        ListRow("Reminders", icon = Icons.Rounded.Notifications, onClick = onReminders)
                    }
                }
            }
            item { SectionHeader("App") }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        ListRow(
                            "Units",
                            subtitle = if (app.units == UnitSystem.METRIC) "Kilograms, centimetres" else "Pounds, inches",
                            icon = Icons.Rounded.Straighten,
                            onClick = { viewModel.setUnits(if (app.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC) },
                        )
                        ListRow("Week starts on", subtitle = app.weekStart.label, icon = Icons.Rounded.Today, onClick = { choosing = "week" })
                        ListRow("Health & data", subtitle = "Health Connect, backup, reset", icon = Icons.Rounded.Storage, onClick = onData)
                        ListRow("About", icon = Icons.Rounded.Info, onClick = onAbout)
                    }
                }
            }
        }
    }

    when (choosing) {
        "week" -> ChoiceDialog("Week starts on", WeekStart.entries, app.weekStart, { it.label }, onDismiss = { choosing = null }) {
            viewModel.setWeekStart(it)
            choosing = null
        }
    }
    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop your plan?") },
            text = { Text("Your history stays. You can set up a new plan any time.") },
            confirmButton = { TextButton(onClick = { viewModel.stopPlan(); confirmStop = false }) { Text("Stop plan") } },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListRow(label(option), onClick = { onPick(option) }, trailing = {
                        RadioButton(selected = option == selected, onClick = { onPick(option) })
                    })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
