package com.radley.homefit.ui.complete

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.domain.Feedback
import com.radley.homefit.domain.SwapDirection
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.StatTile

@Composable
fun CompleteScreen(
    sessionId: Long,
    onDone: () -> Unit,
    viewModel: CompleteViewModel = viewModel(key = "complete-$sessionId") { CompleteViewModel(sessionId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val session = state.session

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(8.dp))
            Text("Workout complete!", style = MaterialTheme.typography.headlineMedium)
            if (session != null) {
                Text(session.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (session != null) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatTile("${session.exercisesCompleted}", "Exercises")
                    StatTile(Format.kcal(session.kcal).removeSuffix(" kcal"), "Kcal", accent = MaterialTheme.colorScheme.secondary)
                    StatTile(Format.clock(session.totalSeconds), "Duration")
                }
            }
        }

        if (state.earned.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp)) {
                    Text("New badge${if (state.earned.size > 1) "s" else ""}!", style = MaterialTheme.typography.titleMedium)
                    state.earned.forEach {
                        Text("🏅 ${it.title} — ${it.description}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }

        if (state.adaptive) {
            Spacer(Modifier.height(16.dp))
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("How did that feel?", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your answer tunes the reps and timing next time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Feedback.entries.forEach { f ->
                            FilterChip(selected = state.feedback == f, onClick = { viewModel.giveFeedback(f) }, label = { Text(f.label) })
                        }
                    }
                    state.swapped?.let {
                        Text(
                            if (it == SwapDirection.EASIER) "We've swapped in easier variations of some moves."
                            else "We've swapped in harder variations of some moves.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        WeightCard(state, onSave = viewModel::logWeight)

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = {
                val s = session ?: return@OutlinedButton
                val text = "I just finished ${s.title}: ${s.exercisesCompleted} exercises, " +
                    "${Format.minutes(s.totalSeconds)}, ${Format.kcal(s.kcal)} 💪 #HomeFit"
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share workout"),
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Share")
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(28.dp)) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WeightCard(state: CompleteState, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    // Pre-fill with the last weigh-in once it has loaded, unless the user has started typing.
    androidx.compose.runtime.LaunchedEffect(state.lastWeightKg) {
        val last = state.lastWeightKg
        if (text.isEmpty() && last != null) text = "%.1f".format(Format.weightValue(last, state.units))
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Log today's weight", style = MaterialTheme.typography.titleMedium)
            if (state.weightLogged) {
                Text(
                    "Saved: ${state.lastWeightKg?.let { Format.weight(it, state.units) }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        label = { Text("Weight (${Format.weightUnit(state.units)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    val value = text.replace(',', '.').toDoubleOrNull()
                    Button(onClick = { value?.let(onSave) }, enabled = value != null && value in 20.0..700.0) { Text("Save") }
                }
            }
        }
    }
}
