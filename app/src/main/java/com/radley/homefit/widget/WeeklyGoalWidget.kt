package com.radley.homefit.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Streaks
import com.radley.homefit.platform.Notifications
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.main.MainActivity
import com.radley.homefit.ui.theme.Accent
import com.radley.homefit.ui.theme.Bg
import com.radley.homefit.ui.theme.Muted
import com.radley.homefit.ui.theme.Surface
import com.radley.homefit.ui.theme.SurfaceHigh
import com.radley.homefit.ui.theme.TextColor
import java.time.LocalDate
import kotlinx.coroutines.flow.first

private data class WidgetState(
    val week: List<Pair<LocalDate, Boolean>>,
    val today: LocalDate,
    val done: Int,
    val goal: Int,
    val streak: Int,
)

/** Weekly goal at a glance, with a one-tap way into today's workout. */
class WeeklyGoalWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val history = ServiceLocator.history
        val settings = ServiceLocator.settings
        val today = history.today()
        val days = history.workoutDays.first()
        val week = Streaks.weekDates(today, settings.currentAppPrefs().weekStart.dayOfWeek)
        val state = WidgetState(
            week = week.map { it to (it.toEpochDay() in days) },
            today = today,
            done = week.count { it.toEpochDay() in days },
            goal = settings.currentProfile().weeklyGoal,
            streak = Streaks.current(days, today.toEpochDay()),
        )
        val open = todayIntent(context)
        provideContent { GlanceTheme { Content(state, open) } }
    }

    @Composable
    private fun Content(state: WidgetState, open: Intent) {
        val primary = ColorProvider(Accent)
        val onSurface = ColorProvider(TextColor)
        val muted = ColorProvider(Muted)
        val onPrimary = ColorProvider(Bg)
        Column(
            GlanceModifier.fillMaxSize().background(Surface).cornerRadius(20.dp).padding(14.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${state.done}/${state.goal} this week",
                    style = TextStyle(color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight(),
                )
                if (state.streak > 0) {
                    Text("🔥 ${state.streak}", style = TextStyle(color = primary, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                state.week.forEachIndexed { i, (date, done) ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Format.weekdayInitial(date), style = TextStyle(color = if (date == state.today) primary else muted, fontSize = 11.sp))
                        Box(
                            GlanceModifier.size(24.dp).cornerRadius(12.dp)
                                .background(if (done) Accent else SurfaceHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (done) "✓" else "${date.dayOfMonth}", style = TextStyle(color = if (done) onPrimary else muted, fontSize = 11.sp))
                        }
                    }
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Box(
                GlanceModifier.fillMaxWidth().height(36.dp).cornerRadius(18.dp).background(Accent)
                    .clickable(actionStartActivity(open)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Start today's workout", style = TextStyle(color = onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            }
        }
    }

    companion object {
        /** Called after anything that changes what the widget shows. */
        suspend fun refresh(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            if (manager.getGlanceIds(WeeklyGoalWidget::class.java).isNotEmpty()) WeeklyGoalWidget().updateAll(context)
        }
    }
}

class WeeklyGoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeeklyGoalWidget()
}

/** Opens the app on today's workout; [MainActivity] reads the extra. */
internal fun todayIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .putExtra(Notifications.EXTRA_OPEN_TODAY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
