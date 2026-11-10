package com.radley.homefit.testing

import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.ContentParser
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.ExerciseCategory
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.Measure
import java.io.File

/** The real bundled content. Unit tests run with the module directory as the working dir. */
val bundledContent: Content by lazy {
    fun read(name: String) = File("src/main/assets/content/$name").readText()
    ContentParser.parse(read("exercises.json"), read("workouts.json"), read("programs.json"))
}

fun exercise(
    id: String,
    measure: Measure = Measure.Reps(10),
    perSide: Boolean = false,
    easier: String? = null,
    harder: String? = null,
    met: Double = 5.0,
) = Exercise(
    id = id,
    name = id,
    category = ExerciseCategory.STRENGTH,
    level = Level.BEGINNER,
    equipment = Equipment.NONE,
    focus = listOf(FocusArea.FULL_BODY),
    primaryMuscles = emptyList(),
    secondaryMuscles = emptyList(),
    defaultMeasure = measure,
    perSide = perSide,
    met = met,
    noJump = true,
    instructions = listOf("Do it."),
    tips = emptyList(),
    images = emptyList(),
    easierId = easier,
    harderId = harder,
)
