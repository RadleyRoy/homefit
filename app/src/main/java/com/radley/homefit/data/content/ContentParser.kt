package com.radley.homefit.data.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything bundled in `assets/content`, indexed for lookup. */
class Content(
    val exercises: Map<String, Exercise>,
    val workouts: Map<String, WorkoutTemplate>,
    val programs: Map<String, Program>,
    /** Names of the photos in `content/covers`, without the extension. */
    val covers: Set<String> = emptySet(),
) {
    fun exercise(id: String): Exercise = exercises[id] ?: error("Unknown exercise $id")

    val libraryWorkouts: List<WorkoutTemplate> by lazy {
        workouts.values.filter { it.category.inLibrary }
    }

    fun focusWorkout(focus: FocusArea, level: Level): WorkoutTemplate? =
        workouts.values.firstOrNull {
            it.category == WorkoutCategory.FOCUS && it.focus == focus && it.level == level
        }

    /**
     * The cover photo for a workout or program: one made for it, else its body area's, else its
     * category's. Null when none of those exist (custom workouts, for example).
     */
    fun cover(id: String?, focus: FocusArea? = null, category: WorkoutCategory? = null): String? =
        listOfNotNull(id, focus?.let { "focus_${it.name}" }, category?.let { "category_${it.name}" })
            .firstOrNull { it in covers }
}

/**
 * Pure JSON → model mapping, kept free of Android so the content-validation tests can run it
 * straight against the files in `src/main/assets`.
 */
object ContentParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(exercisesJson: String, workoutsJson: String, programsJson: String, covers: Set<String> = emptySet()): Content {
        val exercises = json.decodeFromString<List<ExerciseDto>>(exercisesJson)
            .map { it.toModel() }
            .associateBy { it.id }
        val workouts = json.decodeFromString<WorkoutsFile>(workoutsJson).workouts
            .map { it.toModel() }
            .associateBy { it.id }
        val programs = json.decodeFromString<ProgramsFile>(programsJson).programs
            .map { it.toModel() }
            .associateBy { it.id }
        return Content(exercises, workouts, programs, covers)
    }

    /** `"squats x15"` → item. Also the storage format for edited and custom workouts. */
    fun parseItem(text: String): WorkoutItem {
        val parts = text.trim().split(' ')
        require(parts.size == 2) { "Expected \"<exercise> <measure>\", got \"$text\"" }
        return WorkoutItem(parts[0], Measure.parse(parts[1]))
    }

    fun encodeItems(items: List<WorkoutItem>): String =
        items.joinToString(";") { "${it.exerciseId} ${it.measure.encode()}" }

    fun decodeItems(encoded: String): List<WorkoutItem> =
        if (encoded.isBlank()) emptyList() else encoded.split(';').map(::parseItem)

    @Serializable
    private data class ExerciseDto(
        val id: String,
        val name: String,
        val category: ExerciseCategory,
        val level: Level,
        val equipment: Equipment,
        val focus: List<FocusArea>,
        val primaryMuscles: List<String> = emptyList(),
        val secondaryMuscles: List<String> = emptyList(),
        val measure: String,
        val perSide: Boolean = false,
        val met: Double,
        val noJump: Boolean = true,
        val instructions: List<String>,
        val tips: List<String> = emptyList(),
        val images: List<String> = emptyList(),
        val easier: String? = null,
        val harder: String? = null,
    ) {
        fun toModel() = Exercise(
            id = id,
            name = name,
            category = category,
            level = level,
            equipment = equipment,
            focus = focus,
            primaryMuscles = primaryMuscles,
            secondaryMuscles = secondaryMuscles,
            defaultMeasure = Measure.parse(measure),
            perSide = perSide,
            met = met,
            noJump = noJump,
            instructions = instructions,
            tips = tips,
            images = images,
            easierId = easier,
            harderId = harder,
        )
    }

    @Serializable
    private data class WorkoutsFile(val workouts: List<WorkoutDto>)

    @Serializable
    private data class WorkoutDto(
        val id: String,
        val name: String,
        val description: String = "",
        val category: WorkoutCategory,
        val focus: FocusArea? = null,
        val level: Level,
        val rest: Int? = null,
        val tags: List<String> = emptyList(),
        val items: List<String>,
    ) {
        fun toModel() = WorkoutTemplate(
            id = id,
            name = name,
            description = description,
            category = category,
            focus = focus,
            level = level,
            restSeconds = rest,
            tags = tags.toSet(),
            items = items.map(::parseItem),
        )
    }

    @Serializable
    private data class ProgramsFile(val programs: List<ProgramDto>)

    @Serializable
    private data class ProgressionDto(
        val type: String,
        val steps: List<Double> = emptyList(),
        val from: Double = 1.0,
        val to: Double = 1.0,
    ) {
        fun toModel(): Progression = when (type) {
            "WEEKLY" -> Progression.Weekly(steps)
            "LINEAR" -> Progression.Linear(from, to)
            else -> error("Unknown progression $type")
        }
    }

    @Serializable
    private data class ProgramDto(
        val id: String,
        val name: String,
        val description: String,
        val category: ProgramCategory,
        val focus: FocusArea,
        val days: Int,
        val restDays: List<Int> = emptyList(),
        val progression: ProgressionDto,
        val levels: Map<Level, List<String>>,
    ) {
        fun toModel() = Program(
            id = id,
            name = name,
            description = description,
            category = category,
            focus = focus,
            days = days,
            restDays = restDays.toSet(),
            progression = progression.toModel(),
            levels = levels,
        )
    }
}
