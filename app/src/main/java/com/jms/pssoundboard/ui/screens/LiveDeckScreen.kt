package com.jms.pssoundboard.ui.screens

import androidx.compose.ui.unit.em
import androidx.compose.ui.text.TextStyle
import com.jms.pssoundboard.ui.components.PadLabel
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.PointerEventPass
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
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
import com.jms.pssoundboard.ui.components.CapsuleShape
import com.jms.pssoundboard.ui.components.IconBlobButton
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.padColour
import com.jms.pssoundboard.ui.theme.spew
import com.jms.pssoundboard.ui.theme.spewPulse
import kotlin.math.roundToInt
import androidx.compose.runtime.saveable.rememberSaveable

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
    onAddCards: (boardId: String) -> Unit = {},
    onDeletePad: (boardId: String, slot: Int) -> Unit = { _, _ -> },
    onDeleteMultiplePads: (boardId: String, slots: Set<Int>) -> Unit = { _, _ -> },
    onMovePadsToBoard: (fromBoardId: String, toBoardId: String, slots: Set<Int>) -> Unit = { _, _, _ -> },
    onCreateBoardAndMovePads: (name: String, fromBoardId: String, slots: Set<Int>) -> Unit = { _, _, _ -> },
    onSharePads: (pads: List<Pad>) -> Unit = {},
    onUpdatePadsMode: (boardId: String, slots: Set<Int>, mode: PlaybackMode) -> Unit = { _, _, _ -> },
    onUpdatePadsColor: (boardId: String, slots: Set<Int>, colorHex: String) -> Unit = { _, _, _ -> },
    onSetGain: (Float) -> Unit,
    onStopAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeBoard = deckState.activeBoard ?: return
    var managingBoard by remember { mutableStateOf<Board?>(null) }
    var isAddingBoard by remember { mutableStateOf(false) }
    var boardSelectorExpanded by remember { mutableStateOf(false) }
    // While on, tapping a pad opens its editor instead of playing it, so holding a pad only ever plays.
    var editingPads by rememberSaveable { mutableStateOf(false) }

    val activeIndex = deckState.boards.indexOfFirst { it.id == activeBoard.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = activeIndex) { deckState.boards.size }
    val currentBoard = deckState.boards.getOrNull(pagerState.currentPage) ?: activeBoard

    var selectedPadSlots by remember(currentBoard.id) { mutableStateOf(setOf<Int>()) }
    var movingPadsDialog by remember { mutableStateOf(false) }
    var colorPickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(editingPads) {
        if (!editingPads) {
            selectedPadSlots = emptySet()
        }
    }

    // Keep pager synchronized when activeBoardId changes externally (e.g. from dropdown or mini-deck)
    LaunchedEffect(activeBoard.id, deckState.boards.size) {
        val targetIndex = deckState.boards.indexOfFirst { it.id == activeBoard.id }
        if (targetIndex >= 0 && targetIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetIndex)
        }
    }

    // Keep active board in ViewModel synchronized when user swipes to another deck
    LaunchedEffect(pagerState.settledPage) {
        val boardAtPage = deckState.boards.getOrNull(pagerState.settledPage)
        if (boardAtPage != null && boardAtPage.id != activeBoard.id) {
            onSelectBoard(boardAtPage.id)
        }
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    @Composable
    fun DeckSelector(titleSize: TextUnit, modifier: Modifier = Modifier) {
        Box(modifier = modifier) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Change or manage soundboard"
                    ) { boardSelectorExpanded = true }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Spew Deck",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = currentBoard.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = BricolageCondensed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = titleSize,
                            lineHeight = titleSize * 0.95f,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = PlayfulIcons.ChevronDown,
                            contentDescription = "Select soundboard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = boardSelectorExpanded,
                onDismissRequest = { boardSelectorExpanded = false },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .width(260.dp)
            ) {
                Text(
                    text = "SOUNDBOARDS",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
                deckState.boards.forEach { board ->
                    val isActive = board.id == currentBoard.id
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isActive) "✓  ${board.name}" else "    ${board.name}",
                                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${board.pads.count { it.filePath != null }} sounds",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        },
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        boardSelectorExpanded = false
                                        managingBoard = board
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = PlayfulIcons.Edit,
                                    contentDescription = "Manage ${board.name}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        },
                        onClick = {
                            boardSelectorExpanded = false
                            onSelectBoard(board.id)
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .padding(vertical = 4.dp)
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "+ Create new soundboard",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = PlayfulIcons.Plus,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        boardSelectorExpanded = false
                        isAddingBoard = true
                    }
                )
            }
        }
    }

    // The landscape column is only 240 dp wide, so there the toggle gets its own row
    // rather than squeezing the deck name.
    @Composable
    fun DeckTitle(titleSize: TextUnit, toggleOnOwnRow: Boolean) {
        val toggle = @Composable { EditPadsToggle(editing = editingPads, onToggle = { editingPads = !editingPads }) }
        if (toggleOnOwnRow) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeckSelector(titleSize)
                toggle()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DeckSelector(titleSize, Modifier.weight(1f, fill = false))
                toggle()
            }
        }
    }

    @Composable
    fun VolumePill() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.spew.field)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Volume", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Slider(
                value = gain,
                onValueChange = onSetGain,
                valueRange = 0.10f..1.00f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(gain * 100).roundToInt()}%",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (landscape) {
            // Landscape: deck controls in a left column, pads in a wide 2-row grid.
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DeckTitle(30.sp, toggleOnOwnRow = true)
                    Spacer(modifier = Modifier.weight(1f))
                    VolumePill()
                }
                HorizontalPager(
                    state = pagerState,
                    key = { page -> deckState.boards.getOrNull(page)?.id ?: page },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) { page ->
                    val board = deckState.boards.getOrNull(page)
                    if (board != null) {
                        val heroForBoard = board.pads.firstOrNull { pad ->
                            pad.filePath != null && PadRef(board.id, pad.slot) in deckState.favorites
                        }?.slot
                        PadGrid(
                            board = board,
                            deckState = deckState,
                            heroSlot = heroForBoard,
                            playingPads = playingPads,
                            landscape = true,
                            editingPads = editingPads,
                            selectedPadSlots = if (board.id == currentBoard.id) selectedPadSlots else emptySet(),
                            onPressPad = onPressPad,
                            onReleasePad = onReleasePad,
                            onEditPad = onEditPad,
                            onAddCards = onAddCards,
                            onDeletePad = { slot -> onDeletePad(board.id, slot) },
                            onToggleSelectPad = { slot ->
                                selectedPadSlots = if (slot in selectedPadSlots) selectedPadSlots - slot else selectedPadSlots + slot
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeckTitle(38.sp, toggleOnOwnRow = false)
                HorizontalPager(
                    state = pagerState,
                    key = { page -> deckState.boards.getOrNull(page)?.id ?: page },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val board = deckState.boards.getOrNull(page)
                    if (board != null) {
                        val heroForBoard = board.pads.firstOrNull { pad ->
                            pad.filePath != null && PadRef(board.id, pad.slot) in deckState.favorites
                        }?.slot
                        PadGrid(
                            board = board,
                            deckState = deckState,
                            heroSlot = heroForBoard,
                            playingPads = playingPads,
                            landscape = false,
                            editingPads = editingPads,
                            selectedPadSlots = if (board.id == currentBoard.id) selectedPadSlots else emptySet(),
                            onPressPad = onPressPad,
                            onReleasePad = onReleasePad,
                            onEditPad = onEditPad,
                            onAddCards = onAddCards,
                            onDeletePad = { slot -> onDeletePad(board.id, slot) },
                            onToggleSelectPad = { slot ->
                                selectedPadSlots = if (slot in selectedPadSlots) selectedPadSlots - slot else selectedPadSlots + slot
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                VolumePill()
                Spacer(modifier = Modifier.height(2.dp))
            }
        }

        // Stop all only exists while something is spewing; it bounces in over the bottom of the deck.
        AnimatedVisibility(
            visible = playingPads.isNotEmpty(),
            modifier = if (landscape) {
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 40.dp, bottom = 68.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 78.dp)
            },
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .gummy(MaterialTheme.colorScheme.error, PadShape.PEBBLE.shape)
                    .clickable(onClickLabel = "Stop all sounds", role = Role.Button) { onStopAll() }
                    .padding(horizontal = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(PlayfulIcons.Stop, contentDescription = null, tint = MaterialTheme.spew.onStop, modifier = Modifier.size(18.dp))
                Text(
                    text = "Stop all",
                    color = MaterialTheme.spew.onStop,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        // Floating Batch Action Bar when editing decks and 1 or more cards are selected
        AnimatedVisibility(
            visible = editingPads && selectedPadSlots.isNotEmpty(),
            modifier = if (landscape) {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 18.dp)
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 74.dp, start = 14.dp, end = 14.dp)
            },
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 600f)) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                modifier = if (landscape) Modifier.width(380.dp).height(54.dp) else Modifier.fillMaxWidth().height(54.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${selectedPadSlots.size} selected",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .weight(1f)
                    )

                    IconBlobButton(
                        icon = PlayfulIcons.Trash,
                        contentDescription = "Delete selected cards",
                        colour = MaterialTheme.colorScheme.error,
                        onClick = {
                            onDeleteMultiplePads(currentBoard.id, selectedPadSlots)
                            selectedPadSlots = emptySet()
                        }
                    )

                    IconBlobButton(
                        icon = PlayfulIcons.Folder,
                        contentDescription = "Move selected cards to another soundboard",
                        colour = MaterialTheme.colorScheme.primary,
                        onClick = { movingPadsDialog = true }
                    )

                    IconBlobButton(
                        icon = PlayfulIcons.Share,
                        contentDescription = "Share selected cards",
                        colour = MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            val pads = currentBoard.pads.filter { it.slot in selectedPadSlots }
                            onSharePads(pads)
                        }
                    )

                    var overflowMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconBlobButton(
                            icon = PlayfulIcons.More,
                            contentDescription = "More actions for selected cards",
                            onClick = { overflowMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Select all (${currentBoard.pads.size})") },
                                leadingIcon = { Icon(PlayfulIcons.Check, null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    selectedPadSlots = currentBoard.pads.map { it.slot }.toSet()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Deselect all") },
                                leadingIcon = { Icon(PlayfulIcons.Close, null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    selectedPadSlots = emptySet()
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .padding(vertical = 2.dp)
                            )

                            DropdownMenuItem(
                                text = { Text("Tap mode (One-Shot)") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onUpdatePadsMode(currentBoard.id, selectedPadSlots, PlaybackMode.ONE_SHOT)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Hold to play mode") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onUpdatePadsMode(currentBoard.id, selectedPadSlots, PlaybackMode.HOLD_TO_PLAY)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Restart on spam mode") },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onUpdatePadsMode(currentBoard.id, selectedPadSlots, PlaybackMode.RESTART_ON_SPAM)
                                }
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                                    .padding(vertical = 2.dp)
                            )

                            DropdownMenuItem(
                                text = { Text("Change card color…") },
                                leadingIcon = { Icon(PlayfulIcons.Edit, null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    colorPickerDialog = true
                                }
                            )
                        }
                    }

                    IconBlobButton(
                        icon = PlayfulIcons.Close,
                        contentDescription = "Clear selection",
                        onClick = { selectedPadSlots = emptySet() }
                    )
                }
            }
        }
    }

    // Manage Deck Dialog (Rename, Clear, Reset to Defaults, Delete)
    managingBoard?.let { board ->
        var editName by remember(board.id) { mutableStateOf(board.name) }
        Dialog(onDismissRequest = { managingBoard = null }) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.background,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.spew.accent.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "MANAGE DECK: ${board.name}",
                        color = MaterialTheme.spew.accent,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleSmall
                    )

                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Deck Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.spew.accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.spew.accent,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.spew.accent),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Name")
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline)
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
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.spew.warning),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.spew.warning.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear All Pads", style = MaterialTheme.typography.bodySmall)
                        }

                        OutlinedButton(
                            onClick = {
                                onResetBoard(board.id)
                                managingBoard = null
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.spew.accent.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reset Defaults", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // Delete Deck Button (only if more than 1 board exists)
                    if (deckState.boards.size > 1) {
                        Button(
                            onClick = {
                                onDeleteBoard(board.id)
                                managingBoard = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete This Deck", style = MaterialTheme.typography.labelSmall)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "New deck",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    PlayfulField(
                        value = newDeckTitle,
                        onValueChange = { newDeckTitle = it },
                        placeholder = "Deck name, like Movie Quotes"
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SoftButton(text = "Cancel", onClick = { isAddingBoard = false }, light = false, modifier = Modifier.weight(1f))
                        SpewButton(
                            label = "Create deck",
                            contentDescription = "Create deck",
                            onClick = {
                                onAddBoard(newDeckTitle.ifBlank { "New Deck" })
                                isAddingBoard = false
                            },
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }
    }

    // Move Selected Pads to Another Deck Dialog
    if (movingPadsDialog) {
        var createNewDeckInMove by remember { mutableStateOf(false) }
        var newDeckNameInMove by remember { mutableStateOf("") }
        val otherBoards = deckState.boards.filterNot { it.id == currentBoard.id }

        Dialog(onDismissRequest = { movingPadsDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Move ${selectedPadSlots.size} card(s) to:",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (!createNewDeckInMove) {
                        if (otherBoards.isNotEmpty()) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                otherBoards.forEach { targetBoard ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                            .clickable {
                                                onMovePadsToBoard(currentBoard.id, targetBoard.id, selectedPadSlots)
                                                selectedPadSlots = emptySet()
                                                movingPadsDialog = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = targetBoard.name,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${targetBoard.pads.count { it.filePath != null }} sounds",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "No other soundboards exist yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoftButton(
                                text = "Cancel",
                                onClick = { movingPadsDialog = false },
                                light = false,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { createNewDeckInMove = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.3f)
                            ) {
                                Text("+ New soundboard")
                            }
                        }
                    } else {
                        PlayfulField(
                            value = newDeckNameInMove,
                            onValueChange = { newDeckNameInMove = it },
                            placeholder = "New soundboard name"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoftButton(
                                text = "Back",
                                onClick = { createNewDeckInMove = false },
                                light = false,
                                modifier = Modifier.weight(1f)
                            )
                            SpewButton(
                                label = "Create & Move",
                                contentDescription = "Create new soundboard and move selected cards",
                                onClick = {
                                    onCreateBoardAndMovePads(
                                        newDeckNameInMove.ifBlank { "New Deck" },
                                        currentBoard.id,
                                        selectedPadSlots
                                    )
                                    selectedPadSlots = emptySet()
                                    movingPadsDialog = false
                                },
                                modifier = Modifier.weight(1.3f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Change Color of Selected Cards Dialog
    if (colorPickerDialog) {
        val palette = listOf(
            "#008080", "#06B6D4", "#3B82F6", "#6366F1", "#A855F7",
            "#EC4899", "#F97316", "#EAB308", "#22C55E", "#334155"
        )
        Dialog(onDismissRequest = { colorPickerDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Pick color for ${selectedPadSlots.size} card(s)",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        palette.take(5).forEach { hex ->
                            val col = padColour(hex)
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable {
                                        onUpdatePadsColor(currentBoard.id, selectedPadSlots, hex)
                                        colorPickerDialog = false
                                    }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        palette.drop(5).forEach { hex ->
                            val col = padColour(hex)
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .clickable {
                                        onUpdatePadsColor(currentBoard.id, selectedPadSlots, hex)
                                        colorPickerDialog = false
                                    }
                            )
                        }
                    }

                    SoftButton(
                        text = "Cancel",
                        onClick = { colorPickerDialog = false },
                        light = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Scrollable pad grid with no limit on cards:
 * 3 columns in portrait (or 4 columns in landscape) showing every card on the soundboard.
 * The active favorite card (if any) spans across 2 columns as a hero card.
 */
@Composable
private fun PadGrid(
    board: Board,
    deckState: DeckState,
    heroSlot: Int?,
    playingPads: Set<String>,
    landscape: Boolean,
    editingPads: Boolean,
    selectedPadSlots: Set<Int>,
    onPressPad: (PadRef, Pad) -> Unit,
    onReleasePad: (PadRef, Pad) -> Unit,
    onEditPad: (boardId: String, Pad) -> Unit,
    onAddCards: (boardId: String) -> Unit,
    onDeletePad: (slot: Int) -> Unit,
    onToggleSelectPad: (slot: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gap = 8.dp
    val columnsCount = if (landscape) 4 else 3
    val gridState = rememberLazyGridState()

    // Determine the list of pads to display (all pads in the board, or default 12 if empty)
    val displayPads = if (board.pads.isEmpty()) {
        List(PADS_PER_BOARD) { Pad(slot = it) }
    } else {
        board.pads
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columnsCount),
        state = gridState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(
            items = displayPads,
            key = { pad -> "${board.id}:${pad.slot}" },
            span = { pad ->
                if (pad.slot == heroSlot && !landscape) {
                    GridItemSpan(2)
                } else {
                    GridItemSpan(1)
                }
            }
        ) { pad ->
            val padRef = PadRef(board.id, pad.slot)
            val isHero = pad.slot == heroSlot && !landscape
            val cellHeight = if (isHero) 180.dp else 104.dp
            val isSelected = pad.slot in selectedPadSlots

            TactilePad(
                padRef = padRef,
                pad = pad,
                isFavorite = padRef in deckState.favorites,
                isPlaying = padRef.key in playingPads,
                isHero = isHero,
                editing = editingPads,
                isSelected = isSelected,
                anySelected = selectedPadSlots.isNotEmpty(),
                onPress = { onPressPad(padRef, pad) },
                onRelease = { onReleasePad(padRef, pad) },
                onEdit = { onEditPad(board.id, pad) },
                onDelete = { onDeletePad(pad.slot) },
                onToggleSelect = { onToggleSelectPad(pad.slot) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cellHeight)
            )
        }

        // At the bottom of the deck, put a + button to add another 3 cards (no limit)
        item(
            span = { GridItemSpan(columnsCount) },
            key = "add_more_cards_${board.id}"
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .height(52.dp)
                        .gummy(MaterialTheme.colorScheme.primary, CapsuleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Add 3 cards to ${board.name}"
                        ) { onAddCards(board.id) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .gummy(MaterialTheme.spew.onBlob, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = PlayfulIcons.Plus,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "+ Add 3 cards",
                        color = MaterialTheme.spew.onBlob,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Switches the deck between playing pads and editing them. 48 dp tall so it's an easy target. */
@Composable
private fun EditPadsToggle(editing: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(ChipBlob)
            .background(if (editing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = if (editing) "Stop editing pads" else "Edit pads") { onToggle() }
            .semantics { contentDescription = if (editing) "Editing pads, done" else "Edit pads" }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val ink = if (editing) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
        Icon(if (editing) PlayfulIcons.Check else PlayfulIcons.Edit, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(
            text = if (editing) "Done" else "Edit pads",
            color = ink,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1
        )
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
    editing: Boolean,
    isSelected: Boolean = false,
    anySelected: Boolean = false,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
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
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnToggleSelect by rememberUpdatedState(onToggleSelect)

    val hasSound = pad.filePath != null
    val shape = PadShape.of(pad.shape, pad.slot).shape
    val colour = if (hasSound) padColour(pad.colorHex) else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (hasSound) inkOn(colour) else MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildString {
        append("Pad ${pad.slot + 1}, ")
        append(if (hasSound) pad.title else "empty")
        if (hasSound) append(", ${pad.mode.shortLabel}")
        if (isFavorite) append(", favourite")
        if (isPlaying) append(", spewing")
        if (isSelected) append(", selected")
    }
    val modeLabel = when {
        editing -> "Edit"
        isPlaying -> "Spewing"
        else -> pad.mode.shortLabel
    }

    val borderMod = if (isSelected) {
        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .padding(if (isHero) 4.dp else 2.dp)
            .scale(pressScale)
            .spewPulse(isPlaying, colour, shape)
            .then(borderMod)
            .then(if (hasSound) Modifier.gummy(colour, shape) else Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (editing) {
                    onClick(label = if (anySelected) "Toggle select pad" else "Edit pad") {
                        if (anySelected) currentOnToggleSelect() else currentOnEdit()
                        true
                    }
                } else {
                    onClick(label = "Spew") {
                        currentOnPress()
                        currentOnRelease()
                        true
                    }
                }
            }
            // Calibrated touch handling: discriminates between taps/holds and swipes in any direction.
            // If the finger moves more than touchSlop (e.g. user swiping horizontally to switch decks
            // or scrolling vertically through cards), it cancels immediately without activating sound.
            .pointerInput(padRef.key, editing, anySelected) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downId = down.id
                    val downPos = down.position
                    val touchSlop = viewConfiguration.touchSlop

                    var isTapOrHold = false
                    var isCancelled = false
                    val startTime = System.currentTimeMillis()
                    val holdThresholdMs = 70L

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val pointer = event.changes.find { it.id == downId }

                        if (pointer == null || pointer.isConsumed) {
                            isCancelled = true
                            break
                        }

                        val diff = pointer.position - downPos
                        val distanceSq = diff.x * diff.x + diff.y * diff.y
                        if (distanceSq > touchSlop * touchSlop) {
                            // Finger moved in any direction beyond touch slop -> swipe detected!
                            isCancelled = true
                            break
                        }

                        if (!pointer.pressed) {
                            // Finger lifted within touch slop -> genuine tap
                            pointer.consume()
                            if (editing) {
                                if (anySelected) currentOnToggleSelect()
                                else currentOnEdit()
                            } else {
                                if (!isTapOrHold) {
                                    isPressed = true
                                    currentOnPress()
                                    isPressed = false
                                    currentOnRelease()
                                } else {
                                    isPressed = false
                                    currentOnRelease()
                                }
                            }
                            break
                        }

                        val elapsed = System.currentTimeMillis() - startTime
                        if (!editing && !isTapOrHold && elapsed >= holdThresholdMs) {
                            // Held in place without swiping -> begin playing
                            isTapOrHold = true
                            isPressed = true
                            currentOnPress()
                        }
                    }

                    if (isCancelled && isTapOrHold) {
                        isPressed = false
                        currentOnRelease()
                    }
                }
            }
            .padding(if (isHero) PaddingValues(horizontal = 18.dp, vertical = 16.dp) else PaddingValues(horizontal = 8.dp, vertical = 6.dp)),
        contentAlignment = if (isHero) Alignment.TopStart else Alignment.Center
    ) {
        if (isHero) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .then(if (editing) Modifier.padding(horizontal = 26.dp) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(PlayfulIcons.Star, contentDescription = null, tint = ink, modifier = Modifier.size(12.dp))
                    Text(
                        text = if (isPlaying) "Favourite · spewing" else "Favourite",
                        color = ink,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                PadLabel(
                    text = pad.title,
                    style = TextStyle(
                        color = ink,
                        fontFamily = BricolageCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 40.sp,
                        lineHeight = 0.95.em
                    ),
                    maxLines = 3,
                    minFontSize = 20.sp
                )
                Text(modeLabel, color = ink, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = if (editing) Modifier.padding(top = 10.dp) else Modifier
            ) {
                PadLabel(
                    text = if (hasSound) pad.title else "Empty",
                    style = TextStyle(
                        color = ink,
                        fontFamily = BricolageCondensed,
                        fontWeight = if (hasSound) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 16.sp,
                        lineHeight = 1.06.em,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 3,
                    minFontSize = 10.sp
                )
                if (hasSound || editing) {
                    Text(
                        text = modeLabel,
                        color = ink,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // When in deck edit mode, show the '✕' delete button in top-left and select button in top-right
        if (editing) {
            // Top-Left: Small '✕' button to delete / clear this individual card
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 2.dp, start = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .pointerInput(padRef.key) {
                        detectTapGestures(onTap = { currentOnDelete() })
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Delete card ${pad.slot + 1}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = PlayfulIcons.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }

            // Top-Right: Selection checkbox button to toggle group selection
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f))
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
                        shape = CircleShape
                    )
                    .pointerInput(padRef.key, isSelected) {
                        detectTapGestures(onTap = { currentOnToggleSelect() })
                    }
                    .semantics {
                        role = Role.Checkbox
                        contentDescription = if (isSelected) "Card ${pad.slot + 1} selected" else "Select card ${pad.slot + 1}"
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = PlayfulIcons.Check,
                        contentDescription = null,
                        tint = MaterialTheme.spew.onBlob,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
