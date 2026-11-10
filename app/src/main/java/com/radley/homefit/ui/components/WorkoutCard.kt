package com.radley.homefit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.data.content.WorkoutTemplate
import com.radley.homefit.domain.Calories
import com.radley.homefit.domain.ResolvedStep
import com.radley.homefit.domain.WorkoutRef

/** A list entry for anything playable; cheap to build, so lists don't resolve each workout. */
data class WorkoutCardModel(
    val ref: WorkoutRef,
    val title: String,
    val subtitle: String?,
    val category: WorkoutCategory,
    val focus: FocusArea?,
    val level: Level?,
    val seconds: Int,
    val exerciseCount: Int,
    val kcal: Double,
    val tags: Set<String> = emptySet(),
    val noJump: Boolean = true,
    /** Cover or first-exercise photo, as an asset URI; null draws the category glyph. */
    val image: String? = null,
) {
    val color: Color get() = focus?.color ?: category.color
    val icon: ImageVector get() = focus?.icon ?: category.icon
}

fun estimate(items: List<WorkoutItem>, content: Content, restSeconds: Int, weightKg: Double): Pair<Int, Double> {
    val steps = items.mapNotNull { item -> content.exercises[item.exerciseId]?.let { ResolvedStep(it, item.measure) } }
    val seconds = steps.sumOf { it.estimatedSeconds } + restSeconds * (steps.size - 1).coerceAtLeast(0)
    val kcal = steps.sumOf { Calories.kcal(it.exercise.met, weightKg, it.estimatedSeconds * 1000L) }
    return seconds to kcal
}

fun WorkoutTemplate.toCard(content: Content, defaultRest: Int, weightKg: Double): WorkoutCardModel {
    val (seconds, kcal) = estimate(items, content, restSeconds ?: defaultRest, weightKg)
    return WorkoutCardModel(
        ref = WorkoutRef.Builtin(id),
        title = name,
        subtitle = description.takeIf { it.isNotBlank() },
        category = category,
        focus = focus,
        level = level,
        seconds = seconds,
        exerciseCount = items.size,
        kcal = kcal,
        tags = tags,
        noJump = items.all { content.exercises[it.exerciseId]?.noJump != false },
        image = content.coverUri(id, focus, category) ?: content.thumbnailFor(items, focus),
    )
}

@Composable
fun WorkoutRow(
    card: WorkoutCardModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    favorite: Boolean = false,
) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(card.image, card.icon, Modifier.size(width = 84.dp, height = 64.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.Favorite, contentDescription = "Favourite", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(top = 2.dp).width(16.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${Format.minutes(card.seconds)} · ${card.exerciseCount} exercises",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    card.level?.let { LevelBolts(it) }
                }
            }
        }
    }
}
