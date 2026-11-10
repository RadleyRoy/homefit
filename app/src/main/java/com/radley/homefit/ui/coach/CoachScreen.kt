package com.radley.homefit.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.coach.ClaudeApp
import com.radley.homefit.coach.ImportedWorkout
import com.radley.homefit.coach.WorkoutRequest
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.IconBadge
import com.radley.homefit.ui.components.SectionHeader
import com.radley.homefit.ui.components.WorkoutRow
import kotlinx.coroutines.launch

private val LENGTHS = listOf(10, 15, 20, 30, 45)
private val EXTRA_EQUIPMENT = listOf(Equipment.CHAIR, Equipment.WALL, Equipment.DUMBBELL, Equipment.BAR, Equipment.OUTDOORS)

/**
 * The AI coach, run through the Claude app on the user's own Claude plan. Each card builds a
 * prompt from the profile and training history and opens it in Claude; workouts come back via
 * Share → HomeFit or the Paste button, and are saved as ordinary custom workouts.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    onBack: () -> Unit,
    onOpenWorkout: (WorkoutRef) -> Unit,
    viewModel: CoachViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val fromClaude by viewModel.fromClaude.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    var installed by remember { mutableStateOf(ClaudeApp.isInstalled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { installed = ClaudeApp.isInstalled(context) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.open.collect(onOpenWorkout) }

    fun handOff(ask: CoachAsk) = scope.launch {
        val prompt = viewModel.prompt(ask)
        val note = when (ClaudeApp.send(context, prompt)) {
            ClaudeApp.Opened.CLAUDE_APP -> "Opened in Claude. If the message box is empty, paste: the prompt is on your clipboard."
            ClaudeApp.Opened.SHARE_SHEET -> "Pick Claude to send it. The prompt is also on your clipboard."
            ClaudeApp.Opened.NOTHING -> {
                ClaudeApp.openWeb(context)
                "Prompt copied. Paste it into claude.ai."
            }
        }
        snackbar.showSnackbar(note)
    }

    Scaffold(topBar = { BackTopBar("AI coach", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Intro(installed, onOpenWeb = { ClaudeApp.openWeb(context) }) }
            item { AskCard(onAsk = { handOff(CoachAsk.Question(it)) }) }
            item { WorkoutCard(onDesign = { handOff(CoachAsk.Workout(it)) }) }
            item {
                ActionCard(
                    icon = Icons.Rounded.DateRange,
                    title = "Plan my week",
                    body = "A workout for each of your training days, balanced across body areas and adjusted for how recent sessions felt.",
                    action = "Plan with Claude",
                    onClick = { handOff(CoachAsk.Week) },
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.Insights,
                    title = "Review my progress",
                    body = "Claude looks at your last four weeks: consistency, balance, whether you're ready to step up, and what to change next week.",
                    action = "Review with Claude",
                    onClick = { handOff(CoachAsk.Review) },
                )
            }
            item {
                ActionCard(
                    icon = Icons.Rounded.ContentPaste,
                    title = "Bring Claude's workout in",
                    body = "In Claude, tap Copy under the reply, then Paste here. Or use Share on the reply and pick HomeFit.",
                    action = "Paste Claude's reply",
                    onClick = { viewModel.import(ClaudeApp.clipboardText(context)) },
                )
            }
            if (fromClaude.isNotEmpty()) {
                item { SectionHeader("Workouts from Claude") }
                items(fromClaude, key = { it.ref.encode() }) { card -> WorkoutRow(card, onClick = { onOpenWorkout(card.ref) }) }
            }
        }
    }

    pending?.let { workouts ->
        ImportDialog(workouts, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImport)
    }
}

@Composable
private fun Intro(installed: Boolean, onOpenWeb: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Text("Coached by Claude, on your plan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "HomeFit writes the message (your profile, recent workouts and the exercise list) and opens it in the Claude app. " +
                    "It counts against your Claude Pro usage like any chat: no API key, no extra charges. Nothing is sent until you tap send in Claude.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!installed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "The Claude app isn't installed. Install it from the Play Store and sign in, or use claude.ai in your browser.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onOpenWeb) { Text("Open claude.ai") }
            }
        }
    }
}

@Composable
private fun AskCard(onAsk: (String) -> Unit) {
    var question by rememberSaveable { mutableStateOf("") }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Ask anything", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = { Text("How do I get my first full push-up?") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onAsk(question) }, enabled = question.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask Claude")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkoutCard(onDesign: (WorkoutRequest) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(20) }
    var focus by rememberSaveable { mutableStateOf<FocusArea?>(null) }
    var equipment by rememberSaveable { mutableStateOf(setOf(Equipment.CHAIR, Equipment.WALL)) }
    var notes by rememberSaveable { mutableStateOf("") }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Design a workout", style = MaterialTheme.typography.titleMedium)
            ChipLabel("Length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LENGTHS.forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m min") }) }
            }
            ChipLabel("Focus")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = focus == null, onClick = { focus = null }, label = { Text("Claude decides") })
                FocusArea.entries.forEach { f -> FilterChip(selected = focus == f, onClick = { focus = f }, label = { Text(f.label) }) }
            }
            ChipLabel("I have")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EXTRA_EQUIPMENT.forEach { e ->
                    FilterChip(
                        selected = e in equipment,
                        onClick = { equipment = if (e in equipment) equipment - e else equipment + e },
                        label = { Text(e.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Anything else? e.g. go easy on my knees") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onDesign(WorkoutRequest(minutes, focus, equipment, notes)) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get a workout from Claude")
            }
        }
    }
}

@Composable
private fun ChipLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun ActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: String, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, MaterialTheme.colorScheme.primary, size = 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action) }
        }
    }
}

@Composable
private fun ImportDialog(workouts: List<ImportedWorkout>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val skipped = workouts.flatMap { it.skipped }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (workouts.size == 1) "Save this workout?" else "Save ${workouts.size} workouts?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                workouts.forEach { w ->
                    Text("${w.name} · ${w.items.size} exercises", style = MaterialTheme.typography.bodyLarge)
                }
                if (skipped.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Left out ${skipped.size} line${if (skipped.size == 1) "" else "s"} that didn't match an exercise: " +
                            skipped.take(3).joinToString("; ") + if (skipped.size > 3) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
