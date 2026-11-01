package com.jms.pssoundboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PADS_PER_BOARD
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.condensed
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.padColour

private const val PICKER_COLUMNS = 3

/** A small bold label above a group of controls in a dialog. */
@Composable
fun SectionLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
}

/**
 * Chooses where a sound goes: a deck chip for each deck, then that deck's pads as mini pads in
 * rows of three, so all 12 fit on a narrow phone. Picking another deck moves to its first empty pad.
 */
@Composable
fun PadPicker(
    deckState: DeckState,
    selectedBoardId: String,
    selectedSlot: Int,
    onSelect: (boardId: String, slot: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBoard = deckState.board(selectedBoardId) ?: deckState.boards.first()
    val targetedPad = currentBoard.pads.getOrNull(selectedSlot)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionLabel("Which deck?")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(deckState.boards, key = { it.id }) { board ->
                BlobChip(
                    text = board.name,
                    selected = board.id == currentBoard.id,
                    onClick = {
                        val firstEmpty = board.pads.indexOfFirst { it.filePath == null }
                        onSelect(board.id, if (firstEmpty >= 0) firstEmpty else selectedSlot)
                    }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SectionLabel("Which pad?")
            Text(
                text = if (targetedPad?.filePath != null) "Replaces \"${targetedPad.title}\"" else "Empty pad",
                color = if (targetedPad?.filePath != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (0 until PADS_PER_BOARD).chunked(PICKER_COLUMNS).forEach { rowSlots ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowSlots.forEach { slot ->
                        val pad = currentBoard.pads.getOrNull(slot)
                        TargetPad(
                            number = slot + 1,
                            title = pad?.takeIf { it.filePath != null }?.title,
                            colourHex = pad?.colorHex,
                            shape = PadShape.of(pad?.shape, slot),
                            selected = slot == selectedSlot,
                            onClick = { onSelect(currentBoard.id, slot) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** A mini pad in the target grid. Grows with the text, so long names and large fonts aren't cut off. */
@Composable
private fun TargetPad(
    number: Int,
    title: String?,
    colourHex: String?,
    shape: PadShape,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filled = title != null
    val colour = padColour(colourHex ?: "")
    val fill = if (filled) Modifier.gummy(colour, shape.shape) else Modifier.clip(shape.shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    val ink = if (filled) inkOn(colour) else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .heightIn(min = 60.dp)
            .then(fill)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape.shape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = "Pad $number, ${title ?: "empty"}" + if (selected) ", selected" else ""
            }
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$number", color = ink, style = MaterialTheme.typography.labelSmall)
            PadLabel(
                text = title ?: "Empty",
                style = MaterialTheme.typography.labelMedium
                    .condensed(if (filled) FontWeight.Bold else FontWeight.Medium)
                    .copy(color = ink, lineHeight = 1.15.em, textAlign = TextAlign.Center),
                maxLines = 2,
                minFontSize = 9.sp
            )
        }
    }
}
