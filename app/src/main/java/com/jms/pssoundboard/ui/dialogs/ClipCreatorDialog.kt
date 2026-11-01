package com.jms.pssoundboard.ui.dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jms.pssoundboard.audio.AudioDecoder
import com.jms.pssoundboard.audio.ClipRecorder
import com.jms.pssoundboard.audio.WavUtils
import com.jms.pssoundboard.data.model.DeckState
import com.jms.pssoundboard.data.model.PadRef
import com.jms.pssoundboard.ui.components.PadPicker
import com.jms.pssoundboard.ui.components.RecordPanel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import com.jms.pssoundboard.ui.theme.TrimHighlight
import com.jms.pssoundboard.ui.theme.spew

@Composable
fun ClipCreatorDialog(
    deckState: DeckState,
    initialTarget: PadRef?,
    onRecordMic: suspend (stop: AtomicBoolean, onProgress: (Long, Float) -> Unit) -> FloatArray,
    onDecodeFile: suspend (Uri) -> AudioDecoder.Decoded,
    onPreview: (samples: FloatArray, sampleRate: Int, startSec: Float, endSec: Float, normalize: Boolean) -> Unit,
    onSave: (
        samples: FloatArray,
        sampleRate: Int,
        startSec: Float,
        endSec: Float,
        normalize: Boolean,
        title: String,
        target: PadRef,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedMs by remember { mutableLongStateOf(0L) }
    var currentMicLevel by remember { mutableFloatStateOf(0f) }
    var stopRecordFlag by remember { mutableStateOf<AtomicBoolean?>(null) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }

    var samples by remember { mutableStateOf<FloatArray?>(null) }
    var sampleRate by remember { mutableStateOf(ClipRecorder.SAMPLE_RATE) }
    var totalDurationSec by remember { mutableFloatStateOf(0f) }

    var trimRange by remember { mutableStateOf(0f..1f) }
    var normalizeAudio by remember { mutableStateOf(true) }
    var clipTitle by remember { mutableStateOf("Mic Clip") }

    var targetBoardId by remember { mutableStateOf(initialTarget?.boardId ?: deckState.activeBoardId) }
    var targetSlot by remember { mutableStateOf(initialTarget?.slot ?: 0) }
    var isDecoding by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isDecoding = true
                runCatching {
                    val decoded = onDecodeFile(uri)
                    samples = decoded.samples
                    sampleRate = decoded.sampleRate
                    val dur = decoded.durationSec.coerceAtMost(10f)
                    totalDurationSec = dur
                    trimRange = 0f..dur
                    clipTitle = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Imported Clip"
                }
                isDecoding = false
            }
        }
    }

    fun startRecording() {
        val stop = AtomicBoolean(false)
        stopRecordFlag = stop
        isRecording = true
        recordingJob = scope.launch {
            runCatching {
                val recSamples = onRecordMic(stop) { ms, lvl ->
                    recordElapsedMs = ms
                    currentMicLevel = lvl
                }
                if (recSamples.isNotEmpty()) {
                    samples = recSamples
                    sampleRate = ClipRecorder.SAMPLE_RATE
                    val dur = recSamples.size.toFloat() / ClipRecorder.SAMPLE_RATE
                    totalDurationSec = dur
                    trimRange = 0f..dur
                    clipTitle = "Mic Recording"
                }
            }
            isRecording = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.background,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.spew.accent.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CLIP CREATOR & RECORDER",
                            color = MaterialTheme.spew.accent,
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Record phone mic or trim any audio file (max 10s)",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }

                RecordPanel(
                    isRecording = isRecording,
                    elapsedMs = recordElapsedMs,
                    micLevel = currentMicLevel,
                    maxSeconds = 10f,
                    startDescription = "Start recording with the phone's mic",
                    onStart = { startRecording() },
                    onStop = {
                        stopRecordFlag?.set(true)
                        isRecording = false
                    }
                )

                OutlinedButton(
                    onClick = { filePicker.launch("audio/*") },
                    enabled = !isRecording,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.spew.accent),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📂 IMPORT AUDIO", style = MaterialTheme.typography.bodySmall)
                }

                if (isDecoding) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.spew.accent, modifier = Modifier.size(20.dp))
                        Text(
                            text = " Decoding audio track to mono PCM…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Waveform & Trimmer
                val currentSamples = samples
                if (currentSamples != null && totalDurationSec > 0.05f) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "WAVEFORM & TRIM",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                text = String.format(
                                    "Selected: %.2fs - %.2fs (Len: %.2fs)",
                                    trimRange.start, trimRange.endInclusive,
                                    max(0f, trimRange.endInclusive - trimRange.start)
                                ),
                                color = MaterialTheme.colorScheme.secondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        // Waveform visualization
                        WaveformCanvas(
                            samples = currentSamples,
                            totalDurationSec = totalDurationSec,
                            trimStart = trimRange.start,
                            trimEnd = trimRange.endInclusive,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.background)
                        )

                        // Dual-handle Range Slider for trimming
                        RangeSlider(
                            value = trimRange,
                            onValueChange = { trimRange = it },
                            valueRange = 0f..totalDurationSec,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.spew.accent,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Normalize Toggle + Clip Title
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Normalize Peak (-1 dBFS)",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "Boosts loudness cleanly for the loopback cable",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Switch(
                            checked = normalizeAudio,
                            onCheckedChange = { normalizeAudio = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.spew.onAccent,
                                checkedTrackColor = MaterialTheme.spew.accent
                            )
                        )
                    }

                    OutlinedTextField(
                        value = clipTitle,
                        onValueChange = { clipTitle = it },
                        label = { Text("Clip Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.spew.accent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.spew.accent,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    PadPicker(
                        deckState = deckState,
                        selectedBoardId = targetBoardId,
                        selectedSlot = targetSlot,
                        onSelect = { boardId, slot ->
                            targetBoardId = boardId
                            targetSlot = slot
                        }
                    )

                    // Preview and Save Action Buttons
                    val targetBoardName = deckState.board(targetBoardId)?.name ?: "Deck"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onPreview(
                                    currentSamples,
                                    sampleRate,
                                    trimRange.start,
                                    trimRange.endInclusive,
                                    normalizeAudio
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.spew.accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("▶ PREVIEW", style = MaterialTheme.typography.labelSmall)
                        }

                        Button(
                            onClick = {
                                onSave(
                                    currentSamples,
                                    sampleRate,
                                    trimRange.start,
                                    trimRange.endInclusive,
                                    normalizeAudio,
                                    clipTitle,
                                    PadRef(targetBoardId, targetSlot)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.spew.accent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text(
                                text = "SAVE → $targetBoardName #${targetSlot + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WaveformCanvas(
    samples: FloatArray,
    totalDurationSec: Float,
    trimStart: Float,
    trimEnd: Float,
    modifier: Modifier = Modifier,
) {
    val peaks = remember(samples) { WavUtils.waveformPeaks(samples, buckets = 70) }
    // The draw block isn't composable, so read the theme colours here.
    val baselineColour = MaterialTheme.colorScheme.outline
    val selectedBarColour = MaterialTheme.colorScheme.secondary
    val barColour = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val markerColour = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        val startX = (trimStart / totalDurationSec).coerceIn(0f, 1f) * width
        val endX = (trimEnd / totalDurationSec).coerceIn(0f, 1f) * width

        // Highlight selected trim region
        drawRect(
            color = TrimHighlight.copy(alpha = 0.6f),
            topLeft = Offset(startX, 0f),
            size = Size(max(1f, endX - startX), height)
        )

        // Draw center baseline
        drawLine(
            color = baselineColour,
            start = Offset(0f, midY),
            end = Offset(width, midY),
            strokeWidth = 1f
        )

        // Draw waveform bars
        if (peaks.isNotEmpty()) {
            val barWidth = width / peaks.size
            for (i in peaks.indices) {
                val x = i * barWidth + barWidth / 2f
                val h = (peaks[i] * (height / 2f - 4f)).coerceAtLeast(2f)
                val inSelected = x in startX..endX
                val barColor = if (inSelected) selectedBarColour else barColour

                drawLine(
                    color = barColor,
                    start = Offset(x, midY - h),
                    end = Offset(x, midY + h),
                    strokeWidth = (barWidth * 0.7f).coerceAtLeast(1.5f)
                )
            }
        }

        // Draw start / end trim marker lines
        drawLine(
            color = markerColour,
            start = Offset(startX, 0f),
            end = Offset(startX, height),
            strokeWidth = 2f
        )
        drawLine(
            color = markerColour,
            start = Offset(endX, 0f),
            end = Offset(endX, height),
            strokeWidth = 2f
        )
    }
}
