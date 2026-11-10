package com.radley.homefit.coach

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.PermissionDeniedException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.radley.homefit.data.HistoryRepository
import com.radley.homefit.data.SettingsStore
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.ContentRepository
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.data.db.CoachMessageEntity
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CoachException(message: String) : Exception(message)

data class GeneratedWorkout(val name: String, val notes: String, val items: List<WorkoutItem>)

/**
 * The optional LLM coach, using the user's own Anthropic API key.
 *
 * Two calls: a streamed chat reply, and a structured-output workout whose exercise ids are
 * constrained by an enum in the schema to the bundled catalog, so a generated workout can be
 * played like any other without guessing what "jumping jack variation" means.
 *
 * What leaves the phone: the static catalog, the profile and a summary of the last two weeks of
 * sessions. The settings screen says so before a key is entered.
 */
class CoachService(
    private val keys: CoachKeyStore,
    private val settings: SettingsStore,
    private val history: HistoryRepository,
    private val content: ContentRepository,
) {

    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun client(): AnthropicClient {
        val key = keys.load() ?: throw CoachException("Add your Anthropic API key in coach settings first.")
        if (key != clientKey) {
            client?.close()
            client = AnthropicOkHttpClient.builder().apiKey(key).build()
            clientKey = key
        }
        return client!!
    }

    /** Streams the reply through [onDelta] and returns the full text. */
    suspend fun chat(conversation: List<CoachMessageEntity>, onDelta: (String) -> Unit): String = withContext(Dispatchers.IO) {
        val params = baseParams(maxTokens = 64_000, effort = OutputConfig.Effort.LOW)
            .apply { appendConversation(this, conversation) }
            .build()
        val out = StringBuilder()
        var stop: StopReason? = null
        guarded {
            client().messages().createStreaming(params).use { stream ->
                stream.stream().forEach { event ->
                    event.contentBlockDelta().flatMap { it.delta().text() }.ifPresent { delta ->
                        out.append(delta.text())
                        onDelta(delta.text())
                    }
                    event.messageDelta().flatMap { it.delta().stopReason() }.ifPresent { stop = it }
                }
            }
        }
        // A mid-stream refusal leaves partial text that shouldn't be shown as advice.
        if (stop == StopReason.REFUSAL) throw CoachException("The coach can't help with that one. Try rephrasing your question.")
        out.toString().trim()
    }

    suspend fun generateWorkout(request: String): GeneratedWorkout = withContext(Dispatchers.IO) {
        val catalog = content.get()
        val params = baseParams(maxTokens = 16_000, effort = OutputConfig.Effort.MEDIUM, format = workoutSchema(catalog))
            .addUserMessage(
                "Create one workout for me. Request: $request\n\n" +
                    "Use reps for rep-based moves (set seconds to 0) and seconds for holds and intervals " +
                    "(set reps to 0). For moves marked per side, the number is for each side. Start with a " +
                    "warm-up move and end with a stretch. Keep notes to two short sentences.",
            )
            .build()
        val response = guarded { client().messages().create(params) }
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw CoachException("The coach couldn't make that workout. Try describing it differently.")
        }
        val text = response.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
        val parsed = runCatching { json.decodeFromString<WorkoutDto>(text) }
            .getOrElse { throw CoachException("The coach's answer didn't come through properly. Please try again.") }
        val items = parsed.exercises.mapNotNull { e ->
            val exercise = catalog.exercises[e.id] ?: return@mapNotNull null
            val measure = when {
                e.seconds > 0 -> Measure.Timed(e.seconds.coerceIn(5, 600))
                e.reps > 0 -> Measure.Reps(e.reps.coerceIn(1, 200))
                else -> exercise.defaultMeasure
            }
            WorkoutItem(exercise.id, measure)
        }
        if (items.isEmpty()) throw CoachException("The coach didn't pick any exercises. Please try again.")
        GeneratedWorkout(parsed.name.ifBlank { "Coach workout" }, parsed.notes, items)
    }

    fun close() {
        client?.close()
        client = null
        clientKey = null
    }

    private suspend fun baseParams(maxTokens: Long, effort: OutputConfig.Effort, format: JsonOutputFormat? = null): MessageCreateParams.Builder {
        val app = settings.currentAppPrefs()
        val builder = MessageCreateParams.builder()
            .model(app.coachModel)
            .maxTokens(maxTokens)
            .outputConfig(OutputConfig.builder().effort(effort).apply { format?.let { format(it) } }.build())
            .systemOfTextBlockParams(
                listOf(
                    // Stable across every request, so it can be cached; the volatile context follows.
                    TextBlockParam.builder()
                        .text(staticInstructions(content.get()))
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                    TextBlockParam.builder().text(userContext()).build(),
                ),
            )
        // Server-side fallback re-runs a safety decline on Anthropic's recommended model. Haiku
        // has no fallback target, so it's only sent for the Opus and Sonnet tiers.
        if (!app.coachModel.contains("haiku")) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
            builder.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }
        return builder
    }

    /**
     * Last 20 messages; older ones add cost without changing the answer. Turns must start with
     * the user and alternate, and a failed reply can leave two user messages in a row, so runs of
     * one role are merged.
     */
    private fun appendConversation(builder: MessageCreateParams.Builder, conversation: List<CoachMessageEntity>) {
        val turns = mutableListOf<Pair<String, StringBuilder>>()
        conversation.takeLast(20)
            .dropWhile { it.role != ROLE_USER }
            .forEach { m ->
                val text = buildString {
                    append(m.text)
                    if (m.workoutName != null) append("\n[Proposed workout \"${m.workoutName}\": ${m.workoutItems}]")
                }
                val last = turns.lastOrNull()
                if (last != null && last.first == m.role) last.second.append("\n\n").append(text) else turns += m.role to StringBuilder(text)
            }
        turns.forEach { (role, text) ->
            if (role == ROLE_USER) builder.addUserMessage(text.toString()) else builder.addAssistantMessage(text.toString())
        }
    }

    private fun staticInstructions(catalog: Content): String = buildString {
        appendLine("You are the coach inside HomeFit, a home-workout app. Be encouraging, practical and brief: this is read on a phone between sets.")
        appendLine("Give exercise and training advice for healthy adults. You are not a doctor: for pain, injury, pregnancy or medical conditions, give general safety guidance and suggest seeing a professional.")
        appendLine("When recommending moves, prefer ones from the app's catalog below and use their names, so the user can find them.")
        appendLine("Plain text only; short paragraphs or simple dashes for lists.")
        appendLine()
        appendLine("Exercise catalog (id | name | focus | level | measure | equipment):")
        catalog.exercises.values.forEach { e ->
            appendLine("${e.id} | ${e.name} | ${e.focus.joinToString("/") { it.label }} | ${e.level.label} | ${e.defaultMeasure.encode()}${if (e.perSide) " per side" else ""} | ${e.equipment.label}${if (!e.noJump) " | jumping" else ""}")
        }
    }

    private suspend fun userContext(): String {
        val profile = settings.currentProfile()
        val zone = ZoneId.systemDefault()
        val since = System.currentTimeMillis() - 14 * 24 * 3600 * 1000L
        val recent = history.sessions.first().filter { it.startedAt >= since }
        return buildString {
            appendLine("About the user: goal ${profile.goal.label.lowercase()}, ${profile.level.label.lowercase()} level, " +
                "focus on ${profile.focusAreas.joinToString { it.label }}, ${profile.minutesPerSession}-minute sessions, " +
                "${profile.weeklyGoal} days a week${if (profile.quietMode) ", no jumping" else ""}.")
            if (history.hasWeight()) appendLine("Latest weight: ${"%.1f".format(history.latestWeightKg())} kg.")
            appendLine("Workouts in the last 14 days: ${recent.size}.")
            recent.take(10).forEach { s ->
                val date = Instant.ofEpochMilli(s.startedAt).atZone(zone).toLocalDate()
                appendLine("- $date: ${s.title}, ${s.totalSeconds / 60} min${s.feedback?.let { ", felt ${it.lowercase().replace('_', ' ')}" } ?: ""}")
            }
        }
    }

    private fun workoutSchema(catalog: Content): JsonOutputFormat {
        val exercise = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "id" to mapOf("type" to "string", "enum" to catalog.exercises.keys.toList()),
                "reps" to mapOf("type" to "integer"),
                "seconds" to mapOf("type" to "integer"),
            ),
            "required" to listOf("id", "reps", "seconds"),
            "additionalProperties" to false,
        )
        val schema = JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty(
                "properties",
                JsonValue.from(
                    mapOf(
                        "name" to mapOf("type" to "string"),
                        "notes" to mapOf("type" to "string"),
                        "exercises" to mapOf("type" to "array", "items" to exercise),
                    ),
                ),
            )
            .putAdditionalProperty("required", JsonValue.from(listOf("name", "notes", "exercises")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
        return JsonOutputFormat.builder().schema(schema).build()
    }

    /** Most-specific first, so a bad key and a dropped connection read differently. */
    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: UnauthorizedException) {
        throw CoachException("Your API key was rejected. Check it in coach settings.")
    } catch (e: PermissionDeniedException) {
        throw CoachException("Your API key doesn't have access to this model. Try another model in coach settings.")
    } catch (e: RateLimitException) {
        throw CoachException("You've hit your API rate limit. Give it a minute and try again.")
    } catch (e: AnthropicServiceException) {
        throw CoachException("The coach is unavailable right now (${e.statusCode()}). Please try again.")
    } catch (e: AnthropicIoException) {
        throw CoachException("Couldn't reach the coach. Check your internet connection.")
    }

    @Serializable
    private data class WorkoutDto(val name: String, val notes: String = "", val exercises: List<ExerciseDto>)

    @Serializable
    private data class ExerciseDto(val id: String, val reps: Int = 0, val seconds: Int = 0)

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        private val json = Json { ignoreUnknownKeys = true }

        val MODELS = listOf(
            CoachModel("claude-opus-5-5", "Claude Opus 5.5", "Best answers. About $4 / $20 per million tokens in / out."),
            CoachModel("claude-sonnet-5-5", "Claude Sonnet 5.5", "Fast and capable. About $2 / $10 per million tokens."),
            CoachModel("claude-haiku-5-5", "Claude Haiku 5.5", "Cheapest and quickest. About $0.10 / $0.50 per million tokens."),
        )
    }
}

data class CoachModel(val id: String, val label: String, val blurb: String)
