package com.radley.homefit.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.radley.homefit.domain.Feedback

/**
 * Asks why, like the original: "too hard" feeds adaptive difficulty, and when something is
 * already done there's a way to keep it instead of throwing the session away.
 */
@Composable
fun QuitDialog(
    canSave: Boolean,
    onKeepGoing: () -> Unit,
    onQuit: (save: Boolean, reason: Feedback?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        title = { Text("Quit this workout?") },
        text = {
            Column {
                Text(
                    "Tell us why and we'll adjust next time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Option("It's too hard") { onQuit(false, Feedback.TOO_HARD) }
                Option("Just taking a look") { onQuit(false, null) }
                Option("Something came up") { onQuit(false, null) }
                if (canSave) Option("Finish now and save progress") { onQuit(true, null) }
            }
        },
        confirmButton = { TextButton(onClick = onKeepGoing) { Text("Keep going") } },
    )
}

@Composable
private fun Option(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth())
    }
}
