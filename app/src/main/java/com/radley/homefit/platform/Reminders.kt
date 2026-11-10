package com.radley.homefit.platform

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.radley.homefit.R
import com.radley.homefit.data.db.HomeFitDatabase
import com.radley.homefit.data.db.ReminderEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.PlanDayResult
import com.radley.homefit.domain.PlanGenerator
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

object ReminderTimes {

    fun isOn(mask: Int, day: DayOfWeek) = mask and (1 shl day.ordinal) != 0

    fun mask(days: Set<DayOfWeek>) = days.fold(0) { acc, d -> acc or (1 shl d.ordinal) }

    fun days(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { isOn(mask, it) }.toSet()

    /** The first moment strictly after [now] that the reminder should fire, or null if it has no days. */
    fun next(reminder: ReminderEntity, now: ZonedDateTime): ZonedDateTime? {
        if (reminder.daysMask == 0) return null
        for (offset in 0L..7L) {
            val candidate = now.toLocalDate().plusDays(offset)
                .atTime(reminder.hour, reminder.minute)
                .atZone(now.zone)
            if (candidate.isAfter(now) && isOn(reminder.daysMask, candidate.dayOfWeek)) return candidate
        }
        return null
    }
}

/**
 * Exact alarms when the user has allowed them (they asked for 7:00, not "around 7"), otherwise
 * the inexact while-idle variant, which Android may defer by a few minutes in Doze.
 */
class ReminderScheduler(private val context: Context) {

    private val alarms = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(reminder: ReminderEntity) {
        val intent = pendingIntent(reminder.id)
        alarms.cancel(intent)
        if (!reminder.enabled) return
        val at = ReminderTimes.next(reminder, ZonedDateTime.now())?.toInstant()?.toEpochMilli() ?: return
        if (canScheduleExact()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    fun cancel(id: Long) = alarms.cancel(pendingIntent(id))

    fun scheduleAll(reminders: List<ReminderEntity>) = reminders.forEach(::schedule)

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class ReminderRepository(private val db: HomeFitDatabase, private val scheduler: ReminderScheduler) {

    val reminders: Flow<List<ReminderEntity>> = db.reminders().observeAll()

    suspend fun save(reminder: ReminderEntity) {
        val id = db.reminders().upsert(reminder)
        // Upsert returns -1 when it updated an existing row.
        scheduler.schedule(if (reminder.id == 0L) reminder.copy(id = id) else reminder)
    }

    suspend fun delete(reminder: ReminderEntity) {
        db.reminders().delete(reminder)
        scheduler.cancel(reminder.id)
    }

    suspend fun rescheduleAll() = scheduler.scheduleAll(db.reminders().all())
}

/**
 * Posts the reminder, then books the next one. Skips the nudge when there's already a workout
 * today: being told to exercise straight after exercising is how reminders get turned off.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val db = ServiceLocator.database
                val reminder = db.reminders().byId(id) ?: return@launch
                ServiceLocator.reminderScheduler.schedule(reminder)

                val history = ServiceLocator.history
                val today = history.today()
                val doneToday = db.sessions().all().any { history.localDay(it.startedAt) == today.toEpochDay() }
                if (doneToday || !canNotify(context)) return@launch

                val settings = ServiceLocator.settings
                val app = settings.currentAppPrefs()
                val planLine = app.planStartEpochDay?.let { start ->
                    val day = PlanGenerator(ServiceLocator.content.get())
                        .dayFor(settings.currentProfile(), start, app.planSeed, today.toEpochDay(), settings.currentWorkoutPrefs().restSeconds)
                    (day as? PlanDayResult.Train)?.let { "Today's plan: ${it.title}" }
                }
                val notification = NotificationCompat.Builder(context, Notifications.REMINDER_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Time to move 💪")
                    .setContentText(planLine ?: "A few minutes is all it takes. Your workout is ready.")
                    .setContentIntent(Notifications.launchIntent(context, Notifications.EXTRA_OPEN_TODAY, REQUEST_OPEN))
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(Notifications.REMINDER_NOTIFICATION_ID, notification)
                } catch (_: SecurityException) {
                    // Notifications were turned off after the reminder was set.
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        private const val REQUEST_OPEN = 20

        fun canNotify(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

/** Alarms don't survive a reboot, a clock change or an app update; this books them again. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ServiceLocator.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
