package com.jms.pssoundboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.SpewEntry
import com.jms.pssoundboard.data.model.VaultSortOrder
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.ReadyGreen
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateSurface
import com.jms.pssoundboard.ui.theme.SlateSurfaceHigh
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.TealBright
import com.jms.pssoundboard.ui.theme.WarningAmber

@Composable
fun SpewVaultScreen(
    spewEntries: List<SpewEntry>,
    onSpewEntry: (SpewEntry) -> Unit,
    onEquipItem: (LibraryItem) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteEntries: (Set<String>) -> Unit,
    onClearAll: () -> Unit,
    onSaveToDownloads: (SpewEntry) -> Unit,
    onSaveMultipleToDownloads: (List<SpewEntry>) -> Unit,
    onCopyToClipboard: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(VaultSortOrder.MOST_SPEWED) }
    var selectedEntryIds by remember { mutableStateOf(setOf<String>()) }

    val sortedEntries = remember(spewEntries, searchQuery, sortOrder) {
        val filtered = if (searchQuery.isBlank()) {
            spewEntries
        } else {
            spewEntries.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
        }

        when (sortOrder) {
            VaultSortOrder.MOST_SPEWED -> filtered.sortedWith(
                compareByDescending<SpewEntry> { it.isPinned }.thenByDescending { it.spewCount }
            )
            VaultSortOrder.RECENT -> filtered.sortedWith(
                compareByDescending<SpewEntry> { it.isPinned }.thenByDescending { it.lastSpewedEpochMs }
            )
            VaultSortOrder.PINNED_FIRST -> filtered.sortedByDescending { it.isPinned }
            VaultSortOrder.ALPHABETICAL -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    val totalSpews = remember(spewEntries) { spewEntries.sumOf { it.spewCount } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top Header Card with Stats
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SlateSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MASTER CLIP VAULT & SPEW DIRECTORY",
                        color = TealBright,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${spewEntries.size} clips remembered • $totalSpews total party spews",
                        color = SlateTextMuted,
                        fontSize = 11.sp
                    )
                }

                if (spewEntries.isNotEmpty()) {
                    OutlinedButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PanicRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, PanicRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear All", fontSize = 10.sp)
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search remembered clips…", color = SlateTextMuted) },
            singleLine = true,
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    Text(
                        text = "✕",
                        color = SlateTextMuted,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SlateText,
                unfocusedTextColor = SlateText,
                focusedBorderColor = Teal,
                unfocusedBorderColor = SlateOutline,
                focusedContainerColor = SlateSurface,
                unfocusedContainerColor = SlateSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        )

        // Sort Order Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(VaultSortOrder.values()) { order ->
                val isSelected = order == sortOrder
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Teal else SlateSurface)
                        .border(
                            1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.8f) else SlateOutline,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { sortOrder = order }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = order.label,
                        color = if (isSelected) Color.White else SlateText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Batch Selection Bar with 3-Dot Options
        AnimatedVisibility(
            visible = selectedEntryIds.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = SlateSurfaceHigh,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Teal),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "✓ ${selectedEntryIds.size} Selected",
                        color = TealBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Quick Copy button
                        Button(
                            onClick = {
                                val selectedList = sortedEntries.filter { it.id in selectedEntryIds }
                                val text = selectedList.joinToString("\n") {
                                    "${it.title}: ${it.sourceUrl ?: it.localFilePath ?: "Deck Clip"}"
                                }
                                onCopyToClipboard("${selectedList.size} Saved Clips", text)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SlateSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("📋 Copy", fontSize = 11.sp, color = Color.White)
                        }

                        // Quick Save to Downloads button
                        Button(
                            onClick = {
                                val selectedList = sortedEntries.filter { it.id in selectedEntryIds }
                                onSaveMultipleToDownloads(selectedList)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("💾 Save All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // 3-dot Option Menu for Selected Vault Entries
                        var batchMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateSurface)
                                    .border(1.dp, Teal, RoundedCornerShape(8.dp))
                                    .clickable { batchMenuExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⋮", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }

                            DropdownMenu(
                                expanded = batchMenuExpanded,
                                onDismissRequest = { batchMenuExpanded = false },
                                modifier = Modifier.background(SlateSurfaceHigh)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📋 Copy All Links to Clipboard", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        val selectedList = sortedEntries.filter { it.id in selectedEntryIds }
                                        val text = selectedList.joinToString("\n") {
                                            "${it.title}: ${it.sourceUrl ?: it.localFilePath ?: "Deck Clip"}"
                                        }
                                        onCopyToClipboard("${selectedList.size} Saved Clips", text)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💾 Save Selected to Downloads", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        val selectedList = sortedEntries.filter { it.id in selectedEntryIds }
                                        onSaveMultipleToDownloads(selectedList)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🗑️ Delete Selected from Vault", color = PanicRed, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        onDeleteEntries(selectedEntryIds)
                                        selectedEntryIds = emptySet()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("✕ Deselect All", color = SlateTextMuted, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        selectedEntryIds = emptySet()
                                    }
                                )
                            }
                        }

                        // Clear selection button
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(SlateSurface)
                                .clickable { selectedEntryIds = emptySet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = SlateTextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Empty state
        if (spewEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "📂 Spew Vault is Empty",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Every time you fire a sound from your Live Deck, or tap \"⚡ Spew\" in the Sound Library, it is automatically cataloged in this directory with play counts and timestamps so you can spam it again anytime!",
                        color = SlateTextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // Remembered Directory List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(sortedEntries, key = { it.id }) { entry ->
                    val isSelected = selectedEntryIds.contains(entry.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) SlateSurfaceHigh else SlateSurface)
                            .border(
                                1.5.dp,
                                when {
                                    isSelected -> TealBright
                                    entry.isPinned -> Teal
                                    else -> SlateOutline
                                },
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Selection Checkbox / Circle
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Teal else SlateSurfaceHigh)
                                .border(1.5.dp, if (isSelected) TealBright else SlateOutline, CircleShape)
                                .clickable {
                                    selectedEntryIds = if (isSelected) selectedEntryIds - entry.id else selectedEntryIds + entry.id
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Text("✓", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Pin Button
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(if (entry.isPinned) Teal.copy(alpha = 0.3f) else SlateSurfaceHigh)
                                .clickable { onTogglePin(entry.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (entry.isPinned) "★" else "☆",
                                color = if (entry.isPinned) WarningAmber else SlateTextMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Title, Category, Count Badge & Timestamp
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (entry.spewCount > 5) PanicRed.copy(alpha = 0.25f) else Teal.copy(alpha = 0.25f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${entry.spewCount}x",
                                        color = if (entry.spewCount > 5) PanicRed else TealBright,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = entry.category,
                                    color = SlateTextMuted,
                                    fontSize = 10.sp
                                )
                                Text("•", color = SlateTextMuted, fontSize = 10.sp)
                                Text(
                                    text = entry.formattedLastSpewed(),
                                    color = SlateTextMuted.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Action Controls: ⚡ SPEW + ➕ EQUIP + ⋮ 3-Dot Options Menu
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Button(
                                onClick = { onSpewEntry(entry) },
                                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "⚡ SPEW",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SlateSurfaceHigh)
                                    .clickable {
                                        val item = LibraryItem(
                                            id = entry.id,
                                            title = entry.title,
                                            category = entry.category,
                                            url = entry.sourceUrl,
                                            localUri = entry.localFilePath
                                        )
                                        onEquipItem(item)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("➕", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            // 3-dot Option Button for this vault clip
                            var itemMenuExpanded by remember { mutableStateOf(false) }
                            Box {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SlateSurfaceHigh)
                                        .border(1.dp, SlateOutline, RoundedCornerShape(8.dp))
                                        .clickable { itemMenuExpanded = true },
                                contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "⋮",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                DropdownMenu(
                                    expanded = itemMenuExpanded,
                                    onDismissRequest = { itemMenuExpanded = false },
                                    modifier = Modifier.background(SlateSurfaceHigh)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("📋 Copy Sound Link", color = Color.White, fontSize = 12.sp) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            val link = entry.sourceUrl ?: entry.localFilePath ?: entry.title
                                            onCopyToClipboard(entry.title, link)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("💾 Save to Device (Downloads)", color = Color.White, fontSize = 12.sp) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            onSaveToDownloads(entry)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("⚡ Spew into Party", color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            onSpewEntry(entry)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("➕ Equip to Soundboard", color = Color.White, fontSize = 12.sp) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            val item = LibraryItem(
                                                id = entry.id,
                                                title = entry.title,
                                                category = entry.category,
                                                url = entry.sourceUrl,
                                                localUri = entry.localFilePath
                                            )
                                            onEquipItem(item)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (entry.isPinned) "☆ Unpin Clip" else "★ Pin Clip", color = WarningAmber, fontSize = 12.sp) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            onTogglePin(entry.id)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("🗑️ Delete from Vault", color = PanicRed, fontSize = 12.sp) },
                                        onClick = {
                                            itemMenuExpanded = false
                                            onDeleteEntries(setOf(entry.id))
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isSelected) "✓ Deselect Clip" else "☑️ Select Clip",
                                                color = SlateTextMuted,
                                                fontSize = 12.sp
                                            )
                                        },
                                        onClick = {
                                            itemMenuExpanded = false
                                            selectedEntryIds = if (isSelected) selectedEntryIds - entry.id else selectedEntryIds + entry.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
