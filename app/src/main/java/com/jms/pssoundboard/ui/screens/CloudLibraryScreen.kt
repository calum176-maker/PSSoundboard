package com.jms.pssoundboard.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.CloudCatalog
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.OnlineSoundService
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
import kotlinx.coroutines.delay

@Composable
fun CloudLibraryScreen(
    onlineResults: List<LibraryItem>,
    isSearchingOnline: Boolean,
    isLoadingMore: Boolean,
    hasMoreResults: Boolean,
    totalOnlineCount: Int,
    previewingId: String?,
    onSearchOnline: (String) -> Unit,
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

    LaunchedEffect(shouldLoadMore, hasMoreResults, isLoadingMore, isSearchingOnline) {
        if (shouldLoadMore && hasMoreResults && !isLoadingMore && !isSearchingOnline) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Action Bar: File Import, Custom URL, Mic Recorder
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { filePicker.launch("audio/*") },
                colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceHigh),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("📂 File Import", fontSize = 11.sp, color = SlateText, maxLines = 1)
            }

            Button(
                onClick = { customUrlDialogOpen = true },
                colors = ButtonDefaults.buttonColors(containerColor = SlateSurfaceHigh),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("🌐 Direct URL", fontSize = 11.sp, color = SlateText, maxLines = 1)
            }

            Button(
                onClick = onOpenClipCreator,
                colors = ButtonDefaults.buttonColors(containerColor = Teal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1.1f)
            ) {
                Text("● Mic Recorder", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }

        // Live Search Field for Movie Quotes & Internet Memes
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                selectedPreset = null
            },
            placeholder = { Text("Search internet memes & movie quotes…", color = SlateTextMuted) },
            singleLine = true,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSearchingOnline) {
                        CircularProgressIndicator(
                            color = Teal,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    if (searchQuery.isNotBlank()) {
                        Text(
                            text = "✕",
                            color = SlateTextMuted,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable {
                                    searchQuery = ""
                                    selectedPreset = "Trending Memes"
                                    onSearchOnline("")
                                }
                        )
                    }
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

        // Preset Quick Search Chips (Movies, Memes, Gaming)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(OnlineSoundService.PRESET_QUICK_SEARCHES) { (label, query) ->
                val isSelected = selectedPreset == label || (searchQuery.isNotBlank() && searchQuery.equals(query, ignoreCase = true))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Teal else SlateSurface)
                        .border(
                            1.dp,
                            if (isSelected) Color.White.copy(alpha = 0.8f) else SlateOutline,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            selectedPreset = label
                            searchQuery = query
                            onSearchOnline(query)
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.White else SlateText,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Live Search Results Status Header with Sound Counter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) "POPULAR SOUNDS" else "SEARCH RESULTS",
                color = SlateTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (totalOnlineCount > 0) {
                    Text(
                        text = "${displayedItems.size} of $totalOnlineCount sounds",
                        color = TealBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (displayedItems.isNotEmpty()) {
                    Text(
                        text = "${displayedItems.size} sounds",
                        color = TealBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (hasMoreResults) {
                    Text(
                        text = "• Scroll for +10",
                        color = SlateTextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // Batch Selection Bar with 3-Dot Options
        AnimatedVisibility(
            visible = selectedItemIds.isNotEmpty(),
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
                        text = "✓ ${selectedItemIds.size} Selected",
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
                                val selectedList = displayedItems.filter { it.id in selectedItemIds }
                                val text = selectedList.joinToString("\n") {
                                    "${it.title}: ${it.url ?: it.localUri ?: "Instant Synth"}"
                                }
                                onCopyToClipboard("${selectedList.size} Sound Links", text)
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
                                val selectedList = displayedItems.filter { it.id in selectedItemIds }
                                onSaveMultipleToDownloads(selectedList)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("💾 Save All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // 3-dot Option Menu for Selected Items
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
                                        val selectedList = displayedItems.filter { it.id in selectedItemIds }
                                        val text = selectedList.joinToString("\n") {
                                            "${it.title}: ${it.url ?: it.localUri ?: "Instant Synth"}"
                                        }
                                        onCopyToClipboard("${selectedList.size} Sound Links", text)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💾 Save Selected to Downloads", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        val selectedList = displayedItems.filter { it.id in selectedItemIds }
                                        onSaveMultipleToDownloads(selectedList)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("⚡ Spew First Selected Clip", color = TealBright, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        displayedItems.firstOrNull { it.id in selectedItemIds }?.let { onSpewDirectly(it) }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("✕ Deselect All", color = SlateTextMuted, fontSize = 12.sp) },
                                    onClick = {
                                        batchMenuExpanded = false
                                        selectedItemIds = emptySet()
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
                                .clickable { selectedItemIds = emptySet() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = SlateTextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Search Results & Catalog List with Infinite Scroll (10 at a time)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(displayedItems, key = { it.id }) { item ->
                val isPreviewing = previewingId == item.id
                val isSelected = selectedItemIds.contains(item.id)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) SlateSurfaceHigh else SlateSurface)
                        .border(
                            1.5.dp,
                            when {
                                isSelected -> TealBright
                                isPreviewing -> Teal
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Teal else SlateSurfaceHigh)
                            .border(1.5.dp, if (isSelected) TealBright else SlateOutline, CircleShape)
                            .clickable {
                                selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Text("✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Preview Button (Audition on device)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isPreviewing) Teal else SlateSurfaceHigh)
                            .border(1.dp, if (isPreviewing) Color.White else SlateOutline, CircleShape)
                            .clickable { onTogglePreview(item) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPreviewing) "■" else "▶",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Sound Title and Category Tag
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = item.category,
                                color = SlateTextMuted,
                                fontSize = 10.sp
                            )
                            if (item.url != null) {
                                Text("• Online Audio", color = TealBright, fontSize = 9.sp)
                            } else if (item.builtinId != null) {
                                Text("• Instant Synth", color = ReadyGreen, fontSize = 9.sp)
                            }
                        }
                    }

                    // Action Controls: ⚡ SPEW + ➕ EQUIP + ⋮ 3-Dot Options Menu
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Button(
                            onClick = { onSpewDirectly(item) },
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
                                .border(1.dp, SlateOutline, RoundedCornerShape(8.dp))
                                .clickable { onEquipItem(item) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "➕",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // 3-dot Option Button for this sound clip
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
                                        val link = item.url ?: item.localUri ?: item.title
                                        onCopyToClipboard(item.title, link)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💾 Save to Device (Downloads)", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        itemMenuExpanded = false
                                        onSaveToDownloads(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("⚡ Spew into Party", color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        itemMenuExpanded = false
                                        onSpewDirectly(item)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("➕ Equip to Soundboard", color = Color.White, fontSize = 12.sp) },
                                    onClick = {
                                        itemMenuExpanded = false
                                        onEquipItem(item)
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
                                        selectedItemIds = if (isSelected) selectedItemIds - item.id else selectedItemIds + item.id
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Pagination Footer: Shows loading spinner when fetching +10 sounds or load button
            if (isLoadingMore) {
                item(key = "loading_more_indicator") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurfaceHigh)
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Teal,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Loading 10 more sounds…",
                            color = TealBright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (hasMoreResults) {
                item(key = "load_more_button") {
                    OutlinedButton(
                        onClick = onLoadMore,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SlateSurfaceHigh,
                            contentColor = TealBright
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Teal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "▼ Load 10 More Results (${displayedItems.size} of $totalOnlineCount loaded)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (displayedItems.isNotEmpty() && totalOnlineCount > 0) {
                item(key = "all_loaded_indicator") {
                    Text(
                        text = "✓ All $totalOnlineCount sounds loaded",
                        color = SlateTextMuted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }

            item(key = "catalog_footer_note") {
                Text(
                    text = "Powered by open internet sound directories (10 results per page with infinite scroll). Every spewed sound is permanently saved to your Spew Vault.",
                    color = SlateTextMuted.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 10.dp, bottom = 14.dp)
                )
            }
        }
    }

    // Direct Custom Audio URL Dialog
    if (customUrlDialogOpen) {
        var directUrlInput by remember { mutableStateOf("") }
        var directTitleInput by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { customUrlDialogOpen = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SlateSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🌐 IMPORT DIRECT AUDIO URL",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Enter any direct public .mp3, .wav, or .ogg link to stream and equip to your pads.",
                        color = SlateTextMuted,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = directUrlInput,
                        onValueChange = { directUrlInput = it },
                        label = { Text("Audio Stream URL", color = SlateTextMuted) },
                        placeholder = { Text("https://example.com/sound.mp3", color = SlateTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateText,
                            unfocusedTextColor = SlateText,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = SlateOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = directTitleInput,
                        onValueChange = { directTitleInput = it },
                        label = { Text("Display Name", color = SlateTextMuted) },
                        placeholder = { Text("My Custom Audio Clip", color = SlateTextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SlateText,
                            unfocusedTextColor = SlateText,
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = SlateOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { customUrlDialogOpen = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextMuted),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }

                        Button(
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
                            colors = ButtonDefaults.buttonColors(containerColor = Teal),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Equip", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
