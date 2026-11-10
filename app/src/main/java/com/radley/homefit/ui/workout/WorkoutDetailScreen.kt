package com.radley.homefit.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.domain.ResolvedStep
import com.radley.homefit.domain.ResolvedWorkout
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.ExerciseImage
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.LevelBolts
import com.radley.homefit.ui.components.color
import com.radley.homefit.ui.components.icon

@Composable
fun WorkoutDetailScreen(
    ref: WorkoutRef,
    onBack: () -> Unit,
    onEdit: (WorkoutRef) -> Unit,
    onStarted: () -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(key = ref.encode()) { WorkoutDetailViewModel(ref) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorite by viewModel.favorite.collectAsStateWithLifecycle()
    var infoFor by remember { mutableStateOf<ResolvedStep?>(null) }
    var replaceIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var content by remember { mutableStateOf<com.radley.homefit.data.content.Content?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { content = com.radley.homefit.di.ServiceLocator.content.get() }

    val workout = state.workout
    Scaffold(
        topBar = {
            BackTopBar(workout?.title ?: "", onBack) {
                // Program and plan days change from day to day, so only fixed workouts can be favourites.
                if (ref is WorkoutRef.Builtin || ref is WorkoutRef.Custom) {
                    IconButton(onClick = viewModel::toggleFavorite) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (favorite) "Remove from favourites" else "Add to favourites",
                            tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (workout?.canEdit == true) {
                    IconButton(onClick = { onEdit(ref) }) { Icon(Icons.Rounded.Edit, contentDescription = "Edit workout") }
                }
            }
        },
        bottomBar = {
            if (workout != null && workout.steps.isNotEmpty()) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.start(onStarted) },
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            workout == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("This workout isn't available.", style = MaterialTheme.typography.bodyLarge)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), modifier = Modifier.padding(padding)) {
                item { Header(workout, state.weightKg, onReset = { confirmReset = true }) }
                itemsIndexed(workout.steps) { index, step ->
                    StepRow(
                        step,
                        onInfo = { infoFor = step },
                        onReplace = if (workout.canEdit) ({ replaceIndex = index }) else null,
                    )
                }
            }
        }
    }

    infoFor?.let { step -> ExerciseSheet(step.exercise, step.measure, content, onDismiss = { infoFor = null }) }

    replaceIndex?.let { index ->
        val current = workout?.steps?.getOrNull(index)?.exercise
        if (current != null) {
            ReplaceSheet(
                current = current,
                options = viewModel.alternativesFor(index),
                onPick = {
                    viewModel.replace(index, it)
                    replaceIndex = null
                },
                onDismiss = { replaceIndex = null },
            )
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset to default?") },
            text = { Text("Your edits and any automatic difficulty adjustments for this workout will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetToDefault()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(workout: ResolvedWorkout, weightKg: Double, onReset: () -> Unit) {
    val color = workout.focus?.color ?: workout.category.color
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(workout.focus?.icon ?: workout.category.icon, contentDescription = null, tint = color)
                    Spacer(Modifier.width(8.dp))
                    Text(workout.subtitle ?: workout.category.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    HeaderStat(Format.minutes(workout.estimatedSeconds), "Duration")
                    HeaderStat("${workout.steps.size}", "Exercises")
                    HeaderStat(Format.kcal(workout.estimatedKcal(weightKg)), "Estimate")
                }
            }
        }
        if (workout.description.isNotBlank()) {
            Text(
                workout.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        val adjusted = workout.isEdited || workout.difficultyMultiplier != 1.0
        if (adjusted && workout.canEdit) {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer).padding(start = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        workout.difficultyMultiplier > 1.0 -> "Made harder from your feedback"
                        workout.difficultyMultiplier < 1.0 -> "Eased off from your feedback"
                        else -> "You've customised this workout"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReset) { Text("Reset") }
            }
        }
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Exercises", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            workout.level?.let { LevelBolts(it) }
        }
    }
}

@Composable
private fun HeaderStat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepRow(step: ResolvedStep, onInfo: () -> Unit, onReplace: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onInfo).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseImage(step.exercise, Modifier.size(64.dp), animate = false, corner = 12.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(step.exercise.name, style = MaterialTheme.typography.titleSmall)
            Text(
                Format.measure(step.measure, step.exercise.perSide),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (onReplace != null) {
            IconButton(onClick = onReplace) {
                Icon(Icons.Rounded.SwapHoriz, contentDescription = "Replace ${step.exercise.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
