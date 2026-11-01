package com.jms.pssoundboard.ui.dialogs

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkOnBlob
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.SpewYellow
import com.jms.pssoundboard.ui.theme.Surface1
import com.jms.pssoundboard.ui.theme.Surface2
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.SynthTestClipsGenerator
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PlaybackMode
import com.jms.pssoundboard.ui.theme.PadPalette
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateSurface
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.toComposeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadEditorDialog(
    boardName: String,
    pad: Pad,
    isFavorite: Boolean,
    onAudition: (path: String) -> Unit,
    builtinClipResolver: (clipId: String) -> String,
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
            color = Surface1
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
                            color = InkLight,
                            fontFamily = BricolageCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "Deck: $boardName",
                            color = SlateTextMuted,
                            fontSize = 12.sp
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
                        focusedTextColor = SlateText,
                        unfocusedTextColor = SlateText,
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = SlateOutline,
                        focusedLabelColor = Teal,
                        unfocusedLabelColor = SlateTextMuted,
                        cursorColor = Teal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Playback Mode Dropdown
                Text(
                    text = "PLAYBACK TRIGGER MODE",
                    color = SlateTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
                            focusedTextColor = SlateText,
                            unfocusedTextColor = SlateText,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = SlateOutline
                        ),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = modeMenuExpanded,
                        onDismissRequest = { modeMenuExpanded = false },
                        modifier = Modifier.background(SlateSurface)
                    ) {
                        PlaybackMode.values().forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(mode.label, color = SlateText, fontWeight = FontWeight.Bold)
                                        Text(mode.description, color = SlateTextMuted, fontSize = 11.sp)
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
                    color = SlateTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
                    color = SlateTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
                                        if (selected) Modifier.border(3.dp, InkLight, option.shape) else Modifier
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
                    color = SlateTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
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
                                focusedTextColor = SlateText,
                                unfocusedTextColor = SlateText,
                                focusedBorderColor = Teal,
                                unfocusedBorderColor = SlateOutline
                            ),
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = soundMenuExpanded,
                            onDismissRequest = { soundMenuExpanded = false },
                            modifier = Modifier
                                .background(SlateSurface)
                                .height(260.dp)
                        ) {
                            SynthTestClipsGenerator.CLIPS.forEach { builtin ->
                                DropdownMenuItem(
                                    text = {
                                        Text("Synth: ${builtin.title}", color = SlateText, fontSize = 13.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = Surface2, contentColor = InkLight),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Test", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                // Favorite for Mini-Deck Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateSurface)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Pin to Mini-Deck Overlay",
                            color = SlateText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Shows in the floating mini-deck over your voice chat app",
                            color = SlateTextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = favorite,
                        onCheckedChange = { favorite = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Teal
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PanicRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanicRed.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Pad", fontSize = 12.sp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = SpewYellow, contentColor = InkOnBlob),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                color = if (isSelected) Color.White else SlateOutline,
                shape = CircleShape
            )
            .clickable(role = Role.RadioButton, onClickLabel = "Use this colour") { onClick() }
            .semantics { contentDescription = if (isSelected) "Colour $hex, selected" else "Colour $hex" }
    )
}
