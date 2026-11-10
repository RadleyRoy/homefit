package com.radley.homefit.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.ExerciseCategory
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Calories
import com.radley.homefit.domain.ResolvedWorkout
import com.radley.homefit.domain.WorkoutRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WorkoutDetailState(
    val loading: Boolean = true,
    val workout: ResolvedWorkout? = null,
    val weightKg: Double = Calories.DEFAULT_WEIGHT_KG,
    val units: UnitSystem = UnitSystem.METRIC,
)

class WorkoutDetailViewModel(private val ref: WorkoutRef) : ViewModel() {

    private val workouts = ServiceLocator.workouts

    private val _state = MutableStateFlow(WorkoutDetailState())
    val state: StateFlow<WorkoutDetailState> = _state.asStateFlow()

    val favorite: StateFlow<Boolean> = workouts.favorites
        .map { ref.encode() in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private var content: Content? = null

    init {
        refresh()
    }

    /** Called on resume too, so edits made in the editor show when navigating back. */
    fun refresh() = viewModelScope.launch {
        content = workouts.content()
        _state.value = WorkoutDetailState(
            loading = false,
            workout = workouts.resolve(ref),
            weightKg = ServiceLocator.history.latestWeightKg(),
            units = ServiceLocator.settings.currentAppPrefs().units,
        )
    }

    fun toggleFavorite() = viewModelScope.launch { workouts.setFavorite(ref, !favorite.value) }

    fun resetToDefault() = viewModelScope.launch {
        workouts.resetEdits(ref)
        refresh()
    }

    /**
     * Same-purpose swaps first: the move's own easier and harder variants, then moves that train
     * the same area the same way with gear the user is likely to have.
     */
    fun alternativesFor(index: Int): List<Exercise> {
        val c = content ?: return emptyList()
        val w = _state.value.workout ?: return emptyList()
        val current = w.steps.getOrNull(index)?.exercise ?: return emptyList()
        val variants = listOfNotNull(current.easierId, current.harderId).mapNotNull { c.exercises[it] }
        val stretchy = current.category == ExerciseCategory.STRETCH
        val similar = c.exercises.values.filter { e ->
            e.id != current.id &&
                e !in variants &&
                (e.category == ExerciseCategory.STRETCH) == stretchy &&
                e.focus.any { it in current.focus } &&
                (e.equipment == current.equipment || e.equipment == Equipment.NONE)
        }.sortedBy { kotlin.math.abs(it.level.ordinal - current.level.ordinal) }
        return variants + similar
    }

    fun replace(index: Int, replacement: Exercise) = viewModelScope.launch {
        val w = _state.value.workout ?: return@launch
        val items = w.editableItems.toMutableList()
        val old = items.getOrNull(index) ?: return@launch
        val sameKind = (old.measure is Measure.Reps) == (replacement.defaultMeasure is Measure.Reps)
        items[index] = WorkoutItem(replacement.id, if (sameKind) old.measure else replacement.defaultMeasure)
        workouts.saveEdits(ref, items)
        refresh()
    }

    /** Hands the workout to the session controller; the caller navigates to the player. */
    fun start(onStarted: () -> Unit) = viewModelScope.launch {
        val workout = workouts.resolve(ref) ?: return@launch
        ServiceLocator.session.start(workout, ServiceLocator.settings.currentWorkoutPrefs())
        onStarted()
    }
}
