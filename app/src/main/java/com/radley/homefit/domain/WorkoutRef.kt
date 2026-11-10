package com.radley.homefit.domain

/**
 * Names something playable. Encoded as a short string so it can ride in navigation routes,
 * session history and favourites, and so a session can always be replayed from history.
 */
sealed interface WorkoutRef {

    data class Builtin(val id: String) : WorkoutRef
    data class Custom(val id: String) : WorkoutRef
    data class ProgramDay(val programId: String, val day: Int) : WorkoutRef
    data class PlanDay(val epochDay: Long) : WorkoutRef

    /** A workout the LLM coach proposed, stored on the chat message with this id. */
    data class CoachProposal(val messageId: Long) : WorkoutRef

    fun encode(): String = when (this) {
        is Builtin -> "w:$id"
        is Custom -> "c:$id"
        is ProgramDay -> "p:$programId:$day"
        is PlanDay -> "plan:$epochDay"
        is CoachProposal -> "m:$messageId"
    }

    /**
     * Adaptive difficulty is tracked per workout for built-ins and per *program* for program
     * days, so "too hard" on day 3 eases day 4 too. Hand-built workouts are what the user made
     * them, so they don't adapt.
     */
    val difficultyKey: String?
        get() = when (this) {
            is Builtin -> id
            is ProgramDay -> "p:$programId"
            is PlanDay -> "plan"
            is Custom, is CoachProposal -> null
        }

    companion object {
        fun decode(encoded: String): WorkoutRef {
            val parts = encoded.split(':')
            return when (parts[0]) {
                "w" -> Builtin(parts[1])
                "c" -> Custom(parts[1])
                "p" -> ProgramDay(parts[1], parts[2].toInt())
                "plan" -> PlanDay(parts[1].toLong())
                "m" -> CoachProposal(parts[1].toLong())
                else -> throw IllegalArgumentException("Unknown workout ref \"$encoded\"")
            }
        }
    }
}
