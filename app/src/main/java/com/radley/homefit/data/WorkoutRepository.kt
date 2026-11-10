package com.radley.homefit.data

import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.ContentParser
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.ProgramDay
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.data.db.CustomWorkoutEntity
import com.radley.homefit.data.db.DifficultyEntity
import com.radley.homefit.data.db.FavoriteEntity
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.data.db.WorkoutOverrideEntity
import com.radley.homefit.domain.DifficultyAdapter
import com.radley.homefit.domain.DifficultyState
import com.radley.homefit.domain.Feedback
import com.radley.homefit.domain.PlanDayResult
import com.radley.homefit.domain.PlanGenerator
import com.radley.homefit.domain.ResolvedStep
import com.radley.homefit.domain.ResolvedWorkout
import com.radley.homefit.domain.SwapDirection
import com.radley.homefit.domain.WorkoutRef
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Turns a [WorkoutRef] into a [ResolvedWorkout], layering in order: the base items (content,
 * custom workout, plan or coach proposal) → the user's saved edits → program progression →
 * adaptive difficulty. Also owns the tables those layers come from.
 */
class WorkoutRepository(
    private val contentRepository: ContentRepository,
    private val db: HomeFitDatabase,
    private val settings: SettingsStore,
) {

    val favorites: Flow<Set<String>> = db.favorites().observeAll().map { rows -> rows.map { it.refKey }.toSet() }
    val customWorkouts: Flow<List<CustomWorkoutEntity>> = db.customWorkouts().observeAll()

    suspend fun content(): Content = contentRepository.get()

    suspend fun resolve(ref: WorkoutRef): ResolvedWorkout? {
        val content = contentRepository.get()
        val prefs = settings.currentWorkoutPrefs()
        val override = db.overrides().byKey(ref.encode())
        val difficulty = ref.difficultyKey?.let { db.difficulty().byKey(it) }
        val multiplier = difficulty?.multiplier ?: 1.0
        val swaps = difficulty?.let { DifficultyAdapter.decodeSwaps(it.swaps) } ?: emptyMap()

        fun build(
            title: String,
            subtitle: String?,
            description: String,
            category: WorkoutCategory,
            base: List<WorkoutItem>,
            rest: Int?,
            level: Level?,
            focus: com.radley.homefit.data.content.FocusArea?,
            progression: Double = 1.0,
            canEdit: Boolean = true,
            coverKey: String? = null,
        ): ResolvedWorkout {
            val items = override?.let { ContentParser.decodeItems(it.items) } ?: base
            val played = DifficultyAdapter.substitute(items, swaps, content)
            // Content can lose an exercise between versions; a stale saved edit shouldn't crash.
            val steps = played.mapNotNull { item ->
                content.exercises[item.exerciseId]?.let { ResolvedStep(it, item.measure.scaled(progression * multiplier)) }
            }
            return ResolvedWorkout(
                ref = ref,
                title = title,
                subtitle = subtitle,
                description = description,
                category = category,
                focus = focus,
                level = level,
                restSeconds = rest ?: prefs.restSeconds,
                steps = steps,
                editableItems = items.filter { it.exerciseId in content.exercises },
                isEdited = override != null || swaps.isNotEmpty(),
                difficultyMultiplier = multiplier,
                canEdit = canEdit,
                cover = content.cover(coverKey, focus, category)?.let(ContentRepository::coverUri)
                    ?: steps.firstNotNullOfOrNull { it.exercise.images.firstOrNull() }?.let(ContentRepository::imageUri),
            )
        }

        return when (ref) {
            is WorkoutRef.Builtin -> content.workouts[ref.id]?.let { t ->
                build(t.name, t.level.label, t.description, t.category, t.items, t.restSeconds, t.level, t.focus, coverKey = t.id)
            }
            is WorkoutRef.ProgramDay -> {
                val program = content.programs[ref.programId] ?: return null
                val level = db.programProgress().byId(program.id)?.level
                    ?.let { runCatching { Level.valueOf(it) }.getOrNull() }
                    ?: program.availableLevels.first()
                when (val plan = program.dayPlan(ref.day, level)) {
                    ProgramDay.Rest -> null
                    is ProgramDay.Train -> {
                        val t = content.workouts[plan.templateId] ?: return null
                        build(
                            title = "Day ${ref.day}",
                            subtitle = program.name,
                            description = program.description,
                            category = t.category,
                            base = t.items,
                            rest = t.restSeconds,
                            level = level,
                            focus = program.focus,
                            progression = plan.factor,
                            coverKey = program.id,
                        )
                    }
                }
            }
            is WorkoutRef.PlanDay -> {
                val app = settings.currentAppPrefs()
                val start = app.planStartEpochDay ?: return null
                val profile = settings.currentProfile()
                when (val day = PlanGenerator(content).dayFor(profile, start, app.planSeed, ref.epochDay, prefs.restSeconds)) {
                    PlanDayResult.Rest -> null
                    is PlanDayResult.Train -> build(
                        title = day.title,
                        subtitle = "My plan",
                        description = "Generated for your goal: ${profile.goal.label.lowercase()}.",
                        category = WorkoutCategory.GENERATED,
                        base = day.items,
                        rest = null,
                        level = profile.level,
                        focus = day.focus,
                    )
                }
            }
            is WorkoutRef.Custom -> db.customWorkouts().byId(ref.id)?.let { c ->
                build(
                    title = c.name,
                    subtitle = if (c.origin == ORIGIN_COACH) "Designed by Claude" else "My workout",
                    description = "",
                    category = WorkoutCategory.CUSTOM,
                    base = ContentParser.decodeItems(c.items),
                    rest = c.restSeconds,
                    level = null,
                    focus = null,
                )
            }
        }
    }

    /** Saves edits to anything but a custom workout; custom workouts are rewritten in place. */
    suspend fun saveEdits(ref: WorkoutRef, items: List<WorkoutItem>) {
        if (ref is WorkoutRef.Custom) {
            val existing = db.customWorkouts().byId(ref.id) ?: return
            db.customWorkouts().upsert(
                existing.copy(items = ContentParser.encodeItems(items), updatedAt = System.currentTimeMillis()),
            )
        } else {
            db.overrides().upsert(
                WorkoutOverrideEntity(ref.encode(), ContentParser.encodeItems(items), System.currentTimeMillis()),
            )
        }
    }

    suspend fun resetEdits(ref: WorkoutRef) {
        db.overrides().delete(ref.encode())
        ref.difficultyKey?.let { db.difficulty().delete(it) }
    }

    suspend fun createCustom(name: String, items: List<WorkoutItem>, restSeconds: Int? = null, origin: String? = null): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        db.customWorkouts().upsert(
            CustomWorkoutEntity(id, name, ContentParser.encodeItems(items), restSeconds, now, now, origin),
        )
        return id
    }

    suspend fun renameCustom(id: String, name: String) {
        val existing = db.customWorkouts().byId(id) ?: return
        db.customWorkouts().upsert(existing.copy(name = name, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCustom(id: String) {
        db.customWorkouts().delete(id)
        db.favorites().delete(WorkoutRef.Custom(id).encode())
    }

    suspend fun setFavorite(ref: WorkoutRef, favorite: Boolean) {
        if (favorite) db.favorites().upsert(FavoriteEntity(ref.encode(), System.currentTimeMillis()))
        else db.favorites().delete(ref.encode())
    }

    /**
     * Applies post-workout feedback. Returns the swap direction when this was the second
     * "too hard" (or "too easy") in a row and moves were replaced, so the UI can say so; the
     * user can undo it from the workout's "Reset to default".
     */
    suspend fun applyFeedback(ref: WorkoutRef, feedback: Feedback): SwapDirection? {
        val key = ref.difficultyKey ?: return null
        val played = resolve(ref)?.steps?.map { WorkoutItem(it.exercise.id, it.measure) } ?: return null
        val current = db.difficulty().byKey(key)
            ?.let { DifficultyState(it.multiplier, it.hardStreak, it.easyStreak, DifficultyAdapter.decodeSwaps(it.swaps)) }
            ?: DifficultyState()
        val outcome = DifficultyAdapter.apply(current, feedback, played, contentRepository.get())
        db.difficulty().upsert(
            DifficultyEntity(
                key = key,
                multiplier = outcome.state.multiplier,
                hardStreak = outcome.state.hardStreak,
                easyStreak = outcome.state.easyStreak,
                swaps = DifficultyAdapter.encodeSwaps(outcome.state.swaps),
            ),
        )
        return outcome.swap?.takeIf { outcome.state.swaps != current.swaps }
    }

    companion object {
        const val ORIGIN_COACH = "coach"
    }
}
