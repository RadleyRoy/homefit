package com.radley.homefit.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.domain.BodyMetrics
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.ListRow
import com.radley.homefit.ui.components.SectionHeader
import com.radley.homefit.ui.components.StatTile
import com.radley.homefit.ui.components.TabTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun ReportScreen(
    onHistory: () -> Unit,
    onAchievements: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: ReportViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var addingWeight by rememberSaveable { mutableStateOf(false) }
    var editingHeight by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Report")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        StatTile("${state.totalWorkouts}", "Workouts")
                        StatTile(Format.kcal(state.totalKcal).removeSuffix(" kcal"), "Kcal", accent = MaterialTheme.colorScheme.secondary)
                        StatTile("${state.totalMinutes}", "Minutes")
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(
                                "${state.weekMinutes.sum().toInt()} min",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        WeekBars(state.weekMinutes, state.weekDates.map { Format.weekdayInitial(it) }, state.todayIndex)
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Streak", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Current ${state.currentStreak} · Best ${state.bestStreak}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month") }
                            Text(
                                "${state.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${state.month.year}",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            IconButton(onClick = { viewModel.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month") }
                        }
                        MonthCalendar(state)
                    }
                }
            }
            item { SectionHeader("Weight", action = "Add", onAction = { addingWeight = true }) }
            item { WeightCard(state, onAdd = { addingWeight = true }, onHeight = { editingHeight = true }) }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        ListRow(
                            "Badges",
                            subtitle = "${state.badgesEarned} of ${state.badgesTotal} earned",
                            icon = Icons.Rounded.EmojiEvents,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = onAchievements,
                        )
                        ListRow("History", subtitle = "Every workout you've done", icon = Icons.Rounded.History, onClick = onHistory)
                    }
                }
            }
            if (state.recent.isNotEmpty()) {
                item { SectionHeader("Recent", action = "See all", onAction = onHistory) }
                items(state.recent, key = { it.id }) { SessionRow(it) }
            }
        }
    }

    if (addingWeight) {
        NumberDialog(
            title = "Log weight",
            label = "Weight (${Format.weightUnit(state.units)})",
            initial = state.weights.lastOrNull()?.kg?.let { "%.1f".format(Format.weightValue(it, state.units)) } ?: "",
            valid = { it in 20.0..700.0 },
            onDismiss = { addingWeight = false },
            onSave = {
                viewModel.logWeight(LocalDate.now(), it, state.units)
                addingWeight = false
            },
        )
    }
    if (editingHeight) {
        val imperial = state.units == UnitSystem.IMPERIAL
        NumberDialog(
            title = "Height",
            label = if (imperial) "Height (inches)" else "Height (cm)",
            initial = state.heightCm?.let { if (imperial) "%.0f".format(BodyMetrics.cmToInches(it)) else "%.0f".format(it) } ?: "",
            valid = { if (imperial) it in 36.0..100.0 else it in 90.0..250.0 },
            onDismiss = { editingHeight = false },
            onSave = {
                viewModel.setHeight(if (imperial) BodyMetrics.inchesToCm(it) else it)
                editingHeight = false
            },
        )
    }
}

@Composable
private fun MonthCalendar(state: ReportState) {
    val first = state.month.atDay(1)
    val lead = (first.dayOfWeek.value - state.firstDayOfWeek.value + 7) % 7
    val cells = List(lead) { null } + (1..state.month.lengthOfMonth()).map { state.month.atDay(it) }
    val today = LocalDate.now()
    val headers = (0 until 7).map { state.firstDayOfWeek.plus(it.toLong()) }
    Column {
        Row {
            headers.forEach {
                Text(
                    it.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.padding(vertical = 2.dp)) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val done = date.toEpochDay() in state.workoutDays
                            Box(
                                Modifier.fillMaxSize().clip(CircleShape)
                                    .background(if (done) MaterialTheme.colorScheme.primary else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        done -> MaterialTheme.colorScheme.onPrimary
                                        date == today -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun WeightCard(state: ReportState, onAdd: () -> Unit, onHeight: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val latest = state.weights.lastOrNull()
            if (latest == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Log your weight to see your trend and BMI.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, contentDescription = "Add weight") }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Format.weight(latest.kg, state.units), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.size(8.dp))
                    val first = state.weights.first()
                    val delta = latest.kg - first.kg
                    if (state.weights.size > 1) {
                        Text(
                            (if (delta >= 0) "+" else "−") + Format.weight(kotlin.math.abs(delta), state.units) + " since ${Format.dayMonth(LocalDate.ofEpochDay(first.epochDay))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                LineChart(
                    points = state.weights.takeLast(30).map { it.epochDay.toFloat() to Format.weightValue(it.kg, state.units).toFloat() },
                    goal = state.targetWeightKg?.let { Format.weightValue(it, state.units).toFloat() },
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("BMI", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onHeight) {
                    Text(state.heightCm?.let { "Height ${Format.height(it, state.units)}" } ?: "Set height")
                }
            }
            val bmi = state.bmi
            if (bmi != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(bmi), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.size(8.dp))
                    Text(BodyMetrics.category(bmi).label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                BmiScale(bmi)
            } else {
                Text(
                    "Add your height and a weigh-in to calculate it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun SessionRow(session: SessionEntity, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val date = Instant.ofEpochMilli(session.startedAt).atZone(ZoneId.systemDefault()).toLocalDate()
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        ListRow(
            title = session.title,
            subtitle = "${Format.fullDate(date)} · ${Format.minutes(session.totalSeconds)} · ${Format.kcal(session.kcal)}",
            trailing = trailing,
        )
    }
}

@Composable
fun NumberDialog(
    title: String,
    label: String,
    initial: String,
    valid: (Double) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null && valid(value)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
