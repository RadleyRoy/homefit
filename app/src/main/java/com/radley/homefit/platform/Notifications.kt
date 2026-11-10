package com.radley.homefit.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.radley.homefit.R
import com.radley.homefit.ui.main.MainActivity

object Notifications {

    const val REMINDER_CHANNEL = "reminders"
    const val WORKOUT_CHANNEL = "active_workout"

    const val WORKOUT_NOTIFICATION_ID = 1
    const val REMINDER_NOTIFICATION_ID = 2

    /** Extra on the launch intent: land on the player instead of the tabs. */
    const val EXTRA_OPEN_PLAYER = "open_player"

    /** Extra on the launch intent: start today's planned or next program workout. */
    const val EXTRA_OPEN_TODAY = "open_today"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    REMINDER_CHANNEL,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_description) },
                // Low importance: it updates every second, and must never make a sound of its own
                // on top of the voice coach.
                NotificationChannel(
                    WORKOUT_CHANNEL,
                    context.getString(R.string.workout_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.workout_channel_description)
                    setShowBadge(false)
                },
            ),
        )
    }

    fun launchIntent(context: Context, extra: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (extra != null) intent.putExtra(extra, true)
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
