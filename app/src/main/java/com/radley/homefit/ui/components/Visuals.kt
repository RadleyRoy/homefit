package com.radley.homefit.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Accessibility
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Rowing
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.SportsMartialArts
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.ProgramCategory
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.ui.theme.Accent

/**
 * Deep Teal has a single accent, so body areas and categories are told apart by their glyph
 * (cards, chips, the placeholder for exercises without photos), never by colour. These stay as
 * properties so a future palette can vary them again without touching every call site.
 */
val FocusArea.color: Color get() = Accent

val FocusArea.icon: ImageVector
    get() = when (this) {
        FocusArea.ABS -> Icons.Rounded.SportsGymnastics
        FocusArea.CHEST -> Icons.Rounded.FitnessCenter
        FocusArea.ARMS -> Icons.Rounded.SportsMartialArts
        FocusArea.LEGS -> Icons.AutoMirrored.Rounded.DirectionsRun
        FocusArea.BUTT -> Icons.Rounded.AccessibilityNew
        FocusArea.SHOULDERS_BACK -> Icons.Rounded.Rowing
        FocusArea.FULL_BODY -> Icons.Rounded.Accessibility
    }

val WorkoutCategory.icon: ImageVector
    get() = when (this) {
        WorkoutCategory.FOCUS -> Icons.Rounded.FitnessCenter
        WorkoutCategory.WARMUP -> Icons.Rounded.LocalFireDepartment
        WorkoutCategory.STRETCH -> Icons.Rounded.SelfImprovement
        WorkoutCategory.HIIT -> Icons.Rounded.Bolt
        WorkoutCategory.QUICK -> Icons.Rounded.Timer
        WorkoutCategory.LOW_IMPACT -> Icons.Rounded.Spa
        WorkoutCategory.CHAIR -> Icons.Rounded.Chair
        WorkoutCategory.DUMBBELL -> Icons.Rounded.FitnessCenter
        WorkoutCategory.RUNNING -> Icons.AutoMirrored.Rounded.DirectionsRun
        WorkoutCategory.SPECIAL -> Icons.Rounded.Star
        WorkoutCategory.PROGRAM_DAY -> Icons.Rounded.EmojiEvents
        WorkoutCategory.CUSTOM -> Icons.Rounded.Edit
        WorkoutCategory.GENERATED -> Icons.Rounded.AutoAwesome
    }

val WorkoutCategory.color: Color get() = Accent

val ProgramCategory.icon: ImageVector
    get() = when (this) {
        ProgramCategory.CHALLENGE -> Icons.Rounded.EmojiEvents
        ProgramCategory.RECOVERY -> Icons.Rounded.Healing
    }

/** Intensity is shown by how many bolts are lit, so one colour is enough. */
val Level.color: Color get() = Accent

/** One to three filled bolts, the usual shorthand for intensity. */
val Level.bolts: Int get() = ordinal + 1
