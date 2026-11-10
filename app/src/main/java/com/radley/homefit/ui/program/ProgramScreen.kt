package com.radley.homefit.ui.program

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.ProgramStatus
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.ProgramCategory
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.color
import com.radley.homefit.ui.components.icon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProgramViewModel(private val programId: String) : ViewModel() {

    private val programs = ServiceLocator.programs

    val status: StateFlow<ProgramStatus?> = combine(flow { emit(ServiceLocator.content.get()) }, programs.progress) { content, progress ->
        content.programs[programId]?.let { programs.status(it, progress[programId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setLevel(level: Level) = viewModelScope.launch { programs.setLevel(programId, level) }

    /** Rest days are "done" by acknowledging them; that's what unlocks the next workout. */
    fun completeRestDay(day: Int) = viewModelScope.launch {
        val s = status.value ?: return@launch
        programs.completeDay(s.program, day)
        ServiceLocator.history.evaluateAchievements()
    }

    fun restart() = viewModelScope.launch { programs.restart(programId) }
}

@Composable
fun ProgramScreen(
    programId: String,
    onBack: () -> Unit,
    onOpenDay: (WorkoutRef) -> Unit,
    viewModel: ProgramViewModel = viewModel(key = "program-$programId") { ProgramViewModel(programId) },
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var restDay by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmRestart by rememberSaveable { mutableStateOf(false) }

    val s = status
    Scaffold(
        topBar = {
            BackTopBar(s?.program?.name ?: "", onBack) {
                if (s?.started == true) {
                    IconButton(onClick = { confirmRestart = true }) { Icon(Icons.Rounded.RestartAlt, contentDescription = "Restart") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (s != null && !s.finished) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = {
                            if (s.currentDay in s.program.restDays) restDay = s.currentDay
                            else onOpenDay(WorkoutRef.ProgramDay(s.program.id, s.currentDay))
                        },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Text(
                            if (s.currentDay in s.program.restDays) "Day ${s.currentDay}: rest day"
                            else if (s.started) "Start day ${s.currentDay}" else "Start the challenge",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (s == null) return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            val color = s.program.focus.color
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(if (s.program.category == ProgramCategory.RECOVERY) s.program.category.icon else s.program.focus.icon, contentDescription = null, tint = color)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(
                        if (s.finished) "Completed${if (s.timesCompleted > 1) " ×${s.timesCompleted}" else ""}" else "Day ${s.currentDay} of ${s.program.days}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text("${s.percent}% complete", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { s.percent / 100f },
                color = color,
                trackColor = color.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(12.dp))
            Text(s.program.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (s.program.availableLevels.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Level", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    s.program.availableLevels.forEachIndexed { i, level ->
                        SegmentedButton(
                            selected = s.level == level,
                            onClick = { viewModel.setLevel(level) },
                            shape = SegmentedButtonDefaults.itemShape(i, s.program.availableLevels.size),
                        ) { Text(level.label) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            (1..s.program.days).chunked(7).forEachIndexed { week, days ->
                Text("Week ${week + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEach { day ->
                        DayCell(
                            day = day,
                            status = s,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when {
                                    day > s.currentDay -> scope.launch { snackbar.showSnackbar("Finish day ${s.currentDay} first") }
                                    day in s.program.restDays -> if (day == s.currentDay) restDay = day
                                    else -> onOpenDay(WorkoutRef.ProgramDay(s.program.id, day))
                                }
                            },
                        )
                    }
                    repeat(7 - days.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    restDay?.let { day ->
        AlertDialog(
            onDismissRequest = { restDay = null },
            icon = { Icon(Icons.Rounded.NightsStay, contentDescription = null) },
            title = { Text("Day $day is a rest day") },
            text = { Text("Recovery is when your muscles rebuild. Take it easy today; a gentle stretch is fine. Mark it done to unlock the next workout.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.completeRestDay(day)
                    restDay = null
                }) { Text("Mark as done") }
            },
            dismissButton = { TextButton(onClick = { restDay = null }) { Text("Not yet") } },
        )
    }

    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Restart from day 1?") },
            text = { Text("Your workout history is kept; only progress through this program is reset.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restart()
                    confirmRestart = false
                }) { Text("Restart") }
            },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DayCell(day: Int, status: ProgramStatus, modifier: Modifier, onClick: () -> Unit) {
    val done = day <= status.completedDays
    val current = day == status.currentDay
    val rest = day in status.program.restDays
    val locked = day > status.currentDay
    val color = status.program.focus.color
    Box(
        modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (done) color else Color.Transparent)
            .border(if (current) 2.dp else 1.dp, if (current || done) color else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            done -> Icon(Icons.Rounded.Check, contentDescription = "Day $day done", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            rest -> Icon(Icons.Rounded.NightsStay, contentDescription = "Day $day rest", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            locked && day == status.program.days -> Icon(Icons.Rounded.Lock, contentDescription = "Final day", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            else -> Text("$day", style = MaterialTheme.typography.labelLarge, color = if (current) color else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
