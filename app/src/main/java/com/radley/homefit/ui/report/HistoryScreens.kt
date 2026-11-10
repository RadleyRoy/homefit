package com.radley.homefit.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.db.SessionEntity
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.Achievements
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.IconBadge
import com.radley.homefit.ui.components.SectionHeader
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    val sessions: StateFlow<List<SessionEntity>> = ServiceLocator.history.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val badges: StateFlow<Map<String, Long>> = ServiceLocator.history.unlockedAchievements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun delete(id: Long) = viewModelScope.launch { ServiceLocator.history.deleteSession(id) }
}

/** Every session, grouped by month, with replay and delete. */
@Composable
fun HistoryScreen(onBack: () -> Unit, onReplay: (WorkoutRef) -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val zone = ZoneId.systemDefault()
    val byMonth = sessions.groupBy {
        val d = Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
        d.withDayOfMonth(1)
    }

    Scaffold(topBar = { BackTopBar("History", onBack) }) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Workouts you finish will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            byMonth.forEach { (month: LocalDate, list) ->
                item(key = "m-$month") {
                    SectionHeader(
                        "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year} · ${list.size} workout${if (list.size == 1) "" else "s"}",
                    )
                }
                items(list, key = { it.id }) { session ->
                    SessionRow(session, trailing = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { onReplay(WorkoutRef.decode(session.refKey)) }) {
                                Icon(Icons.Rounded.Replay, contentDescription = "Do again")
                            }
                            IconButton(onClick = { deleting = session.id }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                    })
                }
            }
        }
    }

    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this workout?") },
            text = { Text("It will be removed from your history and stats. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun AchievementsScreen(onBack: () -> Unit, viewModel: HistoryViewModel = viewModel()) {
    val unlocked by viewModel.badges.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar("Badges", onBack) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Achievements.all.groupBy { it.group }.forEach { (group, list) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(group.label) }
                items(list, key = { it.id }) { a ->
                    val at = unlocked[a.id]
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            IconBadge(
                                if (at != null) Icons.Rounded.EmojiEvents else Icons.Rounded.Lock,
                                if (at != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            )
                            Text(a.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                            Text(
                                if (at != null) Format.dayMonth(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()) else a.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}
