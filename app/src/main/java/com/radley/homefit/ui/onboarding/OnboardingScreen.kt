package com.radley.homefit.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.Goal
import com.radley.homefit.data.Sex
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.CoverImage
import com.radley.homefit.ui.components.LevelBolts
import com.radley.homefit.ui.components.coverUri
import com.radley.homefit.ui.components.icon
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OnboardingScreen(
    editing: Boolean,
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(key = "onboarding-$editing") { OnboardingViewModel(editing) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler { if (!viewModel.back()) onDone() }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        if (state.step != OnboardingStep.WELCOME) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                IconButton(onClick = { if (!viewModel.back()) onDone() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                LinearProgressIndicator(
                    progress = { state.step.ordinal / (OnboardingStep.entries.size - 1f) },
                    modifier = Modifier.weight(1f).padding(end = 16.dp),
                    drawStopIndicator = {},
                )
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            when (state.step) {
                OnboardingStep.WELCOME -> Welcome()
                OnboardingStep.GOAL -> GoalStep(state, viewModel)
                OnboardingStep.FOCUS -> FocusStep(state, viewModel)
                OnboardingStep.LEVEL -> LevelStep(state, viewModel)
                OnboardingStep.BODY -> BodyStep(state, viewModel)
                OnboardingStep.SCHEDULE -> ScheduleStep(state, viewModel)
                OnboardingStep.REMINDER -> ReminderStep(state, viewModel)
                OnboardingStep.PREVIEW -> PreviewStep(state)
            }
        }
        Column(Modifier.padding(24.dp)) {
            Button(
                onClick = { if (state.step == OnboardingStep.PREVIEW) viewModel.finish(onDone) else viewModel.next() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(
                    when (state.step) {
                        OnboardingStep.WELCOME -> "Build my plan"
                        OnboardingStep.PREVIEW -> if (state.editing) "Update my plan" else "Start my plan"
                        else -> "Continue"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (state.step == OnboardingStep.WELCOME) {
                TextButton(onClick = { viewModel.skip(onDone) }, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
            }
        }
    }
}

@Composable
private fun Title(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(16.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (subtitle != null) {
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CoverImage(
            FocusArea.FULL_BODY.coverUri,
            Icons.Rounded.FitnessCenter,
            Modifier.fillMaxWidth().aspectRatio(1.5f),
            RoundedCornerShape(28.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text("HomeFit", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Workouts for every muscle, at home, with no equipment. A plan that adapts to you, a coach in your ear, and progress you can see.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChoiceCard(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) { Column(Modifier.padding(18.dp)) { content() } }
}

@Composable
private fun GoalStep(state: OnboardingState, vm: OnboardingViewModel) {
    Title("What's your main goal?")
    Goal.entries.forEach { g ->
        ChoiceCard(state.profile.goal == g, onClick = { vm.profile { it.copy(goal = g) } }) {
            Text(g.label, style = MaterialTheme.typography.titleMedium)
            Text(g.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusStep(state: OnboardingState, vm: OnboardingViewModel) {
    Title("Which areas do you want to focus on?", "Pick as many as you like; your plan rotates through them.")
    FocusArea.entries.forEach { f ->
        ChoiceCard(f in state.profile.focusAreas, onClick = { vm.toggleFocus(f) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(f.coverUri, f.icon, Modifier.size(width = 64.dp, height = 48.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.width(14.dp))
                Text(f.label, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun LevelStep(state: OnboardingState, vm: OnboardingViewModel) {
    Title("How many push-ups can you do in one go?", "It's the quickest way to pitch your starting level.")
    Level.entries.forEach { l ->
        ChoiceCard(state.profile.level == l, onClick = { vm.profile { it.copy(level = l) } }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.pushUpRange, style = MaterialTheme.typography.titleMedium)
                    Text(l.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LevelBolts(l, size = 20.dp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Quiet mode", style = MaterialTheme.typography.titleSmall)
            Text("No jumping: easier on knees, neighbours and downstairs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = state.profile.quietMode, onCheckedChange = { on -> vm.profile { it.copy(quietMode = on) } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BodyStep(state: OnboardingState, vm: OnboardingViewModel) {
    Title("A little about you", "Used to estimate calories and your BMI. All optional, and it never leaves your phone.")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        UnitSystem.entries.forEachIndexed { i, u ->
            SegmentedButton(selected = state.units == u, onClick = { vm.setUnits(u) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                Text(if (u == UnitSystem.METRIC) "kg / cm" else "lb / in")
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sex.entries.forEach { s ->
            FilterChip(selected = state.profile.sex == s, onClick = { vm.profile { it.copy(sex = s) } }, label = { Text(s.label) })
        }
    }
    Spacer(Modifier.height(12.dp))
    val yearInvalid = state.birthYearText.length == 4 && parseBirthYear(state.birthYearText) == null
    NumberField(
        value = state.birthYearText,
        label = "Year of birth",
        onChange = vm::setBirthYear,
        decimal = false,
        error = if (yearInvalid) "Enter a year like 1990" else null,
    )
    NumberField(state.heightText, if (state.units == UnitSystem.METRIC) "Height (cm)" else "Height (inches)", vm::setHeight)
    NumberField(state.weightText, "Weight (${Format.weightUnit(state.units)})", vm::setWeight)
    NumberField(state.targetWeightText, "Target weight (${Format.weightUnit(state.units)}), optional", vm::setTargetWeight)
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit, decimal: Boolean = true, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || (decimal && (c == '.' || c == ',')) }) },
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleStep(state: OnboardingState, vm: OnboardingViewModel) {
    Title("When can you train?", "Pick your workout days. Rest days are part of the plan.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = d in state.profile.trainingDays,
                onClick = { vm.toggleDay(d) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("${state.profile.trainingDays.size} days a week", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(24.dp))
    Text("How long per session?", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 15, 20, 30, 45).forEach { m ->
            FilterChip(
                selected = state.profile.minutesPerSession == m,
                onClick = { vm.profile { it.copy(minutesPerSession = m) } },
                label = { Text("$m min") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderStep(state: OnboardingState, vm: OnboardingViewModel) {
    Title("Want a nudge?", "We'll remind you on your workout days, and stay quiet once you've trained.")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Daily reminder", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked = state.reminderOn, onCheckedChange = { vm.setReminder(it) })
    }
    if (state.reminderOn) {
        Spacer(Modifier.height(16.dp))
        val picker = rememberTimePickerState(state.reminderHour, state.reminderMinute)
        LaunchedEffect(picker.hour, picker.minute) { vm.setReminder(true, picker.hour, picker.minute) }
        TimeInput(state = picker)
    }
}

@Composable
private fun PreviewStep(state: OnboardingState) {
    Title("Your first week", "${state.profile.goal.label} · ${state.profile.level.label} · ${state.profile.minutesPerSession} min sessions")
    state.preview.forEach { day ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(64.dp)) {
                Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.titleSmall)
                Text(Format.dayMonth(day.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (day.title != null) {
                Column {
                    Text(day.title, style = MaterialTheme.typography.bodyLarge)
                    Text("${day.moves} exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text("Rest", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Spacer(Modifier.size(8.dp))
    Text(
        "Each week adds a little volume. Tell us how each session felt and we'll adjust.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
