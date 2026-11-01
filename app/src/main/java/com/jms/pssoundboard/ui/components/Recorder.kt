package com.jms.pssoundboard.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jms.pssoundboard.ui.theme.BlobShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.RecordHalo
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.rememberReducedMotion
import com.jms.pssoundboard.ui.theme.spew
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Recording from the phone's mic: the big red record button, the seconds recorded so far and,
 * while recording, the mic level. Asks for the microphone permission before [onStart] when it
 * isn't granted yet, and says how to turn it on if it's refused. [idleHint] shows under the
 * button while not recording.
 */
@Composable
fun RecordPanel(
    isRecording: Boolean,
    elapsedMs: Long,
    micLevel: Float,
    maxSeconds: Float,
    startDescription: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    idleHint: String? = null,
) {
    val context = LocalContext.current
    var micDenied by remember { mutableStateOf(false) }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micDenied = !granted
        if (granted) onStart()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RecordBlob(
            isRecording = isRecording,
            contentDescription = if (isRecording) "Stop recording" else startDescription,
            onClick = {
                if (isRecording) {
                    onStop()
                } else {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) onStart()
                    else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        )

        Text(
            text = String.format(Locale.UK, "%.1f of %.0f seconds", if (isRecording) elapsedMs / 1000f else 0f, maxSeconds),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        AnimatedVisibility(visible = isRecording) {
            LevelMeter(level = micLevel)
        }

        if (idleHint != null && !isRecording) {
            Text(
                text = idleHint,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        if (micDenied && !isRecording) {
            Text(
                text = "Microphone access is off. Allow it when asked, or in Settings › Apps › Spewboard › Permissions.",
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** The big red record button. Wobbles gently while idle unless the user has turned animations off. */
@Composable
private fun RecordBlob(
    isRecording: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val phase = if (reducedMotion || isRecording) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "record_wobble")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "record_wobble_phase"
        ).value
    }
    val shape = remember(phase) {
        val a = (2 * PI * phase).toFloat()
        fun w(offset: Float) = 0.06f * sin(a + offset)
        BlobShape(
            floatArrayOf(.58f + w(0f), .42f - w(0f), .52f + w(2f), .48f - w(2f)),
            floatArrayOf(.50f + w(1f), .56f - w(1f), .44f + w(3f), .50f - w(3f)),
        )
    }
    Box(
        modifier = Modifier
            .size(width = 170.dp, height = 160.dp)
            .border(10.dp, RecordHalo, shape)
            .padding(10.dp)
            .gummy(MaterialTheme.colorScheme.error, shape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (isRecording) PlayfulIcons.Stop else PlayfulIcons.Mic,
                contentDescription = null,
                tint = MaterialTheme.spew.onBlob,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = if (isRecording) "Stop" else "Tap to record",
                color = MaterialTheme.spew.onBlob,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** Live microphone level while recording. */
@Composable
private fun LevelMeter(level: Float) {
    val clamped = level.coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.spew.field)
                .semantics { contentDescription = String.format(Locale.UK, "Microphone level %.0f percent", clamped * 100f) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Text("Mic level", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}
