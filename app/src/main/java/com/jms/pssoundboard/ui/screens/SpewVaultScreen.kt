package com.jms.pssoundboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.SpewEntry
import com.jms.pssoundboard.data.model.VaultSortOrder
import com.jms.pssoundboard.ui.components.BlobChip
import com.jms.pssoundboard.ui.components.CapsuleRow
import com.jms.pssoundboard.ui.components.IconBlobButton
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.PlayfulHeader
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.components.StatusCard
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.components.blobShapeFor
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PinStar
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn

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
    onShareEntry: (SpewEntry) -> Unit = {},
    onShareMultipleEntries: (List<SpewEntry>) -> Unit = {},
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

    fun selectedList() = sortedEntries.filter { it.id in selectedEntryIds }
    fun copySelected() {
        val list = selectedList()
        val text = list.joinToString("\n") { "${it.title}: ${it.sourceUrl ?: it.localFilePath ?: "Deck clip"}" }
        onCopyToClipboard(plural(list.size, "saved clip", "saved clips"), text)
    }
    fun toggleSelected(id: String) {
        selectedEntryIds = if (id in selectedEntryIds) selectedEntryIds - id else selectedEntryIds + id
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayfulHeader(
            eyebrow = "${plural(totalSpews, "spew", "spews")} from ${plural(spewEntries.size, "clip", "clips")}",
            title = "Spew Vault",
            trailing = {
                if (spewEntries.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button, onClick = onClearAll)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Clear all", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        )

        // Sort chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(VaultSortOrder.values()) { order ->
                BlobChip(
                    text = order.plainLabel(),
                    selected = order == sortOrder,
                    onClick = { sortOrder = order }
                )
            }
        }

        PlayfulField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search the vault",
            leadingIcon = PlayfulIcons.Library,
            trailing = {
                if (searchQuery.isNotBlank()) {
                    IconBlobButton(
                        icon = PlayfulIcons.Close,
                        contentDescription = "Clear search",
                        onClick = { searchQuery = "" }
                    )
                }
            }
        )

        // Batch bar for selected entries
        AnimatedVisibility(
            visible = selectedEntryIds.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 22.dp, bottomEnd = 24.dp, bottomStart = 20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "${selectedEntryIds.size} selected",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                IconBlobButton(
                    icon = PlayfulIcons.Copy,
                    contentDescription = "Copy links for selected clips",
                    onClick = { copySelected() }
                )
                IconBlobButton(
                    icon = PlayfulIcons.Download,
                    contentDescription = "Save selected clips to Downloads",
                    onClick = { onSaveMultipleToDownloads(selectedList()) },
                    colour = MaterialTheme.colorScheme.primary
                )

                var batchMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconBlobButton(
                        icon = PlayfulIcons.More,
                        contentDescription = "More options for selected clips",
                        onClick = { batchMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = batchMenuExpanded,
                        onDismissRequest = { batchMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        VaultMenuItem("Copy all links", PlayfulIcons.Copy) {
                            batchMenuExpanded = false
                            copySelected()
                        }
                        VaultMenuItem("Save selected to Downloads", PlayfulIcons.Download) {
                            batchMenuExpanded = false
                            onSaveMultipleToDownloads(selectedList())
                        }
                        VaultMenuItem("Share selected to chat", PlayfulIcons.Share) {
                            batchMenuExpanded = false
                            onShareMultipleEntries(selectedList())
                        }
                        VaultMenuItem("Delete selected", PlayfulIcons.Trash, colour = MaterialTheme.colorScheme.error) {
                            batchMenuExpanded = false
                            onDeleteEntries(selectedEntryIds)
                            selectedEntryIds = emptySet()
                        }
                        VaultMenuItem("Deselect all", PlayfulIcons.Close, colour = MaterialTheme.colorScheme.onSurfaceVariant) {
                            batchMenuExpanded = false
                            selectedEntryIds = emptySet()
                        }
                    }
                }
                IconBlobButton(
                    icon = PlayfulIcons.Close,
                    contentDescription = "Clear selection",
                    onClick = { selectedEntryIds = emptySet() }
                )
            }
        }

        if (spewEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                StatusCard(
                    title = "Nothing spewed yet",
                    body = "Every sound you play from the Deck or the Library lands here, so you can spew it again.",
                    art = {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .gummy(MaterialTheme.colorScheme.primary, PadShape.entries.first().shape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = PlayfulIcons.Vault,
                                contentDescription = null,
                                tint = inkOn(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                )
            }
        } else if (sortedEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    text = "No clips match “$searchQuery”",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(sortedEntries, key = { it.id }) { entry ->
                    VaultRow(
                        entry = entry,
                        isSelected = entry.id in selectedEntryIds,
                        onToggleSelected = { toggleSelected(entry.id) },
                        onSpewEntry = onSpewEntry,
                        onEquipItem = onEquipItem,
                        onTogglePin = onTogglePin,
                        onDeleteEntries = onDeleteEntries,
                        onSaveToDownloads = onSaveToDownloads,
                        onShareEntry = onShareEntry,
                        onCopyToClipboard = onCopyToClipboard
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultRow(
    entry: SpewEntry,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    onSpewEntry: (SpewEntry) -> Unit,
    onEquipItem: (LibraryItem) -> Unit,
    onTogglePin: (String) -> Unit,
    onDeleteEntries: (Set<String>) -> Unit,
    onSaveToDownloads: (SpewEntry) -> Unit,
    onShareEntry: (SpewEntry) -> Unit,
    onCopyToClipboard: (String, String) -> Unit,
) {
    val blobColour = blobColourFor(entry.id)
    val ink = inkOn(blobColour)
    val equip = {
        onEquipItem(
            LibraryItem(
                id = entry.id,
                title = entry.title,
                category = entry.category,
                url = entry.sourceUrl,
                localUri = entry.localFilePath
            )
        )
    }

    CapsuleRow(selected = isSelected) {
        // Count blob doubles as the selection toggle; shows a check when selected.
        val timesSpewed = "Spewed ${plural(entry.spewCount, "time", "times")}"
        Box(
            modifier = Modifier
                .size(width = 52.dp, height = 48.dp)
                .gummy(blobColour, blobShapeFor(entry.id))
                .clickable(
                    role = Role.Checkbox,
                    onClickLabel = if (isSelected) "Deselect ${entry.title}" else "Select ${entry.title}",
                    onClick = onToggleSelected
                )
                .semantics {
                    contentDescription = if (isSelected) "$timesSpewed, selected" else timesSpewed
                },
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = PlayfulIcons.Check,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text(
                    text = "×${entry.spewCount}",
                    color = ink,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            var isScrollingTitle by remember(entry.id) { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (entry.isPinned) {
                    Icon(
                        imageVector = PlayfulIcons.Star,
                        contentDescription = "Pinned",
                        tint = PinStar,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = entry.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    // Two lines before the ellipsis: the Spew, share and menu buttons leave little width
                    maxLines = if (isScrollingTitle) 1 else 2,
                    overflow = if (isScrollingTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isScrollingTitle = !isScrollingTitle
                        }
                        .then(
                            if (isScrollingTitle) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 150,
                                    repeatDelayMillis = 1200,
                                    velocity = 45.dp
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
            Text(
                text = "${entry.category} · ${entry.formattedLastSpewed().replaceFirstChar { it.lowercase() }}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        SpewButton(
            onClick = { onSpewEntry(entry) },
            contentDescription = "Spew ${entry.title} again"
        )

        IconBlobButton(
            icon = PlayfulIcons.Share,
            contentDescription = "Share ${entry.title} to WhatsApp, Signal or other apps",
            onClick = { onShareEntry(entry) },
            size = 40.dp,
            iconSize = 18.dp
        )

        var itemMenuExpanded by remember { mutableStateOf(false) }
        Box {
            IconBlobButton(
                icon = PlayfulIcons.More,
                contentDescription = "More options for ${entry.title}",
                onClick = { itemMenuExpanded = true }
            )
            DropdownMenu(
                expanded = itemMenuExpanded,
                onDismissRequest = { itemMenuExpanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                VaultMenuItem("Share sound file", PlayfulIcons.Share) {
                    itemMenuExpanded = false
                    onShareEntry(entry)
                }
                VaultMenuItem("Copy sound link", PlayfulIcons.Copy) {
                    itemMenuExpanded = false
                    val link = entry.sourceUrl ?: entry.localFilePath ?: entry.title
                    onCopyToClipboard(entry.title, link)
                }
                VaultMenuItem("Save to device", PlayfulIcons.Download) {
                    itemMenuExpanded = false
                    onSaveToDownloads(entry)
                }
                VaultMenuItem("Spew into chat", PlayfulIcons.Play, colour = MaterialTheme.colorScheme.primary) {
                    itemMenuExpanded = false
                    onSpewEntry(entry)
                }
                VaultMenuItem("Equip to a pad", PlayfulIcons.Plus) {
                    itemMenuExpanded = false
                    equip()
                }
                VaultMenuItem(if (entry.isPinned) "Unpin" else "Pin", PlayfulIcons.Star) {
                    itemMenuExpanded = false
                    onTogglePin(entry.id)
                }
                VaultMenuItem(if (isSelected) "Deselect" else "Select", PlayfulIcons.Check) {
                    itemMenuExpanded = false
                    onToggleSelected()
                }
                VaultMenuItem("Delete from vault", PlayfulIcons.Trash, colour = MaterialTheme.colorScheme.error) {
                    itemMenuExpanded = false
                    onDeleteEntries(setOf(entry.id))
                }
            }
        }
    }
}

@Composable
private fun VaultMenuItem(
    text: String,
    icon: ImageVector,
    colour: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text, color = colour, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(18.dp)) },
        onClick = onClick
    )
}

/** "1 spew", "3 spews". */
private fun plural(count: Int, one: String, many: String): String = "$count ${if (count == 1) one else many}"

/** Plain sort labels for the chips. */
private fun VaultSortOrder.plainLabel(): String = when (this) {
    VaultSortOrder.MOST_SPEWED -> "Most spewed"
    VaultSortOrder.RECENT -> "Recent"
    VaultSortOrder.PINNED_FIRST -> "Pinned"
    VaultSortOrder.ALPHABETICAL -> "A–Z"
}
