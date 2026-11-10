package com.radley.homefit.coach

import com.radley.homefit.data.Profile
import com.radley.homefit.data.Sex
import com.radley.homefit.data.UnitSystem
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Equipment
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Measure
import com.radley.homefit.domain.BodyMetrics
import com.radley.homefit.domain.Feedback
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** One finished workout, as Claude needs to see it. */
data class RecentSession(val date: LocalDate, val title: String, val minutes: Int, val feedback: Feedback?)

/** Everything about the user a prompt may mention. Built fresh for each hand-off. */
data class CoachContext(
    val profile: Profile,
    val units: UnitSystem,
    val today: LocalDate,
    val latestWeightKg: Double?,
    val restSeconds: Int,
    val workoutsThisWeek: Int,
    val streakDays: Int,
    /** Newest first; the last four weeks. */
    val recent: List<RecentSession>,
)

/** What the user asked for on the "Design a workout" card. */
data class WorkoutRequest(
    val minutes: Int,
    val focus: FocusArea?,
    /** Besides bodyweight; NONE is always allowed. */
    val equipment: Set<Equipment>,
    val notes: String,
)

/**
 * The prompts HomeFit hands to the Claude app. They run on the user's own Claude plan, so there
 * is no system prompt and no API: each one is a self-contained first message that carries the
 * profile, the recent training and, when Claude is to design workouts, the exercise catalog and
 * the `homefit` block format [CoachImport] reads back.
 *
 * Plain text throughout: the user may read it before sending, and Claude handles plain
 * sections with clear headings as well as anything fancier.
 */
object CoachPrompts {

    const val BLOCK_TAG = "homefit"

    fun ask(ctx: CoachContext, question: String): String = buildString {
        appendLine("I train with HomeFit, a home-workout app. Here is my profile and recent training so your advice fits me.")
        appendLine()
        appendAbout(ctx)
        appendLine()
        appendLine("My question:")
        appendLine(question.trim())
        appendLine()
        append("Keep it practical and reasonably brief; I'm reading on my phone. ")
        append("You're not my doctor, so if it sounds like pain or a medical issue, say what's safe in general and suggest I see a professional.")
    }

    fun workout(ctx: CoachContext, content: Content, request: WorkoutRequest): String = buildString {
        appendLine("Please design one home workout for me to play in my workout app, HomeFit.")
        appendLine()
        appendAbout(ctx)
        appendLine()
        appendLine("What I want:")
        appendLine("- About ${request.minutes} minutes in total, including rests.")
        appendLine("- Focus: ${request.focus?.label ?: "whatever suits my goal and recent training"}.")
        appendLine("- Equipment I have: ${equipmentText(request.equipment)}.")
        if (request.notes.isNotBlank()) appendLine("- Notes: ${request.notes.trim()}")
        appendLine()
        appendRules(ctx)
        appendLine()
        appendLine("Reply with two or three sentences on why this workout suits me, then the workout as one $BLOCK_TAG block.")
        appendLine()
        appendCatalog(content, request.equipment)
    }

    fun week(ctx: CoachContext, content: Content): String = buildString {
        val days = ctx.profile.trainingDays.sorted().joinToString { it.getDisplayName(TextStyle.FULL, Locale.ENGLISH) }
        appendLine("Please plan my next week of home workouts for my workout app, HomeFit.")
        appendLine()
        appendAbout(ctx)
        appendLine()
        appendLine("What I want:")
        appendLine("- One workout for each of my training days ($days), about ${ctx.profile.minutesPerSession} minutes each.")
        appendLine("- Balance the body areas across the week, building on what I've done recently.")
        appendLine("- If my recent feedback says workouts felt too easy or too hard, adjust for it.")
        appendLine()
        appendRules(ctx)
        appendLine()
        appendLine("Reply with a short overview of the week, then one $BLOCK_TAG block per workout, named after its day (for example \"Monday: Legs and core\").")
        appendLine()
        appendCatalog(content, setOf(Equipment.CHAIR, Equipment.WALL))
    }

    fun review(ctx: CoachContext, content: Content): String = buildString {
        appendLine("Please review my last four weeks of home training, logged in my workout app, HomeFit.")
        appendLine()
        appendAbout(ctx)
        appendLine()
        appendLine("Tell me:")
        appendLine("1. How consistent I've been against my weekly goal.")
        appendLine("2. Whether my body areas are balanced or something is being neglected.")
        appendLine("3. Whether I'm ready to progress (more reps, harder variants, a higher level), using my feedback.")
        appendLine("4. One or two specific changes for next week.")
        appendLine()
        appendLine("If you recommend a specific workout, add it at the end as a $BLOCK_TAG block, following these rules:")
        appendRules(ctx)
        appendLine()
        appendCatalog(content, setOf(Equipment.CHAIR, Equipment.WALL))
    }

    private fun StringBuilder.appendAbout(ctx: CoachContext) {
        val p = ctx.profile
        val metric = ctx.units == UnitSystem.METRIC
        appendLine("About me")
        val facts = buildList {
            if (p.sex != Sex.UNSPECIFIED) add(p.sex.label.lowercase())
            p.birthYear?.let { add("${ctx.today.year - it} years old") }
            p.heightCm?.let { add(if (metric) "${it.toInt()} cm tall" else "${BodyMetrics.cmToInches(it).toInt()} in tall") }
            ctx.latestWeightKg?.let { add(if (metric) "%.1f kg".format(Locale.ENGLISH, it) else "%.0f lb".format(Locale.ENGLISH, BodyMetrics.kgToLb(it))) }
        }
        if (facts.isNotEmpty()) appendLine("- ${facts.joinToString(", ").replaceFirstChar { it.uppercase() }}.")
        p.targetWeightKg?.let {
            appendLine("- Target weight: ${if (metric) "%.1f kg".format(Locale.ENGLISH, it) else "%.0f lb".format(Locale.ENGLISH, BodyMetrics.kgToLb(it))}.")
        }
        appendLine("- Goal: ${p.goal.label.lowercase()}. Level: ${p.level.label.lowercase()}.")
        appendLine("- Focus areas: ${p.focusAreas.joinToString { it.label }}.")
        appendLine("- I aim for ${p.weeklyGoal} workouts a week of about ${p.minutesPerSession} minutes, and rest ${ctx.restSeconds} s between moves.")
        if (p.quietMode) appendLine("- No jumping, please (thin floors).")
        appendLine()
        appendLine("Recent training")
        appendLine("- This week: ${ctx.workoutsThisWeek} of ${p.weeklyGoal} workouts done. Current streak: ${ctx.streakDays} day${if (ctx.streakDays == 1) "" else "s"}.")
        if (ctx.recent.isEmpty()) {
            appendLine("- No workouts logged in the last four weeks.")
        } else {
            appendLine("- Last four weeks, newest first:")
            ctx.recent.take(MAX_SESSIONS).forEach { s ->
                val felt = s.feedback?.let { ", felt ${it.label.lowercase()}" } ?: ""
                appendLine("  ${s.date} ${s.title}, ${s.minutes} min$felt")
            }
            if (ctx.recent.size > MAX_SESSIONS) appendLine("  …and ${ctx.recent.size - MAX_SESSIONS} more.")
        }
    }

    private fun StringBuilder.appendRules(ctx: CoachContext) {
        appendLine("Rules for the workout:")
        appendLine("- Use only exercises from the catalog below, written by their id exactly as shown.")
        appendLine("- Start with one or two warm-up moves and finish with one or two stretches.")
        appendLine("- Timed moves take seconds (\"30s\"); rep moves take a count (\"x12\"). For moves marked per side, the number is for each side.")
        appendLine("- Allow about 3 seconds per rep, plus the rest between moves, when fitting the time.")
        appendLine("- Match my level${if (ctx.profile.quietMode) ", and skip anything marked jumping" else ""}.")
        appendLine()
        appendLine("Write each workout in a code block tagged $BLOCK_TAG, exactly like this, so I can copy it into the app:")
        appendLine()
        appendLine("```$BLOCK_TAG")
        appendLine("workout: Core and legs")
        appendLine("rest: ${ctx.restSeconds}")
        appendLine("jumping_jacks 30s")
        appendLine("squats x15")
        appendLine("plank 40s")
        appendLine("```")
    }

    private fun StringBuilder.appendCatalog(content: Content, equipment: Set<Equipment>) {
        val allowed = equipment + Equipment.NONE
        appendLine("Exercise catalog (id: name, how it's measured, body areas, level):")
        content.exercises.values
            .filter { it.equipment in allowed }
            .sortedBy { it.id }
            .forEach { e ->
                val measure = if (e.defaultMeasure is Measure.Timed) "timed" else "reps"
                val extras = listOfNotNull(
                    "per side".takeIf { e.perSide },
                    e.equipment.label.lowercase().takeIf { e.equipment != Equipment.NONE },
                    "jumping".takeIf { !e.noJump },
                )
                append(e.id)
                append(": ${e.name}, $measure")
                if (extras.isNotEmpty()) append(", ${extras.joinToString(", ")}")
                appendLine(", ${e.focus.joinToString("/") { it.label.lowercase() }}, ${e.level.label.lowercase()}")
            }
    }

    private fun equipmentText(equipment: Set<Equipment>): String {
        val extra = equipment - Equipment.NONE
        return if (extra.isEmpty()) "none, bodyweight only" else "bodyweight plus " + extra.joinToString { it.label.lowercase() }
    }

    private const val MAX_SESSIONS = 30
}
