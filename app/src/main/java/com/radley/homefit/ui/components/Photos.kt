package com.radley.homefit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Program
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.ui.theme.Bg

/**
 * A photo cropped to fill its box, for cards and headers. [model] is an asset URI from
 * [ContentRepository]; without one the box shows [fallback] on a tinted ground, so layouts keep
 * their shape for workouts that have no photo (an empty custom workout, say).
 */
@Composable
fun CoverImage(model: String?, fallback: ImageVector, modifier: Modifier = Modifier, shape: Shape? = null) {
    val clipped = if (shape != null) modifier.clip(shape) else modifier
    if (model == null) {
        Box(clipped.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(fallback, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxSize(0.45f))
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = clipped.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** A cover with a dark fade at the bottom so white text laid over it stays readable. */
@Composable
fun CoverWithScrim(model: String?, fallback: ImageVector, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier) {
        CoverImage(model, fallback, Modifier.matchParentSize())
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Bg.copy(alpha = 0.92f)),
            ),
        )
        content()
    }
}

/** Cover for a library workout, as an asset URI. */
fun Content.coverUri(id: String?, focus: FocusArea?, category: WorkoutCategory?): String? =
    cover(id, focus, category)?.let(ContentRepository::coverUri)

/**
 * For workouts without a cover of their own (custom, coach-made): the first move that has a
 * photo, then the body area's cover.
 */
fun Content.thumbnailFor(items: List<WorkoutItem>, focus: FocusArea? = null): String? =
    items.firstNotNullOfOrNull { exercises[it.exerciseId]?.images?.firstOrNull() }?.let(ContentRepository::imageUri)
        ?: coverUri(null, focus, null)

/** Every program and body area ships a cover; BundledContentTest keeps it that way. */
val Program.coverUri: String get() = ContentRepository.coverUri(id)

val FocusArea.coverUri: String get() = ContentRepository.coverUri("focus_$name")
