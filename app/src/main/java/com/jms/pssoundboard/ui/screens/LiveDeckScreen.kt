package com.jms.pssoundboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.model.Board
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PADS_PER_BOARD
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.data.model.PlaybackMode
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.FieldBackground
import com.jms.pssoundboard.ui.theme.Ground
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkMuted
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.SpewYellow
import com.jms.pssoundboard.ui.theme.StopRed
import com.jms.pssoundboard.ui.theme.Surface2
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.TealBright
import com.jms.pssoundboard.ui.theme.WarningAmber
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.padColour
import com.jms.pssoundboard.ui.theme.spewPulse
import kotlin.math.roundToInt

private val StopInk = Color(0xFF1F0604)

@Composable
fun LiveDeckScreen(
    deckState: DeckState,
    gain: Float,
    playingPads: Set<String>,
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

    // The first favourite on this deck that has a sound becomes the double-size hero pad.
    val heroSlot = activeBoard.pads.firstOrNull { pad ->
        pad.filePath != null && PadRef(activeBoard.id, pad.slot) in deckState.favorites
    }?.slot

    // Keep the selected deck's chip in view, e.g. after switching decks from the mini-deck.
    val chipListState = rememberLazyListState()
    val activeIndex = deckState.boards.indexOfFirst { it.id == activeBoard.id }
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) chipListState.animateScrollToItem(activeIndex)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Spew Deck", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = activeBoard.name,
                    color = InkLight,
                    fontFamily = BricolageCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 38.sp,
                    lineHeight = 36.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyRow(
                state = chipListState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(deckState.boards, key = { it.id }) { board ->
                    val isActive = board.id == activeBoard.id
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(ChipBlob)
                            .background(if (isActive) InkLight else Surface2)
                            .clickable(onClickLabel = if (isActive) "Edit deck" else "Open deck") {
                                if (isActive) managingBoard = board else onSelectBoard(board.id)
                            }
                            .padding(start = 16.dp, end = if (isActive) 7.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = board.name,
                            color = if (isActive) Ground else InkLight,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1
                        )
                        if (isActive) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Ground.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(PlayfulIcons.Edit, contentDescription = null, tint = Ground, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
                item(key = "add_deck") {
                    Box(
                        modifier = Modifier
                            .size(width = 48.dp, height = 44.dp)
                            .clip(CircleShape)
                            .background(Surface2)
                            .clickable(onClickLabel = "Add a deck") { isAddingBoard = true }
                            .semantics { contentDescription = "Add a deck" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(PlayfulIcons.Plus, contentDescription = null, tint = InkLight, modifier = Modifier.size(18.dp))
                    }
                }
            }

            PadGrid(
                board = activeBoard,
                deckState = deckState,
                heroSlot = heroSlot,
                playingPads = playingPads,
                onPressPad = onPressPad,
                onReleasePad = onReleasePad,
                onEditPad = onEditPad,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(FieldBackground)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Volume", color = InkMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Slider(
                    value = gain,
                    onValueChange = onSetGain,
                    valueRange = 0.10f..1.00f,
                    colors = SliderDefaults.colors(
                        thumbColor = SpewYellow,
                        activeTrackColor = SpewYellow,
                        inactiveTrackColor = Surface2
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(gain * 100).roundToInt()}%",
                    color = InkLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
        }

        // Stop all only exists while something is spewing; it bounces in over the bottom of the deck.
        AnimatedVisibility(
            visible = playingPads.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 78.dp),
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .gummy(StopRed, PadShape.PEBBLE.shape)
                    .clickable(onClickLabel = "Stop all sounds", role = Role.Button) { onStopAll() }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(PlayfulIcons.Stop, contentDescription = null, tint = StopInk, modifier = Modifier.size(18.dp))
                Text(
                    text = "Stop all",
                    color = StopInk,
                    fontFamily = BricolageCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp
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

/** 3-column pad grid; with a favourite, it takes a double-size cell in the top-left corner. */
@Composable
private fun PadGrid(
    board: Board,
    deckState: DeckState,
    heroSlot: Int?,
    playingPads: Set<String>,
    onPressPad: (PadRef, Pad) -> Unit,
    onReleasePad: (PadRef, Pad) -> Unit,
    onEditPad: (boardId: String, Pad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = 8.dp

    @Composable
    fun Cell(slot: Int, hero: Boolean, cellModifier: Modifier) {
        val pad = board.pads.getOrNull(slot) ?: Pad(slot = slot)
        val padRef = PadRef(board.id, slot)
        key(padRef.key) {
            TactilePad(
                padRef = padRef,
                pad = pad,
                isFavorite = padRef in deckState.favorites,
                isPlaying = padRef.key in playingPads,
                isHero = hero,
                onPress = { onPressPad(padRef, pad) },
                onRelease = { onReleasePad(padRef, pad) },
                onLongClick = { onEditPad(board.id, pad) },
                modifier = cellModifier
            )
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(gap)) {
        if (heroSlot == null) {
            for (row in 0 until 4) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (col in 0 until 3) Cell(row * 3 + col, false, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            val others = (0 until PADS_PER_BOARD).filter { it != heroSlot }
            Row(Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Cell(heroSlot, true, Modifier.weight(2f).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    Cell(others[0], false, Modifier.weight(1f).fillMaxWidth())
                    Cell(others[1], false, Modifier.weight(1f).fillMaxWidth())
                }
            }
            others.drop(2).chunked(3).forEach { rowSlots ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rowSlots.forEach { Cell(it, false, Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        }
    }
}

private val PlaybackMode.shortLabel: String
    get() = when (this) {
        PlaybackMode.ONE_SHOT -> "Tap"
        PlaybackMode.HOLD_TO_PLAY -> "Hold"
        PlaybackMode.RESTART_ON_SPAM -> "Restart"
    }

@Composable
private fun TactilePad(
    padRef: PadRef,
    pad: Pad,
    isFavorite: Boolean,
    isPlaying: Boolean,
    isHero: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember(padRef.key) { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
        label = "pad_press_scale"
    )

    // Capture latest lambdas so pointerInput never executes stale callbacks across board switches
    val currentOnPress by rememberUpdatedState(onPress)
    val currentOnRelease by rememberUpdatedState(onRelease)
    val currentOnLongClick by rememberUpdatedState(onLongClick)

    val hasSound = pad.filePath != null
    val shape = PadShape.of(pad.shape, pad.slot).shape
    val colour = if (hasSound) padColour(pad.colorHex) else Surface2
    val ink = if (hasSound) inkOn(colour) else InkMuted
    val description = buildString {
        append("Pad ${pad.slot + 1}, ")
        append(if (hasSound) pad.title else "empty")
        if (hasSound) append(", ${pad.mode.shortLabel}")
        if (isFavorite) append(", favourite")
        if (isPlaying) append(", spewing")
    }

    Box(
        modifier = modifier
            .padding(if (isHero) 4.dp else 2.dp)
            .scale(pressScale)
            .spewPulse(isPlaying, colour, shape)
            .then(if (hasSound) Modifier.gummy(colour, shape) else Modifier.clip(shape).background(Surface2))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick(label = "Spew") {
                    currentOnPress()
                    currentOnRelease()
                    true
                }
                onLongClick(label = "Edit pad") {
                    currentOnLongClick()
                    true
                }
            }
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
            .padding(if (isHero) PaddingValues(horizontal = 18.dp, vertical = 16.dp) else PaddingValues(horizontal = 8.dp, vertical = 6.dp)),
        contentAlignment = if (isHero) Alignment.TopStart else Alignment.Center
    ) {
        if (isHero) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ground.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(PlayfulIcons.Star, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
                    Text(
                        text = if (isPlaying) "Favourite · spewing" else "Favourite",
                        color = ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = pad.title,
                    color = ink,
                    fontFamily = BricolageCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 40.sp,
                    lineHeight = 38.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(pad.mode.shortLabel, color = ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (hasSound) pad.title else "Empty",
                    color = ink,
                    fontFamily = BricolageCondensed,
                    fontWeight = if (hasSound) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 16.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (hasSound) {
                    Text(
                        text = if (isPlaying) "Spewing" else pad.mode.shortLabel,
                        color = ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
