package com.radley.homefit.session

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.radley.homefit.R
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.player.Phase
import com.radley.homefit.domain.player.ceilSeconds
import com.radley.homefit.platform.Notifications
import com.radley.homefit.ui.components.Format
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the process in the foreground while a workout runs, so the ticker in
 * [WorkoutSessionController] and the voice coach survive the screen turning off. It owns no
 * state; it mirrors the controller into an ongoing notification with pause and skip buttons.
 *
 * `mediaPlayback` type because what it keeps alive is spoken audio guidance.
 */
class WorkoutService : LifecycleService() {

    private val controller get() = ServiceLocator.session

    override fun onCreate() {
        super.onCreate()
        ServiceCompat.startForeground(
            this,
            Notifications.WORKOUT_NOTIFICATION_ID,
            build("Workout", "Getting ready…", null, paused = false),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
        )
        lifecycleScope.launch {
            controller.active
                .map { it?.let(::describe) }
                // The ticker publishes ten times a second; the notification only needs whole seconds.
                .distinctUntilChanged()
                .collect { state ->
                    if (state == null) {
                        stopSelf()
                    } else {
                        notify(build(state.title, state.text, state.progress, state.paused))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_TOGGLE -> controller.togglePause()
            ACTION_SKIP -> controller.skip()
        }
        return START_NOT_STICKY
    }

    private data class NotificationState(val title: String, val text: String, val progress: Pair<Int, Int>?, val paused: Boolean)

    private fun describe(active: ActiveWorkout): NotificationState {
        val steps = active.workout.steps
        val snapshot = active.snapshot
        val (title, text) = when (val p = snapshot.phase) {
            is Phase.Ready -> "Get ready" to "${steps[p.index].exercise.name} in ${p.remainingMs.ceilSeconds()}s"
            is Phase.Work -> {
                val step = steps[p.index]
                val target = step.targetMs
                step.exercise.name to if (target != null) Format.clock((target - p.elapsedMs).ceilSeconds())
                else Format.measure(step.measure, step.exercise.perSide)
            }
            is Phase.Rest -> "Rest · ${Format.clock(p.remainingMs.ceilSeconds())}" to "Next: ${steps[p.nextIndex].exercise.name}"
            Phase.Finished -> "Workout complete" to active.workout.title
        }
        val index = when (val p = snapshot.phase) {
            is Phase.Ready -> p.index
            is Phase.Work -> p.index
            is Phase.Rest -> p.nextIndex
            Phase.Finished -> steps.size
        }
        return NotificationState(
            title = if (snapshot.paused) "Paused · $title" else title,
            text = text,
            progress = index to steps.size,
            paused = snapshot.paused,
        )
    }

    private fun build(title: String, text: String, progress: Pair<Int, Int>?, paused: Boolean): Notification =
        NotificationCompat.Builder(this, Notifications.WORKOUT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Notifications.launchIntent(this, Notifications.EXTRA_OPEN_PLAYER, REQUEST_OPEN))
            .apply { if (progress != null) setProgress(progress.second, progress.first, false) }
            .addAction(0, if (paused) "Resume" else "Pause", action(ACTION_TOGGLE, REQUEST_TOGGLE))
            .addAction(0, "Skip", action(ACTION_SKIP, REQUEST_SKIP))
            .build()

    private fun action(action: String, requestCode: Int): PendingIntent = PendingIntent.getService(
        this,
        requestCode,
        Intent(this, WorkoutService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun notify(notification: Notification) {
        // Without POST_NOTIFICATIONS the foreground service still runs; the notification just
        // isn't shown, which is the user's call.
        runCatching { NotificationManagerCompat.from(this).notify(Notifications.WORKOUT_NOTIFICATION_ID, notification) }
    }

    companion object {
        private const val ACTION_TOGGLE = "com.radley.homefit.TOGGLE"
        private const val ACTION_SKIP = "com.radley.homefit.SKIP"
        private const val REQUEST_OPEN = 10
        private const val REQUEST_TOGGLE = 11
        private const val REQUEST_SKIP = 12

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, WorkoutService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WorkoutService::class.java))
        }
    }
}
