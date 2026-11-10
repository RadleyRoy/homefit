package com.radley.homefit.domain

import com.radley.homefit.data.content.FocusArea

data class AchievementStats(
    val totalWorkouts: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalKcal: Double,
    val totalActiveMinutes: Int,
    val programsCompleted: Int,
    val weightEntries: Int,
    val focusAreasTrained: Set<FocusArea>,
    val earlyBird: Boolean,
    val nightOwl: Boolean,
)

enum class AchievementGroup(val label: String) { WORKOUTS("Workouts"), STREAKS("Streaks"), MILESTONES("Milestones") }

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val group: AchievementGroup,
    val earned: (AchievementStats) -> Boolean,
)

object Achievements {

    val all: List<Achievement> = listOf(
        Achievement("first_workout", "First Step", "Finish your first workout", AchievementGroup.WORKOUTS) { it.totalWorkouts >= 1 },
        Achievement("workouts_10", "Getting Serious", "Finish 10 workouts", AchievementGroup.WORKOUTS) { it.totalWorkouts >= 10 },
        Achievement("workouts_50", "Half Century", "Finish 50 workouts", AchievementGroup.WORKOUTS) { it.totalWorkouts >= 50 },
        Achievement("workouts_100", "Centurion", "Finish 100 workouts", AchievementGroup.WORKOUTS) { it.totalWorkouts >= 100 },
        Achievement("workouts_365", "Year of Sweat", "Finish 365 workouts", AchievementGroup.WORKOUTS) { it.totalWorkouts >= 365 },
        Achievement("streak_3", "On a Roll", "Work out 3 days in a row", AchievementGroup.STREAKS) { it.bestStreak >= 3 },
        Achievement("streak_7", "Full Week", "Work out 7 days in a row", AchievementGroup.STREAKS) { it.bestStreak >= 7 },
        Achievement("streak_14", "Fortnight Fire", "Work out 14 days in a row", AchievementGroup.STREAKS) { it.bestStreak >= 14 },
        Achievement("streak_30", "Unstoppable", "Work out 30 days in a row", AchievementGroup.STREAKS) { it.bestStreak >= 30 },
        Achievement("kcal_1000", "Furnace", "Burn 1,000 kcal in total", AchievementGroup.MILESTONES) { it.totalKcal >= 1_000 },
        Achievement("kcal_5000", "Inferno", "Burn 5,000 kcal in total", AchievementGroup.MILESTONES) { it.totalKcal >= 5_000 },
        Achievement("kcal_20000", "Supernova", "Burn 20,000 kcal in total", AchievementGroup.MILESTONES) { it.totalKcal >= 20_000 },
        Achievement("minutes_100", "Hundred Minutes", "Exercise for 100 minutes in total", AchievementGroup.MILESTONES) { it.totalActiveMinutes >= 100 },
        Achievement("minutes_1000", "Thousand Minutes", "Exercise for 1,000 minutes in total", AchievementGroup.MILESTONES) { it.totalActiveMinutes >= 1_000 },
        Achievement("program_1", "Challenge Accepted", "Complete a challenge or recovery plan", AchievementGroup.MILESTONES) { it.programsCompleted >= 1 },
        Achievement("program_3", "Serial Finisher", "Complete three challenges or plans", AchievementGroup.MILESTONES) { it.programsCompleted >= 3 },
        Achievement("all_focus", "All-Rounder", "Train every body area at least once", AchievementGroup.MILESTONES) {
            it.focusAreasTrained.containsAll(FocusArea.entries)
        },
        Achievement("early_bird", "Early Bird", "Start a workout before 7 am", AchievementGroup.WORKOUTS) { it.earlyBird },
        Achievement("night_owl", "Night Owl", "Start a workout after 10 pm", AchievementGroup.WORKOUTS) { it.nightOwl },
        Achievement("weigh_in_7", "Data Driven", "Log your weight on 7 different days", AchievementGroup.MILESTONES) { it.weightEntries >= 7 },
    )

    private val byId = all.associateBy { it.id }

    fun byId(id: String): Achievement? = byId[id]

    /** Ids newly earned by [stats] that aren't already in [unlocked]. */
    fun newlyEarned(stats: AchievementStats, unlocked: Set<String>): List<Achievement> =
        all.filter { it.id !in unlocked && it.earned(stats) }
}
