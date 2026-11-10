package com.radley.homefit.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.content.WorkoutCategory
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.WorkoutCardModel
import com.radley.homefit.ui.components.WorkoutRow

/** "See all" for one category, or the favourites list. Shares the Discover view model's data. */
@Composable
fun LibraryScreen(
    category: WorkoutCategory?,
    favorites: Boolean,
    title: String,
    onBack: () -> Unit,
    onOpenWorkout: (WorkoutRef) -> Unit,
    viewModel: DiscoverViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cards: List<WorkoutCardModel> = when {
        favorites -> state.favorites.mapNotNull { state.allCards[it] }
        category != null -> state.sections.firstOrNull { it.first == category }?.second.orEmpty()
        else -> emptyList()
    }

    Scaffold(topBar = { BackTopBar(title, onBack) }) { padding ->
        if (cards.isEmpty() && !state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    if (favorites) "Tap the heart on any workout to save it here." else "Nothing here yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(cards, key = { it.ref.encode() }) { card ->
                    WorkoutRow(card, onClick = { onOpenWorkout(card.ref) }, favorite = card.ref.encode() in state.favorites)
                }
            }
        }
    }
}
