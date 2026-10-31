package com.jms.pssoundboard

import com.jms.pssoundboard.ui.dialogs.FeedbackScreens
import com.jms.pssoundboard.ui.dialogs.FeedbackDialog
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jms.pssoundboard.ui.theme.CableAmber
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.Ground
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkMuted
import com.jms.pssoundboard.ui.theme.InkOnBlob
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.SpewYellow
import com.jms.pssoundboard.ui.theme.Surface2
import com.jms.pssoundboard.ui.theme.gummy
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.core.content.ContextCompat
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
            startOverlay()
        } else {
            Toast.makeText(this, "Overlay permission is needed for Mini-Deck", Toast.LENGTH_LONG).show()
        }
    }

    // The overlay works without notifications, but then its Close action is hidden, so start either way.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startOverlay() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake while open
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Light status and navigation bar icons over the dark app
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
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
                val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                if (needsNotificationPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startOverlay()
                }
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }
    }

    private fun startOverlay() {
        FloatingOverlayService.start(this)
        Toast.makeText(this, "Mini-Deck Floating Over Apps!", Toast.LENGTH_SHORT).show()
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
    val playingPads by viewModel.playingPads.collectAsState()
    val previewingId by viewModel.previewingId.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val spewEntries by viewModel.spewEntries.collectAsState()
    val onlineResults by viewModel.onlineSearchResults.collectAsState()
    val isSearchingOnline by viewModel.isSearchingOnline.collectAsState()
    val isLoadingMoreOnline by viewModel.isLoadingMoreOnline.collectAsState()
    val hasMoreOnlineResults by viewModel.hasMoreOnlineResults.collectAsState()
    val totalOnlineCount by viewModel.totalOnlineCount.collectAsState()
    val onlineSearchFailed by viewModel.onlineSearchFailed.collectAsState()
    val voiceFxParams by viewModel.voiceFxParams.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()

    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
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

    LaunchedEffect(Unit) {
        viewModel.undoMessages.collectLatest { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg.text,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) msg.undo()
        }
    }

    Scaffold(
        containerColor = SlateBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!landscape) {
                TopStatusBar(
                    loopbackConnected = loopbackConnected,
                    onLaunchOverlay = onLaunchOverlay,
                    onFeedback = { showFeedback = true }
                )
            }
        },
        bottomBar = {
            if (!landscape) {
                BottomNavBar(
                    currentTab = currentTab,
                    onSelectTab = { currentTab = it }
                )
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        if (landscape) {
            SideNavRail(
                currentTab = currentTab,
                onSelectTab = { currentTab = it },
                loopbackConnected = loopbackConnected,
                onLaunchOverlay = onLaunchOverlay,
                onFeedback = { showFeedback = true }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
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
                        playingPads = playingPads,
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
                        onDeleteEntries = { viewModel.deleteSpewEntries(it) },
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
                        searchFailed = onlineSearchFailed,
                        previewingId = previewingId,
                        onSearchOnline = { viewModel.searchOnline(it) },
                        onRetrySearch = { viewModel.retryOnlineSearch() },
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
                        onToggleTestTone = { viewModel.toggleTestTone() },
                        onSendFeedback = { showFeedback = true }
                    )
                }
            }

            // Busy Overlay Indicator
            androidx.compose.animation.AnimatedVisibility(
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
    }

    if (showFeedback) {
        FeedbackDialog(
            initialScreen = FeedbackScreens.getOrElse(currentTab) { "Somewhere else" },
            configured = viewModel.feedbackConfigured,
            onSend = { category, screen, message, includeDetails ->
                showFeedback = false
                viewModel.sendFeedback(category, screen, message, includeDetails) { report ->
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "PSSoundboard feedback")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }
                    context.startActivity(Intent.createChooser(send, "Share feedback"))
                }
            },
            onDismiss = { showFeedback = false }
        )
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
    onFeedback: () -> Unit,
) {
    val statusColour = if (loopbackConnected) ReadyGreen else CableAmber
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ground)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 9.dp)
                    .clip(ChipBlob)
                    .background(statusColour)
            )
            Text(
                text = if (loopbackConnected) "Loopback cable in: spews go to party"
                else "Speaker mode: plug in the loopback cable to spew into party",
                color = statusColour,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
                maxLines = 2
            )
        }

        FeedbackButton(onFeedback = onFeedback)
        MiniDeckButton(onLaunchOverlay = onLaunchOverlay)
    }
}

@Composable
private fun FeedbackButton(onFeedback: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(ChipBlob)
            .clickable(onClickLabel = "Send feedback to the team", role = Role.Button, onClick = onFeedback)
            .semantics { contentDescription = "Send feedback" },
        contentAlignment = Alignment.Center
    ) {
        Icon(PlayfulIcons.Feedback, contentDescription = null, tint = InkMuted, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BottomNavBar(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Ground)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .selectableGroup()
    ) {
        NavTabs.forEachIndexed { index, (label, icon) ->
            val selected = currentTab == index
            Column(
                modifier = Modifier
                    .weight(1f)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelectTab(index) })
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 56.dp, height = 32.dp)
                        .then(if (selected) Modifier.gummy(SpewYellow, ChipBlob) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) InkOnBlob else InkMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = label,
                    color = if (selected) InkLight else InkMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

private val NavTabs = listOf(
    "Deck" to PlayfulIcons.Deck,
    "Vault" to PlayfulIcons.Vault,
    "Library" to PlayfulIcons.Library,
    "Voice" to PlayfulIcons.Voice,
    "Setup" to PlayfulIcons.Setup,
)

@Composable
private fun MiniDeckButton(onLaunchOverlay: () -> Unit) {
    val overlayRunning by FloatingOverlayService.running.collectAsState()
    val miniDeckShape = PadShape.CLOUD.shape
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 48.dp)
            .then(
                if (overlayRunning) Modifier.gummy(SpewYellow, miniDeckShape)
                else Modifier.clip(miniDeckShape).background(Surface2)
            )
            .clickable(
                onClickLabel = if (overlayRunning) "Close the mini-deck" else "Float the mini-deck over other apps",
                role = Role.Button,
                onClick = onLaunchOverlay
            )
            .semantics {
                contentDescription = if (overlayRunning) "Mini-deck, on" else "Mini-deck, off"
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = PlayfulIcons.MiniDeck,
            contentDescription = null,
            tint = if (overlayRunning) InkOnBlob else InkLight,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Landscape navigation: the bottom bar's tabs stacked down the left, with status and mini-deck below. */
@Composable
private fun SideNavRail(
    currentTab: Int,
    onSelectTab: (Int) -> Unit,
    loopbackConnected: Boolean,
    onLaunchOverlay: () -> Unit,
    onFeedback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .fillMaxHeight()
            .background(Ground)
            .padding(vertical = 6.dp)
            .selectableGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        NavTabs.forEachIndexed { index, (label, icon) ->
            val selected = currentTab == index
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelectTab(index) })
                    .padding(vertical = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 26.dp)
                        .then(if (selected) Modifier.gummy(SpewYellow, ChipBlob) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = if (selected) InkOnBlob else InkMuted, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = label,
                    color = if (selected) InkLight else InkMuted,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        FeedbackButton(onFeedback = onFeedback)
        val statusColour = if (loopbackConnected) ReadyGreen else CableAmber
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 11.dp)
                .clip(ChipBlob)
                .background(statusColour)
                .semantics {
                    contentDescription = if (loopbackConnected) "Loopback cable in" else "Speaker mode, no loopback cable"
                }
        )
        MiniDeckButton(onLaunchOverlay = onLaunchOverlay)
    }
}
