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
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.CloudCatalog
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.OnlineSoundService
import com.jms.pssoundboard.data.SoundSource
import com.jms.pssoundboard.data.model.SpewEntry
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

enum class DurationFilter(val label: String, val shortLabel: String) {
    ANY("Any duration", "All lengths"),
    SHORT("Short (< 3s)", "< 3s"),
    MEDIUM("Medium (3–10s)", "3–10s"),
    LONG("Long (> 10s)", "> 10s")
}

enum class SortOrder(val label: String) {
    DEFAULT("Relevance / Trending"),
    SHORTEST_FIRST("Shortest first"),
    LONGEST_FIRST("Longest first"),
    ALPHABETICAL("Alphabetical (A–Z)")
}

data class VaultRecommendation(
    val title: String,
    val query: String,
    val reason: String
)

private val EXPLORE_CATEGORIES = listOf(
    "Memes",
    "Gaming",
    "Movies & TV",
    "Anime",
    "Sound FX",
    "Reactions",
    "Music"
)

private fun itemMatchesCategory(item: LibraryItem, category: String): Boolean {
    val cat = category.lowercase().trim()
    val itemCat = item.category.lowercase().trim()
    val itemTitle = item.title.lowercase().trim()

    if (itemCat.contains(cat) || cat.contains(itemCat)) return true

    return when (cat) {
        "memes" -> itemCat.contains("meme") || itemCat.contains("trending")
        "gaming" -> itemCat.contains("game") || itemCat.contains("gaming") ||
            itemTitle.contains("roblox") || itemTitle.contains("minecraft") || itemTitle.contains("mario") ||
            itemTitle.contains("gta") || itemTitle.contains("csgo") || itemTitle.contains("halo") || itemTitle.contains("zelda")
        "movies & tv" -> itemCat.contains("movie") || itemCat.contains("tv") || itemCat.contains("film") ||
            itemTitle.contains("movie") || itemTitle.contains("godfather") || itemTitle.contains("pulp") ||
            itemTitle.contains("avengers") || itemTitle.contains("star wars")
        "anime" -> itemCat.contains("anime") || itemCat.contains("manga") || itemCat.contains("japan") ||
            itemTitle.contains("naruto") || itemTitle.contains("jojo") || itemTitle.contains("dbz") || itemTitle.contains("goku") || itemTitle.contains("anime")
        "sound fx" -> itemCat.contains("effect") || itemCat.contains("fx") || itemCat.contains("alarm") ||
            itemCat.contains("impact") || itemCat.contains("cartoon") || itemCat.contains("built-in")
        "reactions" -> itemCat.contains("reaction") || itemCat.contains("crowd")
        "music" -> itemCat.contains("music") || itemCat.contains("tune") || itemCat.contains("song")
        else -> itemCat.contains(cat)
    }
}

private fun extractVaultRecommendations(entries: List<SpewEntry>): List<VaultRecommendation> {
    if (entries.isEmpty()) return emptyList()

    val prioritized = entries.sortedWith(
        compareByDescending<SpewEntry> { it.isPinned }
            .thenByDescending { it.spewCount }
            .thenByDescending { it.lastSpewedEpochMs }
    )

    val list = mutableListOf<VaultRecommendation>()

    // Top spewed / pinned sounds
    prioritized.take(5).forEach { entry ->
        val cleanName = entry.title
            .replace(Regex("(?i)\\.(mp3|wav|ogg|m4a)$"), "")
            .replace(Regex("[-_]"), " ")
            .trim()
        if (cleanName.isNotBlank() && list.none { it.query.equals(cleanName, ignoreCase = true) }) {
            list.add(
                VaultRecommendation(
                    title = cleanName,
                    query = cleanName,
                    reason = if (entry.isPinned) "Pinned in vault" else "Spewed ${entry.spewCount}x"
                )
            )
        }
    }

    // Top vault categories
    val topCategories = entries.groupBy { it.category }
        .filterKeys {
            it.isNotBlank() &&
            !it.equals("custom", ignoreCase = true) &&
            !it.equals("imported file", ignoreCase = true) &&
            !it.equals("direct url", ignoreCase = true) &&
            !it.equals("sound", ignoreCase = true)
        }
        .mapValues { it.value.sumOf { e -> e.spewCount } }
        .toList()
        .sortedByDescending { it.second }
        .take(3)

    topCategories.forEach { (cat, count) ->
        if (list.none { it.query.equals(cat, ignoreCase = true) }) {
            list.add(
                VaultRecommendation(
                    title = cat,
                    query = cat,
                    reason = "Favorite vault category ($count spews)"
                )
            )
        }
    }

    return list
}

@Composable
fun CloudLibraryScreen(
    onlineResults: List<LibraryItem>,
    spewEntries: List<SpewEntry> = emptyList(),
    isSearchingOnline: Boolean,
    isLoadingMore: Boolean,
    hasMoreResults: Boolean,
    totalOnlineCount: Int,
    searchFailed: Boolean,
    loadMoreFailed: Boolean,
    previewingId: String?,
    selectedSoundSource: SoundSource = SoundSource.ALL,
    onSelectSoundSource: (SoundSource) -> Unit = {},
    onSearchOnline: (query: String, category: String?) -> Unit,
    onRetrySearch: () -> Unit,
    onLoadMore: () -> Unit,
    onSpewDirectly: (LibraryItem) -> Unit,
    onTogglePreview: (LibraryItem) -> Unit,
    onEquipItem: (LibraryItem) -> Unit,
    onOpenClipCreator: () -> Unit,
    onDisplayName: (Uri) -> String,
    onSaveToDownloads: (LibraryItem) -> Unit,
    onSaveMultipleToDownloads: (List<LibraryItem>) -> Unit,
    onShareItem: (LibraryItem) -> Unit = {},
    onShareMultipleItems: (List<LibraryItem>) -> Unit = {},
    onCopyToClipboard: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedDuration by remember { mutableStateOf(DurationFilter.ANY) }
    var selectedSort by remember { mutableStateOf(SortOrder.DEFAULT) }
    var filterDialogOpen by remember { mutableStateOf(false) }
    var recommendationsDialogOpen by remember { mutableStateOf(false) }
    var activeVaultRecommendation by remember { mutableStateOf<String?>(null) }
    var customUrlDialogOpen by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var addMenuExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val vaultRecommendations = remember(spewEntries) {
        extractVaultRecommendations(spewEntries)
    }

    val activeFilterCount = (if (selectedCategory != null) 1 else 0) +
        (if (selectedDuration != DurationFilter.ANY) 1 else 0) +
        (if (selectedSoundSource != SoundSource.ALL) 1 else 0) +
        (if (selectedSort != SortOrder.DEFAULT) 1 else 0)

    // Debounce search query and category so user input queries the online API with both
    LaunchedEffect(searchQuery, selectedCategory) {
        delay(350)
        onSearchOnline(searchQuery, selectedCategory)
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

    // Combine local curated catalog with live online search results and apply filter parameters
    val displayedItems = remember(
        onlineResults,
        searchQuery,
        selectedCategory,
        selectedDuration,
        selectedSort
    ) {
        val base = if (searchQuery.isBlank() && selectedCategory == null) {
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

        // Apply category filter
        val categoryFiltered = if (selectedCategory != null) {
            base.filter { itemMatchesCategory(it, selectedCategory!!) }
        } else {
            base
        }

        // Apply duration filter
        val durationFiltered = when (selectedDuration) {
            DurationFilter.ANY -> categoryFiltered
            DurationFilter.SHORT -> categoryFiltered.filter { (it.durationMs ?: 0L) in 1..2999L }
            DurationFilter.MEDIUM -> categoryFiltered.filter { (it.durationMs ?: 0L) in 3000L..9999L }
            DurationFilter.LONG -> categoryFiltered.filter { (it.durationMs ?: 0L) >= 10000L }
        }

        // Apply sort
        when (selectedSort) {
            SortOrder.DEFAULT -> durationFiltered
            SortOrder.SHORTEST_FIRST -> durationFiltered.sortedBy { it.durationMs ?: Long.MAX_VALUE }
            SortOrder.LONGEST_FIRST -> durationFiltered.sortedByDescending { it.durationMs ?: 0L }
            SortOrder.ALPHABETICAL -> durationFiltered.sortedBy { it.title.lowercase() }
        }
    }

    // Only a search has a real total. Trending has none, and its list also holds the built-in sounds.
    val knownTotal = totalOnlineCount.takeIf {
        searchQuery.isNotBlank() && !OnlineSoundService.isTrendingQuery(searchQuery) && it >= displayedItems.size
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
                activeVaultRecommendation = null
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
                                .padding(end = 4.dp)
                                .size(18.dp)
                        )
                    }
                    if (searchQuery.isNotBlank() || selectedCategory != null || activeVaultRecommendation != null) {
                        IconBlobButton(
                            icon = PlayfulIcons.Close,
                            contentDescription = "Clear search",
                            onClick = {
                                searchQuery = ""
                                selectedCategory = null
                                activeVaultRecommendation = null
                                onSearchOnline("", null)
                            }
                        )
                    }
                    // Filter button to configure parameters (Source, Duration, Category, Sort)
                    val hasFilters = activeFilterCount > 0
                    IconBlobButton(
                        icon = PlayfulIcons.Setup,
                        contentDescription = "Search filters and parameters ($activeFilterCount active)",
                        colour = if (hasFilters) MaterialTheme.colorScheme.primary else null,
                        size = 38.dp,
                        iconSize = 18.dp,
                        onClick = { filterDialogOpen = true }
                    )
                }
            }
        )

        // Filter & exploration parameters bar (replaces static suggested keywords)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // "✨ Recommended" button (based on Spew Vault)
            item(key = "btn_recommended") {
                val isRecActive = activeVaultRecommendation != null
                Box(
                    modifier = Modifier
                        .border(
                            2.5.dp,
                            if (isRecActive) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ChipBlob
                        )
                        .padding(2.dp)
                ) {
                    BlobChip(
                        text = if (isRecActive) "✨ Vault: $activeVaultRecommendation" else "✨ Recommended",
                        selected = isRecActive,
                        colour = "#FFB800".toComposeColor(),
                        onClick = { recommendationsDialogOpen = true }
                    )
                }
            }

            // "Filters" button with active badge count
            item(key = "btn_filter_parameters") {
                val hasFilters = activeFilterCount > 0
                Box(
                    modifier = Modifier
                        .border(
                            2.5.dp,
                            if (hasFilters) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ChipBlob
                        )
                        .padding(2.dp)
                ) {
                    BlobChip(
                        text = if (hasFilters) "Filters ($activeFilterCount)" else "Filters",
                        selected = hasFilters,
                        colour = MaterialTheme.colorScheme.primary,
                        onClick = { filterDialogOpen = true }
                    )
                }
            }

            // Quick explore categories
            items(EXPLORE_CATEGORIES, key = { it }) { category ->
                val isSelected = selectedCategory.equals(category, ignoreCase = true)
                val colIndex = (category.hashCode().let { if (it < 0) -it else it }) % PadPalette.size
                Box(
                    modifier = Modifier
                        .border(
                            2.5.dp,
                            if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            ChipBlob
                        )
                        .padding(2.dp)
                ) {
                    BlobChip(
                        text = category,
                        selected = isSelected,
                        colour = PadPalette[colIndex].toComposeColor(),
                        onClick = {
                            activeVaultRecommendation = null
                            selectedCategory = if (isSelected) null else category
                        }
                    )
                }
            }

            // Reset filters chip if any are active
            if (activeFilterCount > 0 || activeVaultRecommendation != null) {
                item(key = "btn_reset_filters") {
                    BlobChip(
                        text = "Reset all ✕",
                        selected = false,
                        colour = MaterialTheme.colorScheme.error,
                        onClick = {
                            selectedCategory = null
                            selectedDuration = DurationFilter.ANY
                            selectedSort = SortOrder.DEFAULT
                            activeVaultRecommendation = null
                            onSelectSoundSource(SoundSource.ALL)
                            searchQuery = ""
                            onSearchOnline("", null)
                        }
                    )
                }
            }
        }

        // Section label with sound counter and active source tag if filtered
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = when {
                        activeVaultRecommendation != null -> "Vault picks: \"$activeVaultRecommendation\""
                        selectedCategory != null -> "Category: $selectedCategory"
                        searchQuery.isBlank() || OnlineSoundService.isTrendingQuery(searchQuery) -> "Trending sounds"
                        else -> "Search results"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (selectedSoundSource != SoundSource.ALL) {
                    Text(
                        text = "(${selectedSoundSource.label})",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (selectedDuration != DurationFilter.ANY) {
                    Text(
                        text = "• ${selectedDuration.shortLabel}",
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            val countText = when {
                knownTotal != null -> "${displayedItems.size} of $knownTotal sounds"
                displayedItems.isNotEmpty() -> "${displayedItems.size} sounds"
                else -> null
            }
            val meta = listOfNotNull(countText, if (hasMoreResults) "scroll for 10 more" else null)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
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
                    style = MaterialTheme.typography.labelLarge,
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
                        PlayfulMenuItem("Share selected to chat", PlayfulIcons.Share) {
                            batchMenuExpanded = false
                            onShareMultipleItems(displayedItems.filter { it.id in selectedItemIds })
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
                            style = MaterialTheme.typography.titleSmall,
                            // Two lines before the ellipsis: the Spew, share and menu buttons leave little width
                            maxLines = if (isScrollingTitle) 1 else 2,
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
                            item.source.contains("tuna", ignoreCase = true) || item.source.contains("voicemod", ignoreCase = true) -> "Voicemod Tuna"
                            item.source.contains("myinstants", ignoreCase = true) -> "MyInstants"
                            item.source.contains("built-in", ignoreCase = true) -> "built-in"
                            item.url != null -> item.source.ifBlank { "online" }
                            item.builtinId != null && !item.category.contains("built-in", ignoreCase = true) -> "built-in"
                            else -> null
                        }
                        val durationText = item.durationMs?.let { ms ->
                            val totalSecs = ms / 1000.0
                            if (totalSecs < 60) {
                                String.format(java.util.Locale.US, "%.1fs", totalSecs)
                            } else {
                                val mins = (totalSecs / 60).toInt()
                                val secs = (totalSecs % 60).toInt()
                                String.format(java.util.Locale.US, "%d:%02d", mins, secs)
                            }
                        }
                        Text(
                            text = listOfNotNull(item.category, durationText, source).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
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
                            PlayfulMenuItem("Share sound file", PlayfulIcons.Share) {
                                itemMenuExpanded = false
                                onShareItem(item)
                            }
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
                            text = "Fetching 10 more soundsâ€¦",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else if (hasMoreResults) {
                item(key = "load_more_button") {
                    SoftButton(
                        text = if (loadMoreFailed) "Couldn't load more. Tap to try again"
                               else if (knownTotal != null) "Load 10 more (${displayedItems.size} of $knownTotal)"
                               else "Load 10 more",
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
                        text = "That's all ${displayedItems.size} sounds",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Paste any direct public .mp3, .wav or .ogg link to stream it and equip it to your pads.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Text("Audio link", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    PlayfulField(
                        value = directUrlInput,
                        onValueChange = { directUrlInput = it },
                        placeholder = "https://example.com/sound.mp3",
                        leadingIcon = PlayfulIcons.Link
                    )

                    Text("Display name", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
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

    if (filterDialogOpen) {
        SearchFilterDialog(
            selectedSource = selectedSoundSource,
            onSelectSource = {
                onSelectSoundSource(it)
                onSearchOnline(searchQuery, selectedCategory)
            },
            selectedCategory = selectedCategory,
            onSelectCategory = { cat ->
                selectedCategory = cat
                onSearchOnline(searchQuery, cat)
            },
            selectedDuration = selectedDuration,
            onSelectDuration = { selectedDuration = it },
            selectedSort = selectedSort,
            onSelectSort = { selectedSort = it },
            onResetFilters = {
                selectedCategory = null
                selectedDuration = DurationFilter.ANY
                selectedSort = SortOrder.DEFAULT
                activeVaultRecommendation = null
                onSelectSoundSource(SoundSource.ALL)
                searchQuery = ""
                onSearchOnline("", null)
            },
            onDismiss = { filterDialogOpen = false }
        )
    }

    if (recommendationsDialogOpen) {
        VaultRecommendationsDialog(
            recommendations = vaultRecommendations,
            hasVaultEntries = spewEntries.isNotEmpty(),
            onSelectRecommendation = { query ->
                searchQuery = query
                activeVaultRecommendation = query.ifBlank { "Trending" }
                onSearchOnline(query, selectedCategory)
            },
            onDismiss = { recommendationsDialogOpen = false }
        )
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
                style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun SearchFilterDialog(
    selectedSource: SoundSource,
    onSelectSource: (SoundSource) -> Unit,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    selectedDuration: DurationFilter,
    onSelectDuration: (DurationFilter) -> Unit,
    selectedSort: SortOrder,
    onSelectSort: (SortOrder) -> Unit,
    onResetFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Search Filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Narrow down and explore sounds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconBlobButton(
                        icon = PlayfulIcons.Close,
                        contentDescription = "Close filter dialog",
                        onClick = onDismiss,
                        size = 36.dp,
                        iconSize = 16.dp
                    )
                }

                // 1. Sound Source
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SOUND SOURCE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SoundSource.entries.forEach { src ->
                            val isSelected = selectedSource == src
                            BlobChip(
                                text = src.label,
                                selected = isSelected,
                                colour = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { onSelectSource(src) }
                            )
                        }
                    }
                }

                // 2. Clip Duration
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DURATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DurationFilter.entries.forEach { dur ->
                            val isSelected = selectedDuration == dur
                            BlobChip(
                                text = dur.shortLabel,
                                selected = isSelected,
                                colour = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { onSelectDuration(dur) }
                            )
                        }
                    }
                }

                // 3. Category
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "EXPLORE CATEGORY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            val isAll = selectedCategory == null
                            BlobChip(
                                text = "All",
                                selected = isAll,
                                colour = if (isAll) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { onSelectCategory(null) }
                            )
                        }
                        items(EXPLORE_CATEGORIES) { cat ->
                            val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                            BlobChip(
                                text = cat,
                                selected = isSelected,
                                colour = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { onSelectCategory(cat) }
                            )
                        }
                    }
                }

                // 4. Sort Order
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SORT BY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(SortOrder.entries) { sort ->
                            val isSelected = selectedSort == sort
                            BlobChip(
                                text = sort.label,
                                selected = isSelected,
                                colour = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                onClick = { onSelectSort(sort) }
                            )
                        }
                    }
                }

                // Dialog buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftButton(
                        text = "Reset all",
                        onClick = onResetFilters,
                        light = false,
                        modifier = Modifier.weight(1f)
                    )
                    SpewButton(
                        label = "Done",
                        contentDescription = "Apply filters",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultRecommendationsDialog(
    recommendations: List<VaultRecommendation>,
    hasVaultEntries: Boolean,
    onSelectRecommendation: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "✨ Vault Recommendations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tuned to your Spew Vault history",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconBlobButton(
                        icon = PlayfulIcons.Close,
                        contentDescription = "Close recommendations",
                        onClick = onDismiss,
                        size = 36.dp,
                        iconSize = 16.dp
                    )
                }

                if (!hasVaultEntries || recommendations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Your Spew Vault is quiet!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Spew sounds into chat or equip pads on your soundboards, and Spewboard will learn your favorite meme themes to recommend community sounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        SoftButton(
                            text = "Explore Trending Memes",
                            onClick = {
                                onSelectRecommendation("")
                                onDismiss()
                            },
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recommendations) { rec ->
                            CapsuleRow(
                                selected = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectRecommendation(rec.query)
                                        onDismiss()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .gummy("#FFB800".toComposeColor(), ChipBlob),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = PlayfulIcons.Star,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = rec.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                SoftButton(
                                    text = "Search",
                                    onClick = {
                                        onSelectRecommendation(rec.query)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoftButton(
                            text = "Surprise Me",
                            onClick = {
                                val randomPick = recommendations.random()
                                onSelectRecommendation(randomPick.query)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        SpewButton(
                            label = "Done",
                            contentDescription = "Close recommendations",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

