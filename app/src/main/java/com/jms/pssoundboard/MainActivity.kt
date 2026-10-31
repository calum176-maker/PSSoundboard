package com.jms.pssoundboard

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.jms.pssoundboard.data.LibraryItem
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.service.FloatingOverlayService
import com.jms.pssoundboard.ui.dialogs.ClipCreatorDialog
import com.jms.pssoundboard.ui.dialogs.EquipTargetDialog
import com.jms.pssoundboard.ui.dialogs.PadEditorDialog
import com.jms.pssoundboard.ui.screens.CloudLibraryScreen
import com.jms.pssoundboard.ui.screens.HardwareGuideScreen
import com.jms.pssoundboard.ui.screens.LiveDeckScreen
import com.jms.pssoundboard.ui.screens.SpewVaultScreen
import com.jms.pssoundboard.ui.screens.VoiceChangerScreen
import com.jms.pssoundboard.ui.theme.PSSoundboardTheme
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.ReadyGreen
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateSurface
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.TealBright
import com.jms.pssoundboard.ui.theme.WarningAmber
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            FloatingOverlayService.start(this)
            Toast.makeText(this, "Floating Mini-Deck Launched!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Overlay permission is needed for Mini-Deck", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake while open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Ensure system status bar icons (clock, battery, notifications) are crisp and dark on the white status bar background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
        }

        setContent {
            PSSoundboardTheme {
                MainScreen(
                    viewModel = viewModel,
                    onLaunchOverlay = { toggleFloatingOverlay() }
                )
            }
        }
    }

    private fun toggleFloatingOverlay() {
        if (FloatingOverlayService.isRunning) {
            FloatingOverlayService.stop(this)
            Toast.makeText(this, "Floating Mini-Deck Closed", Toast.LENGTH_SHORT).show()
        } else {
            if (Settings.canDrawOverlays(this)) {
                FloatingOverlayService.start(this)
                Toast.makeText(this, "Mini-Deck Floating Over Apps!", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }
    }
}

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onLaunchOverlay: () -> Unit,
) {
    val deckState by viewModel.deck.collectAsState()
    val isReady by viewModel.ready.collectAsState()
    val loopbackConnected by viewModel.loopbackConnected.collectAsState()
    val headsetMicDetected by viewModel.headsetMicDetected.collectAsState()
    val outputDeviceName by viewModel.outputDeviceName.collectAsState()
    val gain by viewModel.gain.collectAsState()
    val isTestTonePlaying by viewModel.testTonePlaying.collectAsState()
    val previewingId by viewModel.previewingId.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val spewEntries by viewModel.spewEntries.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val isLoadingMoreOnline by viewModel.isLoadingMoreOnline.collectAsState()
    val hasMoreOnlineResults by viewModel.hasMoreOnlineResults.collectAsState()
    val totalOnlineCount by viewModel.totalOnlineCount.collectAsState()
    val voiceFxParams by viewModel.voiceFxParams.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()

    var currentTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Dialog states
    var editingPadInfo by remember { mutableStateOf<Pair<String, Pad>?>(null) }
    var equippingItem by remember { mutableStateOf<LibraryItem?>(null) }
    var clipCreatorTarget by remember { mutableStateOf<PadRef?>(null) }
    var isClipCreatorOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        containerColor = SlateBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopStatusBar(
                loopbackConnected = loopbackConnected,
                onLaunchOverlay = onLaunchOverlay
            )
        },
        bottomBar = {
            BottomNavBar(
                currentTab = currentTab,
                onSelectTab = { currentTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!isReady) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = Teal)
                        Text(
                            text = "Synthesizing zero-latency audio engine…",
                            color = SlateTextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                when (currentTab) {
                    0 -> LiveDeckScreen(
                        deckState = deckState,
                        gain = gain,
                        onSelectBoard = { viewModel.selectBoard(it) },
                        onRenameBoard = { bId, name -> viewModel.renameBoard(bId, name) },
                        onAddBoard = { name -> viewModel.addBoard(name) },
                        onDeleteBoard = { bId -> viewModel.deleteBoard(bId) },
                        onClearBoard = { bId -> viewModel.clearBoard(bId) },
                        onResetBoard = { bId -> viewModel.resetBoardToDefaults(bId) },
                        onPressPad = { ref, pad -> viewModel.pressPad(ref, pad) },
                        onReleasePad = { ref, pad -> viewModel.releasePad(ref, pad) },
                        onEditPad = { bId, pad -> editingPadInfo = bId to pad },
                        onSetGain = { viewModel.setGain(it) },
                        onStopAll = { viewModel.stopAll() }
                    )
                    1 -> SpewVaultScreen(
                        spewEntries = spewEntries,
                        onSpewEntry = { viewModel.spewFromVault(it) },
                        onEquipItem = { equippingItem = it },
                        onTogglePin = { viewModel.togglePinSpew(it) },
                        onDeleteEntry = { viewModel.deleteSpewEntry(it) },
                        onClearAll = { viewModel.clearSpewVault() },
                        onSaveToDownloads = { viewModel.saveSpewEntryToDownloads(it) },
                        onSaveMultipleToDownloads = { viewModel.saveMultipleSpewEntriesToDownloads(it) },
                        onCopyToClipboard = { label, text -> viewModel.copyToClipboard(label, text) }
                    )
                    2 -> CloudLibraryScreen(
                        onlineResults = onlineResults,
                        isSearchingOnline = isSearchingOnline,
                        isLoadingMore = isLoadingMoreOnline,
                        hasMoreResults = hasMoreOnlineResults,
                        totalOnlineCount = totalOnlineCount,
                        previewingId = previewingId,
                        onSearchOnline = { viewModel.searchOnline(it) },
                        onLoadMore = { viewModel.loadMoreOnline() },
                        onSpewDirectly = { viewModel.spewDirectly(it) },
                        onTogglePreview = { viewModel.togglePreview(it) },
                        onEquipItem = { equippingItem = it },
                        onOpenClipCreator = {
                            clipCreatorTarget = null
                            isClipCreatorOpen = true
                        },
                        onDisplayName = { viewModel.displayName(it) },
                        onSaveToDownloads = { viewModel.saveToDownloads(it) },
                        onSaveMultipleToDownloads = { viewModel.saveMultipleToDownloads(it) },
                        onCopyToClipboard = { label, text -> viewModel.copyToClipboard(label, text) }
                    )
                    3 -> VoiceChangerScreen(
                        voiceFxParams = voiceFxParams,
                        activePresetId = activePresetId,
                        onParamsChanged = { params, pId -> viewModel.updateVoiceFxParams(params, pId) },
                        onRecordAndProcess = { params, stop, onProg -> viewModel.recordAndProcessVoice(params, stop, onProg) },
                        onAuditionSamples = { viewModel.auditionSamples(it) },
                        onSpewSamples = { samples, title -> viewModel.spewProcessedSamples(samples, title) },
                        onSaveProcessedClip = { samples, title -> viewModel.saveProcessedVoiceClip(samples, title) }
                    )
                    4 -> HardwareGuideScreen(
                        loopbackConnected = loopbackConnected,
                        headsetMicDetected = headsetMicDetected,
                        outputDeviceName = outputDeviceName,
                        isTestTonePlaying = isTestTonePlaying,
                        onToggleTestTone = { viewModel.toggleTestTone() }
                    )
                }
            }

            // Busy Overlay Indicator
            AnimatedVisibility(
                visible = busyMessage != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SlateSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Teal),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = Teal, modifier = Modifier.size(24.dp))
                        Text(
                            text = busyMessage ?: "",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Pad Editor Dialog
    editingPadInfo?.let { (boardId, pad) ->
        val board = deckState.board(boardId)
        val padRef = PadRef(boardId, pad.slot)
        val isFav = deckState.favorites.contains(padRef)

        PadEditorDialog(
            boardName = board?.name ?: boardId,
            pad = pad,
            isFavorite = isFav,
            onAudition = { viewModel.auditionFile(it) },
            builtinClipResolver = { viewModel.builtinPath(it) },
            onSave = { updatedPad, favorite ->
                viewModel.savePad(boardId, updatedPad, favorite)
                editingPadInfo = null
            },
            onDismiss = { editingPadInfo = null }
        )
    }

    // Equip To Pad Modal
    equippingItem?.let { item ->
        EquipTargetDialog(
            item = item,
            deckState = deckState,
            onEquip = { target, title ->
                viewModel.equip(item, target, title)
                equippingItem = null
            },
            onDismiss = { equippingItem = null }
        )
    }

    // Clip Creator & Recorder Dialog
    if (isClipCreatorOpen) {
        ClipCreatorDialog(
            deckState = deckState,
            initialTarget = clipCreatorTarget,
            onRecordMic = { stop, onProg -> viewModel.recordClip(stop, onProg) },
            onDecodeFile = { viewModel.decodeForEditing(it) },
            onPreview = { samples, rate, s, e, norm ->
                viewModel.previewClip(samples, rate, s, e, norm)
            },
            onSave = { samples, rate, s, e, norm, title, target ->
                viewModel.saveClip(samples, rate, s, e, norm, title, target) {
                    isClipCreatorOpen = false
                }
            },
            onDismiss = { isClipCreatorOpen = false }
        )
    }
}

@Composable
private fun TopStatusBar(
    loopbackConnected: Boolean,
    onLaunchOverlay: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Phone System Info Bar Background: Pure white, covering the device status bar area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color.White)
        )

        // Obvious Crisp Border separating phone top information bar from the app
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(Color(0xFFB0B0B0))
        )

        // Subtle spacer to bring app down slightly away from camera cutout and info bar
        Spacer(modifier = Modifier.height(4.dp))

        // App Top Bar Header
        Surface(
            color = SlateSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Teal)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PS SOUNDBOARD",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    // Floating Overlay Mini-Deck Launcher Button
                    Button(
                        onClick = onLaunchOverlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (FloatingOverlayService.isRunning) PanicRed else Teal
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp, vertical = 4.dp
                        )
                    ) {
                        Text(
                            text = if (FloatingOverlayService.isRunning) "■ Close Mini-Deck" else "❖ Mini-Deck Overlay",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Live Hardware Status Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (loopbackConnected) ReadyGreen.copy(alpha = 0.20f)
                            else WarningAmber.copy(alpha = 0.20f)
                        )
                        .border(
                            1.dp,
                            if (loopbackConnected) ReadyGreen.copy(alpha = 0.8f)
                            else WarningAmber.copy(alpha = 0.8f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp, horizontal = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (loopbackConnected) ReadyGreen else WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (loopbackConnected)
                                "LOOPBACK PLUG READY (USB/TRRS Active)"
                            else
                                "NO LOOPBACK DETECTED (Speaker Mode)",
                            color = if (loopbackConnected) ReadyGreen else WarningAmber,
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    NavigationBar(
        containerColor = SlateSurface,
        tonalElevation = 8.dp
    ) {
        val tabs = listOf(
            "Live Deck" to "🎮",
            "Spew Vault" to "⚡",
            "Sound Library" to "🌐",
            "Voice FX" to "🎙️",
            "Hardware Guide" to "🔌"
        )

        tabs.forEachIndexed { index, (label, icon) ->
            val isSelected = currentTab == index
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelectTab(index) },
                icon = {
                    Text(
                        text = icon,
                        fontSize = 18.sp
                    )
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TealBright,
                    selectedTextColor = TealBright,
                    indicatorColor = Teal.copy(alpha = 0.35f),
                    unselectedIconColor = SlateTextMuted,
                    unselectedTextColor = SlateTextMuted
                )
            )
        }
    }
}
