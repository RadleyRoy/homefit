package com.radley.homefit.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.content.Content
import com.radley.homefit.domain.ResolvedStep
import com.radley.homefit.domain.player.Phase
import com.radley.homefit.domain.player.ceilSeconds
import com.radley.homefit.session.ActiveWorkout
import com.radley.homefit.ui.components.ExerciseImage
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.theme.TimerStyle
import com.radley.homefit.ui.workout.ExerciseSheet

@Composable
fun PlayerScreen(
    onFinished: (sessionId: Long) -> Unit,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = viewModel(),
) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val keepScreenOn by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    var quitting by rememberSaveable { mutableStateOf(false) }
    var info by remember { mutableStateOf<ResolvedStep?>(null) }
    var content by remember { mutableStateOf<Content?>(null) }

    // Both the session event and `active` going null can signal the end; leave exactly once.
    var left by remember { mutableStateOf(false) }
    fun leave(sessionId: Long?) {
        if (left) return
        left = true
        if (sessionId != null) onFinished(sessionId) else onExit()
    }

    LaunchedEffect(Unit) { content = viewModel.content() }
    LaunchedEffect(Unit) {
        viewModel.finished.collect { id -> leave(id) }
    }
    // Landing here with nothing running (process death, a stale notification) just goes back.
    LaunchedEffect(active == null) {
        if (active == null && !viewModel.isFinishing()) leave(null)
    }

    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    BackHandler {
        viewModel.pause()
        quitting = true
    }

    val current = active ?: return
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopRow(current, onClose = {
                viewModel.pause()
                quitting = true
            })
            Box(Modifier.weight(1f)) {
                when (val phase = current.snapshot.phase) {
                    is Phase.Ready -> ReadyPane(current, phase, viewModel)
                    is Phase.Work -> WorkPane(current, phase, viewModel, onInfo = { info = it })
                    is Phase.Rest -> RestPane(current, phase, viewModel)
                    Phase.Finished -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }

        if (current.snapshot.paused && !quitting) {
            PausedOverlay(
                onResume = viewModel::resume,
                onQuit = { quitting = true },
            )
        }
    }

    if (quitting) {
        QuitDialog(
            canSave = current.snapshot.completed.isNotEmpty(),
            onKeepGoing = {
                quitting = false
                viewModel.resume()
            },
            onQuit = { save, reason ->
                quitting = false
                viewModel.quit(save, reason)
            },
        )
    }

    info?.let { ExerciseSheet(it.exercise, it.measure, content, onDismiss = { info = null }) }
}

@Composable
private fun TopRow(active: ActiveWorkout, onClose: () -> Unit) {
    val steps = active.workout.steps
    val index = when (val p = active.snapshot.phase) {
        is Phase.Ready -> p.index
        is Phase.Work -> p.index
        is Phase.Rest -> p.nextIndex
        Phase.Finished -> steps.size
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Quit workout") }
        // One segment per exercise, like a story progress bar.
        Row(Modifier.weight(1f).height(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).fillMaxSize().clip(CircleShape).background(
                        when {
                            i < index || i in active.snapshot.completed -> MaterialTheme.colorScheme.primary
                            i == index -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                )
            }
        }
        Text(
            "${(index + 1).coerceAtMost(steps.size)}/${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun ReadyPane(active: ActiveWorkout, phase: Phase.Ready, vm: PlayerViewModel) {
    val step = active.workout.steps[phase.index]
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("READY TO GO", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(20.dp))
        ExerciseImage(step.exercise, Modifier.fillMaxWidth(0.8f).aspectRatio(1.2f))
        Spacer(Modifier.height(20.dp))
        Text(step.exercise.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(Format.measure(step.measure, step.exercise.perSide), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
        RingTimer(remainingMs = phase.remainingMs, totalMs = phase.totalMs, size = 140)
        Spacer(Modifier.height(24.dp))
        Button(onClick = vm::skip, modifier = Modifier.height(52.dp).fillMaxWidth(0.7f), shape = RoundedCornerShape(26.dp)) {
            Text("Start now", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WorkPane(active: ActiveWorkout, phase: Phase.Work, vm: PlayerViewModel, onInfo: (ResolvedStep) -> Unit) {
    val step = active.workout.steps[phase.index]
    val target = step.targetMs
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        ExerciseImage(step.exercise, Modifier.fillMaxWidth().weight(1f, fill = false).aspectRatio(1.1f))
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(step.exercise.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            IconButton(onClick = { onInfo(step) }) {
                Icon(Icons.Rounded.Info, contentDescription = "How to do ${step.exercise.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (step.exercise.perSide && target != null) {
            Text(
                if (phase.elapsedMs < target / 2) "First side" else "Second side",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (target != null) {
            Text(Format.clock((target - phase.elapsedMs).ceilSeconds()), style = TimerStyle, color = MaterialTheme.colorScheme.primary)
        } else {
            Text(Format.measure(step.measure, step.exercise.perSide), style = TimerStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                Format.clock((phase.elapsedMs / 1000).toInt()),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = vm::previous, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous exercise", modifier = Modifier.size(32.dp))
            }
            if (target == null) {
                FilledIconButton(onClick = vm::complete, modifier = Modifier.size(84.dp)) {
                    Icon(Icons.Rounded.Check, contentDescription = "Done", modifier = Modifier.size(44.dp))
                }
            } else {
                FilledIconButton(
                    onClick = vm::togglePause,
                    modifier = Modifier.size(84.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                ) {
                    Icon(Icons.Rounded.Pause, contentDescription = "Pause", modifier = Modifier.size(44.dp))
                }
            }
            IconButton(onClick = vm::skip, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Skip exercise", modifier = Modifier.size(32.dp))
            }
        }
        if (target == null) {
            OutlinedButton(onClick = vm::togglePause, modifier = Modifier.padding(bottom = 16.dp)) {
                Icon(Icons.Rounded.Pause, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Pause")
            }
        }
    }
}

@Composable
private fun RestPane(active: ActiveWorkout, phase: Phase.Rest, vm: PlayerViewModel) {
    val next = active.workout.steps[phase.nextIndex]
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text("REST", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        RingTimer(phase.remainingMs, phase.totalMs, size = 200, color = MaterialTheme.colorScheme.tertiary)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = vm::extendRest) { Text("+20s") }
            Button(onClick = vm::skip) { Text("Skip") }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "NEXT ${phase.nextIndex + 1}/${active.workout.steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseImage(next.exercise, Modifier.size(96.dp), corner = 14.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(next.exercise.name, style = MaterialTheme.typography.titleMedium)
                Text(Format.measure(next.measure, next.exercise.perSide), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun RingTimer(remainingMs: Long, totalMs: Long, size: Int, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary) {
    Box(contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { if (totalMs == 0L) 0f else remainingMs.toFloat() / totalMs },
            modifier = Modifier.size(size.dp),
            color = color,
            strokeWidth = 10.dp,
            trackColor = color.copy(alpha = 0.15f),
            gapSize = 0.dp,
        )
        Text("${remainingMs.ceilSeconds()}", style = TimerStyle.copy(fontSize = TimerStyle.fontSize * (size / 200f)), color = color)
    }
}

@Composable
private fun PausedOverlay(onResume: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Paused", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onResume, modifier = Modifier.height(56.dp).width(220.dp), shape = RoundedCornerShape(28.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Resume", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onQuit, modifier = Modifier.width(220.dp)) { Text("Quit workout", color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}
