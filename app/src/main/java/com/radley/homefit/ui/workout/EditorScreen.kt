package com.radley.homefit.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.content.Content
import com.radley.homefit.data.content.Exercise
import com.radley.homefit.data.content.FocusArea
import com.radley.homefit.data.content.Measure
import com.radley.homefit.data.content.WorkoutItem
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.domain.WorkoutRef
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.ExerciseImage
import com.radley.homefit.ui.components.Format
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorState(
    val loading: Boolean = true,
    val name: String = "My workout",
    val items: List<WorkoutItem> = emptyList(),
    val nameEditable: Boolean = true,
    val isCustom: Boolean = false,
    val dirty: Boolean = false,
    val content: Content? = null,
)

class EditorViewModel(private val ref: WorkoutRef?) : ViewModel() {

    private val workouts = ServiceLocator.workouts

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val content = workouts.content()
            val resolved = ref?.let { workouts.resolve(it) }
            _state.value = EditorState(
                loading = false,
                name = resolved?.title ?: "My workout",
                items = resolved?.editableItems.orEmpty(),
                nameEditable = ref == null || ref is WorkoutRef.Custom,
                isCustom = ref is WorkoutRef.Custom,
                content = content,
            )
        }
    }

    private fun edit(transform: (List<WorkoutItem>) -> List<WorkoutItem>) =
        _state.update { it.copy(items = transform(it.items), dirty = true) }

    fun rename(name: String) = _state.update { it.copy(name = name, dirty = true) }

    fun move(from: Int, to: Int) = edit { items ->
        if (to !in items.indices) items else items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun remove(index: Int) = edit { it.filterIndexed { i, _ -> i != index } }

    fun setMeasure(index: Int, measure: Measure) = edit { items ->
        items.mapIndexed { i, item -> if (i == index) item.copy(measure = measure) else item }
    }

    fun add(exercise: Exercise) = edit { it + WorkoutItem(exercise.id, exercise.defaultMeasure) }

    /** Returns the ref to show afterwards: the new custom workout's, or the one being edited. */
    fun save(onSaved: (WorkoutRef) -> Unit) = viewModelScope.launch {
        val s = _state.value
        val name = s.name.trim().ifEmpty { "My workout" }
        val saved = when (ref) {
            null -> WorkoutRef.Custom(workouts.createCustom(name, s.items))
            is WorkoutRef.Custom -> {
                workouts.renameCustom(ref.id, name)
                workouts.saveEdits(ref, s.items)
                ref
            }
            else -> {
                workouts.saveEdits(ref, s.items)
                ref
            }
        }
        onSaved(saved)
    }

    fun delete(onDeleted: () -> Unit) = viewModelScope.launch {
        (ref as? WorkoutRef.Custom)?.let { workouts.deleteCustom(it.id) }
        onDeleted()
    }
}

@Composable
fun EditorScreen(
    ref: WorkoutRef?,
    onBack: () -> Unit,
    onSaved: (saved: WorkoutRef, isNew: Boolean) -> Unit,
    onDeleted: () -> Unit,
    viewModel: EditorViewModel = viewModel(key = "editor-${ref?.encode()}") { EditorViewModel(ref) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var measureIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val back = { if (state.dirty) confirmDiscard = true else onBack() }
    androidx.activity.compose.BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            BackTopBar(if (ref == null) "New workout" else "Edit workout", back) {
                if (state.isCustom) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete workout") }
                }
                TextButton(
                    onClick = { viewModel.save { onSaved(it, ref == null) } },
                    enabled = state.items.isNotEmpty() && (state.dirty || ref == null),
                ) { Text("Save") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picking = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add exercise") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            if (state.nameEditable) {
                item {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::rename,
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }
            if (state.items.isEmpty() && !state.loading) {
                item {
                    Text(
                        "Add exercises to build your workout.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }
            itemsIndexed(state.items) { index, item ->
                val exercise = state.content?.exercises?.get(item.exerciseId) ?: return@itemsIndexed
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExerciseImage(exercise, Modifier.size(52.dp), animate = false, corner = 10.dp, fit = false)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                        TextButton(onClick = { measureIndex = index }, contentPadding = PaddingValues(0.dp)) {
                            Text(Format.measure(item.measure, exercise.perSide))
                        }
                    }
                    IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = index < state.items.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    IconButton(onClick = { viewModel.remove(index) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove")
                    }
                }
            }
        }
    }

    if (picking) {
        ExercisePicker(state.content, onPick = { viewModel.add(it) }, onDismiss = { picking = false })
    }
    measureIndex?.let { i ->
        val item = state.items.getOrNull(i)
        if (item != null) {
            MeasureDialog(item.measure, onDismiss = { measureIndex = null }) {
                viewModel.setMeasure(i, it)
                measureIndex = null
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${state.name}\"?") },
            text = { Text("Your history keeps the sessions you've done, but the workout itself is gone for good.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(onDeleted) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExercisePicker(content: Content?, onPick: (Exercise) -> Unit, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var focus by rememberSaveable { mutableStateOf<FocusArea?>(null) }
    var added by remember { mutableIntStateOf(0) }
    val all = content?.exercises?.values?.sortedBy { it.name }.orEmpty()
    val shown = all.filter {
        (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) && (focus == null || focus in it.focus)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add exercises", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (added > 0) AssistChip(onClick = onDismiss, label = { Text("$added added · Done") })
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search ${all.size} exercises") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FocusArea.entries.forEach { f ->
                    FilterChip(selected = focus == f, onClick = { focus = if (focus == f) null else f }, label = { Text(f.label) })
                }
            }
        }
        LazyColumn(Modifier.height(480.dp)) {
            items(shown, key = { it.id }) { e ->
                ExercisePickRow(e, onClick = {
                    onPick(e)
                    added++
                })
            }
        }
    }
}

@Composable
fun MeasureDialog(initial: Measure, onDismiss: () -> Unit, onSave: (Measure) -> Unit) {
    var reps by rememberSaveable { mutableStateOf(initial is Measure.Reps) }
    var value by rememberSaveable {
        mutableIntStateOf(when (initial) { is Measure.Reps -> initial.count; is Measure.Timed -> initial.seconds })
    }
    val step = if (reps) 1 else 5
    val range = if (reps) 1..200 else 5..600
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How much?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(selected = reps, onClick = { if (!reps) { reps = true; value = 12 } }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Reps") }
                    SegmentedButton(selected = !reps, onClick = { if (reps) { reps = false; value = 30 } }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Time") }
                }
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { value = (value - step).coerceIn(range) }) { Icon(Icons.Rounded.Remove, contentDescription = "Less") }
                    Box(Modifier.width(120.dp), contentAlignment = Alignment.Center) {
                        Text(if (reps) "x$value" else Format.clock(value), style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { value = (value + step).coerceIn(range) }) { Icon(Icons.Rounded.Add, contentDescription = "More") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(if (reps) Measure.Reps(value) else Measure.Timed(value)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
