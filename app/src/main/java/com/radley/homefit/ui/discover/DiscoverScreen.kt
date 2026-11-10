package com.radley.homefit.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.ProgramStatus
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Level
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.Format
import com.radley.homefit.ui.components.IconBadge
import com.radley.homefit.ui.components.SectionHeader
import com.radley.homefit.ui.components.TabTopBar
import com.radley.homefit.ui.components.WorkoutCardModel
import com.radley.homefit.ui.components.WorkoutRow
import com.radley.homefit.ui.components.CoverImage
import com.radley.homefit.ui.components.color
import com.radley.homefit.ui.components.coverUri
import com.radley.homefit.ui.components.icon
import com.radley.homefit.ui.workout.ExercisePickRow
import com.radley.homefit.ui.workout.ExerciseSheet

@Composable
fun DiscoverScreen(
    onOpenWorkout: (WorkoutRef) -> Unit,
    onOpenProgram: (String) -> Unit,
    onSeeAll: (WorkoutCategory) -> Unit,
    onFavorites: () -> Unit,
    onCreate: () -> Unit,
    contentPadding: PaddingValues,
    viewModel: DiscoverViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var exercise by remember { mutableStateOf<Exercise?>(null) }

    Column(Modifier.fillMaxSize()) {
        TabTopBar("Discover")
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = filters.query,
                        onValueChange = { q -> viewModel.update { it.copy(query = q) } },
                        placeholder = { Text("Search workouts and exercises") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (filters.active) IconButton(onClick = viewModel::clear) { Icon(Icons.Rounded.Close, contentDescription = "Clear") }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Filters", tint = if (filters.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            if (filters.active) {
                item {
                    Text(
                        "${state.results.size} workout${if (state.results.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.results, key = { "r-" + it.ref.encode() }) { card ->
                    WorkoutRow(card, onClick = { onOpenWorkout(card.ref) }, favorite = card.ref.encode() in state.favorites)
                }
                if (state.exerciseResults.isNotEmpty()) {
                    item { SectionHeader("Exercises") }
                    items(state.exerciseResults, key = { "e-" + it.id }) { e ->
                        AppCard(Modifier.fillMaxWidth()) { ExercisePickRow(e, onClick = { exercise = e }) }
                    }
                }
                return@LazyColumn
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShortcutCard("Favourites", "${state.favorites.size} saved", Icons.Rounded.Favorite, MaterialTheme.colorScheme.secondary, Modifier.weight(1f), onFavorites)
                    ShortcutCard("New workout", "Build your own", Icons.Rounded.Add, MaterialTheme.colorScheme.primary, Modifier.weight(1f), onCreate)
                }
            }

            if (state.custom.isNotEmpty()) {
                item { SectionHeader("My workouts") }
                items(state.custom, key = { "c-" + it.ref.encode() }) { card ->
                    WorkoutRow(card, onClick = { onOpenWorkout(card.ref) }, favorite = card.ref.encode() in state.favorites)
                }
            }

            for ((category, cards) in state.sections) {
                item(key = "h-$category") { SectionHeader(category.label, action = "See all", onAction = { onSeeAll(category) }) }
                item(key = "row-$category") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cards, key = { it.ref.encode() }) { card -> TileCard(card, onClick = { onOpenWorkout(card.ref) }) }
                    }
                }
            }

            if (state.recovery.isNotEmpty()) {
                item { SectionHeader("Recovery plans") }
                items(state.recovery, key = { "p-" + it.program.id }) { status ->
                    RecoveryRow(status, onClick = { onOpenProgram(status.program.id) })
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(filters, onChange = viewModel::update, onDismiss = { showFilters = false })
    }
    exercise?.let { ExerciseSheet(it, null, state.content, onDismiss = { exercise = null }) }
}

@Composable
private fun ShortcutCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    AppCard(modifier, onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, size = 38.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TileCard(card: WorkoutCardModel, onClick: () -> Unit) {
    AppCard(Modifier.width(176.dp), onClick = onClick) {
        CoverImage(card.image, card.icon, Modifier.fillMaxWidth().height(104.dp))
        Column(Modifier.padding(12.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, minLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Format.minutes(card.seconds)} · ${Format.kcal(card.kcal)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecoveryRow(status: ProgramStatus, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(status.program.coverUri, status.program.category.icon, Modifier.size(width = 84.dp, height = 64.dp), RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(status.program.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        status.finished -> "Completed"
                        status.started -> "Day ${status.currentDay} of ${status.program.days}"
                        else -> "${status.program.days}-day course"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(filters: DiscoverFilters, onChange: ((DiscoverFilters) -> DiscoverFilters) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange { DiscoverFilters(query = it.query) } }) { Text("Reset") }
            }
            FilterGroup("Body area") {
                FocusArea.entries.forEach { f ->
                    FilterChip(selected = filters.focus == f, onClick = { onChange { it.copy(focus = if (it.focus == f) null else f) } }, label = { Text(f.label) })
                }
            }
            FilterGroup("Level") {
                Level.entries.forEach { l ->
                    FilterChip(selected = filters.level == l, onClick = { onChange { it.copy(level = if (it.level == l) null else l) } }, label = { Text(l.label) })
                }
            }
            FilterGroup("Length") {
                DurationFilter.entries.forEach { d ->
                    FilterChip(selected = filters.duration == d, onClick = { onChange { it.copy(duration = d) } }, label = { Text(d.label) })
                }
            }
            FilterGroup("Other") {
                FilterChip(selected = filters.noEquipment, onClick = { onChange { it.copy(noEquipment = !it.noEquipment) } }, label = { Text("No equipment") })
                FilterChip(selected = filters.noJumping, onClick = { onChange { it.copy(noJumping = !it.noJumping) } }, label = { Text("No jumping") })
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Show results") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
