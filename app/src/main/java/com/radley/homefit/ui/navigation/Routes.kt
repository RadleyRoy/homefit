package com.radley.homefit.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object OnboardingRoute
@Serializable data object TabsRoute

/** [ref] is a [com.radley.homefit.domain.WorkoutRef] encoding throughout. */
@Serializable data class WorkoutDetailRoute(val ref: String)
@Serializable data object PlayerRoute
@Serializable data class CompleteRoute(val sessionId: Long)
@Serializable data class ProgramRoute(val programId: String)

/** Null [ref] creates a new custom workout. */
@Serializable data class EditorRoute(val ref: String? = null)
@Serializable data class LibraryRoute(val category: String? = null, val title: String? = null, val favorites: Boolean = false)

@Serializable data object HistoryRoute
@Serializable data object AchievementsRoute
@Serializable data object ProfileRoute
@Serializable data object PlanRoute
@Serializable data object WorkoutSettingsRoute
@Serializable data object RemindersRoute
@Serializable data object DataRoute
@Serializable data object AboutRoute
@Serializable data object CoachRoute
@Serializable data object CoachSettingsRoute
