package com.jms.pssoundboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.SpewEntry
import com.jms.pssoundboard.data.model.VaultSortOrder
import com.jms.pssoundboard.ui.components.BlobChip
import com.jms.pssoundboard.ui.components.IconBlobButton
import com.jms.pssoundboard.ui.components.MenuEntry
import com.jms.pssoundboard.ui.components.MenuTone
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.PlayfulHeader
import com.jms.pssoundboard.ui.components.SelectionBar
import com.jms.pssoundboard.ui.components.SoundRow
import com.jms.pssoundboard.ui.components.StatusCard
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.components.blobShapeFor
import com.jms.pssoundboard.ui.theme.PadShape
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
                    onClick = { sortOrder = order },
                    modifier = Modifier.height(44.dp)
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

        SelectionBar(
            count = selectedEntryIds.size,
            menuDescription = "More options for selected clips",
            menuEntries = listOf(
                MenuEntry("Copy all links", PlayfulIcons.Copy) { copySelected() },
                MenuEntry("Save selected to Downloads", PlayfulIcons.Download) {
                    onSaveMultipleToDownloads(selectedList())
                },
                MenuEntry("Share selected to chat", PlayfulIcons.Share) {
                    onShareMultipleEntries(selectedList())
                },
                MenuEntry("Delete selected", PlayfulIcons.Trash, MenuTone.Danger) {
                    onDeleteEntries(selectedEntryIds)
                    selectedEntryIds = emptySet()
                },
                MenuEntry("Deselect all", PlayfulIcons.Close) { selectedEntryIds = emptySet() },
            ),
            onClear = { selectedEntryIds = emptySet() }
        ) {
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

    SoundRow(
        key = entry.id,
        title = entry.title,
        subtitle = "${entry.category} · ${entry.formattedLastSpewed().replaceFirstChar { it.lowercase() }}",
        selected = isSelected,
        pinned = entry.isPinned,
        onSpew = { onSpewEntry(entry) },
        spewDescription = "Spew ${entry.title} again",
        onShare = { onShareEntry(entry) },
        shareDescription = "Share ${entry.title} to WhatsApp, Signal or other apps",
        menuEntries = listOf(
            MenuEntry("Share sound file", PlayfulIcons.Share) { onShareEntry(entry) },
            MenuEntry("Copy sound link", PlayfulIcons.Copy) {
                onCopyToClipboard(entry.title, entry.sourceUrl ?: entry.localFilePath ?: entry.title)
            },
            MenuEntry("Save to device", PlayfulIcons.Download) { onSaveToDownloads(entry) },
            MenuEntry("Spew into chat", PlayfulIcons.Play, MenuTone.Spew) { onSpewEntry(entry) },
            MenuEntry("Equip to a pad", PlayfulIcons.Plus) { equip() },
            MenuEntry(if (entry.isPinned) "Unpin" else "Pin", PlayfulIcons.Star) { onTogglePin(entry.id) },
            MenuEntry(if (isSelected) "Deselect" else "Select", PlayfulIcons.Check) { onToggleSelected() },
            MenuEntry("Delete from vault", PlayfulIcons.Trash, MenuTone.Danger) { onDeleteEntries(setOf(entry.id)) },
        )
    ) {
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
    }
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
