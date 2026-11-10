package com.radley.homefit.ui.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.coach.CoachException
import com.radley.homefit.coach.CoachService
import com.radley.homefit.data.content.ContentParser
import com.radley.homefit.data.db.CoachMessageEntity
import com.radley.homefit.data.WorkoutRepository
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.WorkoutRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class CoachMode { CHAT, WORKOUT }

class CoachViewModel : ViewModel() {

    private val db = ServiceLocator.database
    private val coach = ServiceLocator.coach

    val messages: StateFlow<List<CoachMessageEntity>> = db.coachMessages().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val hasKey: StateFlow<Boolean> = ServiceLocator.coachKeys.hasKey

    /** The reply as it streams in; null when nothing is in flight. */
    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _errors = Channel<String>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    private val _saved = Channel<WorkoutRef>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    private var job: Job? = null

    fun send(text: String, mode: CoachMode) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _busy.value) return
        job = viewModelScope.launch {
            _busy.value = true
            val now = System.currentTimeMillis()
            val userText = if (mode == CoachMode.WORKOUT) "Make me a workout: $trimmed" else trimmed
            db.coachMessages().insert(CoachMessageEntity(role = CoachService.ROLE_USER, text = userText, createdAt = now))
            try {
                when (mode) {
                    CoachMode.CHAT -> {
                        _streaming.value = ""
                        val reply = coach.chat(db.coachMessages().all()) { delta ->
                            _streaming.value = (_streaming.value ?: "") + delta
                        }
                        db.coachMessages().insert(CoachMessageEntity(role = CoachService.ROLE_ASSISTANT, text = reply, createdAt = System.currentTimeMillis()))
                    }
                    CoachMode.WORKOUT -> {
                        val workout = coach.generateWorkout(trimmed)
                        db.coachMessages().insert(
                            CoachMessageEntity(
                                role = CoachService.ROLE_ASSISTANT,
                                text = workout.notes.ifBlank { "Here's a workout for you." },
                                createdAt = System.currentTimeMillis(),
                                workoutName = workout.name,
                                workoutItems = ContentParser.encodeItems(workout.items),
                            ),
                        )
                    }
                }
            } catch (e: CoachException) {
                _errors.send(e.message ?: "Something went wrong")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _errors.send("Something went wrong: ${e.message}")
            } finally {
                _streaming.value = null
                _busy.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    fun saveWorkout(message: CoachMessageEntity) = viewModelScope.launch {
        val items = message.workoutItems?.let(ContentParser::decodeItems) ?: return@launch
        val id = ServiceLocator.workouts.createCustom(message.workoutName ?: "Coach workout", items, origin = WorkoutRepository.ORIGIN_COACH)
        _saved.send(WorkoutRef.Custom(id))
    }

    fun clear() = viewModelScope.launch { db.coachMessages().clear() }
}
