package com.jms.pssoundboard.service

import kotlin.math.hypot
import androidx.compose.foundation.shape.CircleShape
import android.view.HapticFeedbackConstants
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jms.pssoundboard.PSSoundboardApp
import com.jms.pssoundboard.audio.SoundboardEngine
import com.jms.pssoundboard.data.SoundRepository
import com.jms.pssoundboard.data.model.Pad
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.data.model.PlaybackMode
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.Ground
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkMuted
import com.jms.pssoundboard.ui.theme.InkOnBlob
import com.jms.pssoundboard.ui.theme.PSSoundboardTheme
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.SpewYellow
import com.jms.pssoundboard.ui.theme.StopRed
import com.jms.pssoundboard.ui.theme.Surface2
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.padColour
import com.jms.pssoundboard.ui.theme.spewPulse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Where one blob sits in the loose mini-deck cluster (dp, relative to the cluster). */
private data class BlobSpot(val x: Dp, val y: Dp, val width: Dp, val height: Dp)

/** Staggered two-column cluster, 220 dp wide: slight vertical offsets and varied blob sizes. */
private val PadSpots = listOf(
    BlobSpot(0.dp, 58.dp, 104.dp, 88.dp),
    BlobSpot(116.dp, 56.dp, 104.dp, 80.dp),
    BlobSpot(4.dp, 158.dp, 96.dp, 80.dp),
    BlobSpot(112.dp, 148.dp, 104.dp, 84.dp),
    BlobSpot(6.dp, 250.dp, 96.dp, 78.dp),
    BlobSpot(114.dp, 244.dp, 100.dp, 80.dp),
)
private val ClusterWidth = 220.dp
private val ClusterHeight = 396.dp

/** Room around the cluster for the playing pulse rings, which draw outside each blob. */
private val ClusterMargin = 10.dp


/**
 * Foreground service that renders a collapsible, draggable floating Mini-Deck
 * over other apps (the voice chat app: PlayStation App, Discord and so on).
 * Close it from the × on the open deck, by dragging it onto the close target, or from its notification.
 */
class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        const val ACTION_START = "com.jms.pssoundboard.action.START_OVERLAY"
        const val ACTION_STOP = "com.jms.pssoundboard.action.STOP_OVERLAY"
        const val ACTION_TOGGLE = "com.jms.pssoundboard.action.TOGGLE_OVERLAY"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "floating_deck_channel"

        private val _running = MutableStateFlow(false)

        /** Observable so the in-app toggle button updates as soon as the overlay starts or stops. */
        val running: StateFlow<Boolean> = _running.asStateFlow()
        val isRunning: Boolean get() = _running.value

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val isExpanded = mutableStateOf(false)

    private val app: PSSoundboardApp get() = application as PSSoundboardApp
    private val engine: SoundboardEngine get() = app.engine
    private val repository: SoundRepository get() = app.repository

    private lateinit var layoutParams: WindowManager.LayoutParams
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Drag-to-close: a target at the bottom of the screen while the overlay is being dragged
    private var dismissView: View? = null
    private val overDismiss = mutableStateOf(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                isExpanded.value = !isExpanded.value
                updateOverlayWindow()
            }
            else -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForegroundNotification()
                if (overlayView == null) {
                    showOverlay()
                }
                _running.value = true
            }
        }
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Spewboard mini-deck is open")
            .setContentText("Tap to go back to Spewboard")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close mini-deck", stopPending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Spewboard mini-deck",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the floating mini soundboard is active over other apps"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        if (overlayView != null) return

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                PSSoundboardTheme {
                    val expanded = isExpanded.value
                    if (expanded) {
                        ExpandedMiniDeck(
                            onCollapse = {
                                isExpanded.value = false
                                updateOverlayWindow()
                            },
                            onClose = { closeOverlay() },
                            onStopAll = { engine.stopAll() },
                            onPadPress = { ref, pad -> engine.pressPad(ref.key, pad) },
                            onPadRelease = { ref, pad -> engine.releasePad(ref.key, pad) }
                        )
                    } else {
                        CollapsedBubble(
                            onClick = {
                                isExpanded.value = true
                                updateOverlayWindow()
                            }
                        )
                    }
                }
            }
        }

        // Expanding next to an edge would push the deck off screen, so re-fit after any resize.
        composeView.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
            if (r - l != oldR - oldL || b - t != oldB - oldT) {
                composeView.post { moveOverlayTo(layoutParams.x, layoutParams.y) }
            }
        }

        overlayView = composeView
        runCatching {
            windowManager.addView(composeView, layoutParams)
        }
    }

    private fun updateOverlayWindow() {
        val view = overlayView ?: return
        runCatching {
            windowManager.updateViewLayout(view, layoutParams)
        }
    }

    /**
     * Drag handle for the overlay window. A listener on the ComposeView itself never sees touches
     * that Compose consumes (the bubble could not be dragged), so the handle reads the raw
     * MotionEvent instead. Raw screen coordinates stay stable while the window moves under the
     * finger. A press that never passes the drag threshold counts as a tap.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun Modifier.overlayDragHandle(onTap: (() -> Unit)? = null): Modifier =
        pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (isDragging || abs(dx) > 10 || abs(dy) > 10) {
                        isDragging = true
                        moveOverlayTo(initialX + dx, initialY + dy)
                        showDismissTarget()
                        val over = isOverDismissTarget(event.rawX, event.rawY)
                        if (over && !overDismiss.value) {
                            overlayView?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }
                        overDismiss.value = over
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dropOnClose = isDragging && overDismiss.value
                    hideDismissTarget()
                    when {
                        dropOnClose -> closeOverlay()
                        !isDragging -> onTap?.invoke()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    hideDismissTarget()
                }
            }
            true
        }

    /**
     * Moves the window, keeping it on screen and clear of the status and navigation bars, where
     * the system would take the next drag and the overlay could no longer be grabbed.
     */
    private fun moveOverlayTo(x: Int, y: Int) {
        val view = overlayView ?: return
        val left: Int
        val top: Int
        val right: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or
                    android.view.WindowInsets.Type.mandatorySystemGestures()
            )
            left = 0
            top = bars.top
            right = metrics.bounds.width()
            bottom = metrics.bounds.height() - bars.bottom
        } else {
            val dm = resources.displayMetrics
            left = 0; top = 0; right = dm.widthPixels; bottom = dm.heightPixels
        }
        // Layout coordinates can be offset from screen coordinates (e.g. below the status bar).
        val onScreen = IntArray(2).also { view.getLocationOnScreen(it) }
        val offsetX = onScreen[0] - layoutParams.x
        val offsetY = onScreen[1] - layoutParams.y
        val minX = left - offsetX
        val minY = top - offsetY
        layoutParams.x = x.coerceIn(minX, (right - view.width - offsetX).coerceAtLeast(minX))
        layoutParams.y = y.coerceIn(minY, (bottom - view.height - offsetY).coerceAtLeast(minY))
        updateOverlayWindow()
    }

    private fun closeOverlay() {
        hideDismissTarget()
        stopSelf()
    }

    /** Shows the close target at the bottom of the screen (once per drag). */
    private fun showDismissTarget() {
        if (dismissView != null) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (48 * resources.displayMetrics.density).toInt()
        }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent { PSSoundboardTheme { DismissTarget(active = overDismiss.value) } }
        }
        dismissView = view
        runCatching { windowManager.addView(view, params) }
    }

    private fun hideDismissTarget() {
        overDismiss.value = false
        dismissView?.let { runCatching { windowManager.removeView(it) } }
        dismissView = null
    }

    /** True when the finger is close enough to the close target to drop the overlay on it. */
    private fun isOverDismissTarget(rawX: Float, rawY: Float): Boolean {
        val target = dismissView ?: return false
        if (target.width == 0) return false
        val at = IntArray(2).also { target.getLocationOnScreen(it) }
        val centreX = at[0] + target.width / 2f
        val centreY = at[1] + target.width / 2f // the circle sits at the top of the target
        val reach = 72 * resources.displayMetrics.density
        return hypot(rawX - centreX, rawY - centreY) < reach
    }

    override fun onDestroy() {
        hideDismissTarget()
        _running.value = false
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
            overlayView = null
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }


    // -----------------------------------------------------------------------------------------
    // Composables for Floating Overlay
    // -----------------------------------------------------------------------------------------

    /** Collapsed state: one yellow gummy bubble. Drag to move, tap to open. */
    @Composable
    private fun CollapsedBubble(onClick: () -> Unit) {
        val currentOnClick by rememberUpdatedState(onClick)
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 56.dp)
                .gummy(SpewYellow, PadShape.PEBBLE.shape)
                .semantics {
                    role = Role.Button
                    contentDescription = "Mini-deck. Drag to move, tap to open"
                    onClick(label = "Open the mini-deck") {
                        currentOnClick()
                        true
                    }
                }
                .overlayDragHandle(onTap = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(PlayfulIcons.MiniDeck, contentDescription = null, tint = InkOnBlob, modifier = Modifier.size(24.dp))
        }
    }

    /**
     * Expanded state: a loose cluster of gummy pads with a dark grip blob on top (drag to move,
     * tap to tuck away) and a red Stop blob underneath.
     */
    @Composable
    private fun ExpandedMiniDeck(
        onCollapse: () -> Unit,
        onClose: () -> Unit,
        onStopAll: () -> Unit,
        onPadPress: (PadRef, Pad) -> Unit,
        onPadRelease: (PadRef, Pad) -> Unit,
    ) {
        val deckState by repository.state.collectAsState()
        val playingPads by engine.playingPads.collectAsState()
        val miniPads = remember(deckState) { deckState.miniDeckPads() }
        val currentOnCollapse by rememberUpdatedState(onCollapse)
        val currentOnClose by rememberUpdatedState(onClose)

        Box(
            modifier = Modifier
                .padding(ClusterMargin)
                .size(ClusterWidth, ClusterHeight)
        ) {
            // Grip: the drag handle for the expanded deck.
            Box(
                modifier = Modifier
                    .offset(x = 80.dp, y = 0.dp)
                    .size(width = 60.dp, height = 48.dp)
                    .gummy(Ground, ChipBlob)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Mini-deck grip. Drag to move, tap to tuck away"
                        onClick(label = "Tuck away") {
                            currentOnCollapse()
                            true
                        }
                    }
                    .overlayDragHandle(onTap = onCollapse),
                contentAlignment = Alignment.Center
            ) {
                GripDots()
            }

            // Close: puts the mini-deck away without going back to the app
            Box(
                modifier = Modifier
                    .offset(x = 172.dp, y = 2.dp)
                    .size(width = 46.dp, height = 44.dp)
                    .gummy(Ground, PadShape.PEBBLE.shape)
                    .semantics {
                        role = Role.Button
                        contentDescription = "Close the mini-deck"
                        onClick(label = "Close the mini-deck") {
                            currentOnClose()
                            true
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onTap = { currentOnClose() }) },
                contentAlignment = Alignment.Center
            ) {
                Icon(PlayfulIcons.Close, contentDescription = null, tint = InkLight, modifier = Modifier.size(18.dp))
            }

            PadSpots.forEachIndexed { index, spot ->
                val entry = miniPads.getOrNull(index)
                val spotModifier = Modifier
                    .offset(x = spot.x, y = spot.y)
                    .size(spot.width, spot.height)
                if (entry != null) {
                    val (ref, pad) = entry
                    MiniPad(
                        ref = ref,
                        pad = pad,
                        isPlaying = ref.key in playingPads,
                        onPress = onPadPress,
                        onRelease = onPadRelease,
                        modifier = spotModifier
                    )
                } else {
                    EmptyMiniPad(index, spotModifier)
                }
            }

            StopBlob(
                onStopAll = onStopAll,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = 112.dp, height = 56.dp)
            )
        }
    }

    /** Six small dots, the usual sign for something you can grab and drag. */
    @Composable
    private fun GripDots() {
        Canvas(modifier = Modifier.size(width = 22.dp, height = 12.dp)) {
            val radius = 1.8.dp.toPx()
            val xs = listOf(radius, size.width / 2f, size.width - radius)
            val ys = listOf(size.height * 0.22f, size.height * 0.78f)
            for (x in xs) for (y in ys) drawCircle(InkLight, radius, Offset(x, y))
        }
    }

    @Composable
    private fun MiniPad(
        ref: PadRef,
        pad: Pad,
        isPlaying: Boolean,
        onPress: (PadRef, Pad) -> Unit,
        onRelease: (PadRef, Pad) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        var isPressed by remember(ref.key) { mutableStateOf(false) }
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.92f else 1f,
            animationSpec = spring(dampingRatio = 0.45f, stiffness = 700f),
            label = "mini_pad_press_scale"
        )
        val currentOnPress by rememberUpdatedState(onPress)
        val currentOnRelease by rememberUpdatedState(onRelease)

        val shape = PadShape.of(pad.shape, pad.slot).shape
        val colour = padColour(pad.colorHex)
        val ink = inkOn(colour)
        val isHold = pad.mode == PlaybackMode.HOLD_TO_PLAY
        val description = buildString {
            append(pad.title)
            append(", ")
            append(pad.mode.label.lowercase())
            if (isPlaying) append(", spewing")
        }

        Box(
            modifier = modifier
                .scale(pressScale)
                .spewPulse(isPlaying, colour, shape)
                .gummy(colour, shape)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = description
                    onClick(label = "Spew") {
                        currentOnPress(ref, pad)
                        currentOnRelease(ref, pad)
                        true
                    }
                }
                .pointerInput(ref.key + pad.mode) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            currentOnPress(ref, pad)
                            tryAwaitRelease()
                            isPressed = false
                            currentOnRelease(ref, pad)
                        }
                    )
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = pad.title,
                    color = ink,
                    fontFamily = BricolageCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (isHold) {
                    Text(text = "Hold", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    /** A mini-deck slot with no sound yet: a quiet blob that does nothing when tapped. */
    @Composable
    private fun EmptyMiniPad(index: Int, modifier: Modifier = Modifier) {
        val shape = PadShape.of(null, index).shape
        Box(
            modifier = modifier
                .clip(shape)
                .background(Surface2)
                .semantics { contentDescription = "Empty mini-deck slot" },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Empty", color = InkMuted, fontFamily = BricolageCondensed, fontSize = 14.sp)
        }
    }

    @Composable
    private fun StopBlob(onStopAll: () -> Unit, modifier: Modifier = Modifier) {
        val currentOnStopAll by rememberUpdatedState(onStopAll)
        Row(
            modifier = modifier
                .gummy(StopRed, PadShape.PEBBLE.shape)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = "Stop all sounds"
                    onClick(label = "Stop all sounds") {
                        currentOnStopAll()
                        true
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnStopAll() })
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            Icon(PlayfulIcons.Stop, contentDescription = null, tint = InkOnBlob, modifier = Modifier.size(14.dp))
            Text(
                text = "Stop",
                color = InkOnBlob,
                fontFamily = BricolageCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp
            )
        }
    }

    /** The drop target shown while dragging: drop the mini-deck on it to close it. */
    @Composable
    private fun DismissTarget(active: Boolean) {
        val grow by animateFloatAsState(if (active) 1.2f else 1f, spring(), label = "dismissGrow")
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .scale(grow)
                    .gummy(if (active) StopRed else Surface2, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(PlayfulIcons.Close, contentDescription = null, tint = if (active) InkOnBlob else InkLight, modifier = Modifier.size(26.dp))
            }
            Text(
                text = if (active) "Let go to close" else "Drop here to close",
                color = InkLight,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Ground.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}
