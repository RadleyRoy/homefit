package com.radley.homefit.coach

import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem

/** A workout read out of a Claude reply, plus the lines that couldn't be matched to a move. */
data class ImportedWorkout(
    val name: String,
    val items: List<WorkoutItem>,
    val restSeconds: Int?,
    val skipped: List<String>,
)

/**
 * Reads workouts out of text the user copied or shared from the Claude app.
 *
 * Claude is asked for ```homefit blocks (see [CoachPrompts]), but a chat reply is still prose
 * written by a model and pasted by a person, so this is deliberately forgiving: bullets,
 * numbering, "×12", "12 reps", "45 sec" and an exercise's display name instead of its id are all
 * accepted. Anything still unrecognisable is reported rather than guessed at.
 */
object CoachImport {

    fun parse(text: String, content: Content): List<ImportedWorkout> {
        val normalized = text.replace("\r\n", "\n")
        val blocks = FENCE.findAll(normalized).map { it.groupValues[1] }.toList()
        // No tagged block: the user may have copied just the inside of one.
        val sources = blocks.ifEmpty { listOf(normalized) }
        val byName = content.exercises.values.associateBy { key(it.name) }
        return sources.flatMap { parseBlock(it, content, byName) }.filter { it.items.isNotEmpty() }
    }

    private fun parseBlock(block: String, content: Content, byName: Map<String, Exercise>): List<ImportedWorkout> {
        val out = mutableListOf<ImportedWorkout>()
        var name: String? = null
        var rest: Int? = null
        var items = mutableListOf<WorkoutItem>()
        var skipped = mutableListOf<String>()

        fun flush() {
            if (items.isNotEmpty() || name != null) {
                out += ImportedWorkout(name?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME, items, rest, skipped)
            }
            name = null
            rest = null
            items = mutableListOf()
            skipped = mutableListOf()
        }

        for (raw in block.lines()) {
            val line = raw.trim().replace(BULLET, "").trim()
            if (line.isEmpty() || line.startsWith("```")) continue
            val header = HEADER.matchEntire(line)
            if (header != null) {
                val (keyword, value) = header.destructured
                when (keyword.lowercase()) {
                    "workout", "name", "title" -> {
                        flush()
                        name = value.trim().trim('"', '*')
                    }
                    "rest" -> rest = SECONDS.find(value)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, MAX_REST)
                }
                continue
            }
            val item = parseItem(line, content, byName)
            if (item != null) items += item else skipped += line
        }
        flush()
        return out
    }

    private fun parseItem(line: String, content: Content, byName: Map<String, Exercise>): WorkoutItem? {
        val measureMatch = MEASURE.find(line) ?: return null
        val measure = measureOf(measureMatch) ?: return null
        val subject = line.substring(0, measureMatch.range.first).trim().trimEnd('-', ':', '—', '–', ',').trim()
        val exercise = content.exercises[subject.lowercase().replace('-', '_').replace(' ', '_')]
            ?: byName[key(subject)]
            ?: return null
        return WorkoutItem(exercise.id, measure)
    }

    private fun measureOf(m: MatchResult): Measure? {
        val (repsX, repsWord, secs, mins) = m.destructured
        return when {
            repsX.isNotEmpty() -> Measure.Reps(repsX.toInt().coerceIn(1, MAX_REPS))
            repsWord.isNotEmpty() -> Measure.Reps(repsWord.toInt().coerceIn(1, MAX_REPS))
            secs.isNotEmpty() -> Measure.Timed(secs.toInt().coerceIn(MIN_SECONDS, MAX_SECONDS))
            mins.isNotEmpty() -> Measure.Timed((mins.toInt() * 60).coerceIn(MIN_SECONDS, MAX_SECONDS))
            else -> null
        }
    }

    /** "Push-Ups", "push ups" and "pushups" all compare equal. */
    private fun key(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

    private const val DEFAULT_NAME = "Claude workout"
    private const val MAX_REPS = 200
    private const val MIN_SECONDS = 5
    private const val MAX_SECONDS = 1800
    private const val MAX_REST = 300

    private val FENCE = Regex("```\\s*${CoachPrompts.BLOCK_TAG}[^\\n]*\\n(.*?)```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val BULLET = Regex("^(?:[-*•]|\\d+[.)])\\s*")
    private val HEADER = Regex("^(workout|name|title|rest)\\s*:\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val SECONDS = Regex("(\\d+)")

    /** "x12" / "×12" / "12x", "12 reps", "30s" / "30 sec", "2 min"; the first on a line is used. */
    private val MEASURE = Regex(
        "(?:[x×]\\s*(\\d+)\\b|\\b(\\d+)\\s*(?:reps?|times|x)\\b|\\b(\\d+)\\s*(?:s|secs?|seconds?)\\b|\\b(\\d+)\\s*(?:m|mins?|minutes?)\\b)",
        RegexOption.IGNORE_CASE,
    )
}
