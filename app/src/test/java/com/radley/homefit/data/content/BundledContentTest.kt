package com.radley.homefit.data.content

import com.radley.homefit.testing.bundledContent
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.Test

/**
 * Guards the hand-written content files. Every mistake here would otherwise surface as a
 * missing exercise in the middle of someone's workout.
 */
class BundledContentTest {

    private val content = bundledContent

    @Test
    fun `every workout item names a real exercise`() {
        val missing = content.workouts.values.flatMap { w ->
            w.items.filter { it.exerciseId !in content.exercises }.map { "${w.id} -> ${it.exerciseId}" }
        }
        assertTrue(missing.isEmpty(), "Unknown exercises: $missing")
    }

    @Test
    fun `every program template exists and is a program day`() {
        for (program in content.programs.values) {
            for ((level, templates) in program.levels) {
                for (id in templates) {
                    val template = content.workouts[id] ?: fail("${program.id}/$level uses missing $id")
                    assertEquals(WorkoutCategory.PROGRAM_DAY, template.category, "$id should be PROGRAM_DAY")
                }
            }
            assertTrue(program.restDays.all { it in 1..program.days }, "${program.id} rest day out of range")
            assertTrue(program.days !in program.restDays, "${program.id} ends on a rest day")
        }
    }

    @Test
    fun `program days are only ever used by programs`() {
        val used = content.programs.values.flatMap { p -> p.levels.values.flatten() }.toSet()
        val orphans = content.workouts.values
            .filter { it.category == WorkoutCategory.PROGRAM_DAY && it.id !in used }
            .map { it.id }
        assertTrue(orphans.isEmpty(), "Unused program-day templates: $orphans")
    }

    @Test
    fun `every focus area has a workout at every level`() {
        for (focus in FocusArea.entries) for (level in Level.entries) {
            assertTrue(content.focusWorkout(focus, level) != null, "No ${focus.name} ${level.name} workout")
        }
    }

    @Test
    fun `variant links point at real exercises and never at themselves`() {
        for (e in content.exercises.values) {
            for (link in listOfNotNull(e.easierId, e.harderId)) {
                assertTrue(link in content.exercises, "${e.id} links to missing $link")
                assertTrue(link != e.id, "${e.id} links to itself")
            }
        }
    }

    @Test
    fun `every referenced photo is bundled`() {
        val missing = content.exercises.values.flatMap { it.images }
            .filterNot { File("src/main/assets/content/images/$it").isFile }
        assertTrue(missing.isEmpty(), "Missing photos: $missing")
    }

    @Test
    fun `every program and focus area has a cover photo`() {
        val missing = content.programs.keys.filter { content.cover(it) == null } +
            FocusArea.entries.filter { content.cover(null, focus = it) == null }.map { it.name }
        assertTrue(missing.isEmpty(), "No cover for: $missing")
    }

    @Test
    fun `every cover belongs to a workout, program, focus area or category`() {
        val keys = content.workouts.keys + content.programs.keys +
            FocusArea.entries.map { "focus_${it.name}" } + WorkoutCategory.entries.map { "category_${it.name}" }
        val orphans = content.covers - keys
        assertTrue(orphans.isEmpty(), "Covers nothing uses: $orphans")
    }

    @Test
    fun `every bundled photo is credited unless it is public domain from free-exercise-db`() {
        fun json(path: String) = Json.parseToJsonElement(File(path).readText())
        val credited = json("src/main/assets/content/credits.json").jsonArray
            .map { it.jsonObject.getValue("file").jsonPrimitive.content }.toSet()
        // Written by tools/import-exercises.mjs: bundled path -> "fedb:…" / "commons:…" / "pexels:…".
        val sources = json("../tools/photo-sources.json").jsonObject.mapValues { it.value.jsonPrimitive.content }
        val bundled = content.exercises.values.flatMap { it.images }.map { "images/$it" } + content.covers.map { "covers/$it.jpg" }

        assertTrue(credited.all { File("src/main/assets/content/$it").isFile }, "Credited photos missing: ${credited.filterNot { File("src/main/assets/content/$it").isFile }}")
        val unknown = bundled.filter { it !in sources }
        assertTrue(unknown.isEmpty(), "Photos with no recorded source: $unknown")
        val uncredited = bundled.filter { !sources.getValue(it).startsWith("fedb:") && it !in credited }
        assertTrue(uncredited.isEmpty(), "Photos without a credit: $uncredited")
    }

    @Test
    fun `workout ids are unique across workouts and programs share no ids with workouts`() {
        val overlap = content.workouts.keys intersect content.programs.keys
        assertTrue(overlap.isEmpty(), "Ids used for both: $overlap")
    }

    @Test
    fun `item encoding round-trips`() {
        val items = content.workouts.getValue("abs_beginner").items
        assertEquals(items, ContentParser.decodeItems(ContentParser.encodeItems(items)))
    }
}
