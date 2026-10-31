package com.jms.pssoundboard.ui.dialogs

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateSurface
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.TealBright
import com.jms.pssoundboard.ui.theme.WarningAmber
import com.jms.pssoundboard.ui.theme.toComposeColor

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
                .padding(6.dp),
            shape = RoundedCornerShape(20.dp),
            color = SlateBackground,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Teal.copy(alpha = 0.7f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Column {
                    Text(
                        text = "EQUIP SOUND TO PAD",
                        color = TealBright,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Clip: \"${item.title}\" (${item.category})",
                        color = SlateText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Custom Title Override
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("Pad Label on Deck") },
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

                // Target Board Selector Tabs
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "1. CHOOSE TARGET DECK:",
                        color = SlateTextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(deckState.boards, key = { it.id }) { board ->
                            val isSelected = board.id == selectedBoardId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Teal else SlateSurface)
                                    .border(1.dp, if (isSelected) Color.White else SlateOutline, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedBoardId = board.id
                                        // Pick first empty slot on this board if available
                                        val firstEmpty = board.pads.indexOfFirst { it.filePath == null }
                                        if (firstEmpty >= 0) selectedSlot = firstEmpty
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = board.name,
                                    color = if (isSelected) Color.White else SlateText,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Target Pad Slot Grid (1 - 12)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. TARGET PAD IN \"${currentBoard.name.uppercase()}\":",
                            color = TealBright,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = if (targetedPad?.filePath != null) "Overwriting: ${targetedPad.title}" else "Empty Slot",
                            color = if (targetedPad?.filePath != null) WarningAmber else SlateTextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (row in 0 until 4) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (col in 0 until 3) {
                                    val slot = row * 3 + col
                                    val pad = currentBoard.pads.getOrNull(slot)
                                    val isSelected = slot == selectedSlot
                                    val hasClip = pad?.filePath != null
                                    val padColor = pad?.colorHex?.toComposeColor(Teal) ?: Teal

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) Teal
                                                else if (hasClip) padColor.copy(alpha = 0.35f)
                                                else SlateSurface
                                            )
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color.White else if (hasClip) padColor.copy(alpha = 0.7f) else SlateOutline,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable { selectedSlot = slot }
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "#${slot + 1}",
                                                color = if (isSelected) Color.White else if (hasClip) Color.White.copy(alpha = 0.8f) else SlateTextMuted,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = pad?.title ?: "Empty",
                                                color = if (isSelected) Color.White else if (hasClip) Color.White else SlateTextMuted,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextMuted),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            val target = PadRef(selectedBoardId, selectedSlot)
                            onEquip(target, customTitle.ifBlank { item.title })
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Teal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.5f)
                    ) {
                        Text(
                            text = "Equip → ${currentBoard.name} #${selectedSlot + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
