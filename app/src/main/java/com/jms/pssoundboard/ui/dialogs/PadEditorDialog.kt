package com.jms.pssoundboard.ui.dialogs

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.padColour
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.SynthTestClipsGenerator
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PlaybackMode
import com.jms.pssoundboard.ui.theme.PadPalette
import com.jms.pssoundboard.ui.theme.spew

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadEditorDialog(
    boardName: String,
    pad: Pad,
    isFavorite: Boolean,
    onAudition: (path: String) -> Unit,
    builtinClipResolver: (clipId: String) -> String,
    onShareSound: (title: String, filePath: String) -> Unit = { _, _ -> },
    onSave: (updatedPad: Pad, isFavorite: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(pad.title) }
    var selectedColor by remember { mutableStateOf(pad.colorHex) }
    var selectedMode by remember { mutableStateOf(pad.mode) }
    var favorite by remember { mutableStateOf(isFavorite) }
    var currentFilePath by remember { mutableStateOf(pad.filePath) }
    var selectedShape by remember { mutableStateOf(PadShape.of(pad.shape, pad.slot)) }

    var modeMenuExpanded by remember { mutableStateOf(false) }
    var soundMenuExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Edit pad ${pad.slot + 1}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Deck: $boardName",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Live preview of the pad's colour and shape
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 56.dp)
                            .gummy(padColour(selectedColor), selectedShape.shape)
                    )
                }

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Pad Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.spew.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.spew.accent,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.spew.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Playback Mode Dropdown
                Text(
                    text = "PLAYBACK TRIGGER MODE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )

                ExposedDropdownMenuBox(
                    expanded = modeMenuExpanded,
                    onExpandedChange = { modeMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${selectedMode.label} — ${selectedMode.description}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeMenuExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.spew.accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        PlaybackMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        Text(mode.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = {
                                    selectedMode = mode
                                    modeMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Color Palette
                Text(
                    text = "PAD COLOR ACCENT",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PadPalette.take(6).forEach { hex ->
                        ColorCircle(hex, isSelected = padColour(selectedColor) == padColour(hex)) { selectedColor = hex }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PadPalette.drop(6).take(6).forEach { hex ->
                        ColorCircle(hex, isSelected = padColour(selectedColor) == padColour(hex)) { selectedColor = hex }
                    }
                }

                // Blob shape
                Text(
                    text = "SHAPE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PadShape.entries.forEach { option ->
                        val selected = option == selectedShape
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .selectable(selected = selected, role = Role.RadioButton) { selectedShape = option }
                                .semantics { contentDescription = "${option.label} shape" },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 40.dp else 34.dp)
                                    .then(
                                        if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, option.shape) else Modifier
                                    )
                                    .padding(if (selected) 4.dp else 0.dp)
                                    .gummy(padColour(selectedColor), option.shape)
                            )
                        }
                    }
                }

                // Audio Clip Selection & Test Button
                Text(
                    text = "ASSIGNED SOUND CLIP",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = soundMenuExpanded,
                        onExpandedChange = { soundMenuExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        val displaySound = currentFilePath?.substringAfterLast('/') ?: "No Sound Assigned"
                        OutlinedTextField(
                            value = displaySound,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = soundMenuExpanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.spew.accent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = soundMenuExpanded,
                            onDismissRequest = { soundMenuExpanded = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                                .height(260.dp)
                        ) {
                            SynthTestClipsGenerator.CLIPS.forEach { builtin ->
                                DropdownMenuItem(
                                    text = {
                                        Text("Synth: ${builtin.title}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                    },
                                    onClick = {
                                        currentFilePath = builtinClipResolver(builtin.id)
                                        if (title.isBlank() || title == "Empty") {
                                            title = builtin.title
                                        }
                                        selectedColor = builtin.colorHex
                                        soundMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { currentFilePath?.let { onAudition(it) } },
                        enabled = currentFilePath != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Test", style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = { currentFilePath?.let { onShareSound(title, it) } },
                        enabled = currentFilePath != null,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = PlayfulIcons.Share,
                            contentDescription = "Share audio file to chat",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Favorite for Mini-Deck Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pin to Mini-Deck Overlay",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            text = "Shows in the floating mini-deck over your voice chat app",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.spew.onAccent,
                            checkedTrackColor = MaterialTheme.spew.accent
                        )
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            currentFilePath = null
                            title = "Empty"
                            selectedColor = "#334155"
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Pad", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val updated = pad.copy(
                                title = title.ifBlank { "Pad ${pad.slot + 1}" },
                                colorHex = selectedColor,
                                mode = selectedMode,
                                filePath = currentFilePath,
                                shape = selectedShape.name
                            )
                            onSave(updated, favorite)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.spew.onBlob),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Save Changes", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorCircle(hex: String, isSelected: Boolean, onClick: () -> Unit) {
    val color = padColour(hex)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(role = Role.RadioButton, onClickLabel = "Use this colour") { onClick() }
            .semantics { contentDescription = if (isSelected) "Colour $hex, selected" else "Colour $hex" }
    )
}
