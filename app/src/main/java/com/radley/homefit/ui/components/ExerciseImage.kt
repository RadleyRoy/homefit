package com.radley.homefit.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.FocusArea
import kotlinx.coroutines.delay

/**
 * The exercise "animation": the dataset ships a start and an end photo per move, and
 * cross-fading between them on a steady beat reads as the motion. Exercises without photos
 * yet show their focus-area glyph instead, so the layout never collapses.
 *
 * Photos are on white, so they sit on a white tile even in dark theme rather than being
 * inverted or dimmed.
 */
@Composable
fun ExerciseImage(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    corner: Dp = 16.dp,
    frameMs: Long = 900,
) {
    val shape = RoundedCornerShape(corner)
    if (exercise.images.isEmpty()) {
        Pictogram(exercise.focus.firstOrNull() ?: FocusArea.FULL_BODY, modifier.clip(shape))
        return
    }
    var frame by remember(exercise.id) { mutableIntStateOf(0) }
    if (animate && exercise.images.size > 1) {
        LaunchedEffect(exercise.id) {
            while (true) {
                delay(frameMs)
                frame = (frame + 1) % exercise.images.size
            }
        }
    }
    Box(modifier.clip(shape).background(Color.White)) {
        Crossfade(targetState = frame, animationSpec = tween(350), label = "exercise-frame") { index ->
            AsyncImage(
                model = ContentRepository.imageUri(exercise.images[index]),
                contentDescription = exercise.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun Pictogram(focus: FocusArea, modifier: Modifier = Modifier) {
    Box(
        modifier.background(focus.color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            focus.icon,
            contentDescription = null,
            tint = focus.color,
            modifier = Modifier.fillMaxSize(0.42f),
        )
    }
}
