package com.radley.homefit.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radley.homefit.data.content.Content
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Feedback
import com.radley.homefit.session.ActiveWorkout
import com.radley.homefit.session.SessionEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** A thin view of [com.radley.homefit.session.WorkoutSessionController], which owns the workout. */
class PlayerViewModel : ViewModel() {

    private val session = ServiceLocator.session

    val active: StateFlow<ActiveWorkout?> = session.active

    val keepScreenOn: StateFlow<Boolean> = ServiceLocator.settings.workoutPrefs
        .map { it.keepScreenOn }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var finishing = false

    /** Session id when the workout was saved; null when it was abandoned. */
    val finished: Flow<Long?> = session.events
        .onEach { finishing = true }
        .map { (it as? SessionEvent.Finished)?.sessionId }

    /**
     * True between the last exercise ending and the session being saved, when [active] has
     * gone null but the screen should wait for [finished] rather than bail out.
     */
    fun isFinishing() = finishing || session.isSaving

    suspend fun content(): Content = ServiceLocator.content.get()

    fun togglePause() = session.togglePause()
    fun pause() = session.pause()
    fun resume() = session.resume()
    fun skip() = session.skip()
    fun previous() = session.previous()
    fun complete() = session.complete()
    fun extendRest() = session.extendRest()
    fun quit(save: Boolean, reason: Feedback?) = session.quit(save, reason)
}
