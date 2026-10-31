package com.jms.pssoundboard.service

import android.annotation.SuppressLint
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.jms.pssoundboard.ui.theme.PanicRed
import com.jms.pssoundboard.ui.theme.PSSoundboardTheme
import com.jms.pssoundboard.ui.theme.SlateBackground
import com.jms.pssoundboard.ui.theme.SlateOutline
import com.jms.pssoundboard.ui.theme.SlateSurface
import com.jms.pssoundboard.ui.theme.SlateText
import com.jms.pssoundboard.ui.theme.SlateTextMuted
import com.jms.pssoundboard.ui.theme.Teal
import com.jms.pssoundboard.ui.theme.toComposeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Foreground service that renders a collapsible, draggable floating Mini-Deck
 * over third-party applications (specifically the PlayStation App party chat screen).
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
            .setContentTitle("PS Soundboard Mini-Deck Active")
            .setContentText("Tap to return to app • Floating over PS App")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close Overlay", stopPending)
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
                "PS Soundboard Floating Overlay",
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
                    }
                }
                MotionEvent.ACTION_UP -> if (!isDragging) onTap?.invoke()
                MotionEvent.ACTION_CANCEL -> isDragging = false
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

    override fun onDestroy() {
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

    @Composable
    private fun CollapsedBubble(onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Teal.copy(alpha = 0.92f))
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .overlayDragHandle(onTap = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Text(
                    text = "DECK",
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp
                )
            }
        }
    }

    @Composable
    private fun ExpandedMiniDeck(
        onCollapse: () -> Unit,
        onStopAll: () -> Unit,
        onPadPress: (PadRef, Pad) -> Unit,
        onPadRelease: (PadRef, Pad) -> Unit,
    ) {
        val deckState by repository.state.collectAsState()
        val miniPads = remember(deckState) { deckState.miniDeckPads() }

        Column(
            modifier = Modifier
                .width(280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SlateBackground.copy(alpha = 0.96f))
                .border(1.5.dp, Teal.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                .padding(10.dp)
        ) {
            // Header: Title + Drag Hint + Collapse Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .overlayDragHandle(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Teal)
                    )
                    Text(
                        text = " MINI DECK (PS APP)",
                        color = SlateText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onStopAll,
                        colors = ButtonDefaults.buttonColors(containerColor = PanicRed),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text("STOP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SlateSurface)
                            .border(1.dp, SlateOutline, CircleShape)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { onCollapse() })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = SlateTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "Hold & drag top bar to reposition over PS App",
                color = SlateTextMuted,
                fontSize = 10.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .overlayDragHandle()
                    .padding(top = 2.dp, bottom = 8.dp)
            )

            // 3 x 2 grid of favorite / active pads
            val row1 = miniPads.take(3)
            val row2 = miniPads.drop(3).take(3)

            MiniPadRow(row1, onPadPress, onPadRelease)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(6.dp))
            MiniPadRow(row2, onPadPress, onPadRelease)
        }
    }

    @Composable
    private fun MiniPadRow(
        pads: List<Pair<PadRef, Pad>>,
        onPadPress: (PadRef, Pad) -> Unit,
        onPadRelease: (PadRef, Pad) -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (i in 0 until 3) {
                val pair = pads.getOrNull(i)
                if (pair != null) {
                    val (ref, pad) = pair
                    var isPressed by remember { mutableStateOf(false) }
                    val padColor = pad.colorHex.toComposeColor(Teal)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(height = 54.dp, width = 0.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPressed) padColor else padColor.copy(alpha = 0.45f))
                            .border(
                                1.5.dp,
                                if (isPressed) Color.White else padColor.copy(alpha = 0.8f),
                                RoundedCornerShape(10.dp)
                            )
                            .pointerInput(ref.key + pad.mode) {
                                detectTapGestures(
                                    onPress = {
                                        isPressed = true
                                        onPadPress(ref, pad)
                                        tryAwaitRelease()
                                        isPressed = false
                                        onPadRelease(ref, pad)
                                    }
                                )
                            }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = pad.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp
                            )
                            Text(
                                text = pad.mode.label,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(height = 54.dp, width = 0.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SlateSurface.copy(alpha = 0.4f))
                            .border(1.dp, SlateOutline.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "-",
                            color = SlateTextMuted.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
