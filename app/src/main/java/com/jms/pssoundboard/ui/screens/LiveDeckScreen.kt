package com.jms.pssoundboard.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.model.Board
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.data.model.PlaybackMode
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateSurface
import com.jms.pssoundboard.ui.theme.SlateSurfaceHigh
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.TealBright
import com.jms.pssoundboard.ui.theme.WarningAmber
import com.jms.pssoundboard.ui.theme.toComposeColor
import kotlin.math.roundToInt

@Composable
fun LiveDeckScreen(
    deckState: DeckState,
    gain: Float,
    onSelectBoard: (boardId: String) -> Unit,
    onRenameBoard: (boardId: String, name: String) -> Unit,
    onAddBoard: (name: String) -> Unit,
    onDeleteBoard: (boardId: String) -> Unit,
    onClearBoard: (boardId: String) -> Unit,
    onResetBoard: (boardId: String) -> Unit,
    onPressPad: (PadRef, Pad) -> Unit,
    onReleasePad: (PadRef, Pad) -> Unit,
    onEditPad: (boardId: String, Pad) -> Unit,
    onSetGain: (Float) -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBoard = deckState.activeBoard ?: return
    var managingBoard by remember { mutableStateOf<Board?>(null) }
    var isAddingBoard by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Switchable Soundboard Pages (Scrollable Tabs)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(deckState.boards, key = { it.id }) { board ->
                val isActive = board.id == activeBoard.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isActive) Teal else SlateSurface)
                        .border(
                            1.dp,
                            if (isActive) Color.White.copy(alpha = 0.9f) else SlateOutline,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectBoard(board.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = board.name,
                            color = if (isActive) Color.White else SlateText,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .clickable { managingBoard = board }
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "⚙",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // "+ New Deck" Button
            item {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateSurfaceHigh)
                        .border(1.dp, SlateOutline, RoundedCornerShape(10.dp))
                        .clickable { isAddingBoard = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "+ New Deck",
                        color = TealBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Active Deck Header Indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TealBright)
                )
                Text(
                    text = activeBoard.name.uppercase(),
                    color = TealBright,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "• ${activeBoard.pads.count { it.filePath != null }}/12 sounds",
                    color = SlateTextMuted,
                    fontSize = 11.sp
                )
            }
            Text(
                text = "Tap: Fire • Hold: Loop • Long-press: Edit",
                color = SlateTextMuted,
                fontSize = 10.sp
            )
        }

        // 3x4 Responsive Tactile Pad Grid (12 Pads with per-pad key isolation)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until 3) {
                            val slot = row * 3 + col
                            val pad = activeBoard.pads.getOrNull(slot) ?: Pad(slot = slot)
                            val padRef = PadRef(activeBoard.id, slot)
                            val isFav = deckState.favorites.contains(padRef)

                            key(padRef.key) {
                                TactilePad(
                                    padRef = padRef,
                                    pad = pad,
                                    isFavorite = isFav,
                                    onPress = { onPressPad(padRef, pad) },
                                    onRelease = { onReleasePad(padRef, pad) },
                                    onLongClick = { onEditPad(activeBoard.id, pad) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Loopback Output Gain Slider (10% - 100%)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SlateSurface)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "LOOPBACK GAIN",
                color = SlateTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = gain,
                onValueChange = onSetGain,
                valueRange = 0.10f..1.00f,
                colors = SliderDefaults.colors(
                    thumbColor = TealBright,
                    activeTrackColor = Teal,
                    inactiveTrackColor = SlateOutline
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(gain * 100).roundToInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }

        // Full-width STOP ALL SOUNDS Panic Button
        Button(
            onClick = onStopAll,
            colors = ButtonDefaults.buttonColors(containerColor = PanicRed),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color.White, CircleShape)
                )
                Text(
                    text = "  ■  STOP ALL SOUNDS (PANIC)",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }

    // Manage Deck Dialog (Rename, Clear, Reset to Defaults, Delete)
    managingBoard?.let { board ->
        var editName by remember(board.id) { mutableStateOf(board.name) }
        Dialog(onDismissRequest = { managingBoard = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SlateBackground,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Teal.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "MANAGE DECK: ${board.name}",
                        color = Teal,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Deck Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateText,
                            unfocusedTextColor = SlateText,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = SlateOutline,
                            focusedLabelColor = Teal,
                            unfocusedLabelColor = SlateTextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                onRenameBoard(board.id, editName)
                                managingBoard = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Name")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(SlateOutline)
                    )

                    // Reset & Clear Options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onClearBoard(board.id)
                                managingBoard = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarningAmber),
                            border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear All 12 Pads", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                onResetBoard(board.id)
                                managingBoard = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Defaults", fontSize = 11.sp)
                        }
                    }

                    // Delete Deck Button (only if more than 1 board exists)
                    if (deckState.boards.size > 1) {
                        Button(
                            onClick = {
                                onDeleteBoard(board.id)
                                managingBoard = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PanicRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete This Deck", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Add New Deck Dialog
    if (isAddingBoard) {
        var newDeckTitle by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { isAddingBoard = false }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = SlateBackground,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Teal.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "CREATE NEW SOUNDBOARD",
                        color = Teal,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )

                    OutlinedTextField(
                        value = newDeckTitle,
                        onValueChange = { newDeckTitle = it },
                        label = { Text("Deck Name (e.g. Movie Quotes)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateText,
                            unfocusedTextColor = SlateText,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = SlateOutline,
                            focusedLabelColor = Teal,
                            unfocusedLabelColor = SlateTextMuted
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isAddingBoard = false },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = SlateTextMuted)
                        }

                        Button(
                            onClick = {
                                onAddBoard(newDeckTitle.ifBlank { "New Deck" })
                                isAddingBoard = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("Create Deck", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TactilePad(
    padRef: PadRef,
    pad: Pad,
    isFavorite: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember(padRef.key) { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        label = "pad_press_scale"
    )

    // Capture latest lambdas so pointerInput never executes stale callbacks across board switches
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val baseColor = pad.colorHex.toComposeColor(Teal)
    val hasSound = pad.filePath != null

    Box(
        modifier = modifier
            .scale(animatedScale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isPressed) {
                    Brush.verticalGradient(
                        colors = listOf(baseColor, baseColor.copy(alpha = 0.8f))
                    )
                } else if (hasSound) {
                    Brush.verticalGradient(
                        colors = listOf(
                            baseColor.copy(alpha = 0.55f),
                            baseColor.copy(alpha = 0.30f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(SlateSurfaceHigh, SlateSurface)
                    )
                }
            )
            .border(
                width = if (isPressed) 2.5.dp else if (hasSound) 1.5.dp else 1.dp,
                color = if (isPressed) Color.White else if (hasSound) baseColor.copy(alpha = 0.85f) else SlateOutline,
                shape = RoundedCornerShape(14.dp)
            )
            .pointerInput(padRef.key) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        currentOnPress()
                        tryAwaitRelease()
                        isPressed = false
                        currentOnRelease()
                    },
                    onLongPress = {
                        currentOnLongClick()
                    }
                )
            }
            .padding(8.dp)
    ) {
        // Slot number + mini-deck favorite pin indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${pad.slot + 1}",
                color = if (hasSound) Color.White.copy(alpha = 0.7f) else SlateTextMuted.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )

            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(TealBright)
                )
            }
        }

        // Title and Playback Mode Label
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pad.title,
                color = if (hasSound) Color.White else SlateTextMuted,
                fontWeight = if (hasSound) FontWeight.Black else FontWeight.Normal,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            if (hasSound) {
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = when (pad.mode) {
                            PlaybackMode.ONE_SHOT -> "ONE-SHOT"
                            PlaybackMode.HOLD_TO_PLAY -> "HOLD"
                            PlaybackMode.RESTART_ON_SPAM -> "RESTART"
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
