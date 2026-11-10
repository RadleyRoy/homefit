package com.radley.homefit.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.ProgramStatus
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.ContentParser
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.ProgramCategory
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.data.db.CustomWorkoutEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Calories
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.WorkoutCardModel
import com.radley.homefit.ui.components.estimate
import com.radley.homefit.ui.components.thumbnailFor
import com.radley.homefit.ui.components.toCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class DurationFilter(val label: String, val range: IntRange) {
    ANY("Any length", 0..Int.MAX_VALUE),
    SHORT("Under 10 min", 0 until 600),
    MEDIUM("10–20 min", 600 until 1200),
    LONG("20+ min", 1200..Int.MAX_VALUE),
}

data class DiscoverFilters(
    val query: String = "",
    val focus: FocusArea? = null,
    val level: Level? = null,
    val duration: DurationFilter = DurationFilter.ANY,
    val noEquipment: Boolean = false,
    val noJumping: Boolean = false,
) {
    val active: Boolean get() = query.isNotBlank() || focus != null || level != null ||
        duration != DurationFilter.ANY || noEquipment || noJumping
}

data class DiscoverState(
    val loading: Boolean = true,
    val sections: List<Pair<WorkoutCategory, List<WorkoutCardModel>>> = emptyList(),
    val recovery: List<ProgramStatus> = emptyList(),
    val custom: List<WorkoutCardModel> = emptyList(),
    val favorites: Set<String> = emptySet(),
    /** Every library and custom workout by encoded ref, for the favourites list. */
    val allCards: Map<String, WorkoutCardModel> = emptyMap(),
    val results: List<WorkoutCardModel> = emptyList(),
    val exerciseResults: List<Exercise> = emptyList(),
    val content: Content? = null,
)

class DiscoverViewModel : ViewModel() {

    private val _filters = MutableStateFlow(DiscoverFilters())
    val filters: StateFlow<DiscoverFilters> = _filters

    private val content = flow { emit(ServiceLocator.content.get()) }

    private val sectionOrder = listOf(
        WorkoutCategory.SPECIAL, WorkoutCategory.HIIT, WorkoutCategory.QUICK, WorkoutCategory.STRETCH,
        WorkoutCategory.WARMUP, WorkoutCategory.LOW_IMPACT, WorkoutCategory.CHAIR, WorkoutCategory.RUNNING,
        WorkoutCategory.DUMBBELL,
    )

    val state: StateFlow<DiscoverState> = combine(
        content,
        ServiceLocator.settings.workoutPrefs,
        ServiceLocator.workouts.customWorkouts,
        ServiceLocator.workouts.favorites,
        combine(ServiceLocator.programs.progress, ServiceLocator.history.weights, _filters) { a, b, c -> Triple(a, b, c) },
    ) { content, prefs, custom, favorites, (progress, weights, filters) ->
        val weight = weights.lastOrNull()?.kg ?: Calories.DEFAULT_WEIGHT_KG
        val library = content.libraryWorkouts.map { it.toCard(content, prefs.restSeconds, weight) }
        val customCards = custom.map { it.toCard(content, prefs.restSeconds, weight) }

        DiscoverState(
            loading = false,
            sections = sectionOrder.map { cat -> cat to library.filter { it.category == cat } }.filter { it.second.isNotEmpty() },
            recovery = content.programs.values.filter { it.category == ProgramCategory.RECOVERY }
                .map { ServiceLocator.programs.status(it, progress[it.id]) },
            custom = customCards,
            favorites = favorites,
            allCards = (library + customCards).associateBy { it.ref.encode() },
            results = if (filters.active) (library + customCards).filter { matches(it, filters, content) } else emptyList(),
            exerciseResults = if (filters.query.length >= 2) {
                content.exercises.values.filter { it.name.contains(filters.query.trim(), ignoreCase = true) }.take(20)
            } else emptyList(),
            content = content,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiscoverState())

    fun update(transform: (DiscoverFilters) -> DiscoverFilters) = _filters.update(transform)

    fun clear() {
        _filters.value = DiscoverFilters()
    }

    private fun matches(card: WorkoutCardModel, f: DiscoverFilters, content: Content): Boolean {
        val q = f.query.trim()
        if (q.isNotEmpty()) {
            val haystack = listOfNotNull(card.title, card.subtitle, card.category.label, card.focus?.label) + card.tags
            if (haystack.none { it.contains(q, ignoreCase = true) }) return false
        }
        if (f.focus != null && card.focus != f.focus) return false
        if (f.level != null && card.level != f.level) return false
        if (card.seconds !in f.duration.range) return false
        if (f.noJumping && !card.noJump) return false
        if (f.noEquipment) {
            val ref = card.ref as? WorkoutRef.Builtin
            val items = ref?.let { content.workouts[it.id]?.items }.orEmpty()
            if (items.any { content.exercises[it.exerciseId]?.equipment !in setOf(Equipment.NONE, null) }) return false
        }
        return true
    }
}

fun CustomWorkoutEntity.toCard(content: Content, defaultRest: Int, weightKg: Double): WorkoutCardModel {
    val items = ContentParser.decodeItems(this.items)
    val (seconds, kcal) = estimate(items, content, restSeconds ?: defaultRest, weightKg)
    return WorkoutCardModel(
        ref = WorkoutRef.Custom(id),
        title = name,
        subtitle = null,
        category = WorkoutCategory.CUSTOM,
        focus = null,
        level = null,
        seconds = seconds,
        exerciseCount = items.size,
        kcal = kcal,
        noJump = items.all { content.exercises[it.exerciseId]?.noJump != false },
        image = content.thumbnailFor(items),
    )
}
