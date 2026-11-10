package com.radley.homefit.data.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Loads the bundled content once per process. It's read-only and about 150 KB of JSON, so a
 * single in-memory copy beats putting it in Room.
 */
class ContentRepository(private val context: Context) {

    private val mutex = Mutex()

    @Volatile
    private var cached: Content? = null

    suspend fun get(): Content = cached ?: mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            ContentParser.parse(
                exercisesJson = read("exercises.json"),
                workoutsJson = read("workouts.json"),
                programsJson = read("programs.json"),
            )
        }.also { cached = it }
    }

    private fun read(name: String): String =
        context.assets.open("content/$name").bufferedReader().use { it.readText() }

    companion object {
        /** For Coil and anything else that wants a URI for an exercise photo. */
        fun imageUri(path: String) = "file:///android_asset/content/images/$path"
    }
}
