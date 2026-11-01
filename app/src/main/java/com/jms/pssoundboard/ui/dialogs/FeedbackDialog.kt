package com.jms.pssoundboard.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.FeedbackRepository
import com.jms.pssoundboard.ui.components.BlobChip
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.theme.spew

private val Categories = listOf("Bug", "Idea", "Looks off")

/** Screens a tester can say the feedback is about. */
val FeedbackScreens = listOf("Deck", "Vault", "Library", "Voice", "Setup", "Mini-deck", "Somewhere else")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedbackDialog(
    initialScreen: String,
    configured: Boolean,
    onSend: (category: String, screen: String, message: String, includeDetails: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(Categories.first()) }
    var screen by rememberSaveable { mutableStateOf(initialScreen) }
    var message by rememberSaveable { mutableStateOf("") }
    var includeDetails by rememberSaveable { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Send feedback",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium
                )

                Label("What kind?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Categories.forEach { option ->
                        BlobChip(text = option, selected = option == category, onClick = { category = option })
                    }
                }

                Label("Which screen?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackScreens.forEach { option ->
                        BlobChip(text = option, selected = option == screen, onClick = { screen = option })
                    }
                }

                Label("What happened, or what would be better?")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.spew.field)
                        .padding(14.dp)
                ) {
                    if (message.isEmpty()) {
                        Text(
                            "e.g. Tapped Airhorn in the mini-deck and nothing played",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    BasicTextField(
                        value = message,
                        onValueChange = { message = it.take(FeedbackRepository.MAX_MESSAGE_LENGTH) },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 92.dp)
                            .semantics { contentDescription = "Feedback message" }
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include phone details", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                        Text(
                            "App version, phone model, Android version and whether the cable is plugged in",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = includeDetails,
                        onCheckedChange = { includeDetails = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.background)
                    )
                }

                Text(
                    text = if (configured) {
                        "Your feedback goes privately to the Spewboard team. No need to add your name or contact details."
                    } else {
                        "This test build can't send directly. Tap Share to pass your report on by message or email."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoftButton(text = "Cancel", onClick = onDismiss, light = false)
                    SpewButton(
                        label = if (configured) "Send" else "Share",
                        contentDescription = if (configured) "Send feedback" else "Share feedback report",
                        onClick = {
                            if (message.isNotBlank()) onSend(category, screen, message, includeDetails)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
}
