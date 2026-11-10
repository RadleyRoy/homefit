package com.radley.homefit.ui.coach

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.radley.homefit.coach.CoachService
import com.radley.homefit.data.AppPrefs
import com.radley.homefit.di.ServiceLocator
import com.radley.homefit.ui.components.AppCard
import com.radley.homefit.ui.components.BackTopBar
import com.radley.homefit.ui.components.ListRow
import com.radley.homefit.ui.components.SectionHeader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CoachSettingsViewModel : ViewModel() {
    private val keys = ServiceLocator.coachKeys
    val hasKey: StateFlow<Boolean> = keys.hasKey
    val app: StateFlow<AppPrefs> = ServiceLocator.settings.appPrefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPrefs())

    fun saveKey(key: String) {
        keys.save(key)
        ServiceLocator.coach.close()
    }

    fun clearKey() {
        keys.clear()
        ServiceLocator.coach.close()
    }

    fun setModel(id: String) = viewModelScope.launch { ServiceLocator.settings.updateAppPrefs { it.copy(coachModel = id) } }
}

@Composable
fun CoachSettingsScreen(onBack: () -> Unit, viewModel: CoachSettingsViewModel = viewModel()) {
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    val app by viewModel.app.collectAsStateWithLifecycle()
    var key by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(topBar = { BackTopBar("Coach settings", onBack) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            SectionHeader("API key")
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (hasKey) {
                        Text("A key is saved, encrypted on this device.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = viewModel::clearKey) { Text("Remove key") }
                    } else {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { key = it.trim() },
                            label = { Text("Anthropic API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = {
                                viewModel.saveKey(key)
                                key = ""
                            }, enabled = key.startsWith("sk-ant-")) { Text("Save key") }
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://platform.claude.com/settings/keys")))
                            }) { Text("Get a key") }
                        }
                    }
                }
            }

            SectionHeader("Model")
            AppCard(Modifier.fillMaxWidth()) {
                Column {
                    CoachService.MODELS.forEach { m ->
                        ListRow(m.label, subtitle = m.blurb, onClick = { viewModel.setModel(m.id) }, trailing = {
                            RadioButton(selected = app.coachModel == m.id, onClick = { viewModel.setModel(m.id) })
                        })
                    }
                }
            }

            SectionHeader("Privacy")
            Text(
                "When you use the coach, your message, your profile (goal, level, focus areas, schedule, latest weight), " +
                    "a summary of your last two weeks of workouts and the app's exercise list are sent to Anthropic's API. " +
                    "Nothing is sent unless you open the coach and send a message. Usage is billed to your Anthropic account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
