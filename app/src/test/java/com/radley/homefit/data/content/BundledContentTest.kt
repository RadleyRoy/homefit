package com.radley.homefit.data.content

import com.radley.homefit.testing.bundledContent
import java.io.File
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
