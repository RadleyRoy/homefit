package com.radley.homefit.ui.me

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.db.ReminderEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.platform.ReminderReceiver
import com.radley.homefit.platform.ReminderTimes
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.BackTopBar
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel : ViewModel() {
    private val repo = ServiceLocator.reminders
    val reminders: StateFlow<List<ReminderEntity>> = repo.reminders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(reminder: ReminderEntity) = viewModelScope.launch { repo.save(reminder) }
    fun delete(reminder: ReminderEntity) = viewModelScope.launch { repo.delete(reminder) }
    fun canScheduleExact() = ServiceLocator.reminderScheduler.canScheduleExact()

    /** After the exact-alarm grant changes, re-book everything with the better API. */
    fun reschedule() = viewModelScope.launch { repo.rescheduleAll() }
}

@Composable
fun RemindersScreen(onBack: () -> Unit, viewModel: RemindersViewModel = viewModel()) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<ReminderEntity?>(null) }
    var exact by remember { mutableStateOf(viewModel.canScheduleExact()) }
    var canNotify by remember { mutableStateOf(ReminderReceiver.canNotify(context)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }

    LifecycleResumeEffect(Unit) {
        val nowExact = viewModel.canScheduleExact()
        if (nowExact != exact) viewModel.reschedule()
        exact = nowExact
        canNotify = ReminderReceiver.canNotify(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = { BackTopBar("Reminders", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!canNotify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    editing = ReminderEntity(hour = 19, minute = 0, daysMask = ReminderTimes.mask(DayOfWeek.entries.toSet()), enabled = true)
                },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add reminder") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!canNotify) {
                item {
                    Banner("Notifications are off for HomeFit, so reminders can't appear.", "Allow") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            if (!exact) {
                item {
                    Banner("Reminders may arrive a few minutes late. Allow alarms for exact times.", "Allow") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                        }
                    }
                }
            }
            if (reminders.isEmpty()) {
                item {
                    Text(
                        "No reminders yet. People who schedule a time are far more likely to keep a streak going.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(reminders, key = { it.id }) { r ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.clickable { editing = r }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(timeLabel(r.hour, r.minute), style = MaterialTheme.typography.headlineSmall)
                            Text(daysLabel(r.daysMask), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.delete(r) }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete") }
                        Switch(checked = r.enabled, onCheckedChange = { viewModel.save(r.copy(enabled = it)) })
                    }
                }
            }
        }
    }

    editing?.let { r ->
        ReminderDialog(r, onDismiss = { editing = null }) {
            viewModel.save(it)
            editing = null
        }
    }
}

@Composable
private fun Banner(text: String, action: String, onAction: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReminderDialog(initial: ReminderEntity, onDismiss: () -> Unit, onSave: (ReminderEntity) -> Unit) {
    val time = rememberTimePickerState(initial.hour, initial.minute)
    var days by remember { mutableStateOf(ReminderTimes.days(initial.daysMask)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = time) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        FilterChip(
                            selected = d in days,
                            onClick = { days = if (d in days) days - d else days + d },
                            label = { Text(d.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(initial.copy(hour = time.hour, minute = time.minute, daysMask = ReminderTimes.mask(days), enabled = true)) },
                enabled = days.isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun timeLabel(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

private fun daysLabel(mask: Int): String {
    val days = ReminderTimes.days(mask)
    return when {
        days.size == 7 -> "Every day"
        days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
        days == DayOfWeek.entries.take(5).toSet() -> "Weekdays"
        else -> DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
}
