package com.radley.homefit.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.Measure
import com.radley.homefit.ui.components.ExerciseImage
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.LevelBolts
import com.radley.homefit.ui.components.Pill
import com.radley.homefit.ui.components.color

/**
 * How to do a move: the animation, steps, tips and muscles, with links to its easier and harder
 * variants. Following a link swaps the sheet's content in place rather than stacking sheets.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseSheet(
    exercise: Exercise,
    measure: Measure?,
    content: Content?,
    onDismiss: () -> Unit,
) {
    var shown by remember(exercise.id) { mutableStateOf(exercise) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            ExerciseImage(shown, Modifier.fillMaxWidth().aspectRatio(1.3f))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(shown.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                LevelBolts(shown.level, size = 18.dp)
            }
            val shownMeasure = if (shown.id == exercise.id && measure != null) measure else shown.defaultMeasure
            Text(
                Format.measure(shownMeasure, shown.perSide),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.focus.forEach { Pill(it.label, it.color) }
                if (shown.equipment != Equipment.NONE) Pill(shown.equipment.label, MaterialTheme.colorScheme.onSurfaceVariant)
                if (!shown.noJump) Pill("Jumping", MaterialTheme.colorScheme.secondary)
            }

            Heading("How to do it")
            shown.instructions.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (shown.tips.isNotEmpty()) {
                Heading("Coach tips")
                shown.tips.forEach { tip ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (shown.primaryMuscles.isNotEmpty()) {
                Heading("Muscles")
                Text(
                    buildString {
                        append(shown.primaryMuscles.joinToString(", ") { Format.muscle(it) })
                        if (shown.secondaryMuscles.isNotEmpty()) {
                            append("  ·  also ")
                            append(shown.secondaryMuscles.joinToString(", ") { Format.muscle(it) })
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val variants = listOfNotNull(
                shown.easierId?.let { content?.exercises?.get(it) }?.let { "Easier" to it },
                shown.harderId?.let { content?.exercises?.get(it) }?.let { "Harder" to it },
            )
            if (variants.isNotEmpty()) {
                Heading("Variations")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    variants.forEach { (label, e) ->
                        AssistChip(onClick = { shown = e }, label = { Text("$label: ${e.name}") })
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

/** One-tap replace: pick any of [options] to swap in for the current move. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplaceSheet(
    current: Exercise,
    options: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Replace ${current.name}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(options, key = { it.id }) { e ->
                ExercisePickRow(e, onClick = { onPick(e) }, badge = when (e.id) {
                    current.easierId -> "Easier"
                    current.harderId -> "Harder"
                    else -> null
                })
            }
        }
    }
}

@Composable
fun ExercisePickRow(exercise: Exercise, onClick: () -> Unit, badge: String? = null, trailing: (@Composable () -> Unit)? = null) {
    com.radley.homefit.ui.components.ListRow(
        title = exercise.name,
        subtitle = listOfNotNull(badge, exercise.focus.joinToString { it.label }, Format.measure(exercise.defaultMeasure, exercise.perSide)).joinToString(" · "),
        onClick = onClick,
        trailing = trailing ?: {
            ExerciseImage(exercise, Modifier.size(52.dp), animate = false, corner = 10.dp)
        },
    )
}
