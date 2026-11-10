package com.radley.homefit.ui.me

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.BuildConfig
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.data.Profile
import com.radley.homefit.data.Sex
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.WorkoutPrefs
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.BodyMetrics
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.ListRow
import com.radley.homefit.ui.components.SectionHeader
import com.radley.homefit.ui.report.NumberDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val settings = ServiceLocator.settings
    val prefs: StateFlow<WorkoutPrefs> = settings.workoutPrefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutPrefs())
    val profile: StateFlow<Profile> = settings.profile.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Profile())
    val app: StateFlow<AppPrefs> = settings.appPrefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())

    fun updatePrefs(transform: (WorkoutPrefs) -> WorkoutPrefs) = viewModelScope.launch { settings.updateWorkoutPrefs(transform) }
    fun updateProfile(transform: (Profile) -> Profile) = viewModelScope.launch { settings.updateProfile(transform) }

    fun testVoice() = ServiceLocator.voice.say("Ready to go. First up, jumping jacks, 30 seconds.", interrupt = true)
}

@Composable
fun WorkoutSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(topBar = { BackTopBar("Workout settings", onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Rest between exercises: ${prefs.restSeconds}s", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = prefs.restSeconds.toFloat(),
                        onValueChange = { v -> viewModel.updatePrefs { it.copy(restSeconds = (v / 5).toInt() * 5) } },
                        valueRange = WorkoutPrefs.REST_RANGE.first.toFloat()..WorkoutPrefs.REST_RANGE.last.toFloat(),
                    )
                    Text(
                        "HIIT, Tabata and stretch routines keep their own short rests.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Get-ready countdown: ${prefs.countdownSeconds}s", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                    Slider(
                        value = prefs.countdownSeconds.toFloat(),
                        onValueChange = { v -> viewModel.updatePrefs { it.copy(countdownSeconds = v.toInt()) } },
                        valueRange = WorkoutPrefs.COUNTDOWN_RANGE.first.toFloat()..WorkoutPrefs.COUNTDOWN_RANGE.last.toFloat(),
                    )
                }
            }
            SectionHeader("Sound")
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow("Voice coach", "Announces each move, rests and countdowns", Icons.Rounded.RecordVoiceOver, prefs.voiceEnabled) { on ->
                        viewModel.updatePrefs { it.copy(voiceEnabled = on) }
                    }
                    ToggleRow("Coach tips", "A form cue during each rest", Icons.Rounded.Lightbulb, prefs.coachTips) { on ->
                        viewModel.updatePrefs { it.copy(coachTips = on) }
                    }
                    ToggleRow("Countdown beeps", null, Icons.Rounded.MusicNote, prefs.soundEffects) { on ->
                        viewModel.updatePrefs { it.copy(soundEffects = on) }
                    }
                    ListRow("Test voice", subtitle = "Your music keeps playing, just quieter", icon = Icons.Rounded.RecordVoiceOver, onClick = viewModel::testVoice)
                    ListRow("Text-to-speech engine", subtitle = "Change the voice or language in system settings", icon = Icons.Rounded.Settings, onClick = {
                        ServiceLocator.voice.openTtsSettings(context)
                    })
                }
            }
            SectionHeader("Screen")
            AppCard(Modifier.fillMaxWidth()) {
                ToggleRow("Keep screen on", "During workouts", Icons.Rounded.LightMode, prefs.keepScreenOn) { on ->
                    viewModel.updatePrefs { it.copy(keepScreenOn = on) }
                }
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListRow(title, subtitle = subtitle, icon = icon, onClick = { onChange(!checked) }, trailing = {
        Switch(checked = checked, onCheckedChange = onChange)
    })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val app by viewModel.app.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val imperial = app.units == UnitSystem.IMPERIAL

    Scaffold(topBar = { BackTopBar("My profile", onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Sex", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sex.entries.forEach { s ->
                            FilterChip(selected = profile.sex == s, onClick = { viewModel.updateProfile { it.copy(sex = s) } }, label = { Text(s.label) })
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(5.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    ListRow("Year of birth", subtitle = profile.birthYear?.toString() ?: "Not set", onClick = { editing = "year" })
                    ListRow("Height", subtitle = profile.heightCm?.let { Format.height(it, app.units) } ?: "Not set", onClick = { editing = "height" })
                    ListRow("Target weight", subtitle = profile.targetWeightKg?.let { Format.weight(it, app.units) } ?: "Not set", onClick = { editing = "target" })
                    ListRow("Weekly goal", subtitle = "${profile.weeklyGoal} days", onClick = { editing = "goal" })
                    ToggleRow("Quiet mode", "Plans leave out jumping moves", Icons.Rounded.MusicNote, profile.quietMode) { on ->
                        viewModel.updateProfile { it.copy(quietMode = on) }
                    }
                }
            }
            Text(
                "Goal, level, focus areas and training days live in My plan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    when (editing) {
        "year" -> NumberDialog("Year of birth", "Year", profile.birthYear?.toString() ?: "", { it in 1900.0..2030.0 }, { editing = null }) { v ->
            viewModel.updateProfile { it.copy(birthYear = v.toInt()) }
            editing = null
        }
        "height" -> NumberDialog(
            "Height",
            if (imperial) "Inches" else "Centimetres",
            profile.heightCm?.let { "%.0f".format(if (imperial) BodyMetrics.cmToInches(it) else it) } ?: "",
            { if (imperial) it in 36.0..100.0 else it in 90.0..250.0 },
            { editing = null },
        ) { v ->
            viewModel.updateProfile { it.copy(heightCm = if (imperial) BodyMetrics.inchesToCm(v) else v) }
            editing = null
        }
        "target" -> NumberDialog(
            "Target weight",
            Format.weightUnit(app.units),
            profile.targetWeightKg?.let { "%.1f".format(Format.weightValue(it, app.units)) } ?: "",
            { it in 20.0..700.0 },
            { editing = null },
        ) { v ->
            viewModel.updateProfile { it.copy(targetWeightKg = if (imperial) BodyMetrics.lbToKg(v) else v) }
            editing = null
        }
        "goal" -> com.radley.homefit.ui.home.WeeklyGoalDialog(profile.weeklyGoal, onDismiss = { editing = null }) { g ->
            viewModel.updateProfile { it.copy(weeklyGoal = g) }
            editing = null
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = { BackTopBar("About", onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("HomeFit ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "A personal home-workout app. No accounts, no ads, no tracking: everything stays on this phone unless you export it or turn on Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            SectionHeader("Credits")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Exercise photos and many instructions come from free-exercise-db, released into the public domain under the Unlicense.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/yuhonas/free-exercise-db")))
                        }) { Text("github.com/yuhonas/free-exercise-db") }
                    }
                }
            }
            SectionHeader("Health note")
            Text(
                "Calorie figures are estimates from standard MET values. Stop if anything hurts, and check with a doctor before starting a new programme if you have a medical condition.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
