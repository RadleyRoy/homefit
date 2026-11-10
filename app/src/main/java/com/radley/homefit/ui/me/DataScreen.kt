package com.radley.homefit.ui.me

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.data.BackupManager
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.platform.HealthConnectSync
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.ListRow
import com.radley.homefit.ui.components.SectionHeader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DataViewModel : ViewModel() {
    private val backup = ServiceLocator.backup
    private val health = ServiceLocator.health

    val app: StateFlow<AppPrefs> = ServiceLocator.settings.appPrefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())
    val healthStatus = MutableStateFlow(HealthConnectSync.Status.UNAVAILABLE)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        refreshHealth()
    }

    fun refreshHealth() = viewModelScope.launch {
        healthStatus.value = health.status()
    }

    fun export(uri: Uri) = run("Backup saved") { backup.export(uri) }

    fun import(uri: Uri) = run(null) {
        val count = backup.import(uri)
        ServiceLocator.reminders.rescheduleAll()
        _messages.send("Restored $count workouts")
    }

    fun resetProgress() = run("Progress reset") { backup.resetProgress() }

    fun deleteEverything(onDone: () -> Unit) = viewModelScope.launch {
        ServiceLocator.database.reminders().all().forEach { ServiceLocator.reminderScheduler.cancel(it.id) }
        backup.deleteEverything()
        onDone()
    }

    fun setHealthSync(on: Boolean) = viewModelScope.launch {
        ServiceLocator.settings.updateAppPrefs { it.copy(healthConnectSync = on) }
        if (on) syncHealth()
    }

    fun syncHealth() = run(null) {
        val imported = health.importWeights()
        _messages.send(if (imported > 0) "Imported $imported weigh-ins from Health Connect" else "Health Connect is up to date")
    }

    private fun run(success: String?, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            success?.let { _messages.send(it) }
        } catch (e: Exception) {
            _messages.send(e.message ?: "Something went wrong")
        }
    }
}

@Composable
fun DataScreen(onBack: () -> Unit, onDeletedAll: () -> Unit, viewModel: DataViewModel = viewModel()) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    val health by viewModel.healthStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val healthPermissions = rememberLauncherForActivityResult(HealthConnectSync.permissionContract()) { granted ->
        viewModel.refreshHealth()
        if (granted.containsAll(HealthConnectSync.PERMISSIONS)) viewModel.setHealthSync(true)
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(topBar = { BackTopBar("Health & data", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionHeader("Health Connect")
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    when (health) {
                        HealthConnectSync.Status.UNAVAILABLE -> ListRow(
                            "Not available",
                            subtitle = "Health Connect isn't supported on this phone.",
                            icon = Icons.Rounded.Favorite,
                        )
                        HealthConnectSync.Status.NEEDS_INSTALL -> ListRow(
                            "Install Health Connect",
                            subtitle = "Needed to share workouts with Fit, Samsung Health and others.",
                            icon = Icons.Rounded.Favorite,
                            onClick = { ServiceLocator.health.openInstall() },
                        )
                        HealthConnectSync.Status.NEEDS_PERMISSION -> ListRow(
                            "Connect",
                            subtitle = "Write workouts, calories and weight; read weight from other apps.",
                            icon = Icons.Rounded.Favorite,
                            onClick = { healthPermissions.launch(HealthConnectSync.PERMISSIONS) },
                        )
                        HealthConnectSync.Status.READY -> {
                            ToggleRow("Sync workouts", "Each finished workout is written to Health Connect", Icons.Rounded.Favorite, app.healthConnectSync) {
                                viewModel.setHealthSync(it)
                            }
                            ListRow("Import weight", subtitle = "Pull weigh-ins logged by other apps", icon = Icons.Rounded.Sync, onClick = viewModel::syncHealth)
                        }
                    }
                }
            }

            SectionHeader("Backup")
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    ListRow(
                        "Export backup",
                        subtitle = "Save everything to a file, e.g. on Google Drive",
                        icon = Icons.Rounded.Upload,
                        onClick = { exportLauncher.launch(BackupManager.suggestedName()) },
                    )
                    ListRow(
                        "Restore backup",
                        subtitle = "Replaces everything on this phone",
                        icon = Icons.Rounded.Download,
                        onClick = { confirm = "restore" },
                    )
                }
            }
            Text(
                "Android also backs the app up to your Google account automatically, if device backup is on.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )

            SectionHeader("Reset")
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    ListRow(
                        "Reset progress",
                        subtitle = "Clears history, challenge progress, badges and difficulty tuning",
                        icon = Icons.Rounded.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { confirm = "reset" },
                    )
                    ListRow(
                        "Delete all data",
                        subtitle = "Everything, including your profile and custom workouts",
                        icon = Icons.Rounded.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { confirm = "delete" },
                    )
                }
            }
        }
    }

    when (confirm) {
        "restore" -> Confirm("Restore a backup?", "Everything currently on this phone will be replaced by the backup's contents.", "Choose file", { confirm = null }) {
            importLauncher.launch(arrayOf(BackupManager.MIME, "*/*"))
        }
        "reset" -> Confirm("Reset progress?", "Your workout history, challenge progress and badges will be permanently deleted. Custom workouts, weights and settings are kept.", "Reset", { confirm = null }) {
            viewModel.resetProgress()
        }
        "delete" -> Confirm("Delete all data?", "This permanently deletes your history, weights, custom workouts, reminders and profile. It can't be undone unless you have a backup.", "Delete everything", { confirm = null }) {
            viewModel.deleteEverything(onDeletedAll)
        }
    }
}

@Composable
private fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(action) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
