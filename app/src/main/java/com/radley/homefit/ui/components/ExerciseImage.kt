package com.radley.homefit.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.platform.ExerciseVideos
import kotlinx.coroutines.delay

/**
 * The exercise "animation": most moves ship a start and an end photo, and cross-fading between
 * them on a steady beat reads as the motion (burpees have four). Large views [fit] the whole
 * photo so no limb is cut off; list thumbnails crop to fill their square.
 *
 * The few moves without a freely licensed photo show a "watch how" panel instead, which opens
 * a YouTube search for the move: a demonstration beats a glyph that only names the body area.
 */
@Composable
fun ExerciseImage(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    corner: Dp = 16.dp,
    frameMs: Long = 900,
    fit: Boolean = true,
) {
    val shape = RoundedCornerShape(corner)
    if (exercise.images.isEmpty()) {
        WatchHow(exercise, modifier.clip(shape))
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
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Crossfade(targetState = frame, animationSpec = tween(350), label = "exercise-frame") { index ->
            AsyncImage(
                model = ContentRepository.imageUri(exercise.images[index]),
                contentDescription = exercise.name,
                contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WatchHow(exercise: Exercise, modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
        // Small thumbnails sit inside tappable rows, so only the large view opens the video.
        val roomy = maxWidth > 140.dp
        val iconSize = if (roomy) 56.dp else maxWidth * 0.5f
        val tap = if (roomy) {
            Modifier.fillMaxSize().clickable(onClickLabel = "Watch how to do ${exercise.name} on YouTube") { ExerciseVideos.open(context, exercise.name) }
        } else Modifier
        Column(tap, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                Icons.Rounded.PlayCircle,
                contentDescription = if (roomy) null else "Watch how on YouTube",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(iconSize),
            )
            if (roomy) {
                Text(
                    "Watch how on YouTube",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "No photo of this move yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
