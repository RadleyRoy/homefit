package com.radley.homefit.ui.complete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Achievement
import com.radley.homefit.domain.BodyMetrics
import com.radley.homefit.domain.Feedback
import com.radley.homefit.domain.SwapDirection
import com.radley.homefit.domain.WorkoutRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompleteState(
    val session: SessionEntity? = null,
    val earned: List<Achievement> = emptyList(),
    val units: UnitSystem = UnitSystem.METRIC,
    val lastWeightKg: Double? = null,
    val weightLogged: Boolean = false,
    val feedback: Feedback? = null,
    /** Set when feedback crossed the swap threshold, so the screen can say what changed. */
    val swapped: SwapDirection? = null,
    val adaptive: Boolean = false,
)

class CompleteViewModel(private val sessionId: Long) : ViewModel() {

    private val history = ServiceLocator.history

    private val _state = MutableStateFlow(CompleteState())
    val state: StateFlow<CompleteState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val session = history.session(sessionId)
            _state.value = CompleteState(
                session = session,
                earned = ServiceLocator.session.lastEarned,
                units = ServiceLocator.settings.currentAppPrefs().units,
                lastWeightKg = if (history.hasWeight()) history.latestWeightKg() else null,
                feedback = session?.feedback?.let { runCatching { Feedback.valueOf(it) }.getOrNull() },
                adaptive = session?.let { WorkoutRef.decode(it.refKey).difficultyKey != null } ?: false,
            )
        }
    }

    /** Feedback is recorded once per session; changing your mind just updates the label. */
    fun giveFeedback(feedback: Feedback) = viewModelScope.launch {
        val session = _state.value.session ?: return@launch
        val first = _state.value.feedback == null
        history.setFeedback(session.id, feedback.name)
        val swapped = if (first) ServiceLocator.workouts.applyFeedback(WorkoutRef.decode(session.refKey), feedback) else null
        _state.update { it.copy(feedback = feedback, swapped = swapped ?: it.swapped) }
    }

    fun logWeight(value: Double) = viewModelScope.launch {
        val kg = if (_state.value.units == UnitSystem.METRIC) value else BodyMetrics.lbToKg(value)
        history.logWeight(history.today().toEpochDay(), kg)
        history.evaluateAchievements()
        _state.update { it.copy(weightLogged = true, lastWeightKg = kg) }
    }
}
