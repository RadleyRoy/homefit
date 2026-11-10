package com.radley.homefit.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.ProgramStatus
import com.radley.homefit.data.content.Level
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.CoverImage
import com.radley.homefit.ui.components.LevelBolts
import com.radley.homefit.ui.components.SectionHeader
import com.radley.homefit.ui.components.TabTopBar
import com.radley.homefit.ui.components.WorkoutRow
import com.radley.homefit.ui.components.color
import com.radley.homefit.ui.components.coverUri
import com.radley.homefit.ui.components.icon

@Composable
fun HomeScreen(
    onOpenWorkout: (WorkoutRef) -> Unit,
    onOpenProgram: (String) -> Unit,
    onSetUpPlan: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var level by rememberSaveable { mutableStateOf(Level.BEGINNER) }
    var editingGoal by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Training")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                HeroCard(
                    state = state,
                    onStart = { ref -> onOpenWorkout(ref) },
                    onPlan = onSetUpPlan,
                    onEditGoal = { editingGoal = true },
                )
            }
            if (state.challenges.isNotEmpty()) {
                item { SectionHeader("Challenges") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.challenges, key = { it.program.id }) { status ->
                            ChallengeCard(status, onClick = { onOpenProgram(status.program.id) })
                        }
                    }
                }
            }
            item { SectionHeader("Body focus") }
            item {
                SecondaryTabRow(selectedTabIndex = level.ordinal, containerColor = Color.Transparent) {
                    Level.entries.forEach { l ->
                        Tab(selected = level == l, onClick = { level = l }, text = { Text(l.label) })
                    }
                }
            }
            items(state.focusByLevel[level].orEmpty(), key = { it.ref.encode() }) { card ->
                WorkoutRow(card, onClick = { onOpenWorkout(card.ref) })
            }
        }
    }

    if (editingGoal) {
        WeeklyGoalDialog(state.weeklyGoal, onDismiss = { editingGoal = false }) {
            viewModel.setWeeklyGoal(it)
            editingGoal = false
        }
    }
}

/**
 * Today at a glance: what to do, how far through the weekly goal you are (the ring), and which
 * days this week you've trained (the squares). One card instead of three, so the Start button
 * is always above the fold.
 */
@Composable
private fun HeroCard(
    state: HomeUiState,
    onStart: (WorkoutRef) -> Unit,
    onPlan: () -> Unit,
    onEditGoal: () -> Unit,
) {
    val (title, subtitle) = when (val today = state.today) {
        is TodayCard.Train -> today.card.title to buildString {
            if (today.doneToday) append("Done today · ")
            append("${Format.minutes(today.card.seconds)} · ${today.card.exerciseCount} exercises")
        }
        is TodayCard.Rest -> "Rest day" to (today.nextTraining?.let { "Your plan picks up ${Format.fullDate(it)}" } ?: "Recovery is part of training")
        TodayCard.NoPlan -> "This week" to "${state.weekDone} of ${state.weeklyGoal} workouts done"
    }
    val percent = if (state.weeklyGoal == 0) 0 else (state.weekDone * 100 / state.weeklyGoal).coerceAtMost(100)

    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GoalRing(percent)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pill = RoundedCornerShape(28.dp)
                when (val today = state.today) {
                    is TodayCard.Train -> Button(
                        onClick = { onStart(today.card.ref) },
                        shape = pill,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text(if (today.doneToday) "AGAIN" else "START", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    is TodayCard.Rest -> Button(
                        onClick = { onStart(WorkoutRef.Builtin(REST_DAY_STRETCH)) },
                        shape = pill,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("STRETCH", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    TodayCard.NoPlan -> Button(
                        onClick = onPlan,
                        shape = pill,
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("SET UP MY PLAN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
                if (state.today !is TodayCard.NoPlan) {
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = onPlan, shape = pill, modifier = Modifier.height(52.dp)) {
                        Text("Plans", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.weight(1f)) {
                    state.week.forEach { DaySquare(it) }
                }
                if (state.streak > 0) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        "${state.streak}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { contentDescription = "${state.streak} day streak" },
                    )
                }
                IconButton(onClick = onEditGoal, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit weekly goal", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun GoalRing(percent: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics { contentDescription = "Weekly goal $percent% complete" }) {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier.size(64.dp),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 6.dp,
            gapSize = 0.dp,
        )
        Text("$percent%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DaySquare(day: DayCell) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (day.done) accent else Color.Transparent)
            .border(
                width = if (day.isToday && !day.done) 2.dp else 1.dp,
                color = if (day.done || day.isToday) accent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(4.dp),
            )
            .semantics {
                contentDescription = "${Format.fullDate(day.date)}: ${if (day.done) "worked out" else "no workout"}"
            },
    )
}

@Composable
private fun ChallengeCard(status: ProgramStatus, onClick: () -> Unit) {
    val color = status.program.focus.color
    AppCard(Modifier.width(220.dp), onClick = onClick) {
        CoverImage(status.program.coverUri, status.program.focus.icon, Modifier.fillMaxWidth().height(112.dp))
        Column(Modifier.padding(14.dp)) {
            Text(status.program.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        status.finished -> "Completed"
                        status.started -> "Day ${status.currentDay} of ${status.program.days}"
                        else -> "${status.program.days} days"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (status.program.availableLevels.size > 1) LevelBolts(status.level)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { status.percent / 100f },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun WeeklyGoalDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var value by rememberSaveable { mutableFloatStateOf(current.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly goal") },
        text = {
            Column {
                Text("Work out on ${value.toInt()} days a week", style = MaterialTheme.typography.bodyLarge)
                Slider(value = value, onValueChange = { value = it }, valueRange = 1f..7f, steps = 5)
                Text(
                    "Most people see steady progress with 3–5 days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.toInt()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What the rest-day card suggests instead of a workout. */
private const val REST_DAY_STRETCH = "stretch_bedtime"
