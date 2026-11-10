package com.radley.homefit.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.coach.CoachService
import com.radley.homefit.data.content.ContentParser
import com.radley.homefit.data.db.CoachMessageEntity
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.IconBadge

private val chatSuggestions = listOf(
    "How do I get better at push-ups?",
    "My lower back aches after crunches. What should I change?",
    "What should I eat before a morning workout?",
)
private val workoutSuggestions = listOf(
    "15 minutes, fat burn, no jumping",
    "Upper body with a chair, 20 minutes",
    "Gentle stretch for after a long run",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenWorkout: (WorkoutRef) -> Unit,
    viewModel: CoachViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streaming.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(CoachMode.CHAT) }
    val input = rememberTextFieldState()
    val snackbar = remember { SnackbarHostState() }
    val list = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.errors.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { viewModel.saved.collect { onOpenWorkout(it) } }
    LaunchedEffect(messages.size, streaming?.length) {
        val count = messages.size + if (streaming != null) 1 else 0
        if (count > 0) list.animateScrollToItem(count - 1)
    }

    fun submit(text: String) {
        viewModel.send(text, mode)
        input.clearText()
    }

    Scaffold(
        topBar = {
            BackTopBar("AI coach", onBack) {
                if (messages.isNotEmpty()) IconButton(onClick = viewModel::clear) { Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear chat") }
                IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Coach settings") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!hasKey) {
            NoKey(Modifier.padding(padding), onSettings)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (messages.isEmpty() && streaming == null) {
                    item {
                        Column {
                            Text(
                                if (mode == CoachMode.CHAT) "Ask anything about training, form or recovery." else "Describe the workout you want and the coach will build it from the app's exercises.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                (if (mode == CoachMode.CHAT) chatSuggestions else workoutSuggestions).forEach { s ->
                                    AssistChip(onClick = { submit(s) }, label = { Text(s) })
                                }
                            }
                        }
                    }
                }
                items(messages, key = { it.id }) { m ->
                    Bubble(m, onOpen = { onOpenWorkout(WorkoutRef.CoachProposal(m.id)) }, onSave = { viewModel.saveWorkout(m) })
                }
                streaming?.let { partial ->
                    item(key = "streaming") {
                        Bubble(CoachMessageEntity(role = CoachService.ROLE_ASSISTANT, text = partial.ifEmpty { "…" }, createdAt = 0), onOpen = {}, onSave = {})
                    }
                }
                if (busy && streaming == null) {
                    item(key = "thinking") { Text("Building your workout…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == CoachMode.CHAT, onClick = { mode = CoachMode.CHAT }, label = { Text("Chat") })
                    FilterChip(
                        selected = mode == CoachMode.WORKOUT,
                        onClick = { mode = CoachMode.WORKOUT },
                        label = { Text("Make a workout") },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        state = input,
                        placeholder = { Text(if (mode == CoachMode.CHAT) "Ask your coach" else "e.g. 20 min abs, no equipment") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        onKeyboardAction = { submit(input.text.toString()) },
                    )
                    if (busy) {
                        IconButton(onClick = viewModel::stop) { Icon(Icons.Rounded.Stop, contentDescription = "Stop") }
                    } else {
                        IconButton(onClick = { submit(input.text.toString()) }, enabled = input.text.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: CoachMessageEntity, onOpen: () -> Unit, onSave: () -> Unit) {
    val mine = message.role == CoachService.ROLE_USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer)
                .padding(14.dp),
        ) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            val items = message.workoutItems?.let(ContentParser::decodeItems)
            if (message.workoutName != null && items != null) {
                Spacer(Modifier.height(10.dp))
                Text(message.workoutName, style = MaterialTheme.typography.titleMedium)
                Text("${items.size} exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpen) { Text("Open") }
                    OutlinedButton(onClick = onSave) { Text("Save") }
                }
            }
        }
    }
}

@Composable
private fun NoKey(modifier: Modifier, onSettings: () -> Unit) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        IconBadge(Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary, size = 80.dp)
        Spacer(Modifier.height(20.dp))
        Text("Your AI coach", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Chat about training and generate workouts on demand with Claude. It uses your own Anthropic API key, so you pay Anthropic directly for what you use. Everything else in the app works without it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onSettings) { Text("Add API key") }
    }
}
