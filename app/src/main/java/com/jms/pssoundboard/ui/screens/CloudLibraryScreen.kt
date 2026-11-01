package com.jms.pssoundboard.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.CloudCatalog
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.OnlineSoundService
import com.jms.pssoundboard.data.SoundSource
import com.jms.pssoundboard.ui.components.BlobChip
import com.jms.pssoundboard.ui.components.CapsuleRow
import com.jms.pssoundboard.ui.components.CapsuleShape
import com.jms.pssoundboard.ui.components.IconBlobButton
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.PlayfulHeader
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.components.StatusCard
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.components.blobShapeFor
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.PadPalette
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.spew
import com.jms.pssoundboard.ui.theme.spewPulse
import com.jms.pssoundboard.ui.theme.toComposeColor
import kotlinx.coroutines.delay

private val AddBlobColour = "#A46BFF".toComposeColor()
private val MicBlobColour = "#FF3D6E".toComposeColor()

@Composable
fun CloudLibraryScreen(
    onlineResults: List<LibraryItem>,
    isSearchingOnline: Boolean,
    isLoadingMore: Boolean,
    hasMoreResults: Boolean,
    totalOnlineCount: Int,
    searchFailed: Boolean,
    loadMoreFailed: Boolean,
    previewingId: String?,
    selectedSoundSource: SoundSource = SoundSource.ALL,
    onSelectSoundSource: (SoundSource) -> Unit = {},
    onSearchOnline: (String) -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMore: () -> Unit,
    onSpewDirectly: (LibraryItem) -> Unit,
    onTogglePreview: (LibraryItem) -> Unit,
    onEquipItem: (LibraryItem) -> Unit,
    onOpenClipCreator: () -> Unit,
    onDisplayName: (Uri) -> String,
    onSaveToDownloads: (LibraryItem) -> Unit,
    onSaveMultipleToDownloads: (List<LibraryItem>) -> Unit,
    onCopyToClipboard: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf<String?>("Trending Memes") }
    var customUrlDialogOpen by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Debounce search query so user typing queries the online API
    LaunchedEffect(searchQuery) {
        if (selectedPreset == null || searchQuery.isNotBlank()) {
            delay(400)
            onSearchOnline(searchQuery)
        }
    }

    // Infinite scroll detection: automatically fetch next 10 sounds as user scrolls down
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 1
            totalItems > 0 && lastVisible >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore, hasMoreResults, isLoadingMore, isSearchingOnline, loadMoreFailed) {
        if (shouldLoadMore && hasMoreResults && !isLoadingMore && !isSearchingOnline && !loadMoreFailed) {
            onLoadMore()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = onDisplayName(uri)
            val item = LibraryItem(
                id = "local:${System.currentTimeMillis()}",
                title = name.substringBeforeLast('.'),
                category = "Imported File",
                localUri = uri.toString()
            )
            onEquipItem(item)
        }
    }

    // Combine local curated catalog with live online search results (deduplicating)
    val displayedItems = remember(onlineResults, searchQuery) {
        if (searchQuery.isBlank()) {
            val localItems = CloudCatalog.items
            val localIds = localItems.map { it.id }.toSet()
            val localTitles = localItems.map { it.title.lowercase().trim() }.toSet()
            val onlineFiltered = onlineResults.filterNot { online ->
                online.id in localIds || online.title.lowercase().trim() in localTitles
            }
            localItems + onlineFiltered
        } else {
            onlineResults
        }
    }

    fun copySelected() {
        val selectedList = displayedItems.filter { it.id in selectedItemIds }
        val text = selectedList.joinToString("\n") {
            "${it.title}: ${it.url ?: it.localUri ?: "Instant Synth"}"
        }
        onCopyToClipboard("${selectedList.size} Sound Links", text)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header: title plus record and "add your own sound" blobs
        PlayfulHeader(
            eyebrow = "Find it, spew it",
            title = "Library",
            trailing = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconBlobButton(
                        icon = PlayfulIcons.Mic,
                        contentDescription = "Record a sound with the mic",
                        onClick = onOpenClipCreator,
                        colour = MicBlobColour,
                        size = 48.dp,
                        iconSize = 20.dp
                    )
                    Box {
                        IconBlobButton(
                            icon = PlayfulIcons.Plus,
                            contentDescription = "Add your own sound",
                            onClick = { addMenuExpanded = true },
                            colour = AddBlobColour,
                            size = 52.dp,
                            iconSize = 22.dp
                        )
                        DropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            PlayfulMenuItem("Import a file", PlayfulIcons.Folder) {
                                addMenuExpanded = false
                                filePicker.launch("audio/*")
                            }
                            PlayfulMenuItem("Add from a web link", PlayfulIcons.Link) {
                                addMenuExpanded = false
                                customUrlDialogOpen = true
                            }
                            PlayfulMenuItem("Record with the mic", PlayfulIcons.Mic) {
                                addMenuExpanded = false
                                onOpenClipCreator()
                            }
                        }
                    }
                }
            }
        )

        // Live search field for movie quotes and internet memes
        PlayfulField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                selectedPreset = null
            },
            placeholder = "Search sounds",
            leadingIcon = PlayfulIcons.Library,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearchingOnline) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(18.dp)
                        )
                    }
                    if (searchQuery.isNotBlank()) {
                        IconBlobButton(
                            icon = PlayfulIcons.Close,
                            contentDescription = "Clear search",
                            onClick = {
                                searchQuery = ""
                                selectedPreset = "Trending Memes"
                                onSearchOnline("")
                            }
                        )
                    }
                }
            }
        )

        // Sound source filter chips (All sources, 101Soundboards, MyInstants)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SoundSource.entries.forEachIndexed { index, src ->
                val isSelected = selectedSoundSource == src
                BlobChip(
                    text = src.label,
                    selected = isSelected,
                    colour = PadPalette[(index + 3) % PadPalette.size].toComposeColor(),
                    onClick = { onSelectSoundSource(src) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Preset quick search chips, each a gummy colour; the selected one gets a light ring
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(OnlineSoundService.PRESET_QUICK_SEARCHES) { index, (label, query) ->
                val isSelected = selectedPreset == label ||
                    (searchQuery.isNotBlank() && searchQuery.equals(query, ignoreCase = true))
                Box(
                    modifier = Modifier
                        .border(2.5.dp, if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent, ChipBlob)
                        .padding(3.dp)
                ) {
                    BlobChip(
                        text = label,
                        selected = isSelected,
                        colour = PadPalette[index % PadPalette.size].toComposeColor(),
                        onClick = {
                            selectedPreset = label
                            searchQuery = query
                            onSearchOnline(query)
                        }
                    )
                }
            }
        }

        // Section label with sound counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "Popular sounds" else "Search results",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            val countText = when {
                totalOnlineCount > 0 -> "${displayedItems.size} of $totalOnlineCount sounds"
                displayedItems.isNotEmpty() -> "${displayedItems.size} sounds"
                else -> null
            }
            val meta = listOfNotNull(countText, if (hasMoreResults) "scroll for 10 more" else null)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Batch selection bar
        AnimatedVisibility(
            visible = selectedItemIds.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, MaterialTheme.colorScheme.primary, CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CapsuleShape)
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${selectedItemIds.size} selected",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                SoftButton(text = "Copy", onClick = { copySelected() }, light = false)
                SoftButton(
                    text = "Save all",
                    onClick = {
                        onSaveMultipleToDownloads(displayedItems.filter { it.id in selectedItemIds })
                    }
                )

                var batchMenuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconBlobButton(
                        icon = PlayfulIcons.More,
                        contentDescription = "More options for selected sounds",
                        onClick = { batchMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = batchMenuExpanded,
                        onDismissRequest = { batchMenuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        PlayfulMenuItem("Copy all links", PlayfulIcons.Copy) {
                            batchMenuExpanded = false
                            copySelected()
                        }
                        PlayfulMenuItem("Save selected to Downloads", PlayfulIcons.Download) {
                            batchMenuExpanded = false
                            onSaveMultipleToDownloads(displayedItems.filter { it.id in selectedItemIds })
                        }
                        PlayfulMenuItem("Spew the first one", PlayfulIcons.Speaker, highlight = true) {
                            batchMenuExpanded = false
                            displayedItems.firstOrNull { it.id in selectedItemIds }?.let { onSpewDirectly(it) }
                        }
                        PlayfulMenuItem("Deselect all", PlayfulIcons.Close) {
                            batchMenuExpanded = false
                            selectedItemIds = emptySet()
                        }
                    }
                }

                IconBlobButton(
                    icon = PlayfulIcons.Close,
                    contentDescription = "Clear selection",
                    onClick = { selectedItemIds = emptySet() }
                )
            }
        }

        // Results and catalogue list with infinite scroll (10 at a time)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isSearchingOnline && (searchFailed || (searchQuery.isNotBlank() && displayedItems.isEmpty()))) {
                item(key = "search_status") {
                    SearchStatusCard(
                        failed = searchFailed,
                        query = searchQuery,
                        onRetry = onRetrySearch
                    )
                }
            }

            items(displayedItems, key = { it.id }) { item ->
                val isPreviewing = previewingId == item.id
                val isSelected = selectedItemIds.contains(item.id)
                val blobColour = blobColourFor(item.id)
                val blobShape = blobShapeFor(item.id)
                val toggleSelection = {
                    selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                }

                CapsuleRow(
                    selected = isSelected,
                    modifier = if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CapsuleShape) else Modifier
                ) {
                    // While choosing several, each row shows a tick to add or remove it
                    if (selectedItemIds.isNotEmpty()) {
                        IconBlobButton(
                            icon = PlayfulIcons.Check,
                            contentDescription = if (isSelected) "Deselect ${item.title}" else "Select ${item.title}",
                            onClick = toggleSelection,
                            colour = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    }

                    // Preview on this phone: the coloured blob
                    IconBlobButton(
                        icon = if (isPreviewing) PlayfulIcons.Stop else PlayfulIcons.Play,
                        contentDescription = if (isPreviewing) "Stop preview of ${item.title}" else "Preview ${item.title} on this phone",
                        onClick = { onTogglePreview(item) },
                        colour = blobColour,
                        shape = blobShape,
                        size = 48.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.spewPulse(isPreviewing, blobColour, blobShape)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        var isScrollingTitle by remember(item.id) { mutableStateOf(false) }

                        Text(
                            text = item.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = if (isScrollingTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
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
                        val source = when {
                            item.source.contains("101", ignoreCase = true) -> "101Soundboards"
                            item.source.contains("myinstants", ignoreCase = true) -> "MyInstants"
                            item.source.contains("built-in", ignoreCase = true) -> "built-in"
                            item.url != null -> "online"
                            item.builtinId != null && !item.category.contains("built-in", ignoreCase = true) -> "built-in"
                            else -> null
                        }
                        Text(
                            text = listOfNotNull(item.category, source).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    SpewButton(
                        onClick = { onSpewDirectly(item) },
                        contentDescription = "Spew ${item.title} into voice chat"
                    )

                    var itemMenuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconBlobButton(
                            icon = PlayfulIcons.More,
                            contentDescription = "More options for ${item.title}",
                            onClick = { itemMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = itemMenuExpanded,
                            onDismissRequest = { itemMenuExpanded = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            PlayfulMenuItem("Spew into chat", PlayfulIcons.Speaker, highlight = true) {
                                itemMenuExpanded = false
                                onSpewDirectly(item)
                            }
                            PlayfulMenuItem("Equip to a pad", PlayfulIcons.Plus) {
                                itemMenuExpanded = false
                                onEquipItem(item)
                            }
                            PlayfulMenuItem("Copy sound link", PlayfulIcons.Copy) {
                                itemMenuExpanded = false
                                val link = item.url ?: item.localUri ?: item.title
                                onCopyToClipboard(item.title, link)
                            }
                            PlayfulMenuItem("Save to Downloads", PlayfulIcons.Download) {
                                itemMenuExpanded = false
                                onSaveToDownloads(item)
                            }
                            PlayfulMenuItem(
                                if (isSelected) "Deselect" else "Select",
                                if (isSelected) PlayfulIcons.Close else PlayfulIcons.Check
                            ) {
                                itemMenuExpanded = false
                                toggleSelection()
                            }
                        }
                    }
                }
            }

            // Pagination footer: spinner while fetching, otherwise a load-more button
            if (isLoadingMore) {
                item(key = "loading_more_indicator") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, CapsuleShape)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Fetching 10 more sounds…",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (hasMoreResults) {
                item(key = "load_more_button") {
                    SoftButton(
                        text = if (loadMoreFailed) "Couldn't load more. Tap to try again"
                               else "Load 10 more (${displayedItems.size} of $totalOnlineCount)",
                        onClick = onLoadMore,
                        light = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            } else if (displayedItems.isNotEmpty() && totalOnlineCount > 0) {
                item(key = "all_loaded_indicator") {
                    Text(
                        text = "That's all $totalOnlineCount sounds",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }

            item(key = "catalog_footer_note") {
                Text(
                    text = "Powered by open internet sound directories, 10 results at a time. Every sound you spew is saved to your Spew Vault.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 14.dp)
                )
            }
        }
    }

    // Direct custom audio URL dialog
    if (customUrlDialogOpen) {
        var directUrlInput by remember { mutableStateOf("") }
        var directTitleInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { customUrlDialogOpen = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Add from a web link",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = BricolageCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp
                    )
                    Text(
                        text = "Paste any direct public .mp3, .wav or .ogg link to stream it and equip it to your pads.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Text("Audio link", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    PlayfulField(
                        value = directUrlInput,
                        onValueChange = { directUrlInput = it },
                        placeholder = "https://example.com/sound.mp3",
                        leadingIcon = PlayfulIcons.Link
                    )

                    Text("Display name", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    PlayfulField(
                        value = directTitleInput,
                        onValueChange = { directTitleInput = it },
                        placeholder = "My custom sound"
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoftButton(
                            text = "Cancel",
                            onClick = { customUrlDialogOpen = false },
                            light = false,
                            modifier = Modifier.weight(1f)
                        )
                        SpewButton(
                            label = "Equip",
                            contentDescription = "Equip this link to a pad",
                            onClick = {
                                if (directUrlInput.isNotBlank()) {
                                    val title = directTitleInput.ifBlank {
                                        directUrlInput.substringAfterLast('/').substringBefore('?')
                                    }
                                    val item = LibraryItem(
                                        id = "url:${directUrlInput.hashCode()}",
                                        title = title,
                                        category = "Direct URL",
                                        url = directUrlInput.trim()
                                    )
                                    customUrlDialogOpen = false
                                    onEquipItem(item)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** A dropdown entry with a playful icon; [highlight] marks the Spew action in yellow. */
@Composable
private fun PlayfulMenuItem(
    text: String,
    icon: ImageVector,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            )
        },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        },
        onClick = onClick,
        modifier = Modifier.height(48.dp)
    )
}

/** Shown above the list when the online search failed, or a search found nothing. */
@Composable
private fun SearchStatusCard(failed: Boolean, query: String, onRetry: () -> Unit) {
    StatusCard(
        title = if (failed) "The internet's having a nap" else "No sounds found for \"$query\"",
        body = if (failed) "Can't reach the online library. Built-in sounds still spew fine."
        else "Try a shorter or different search.",
        art = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .gummy(if (failed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainer, ChipBlob),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (failed) PlayfulIcons.Link else PlayfulIcons.Library,
                    contentDescription = null,
                    tint = if (failed) MaterialTheme.spew.onBlob else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        action = if (failed) {
            { SoftButton(text = "Retry", onClick = onRetry) }
        } else null
    )
}
