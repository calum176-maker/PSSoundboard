package com.jms.pssoundboard.ui.dialogs

import androidx.compose.ui.text.TextStyle
import com.jms.pssoundboard.ui.components.PadLabel
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.ui.components.BlobChip
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.padColour

@Composable
fun EquipTargetDialog(
    item: LibraryItem,
    deckState: DeckState,
    onEquip: (target: PadRef, title: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedBoardId by remember { mutableStateOf(deckState.activeBoardId) }
    var selectedSlot by remember { mutableIntStateOf(0) }
    var customTitle by remember(item.id) { mutableStateOf(item.title) }

    val currentBoard = deckState.boards.firstOrNull { it.id == selectedBoardId } ?: deckState.boards.first()
    val targetedPad = currentBoard.pads.getOrNull(selectedSlot)

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
                Column {
                    Text(
                        text = "Add to a pad",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${item.title} · ${item.category}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                SectionLabel("Name on the pad")
                PlayfulField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    placeholder = "Name on the pad"
                )

                SectionLabel("Which deck?")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(deckState.boards, key = { it.id }) { board ->
                        BlobChip(
                            text = board.name,
                            selected = board.id == selectedBoardId,
                            onClick = {
                                selectedBoardId = board.id
                                // Pick first empty slot on this board if available
                                val firstEmpty = board.pads.indexOfFirst { it.filePath == null }
                                if (firstEmpty >= 0) selectedSlot = firstEmpty
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
                    for (row in 0 until 4) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (col in 0 until 3) {
                                val slot = row * 3 + col
                                val pad = currentBoard.pads.getOrNull(slot)
                                TargetPad(
                                    number = slot + 1,
                                    title = pad?.takeIf { it.filePath != null }?.title,
                                    colourHex = pad?.colorHex,
                                    shape = PadShape.of(pad?.shape, slot),
                                    selected = slot == selectedSlot,
                                    onClick = { selectedSlot = slot },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoftButton(text = "Cancel", onClick = onDismiss, light = false, modifier = Modifier.weight(1f))
                    SpewButton(
                        label = "Add to pad ${selectedSlot + 1}",
                        contentDescription = "Add to pad ${selectedSlot + 1} in ${currentBoard.name}",
                        onClick = {
                            onEquip(PadRef(selectedBoardId, selectedSlot), customTitle.ifBlank { item.title })
                            onDismiss()
                        },
                        modifier = Modifier.weight(1.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
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
                style = TextStyle(
                    color = ink,
                    fontSize = 13.sp,
                    fontWeight = if (filled) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = 1.15.em,
                    textAlign = TextAlign.Center
                ),
                maxLines = 2
            )
        }
    }
}
