package com.radley.homefit.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/**
 * "How do I do this?" links. A YouTube search rather than a fixed video: it can't go dead, it
 * opens in the YouTube app when installed, and the top results for "<move> proper form" are
 * reliably coaching videos. The query holds only the exercise name, never anything personal.
 */
object ExerciseVideos {

    fun searchUrl(exerciseName: String): String =
        "https://www.youtube.com/results?search_query=" +
            URLEncoder.encode("how to do $exerciseName exercise proper form", "UTF-8")

    /** False when nothing on the phone can open a web link. */
    fun open(context: Context, exerciseName: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl(exerciseName))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
