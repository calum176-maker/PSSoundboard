package com.jms.pssoundboard.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.ui.components.PadPicker
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.SectionLabel
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton

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

                PadPicker(
                    deckState = deckState,
                    selectedBoardId = selectedBoardId,
                    selectedSlot = selectedSlot,
                    onSelect = { boardId, slot ->
                        selectedBoardId = boardId
                        selectedSlot = slot
                    }
                )

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
