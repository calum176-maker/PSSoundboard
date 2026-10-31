package com.jms.pssoundboard.ui.screens

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.jms.pssoundboard.audio.ClipRecorder
import com.jms.pssoundboard.audio.VoiceFxParams
import com.jms.pssoundboard.audio.VoicePreset
import com.jms.pssoundboard.ui.components.CapsuleShape
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.PlayfulHeader
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.theme.BlobShape
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.CableAmber
import com.jms.pssoundboard.ui.theme.FieldBackground
import com.jms.pssoundboard.ui.theme.Ground
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkMuted
import com.jms.pssoundboard.ui.theme.InkOnBlob
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.ReadyGreen
import com.jms.pssoundboard.ui.theme.SpewYellow
import com.jms.pssoundboard.ui.theme.StopRed
import com.jms.pssoundboard.ui.theme.Surface1
import com.jms.pssoundboard.ui.theme.Surface2
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/** Each effect gets its own bright gummy colour (the stored preset colours are too dark for blobs). */
private val PresetColours = mapOf(
    "natural" to Color(0xFFE6DCF2),
    "chipmunk" to Color(0xFFFFA400),
    "deep" to Color(0xFFA46BFF),
    "demon" to Color(0xFFFF6B1F),
    "robot" to Color(0xFF4A86FF),
    "radio" to Color(0xFF3EE06A),
    "alien" to Color(0xFFFF45D0),
    "cave" to Color(0xFF16D4F5),
    "arnold" to Color(0xFF00D9C0),
    "vader" to Color(0xFFFF3D6E),
    "morgan" to Color(0xFFFFE020),
    "batman" to Color(0xFFFF7A6E),
    "ramsay" to Color(0xFFAEF51A),
    "yoda" to Color(0xFF3EE06A),
)

private fun presetColour(preset: VoicePreset): Color = PresetColours[preset.id] ?: blobColourFor(preset.id)

private fun presetShape(preset: VoicePreset): Shape =
    if (preset == VoicePreset.NATURAL) PadShape.ROUND.shape
    else PadShape.entries[preset.ordinal % PadShape.entries.size].shape

private val PitchColour = Color(0xFFFF3D6E)
private val RobotColour = Color(0xFF4A86FF)
private val EchoColour = Color(0xFF3EE06A)
private val DriveColour = Color(0xFFFF6B1F)
private val GainColour = Color(0xFFFFE020)
private val GateColour = Color(0xFF16D4F5)
private val DisabledInk = Color(0xFF8F82A3)
private val RecordHalo = Color(0xFF2A1620)

private const val MAX_RECORD_SECONDS = 10f

@Composable
fun VoiceChangerScreen(
    voiceFxParams: VoiceFxParams,
    activePresetId: String,
    onParamsChanged: (VoiceFxParams, String) -> Unit,
    onRecordAndProcess: suspend (VoiceFxParams, AtomicBoolean, (Long, Float) -> Unit) -> FloatArray,
    onAuditionSamples: (FloatArray) -> Unit,
    onSpewSamples: (FloatArray, String) -> Unit,
    onSaveProcessedClip: (FloatArray, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isRecording by remember { mutableStateOf(false) }
    var recordElapsedMs by remember { mutableLongStateOf(0L) }
    var currentMicLevel by remember { mutableFloatStateOf(0f) }
    var stopRecordFlag by remember { mutableStateOf<AtomicBoolean?>(null) }
    var lastProcessedSamples by remember { mutableStateOf<FloatArray?>(null) }
    var clipTitle by remember { mutableStateOf("Voice FX Recording") }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredPresets = remember(searchQuery) {
        if (searchQuery.isBlank()) VoicePreset.entries
        else VoicePreset.entries.filter { it.label.contains(searchQuery, ignoreCase = true) || it.id.contains(searchQuery, ignoreCase = true) }
    }

    val context = LocalContext.current
    var micDenied by remember { mutableStateOf(false) }

    // Recording failures are reported by the ViewModel, which returns an empty array.
    fun startRecording() {
        val stop = AtomicBoolean(false)
        stopRecordFlag = stop
        isRecording = true
        scope.launch {
            val samples = onRecordAndProcess(voiceFxParams, stop) { ms, lvl ->
                recordElapsedMs = ms
                currentMicLevel = lvl
            }
            isRecording = false
            if (samples.isNotEmpty()) lastProcessedSamples = samples
        }
    }

    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micDenied = !granted
        if (granted) startRecording()
    }

    val effectName = VoicePreset.byId(activePresetId)?.label
        ?: if (activePresetId == VoiceFxParams.CUSTOM_ID) "custom" else "current"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Ground)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PlayfulHeader(eyebrow = "Mangle your voice, then spew it", title = "Voice FX")
        }

        // Effect presets: a grid of gummy blobs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayfulField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search effects",
                    leadingIcon = PlayfulIcons.Library
                )
                if (filteredPresets.isEmpty()) {
                    Text("No effects match \"$searchQuery\".", color = InkMuted, fontSize = 14.sp)
                }
                filteredPresets.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { preset ->
                            PresetBlob(
                                preset = preset,
                                selected = preset.id == activePresetId,
                                onClick = { onParamsChanged(preset.params, preset.id) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }

        // Record blob, status and after-recording actions
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RecordBlob(
                    isRecording = isRecording,
                    contentDescription = if (isRecording) "Stop recording"
                    else "Start recording with the $effectName effect",
                    onClick = {
                        if (!isRecording) {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) startRecording()
                            else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            stopRecordFlag?.set(true)
                            isRecording = false
                        }
                    }
                )

                Text(
                    text = String.format(Locale.UK, "%.1f of %.0f seconds", if (isRecording) recordElapsedMs / 1000f else 0f, MAX_RECORD_SECONDS),
                    color = InkMuted,
                    fontSize = 14.sp
                )

                AnimatedVisibility(visible = isRecording) {
                    LevelMeter(level = currentMicLevel)
                }

                if (!isRecording) {
                    Text(
                        text = "Records up to 10 seconds, then adds the effect. It is not live.",
                        color = InkMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (micDenied && !isRecording) {
                    Text(
                        text = "Microphone access is off. Allow it when asked, or in Settings › Apps › PS Party Soundboard › Permissions.",
                        color = CableAmber,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                val samples = lastProcessedSamples
                if (samples != null && !isRecording) {
                    Text(
                        text = String.format(Locale.UK, "Ready: %.1f seconds with the effect", samples.size.toFloat() / ClipRecorder.SAMPLE_RATE),
                        color = ReadyGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                val ready = samples != null && !isRecording
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionPill(
                        text = "Preview",
                        enabled = ready,
                        onClick = { samples?.let(onAuditionSamples) },
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 18.dp, bottomEnd = 20.dp, bottomStart = 16.dp)
                    )
                    if (ready) {
                        SpewButton(
                            onClick = { samples?.let { onSpewSamples(it, clipTitle) } },
                            contentDescription = "Spew the recording into party chat",
                            label = "Spew it"
                        )
                    } else {
                        ActionPill(
                            text = "Spew it",
                            enabled = false,
                            onClick = {},
                            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 22.dp, bottomEnd = 16.dp, bottomStart = 20.dp)
                        )
                    }
                    ActionPill(
                        text = "Save",
                        enabled = ready,
                        onClick = { showSaveDialog = true },
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 16.dp, bottomEnd = 22.dp, bottomStart = 18.dp)
                    )
                }
            }
        }

        // Fine-tune capsule
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CapsuleShape)
                    .background(Surface1)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fine-tune", color = InkLight, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, fontFamily = BricolageCondensed)
                    if (activePresetId == VoiceFxParams.CUSTOM_ID) {
                        Text(text = "(Preset: Customized)", color = CableAmber, fontSize = 12.sp)
                    }
                }

                // Pitch (-12 to +12 semitones)
                ParamSlider(
                    label = "Pitch",
                    valueText = String.format(Locale.UK, "%+.0f st", voiceFxParams.pitchSemitones),
                    value = voiceFxParams.pitchSemitones,
                    onValueChange = {
                        onParamsChanged(voiceFxParams.copy(pitchSemitones = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                    },
                    valueRange = -12f..12f,
                    steps = 23, // whole semitones: 25 positions from -12 to +12
                    colour = PitchColour
                )

                // Robot ring mod (0 to 1)
                ParamSlider(
                    label = "Robot",
                    valueText = String.format(Locale.UK, "%.0f%%", voiceFxParams.robot * 100f),
                    value = voiceFxParams.robot,
                    onValueChange = {
                        onParamsChanged(voiceFxParams.copy(robot = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                    },
                    valueRange = 0f..1f,
                    colour = RobotColour
                )

                // Robot frequency (20 to 200 Hz), only when the robot effect is on
                if (voiceFxParams.robot > 0.01f) {
                    ParamSlider(
                        label = "Robot Hz",
                        valueText = String.format(Locale.UK, "%.0f Hz", voiceFxParams.robotHz),
                        value = voiceFxParams.robotHz,
                        onValueChange = {
                            onParamsChanged(voiceFxParams.copy(robotHz = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                        },
                        valueRange = 20f..200f,
                        colour = RobotColour,
                        labelWidth = 76.dp
                    )
                }

                // Echo mix (0 to 1)
                ParamSlider(
                    label = "Echo",
                    valueText = String.format(Locale.UK, "%.0f%%", voiceFxParams.echo * 100f),
                    value = voiceFxParams.echo,
                    onValueChange = {
                        onParamsChanged(voiceFxParams.copy(echo = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                    },
                    valueRange = 0f..1f,
                    colour = EchoColour
                )

                // "More" expander
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(role = Role.Button, onClickLabel = if (showMore) "Hide more settings" else "Show more settings") { showMore = !showMore },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(if (showMore) "Less" else "More", color = InkMuted, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Icon(
                        imageVector = PlayfulIcons.ChevronDown,
                        contentDescription = null,
                        tint = InkMuted,
                        modifier = Modifier.size(18.dp).scale(scaleX = 1f, scaleY = if (showMore) -1f else 1f)
                    )
                }

                AnimatedVisibility(visible = showMore) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Radio filter toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Radio filter", color = InkLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Walkie-talkie band-pass", color = InkMuted, fontSize = 12.sp)
                            }
                            Switch(
                                checked = voiceFxParams.radio,
                                onCheckedChange = {
                                    onParamsChanged(voiceFxParams.copy(radio = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = InkOnBlob,
                                    checkedTrackColor = EchoColour,
                                    uncheckedThumbColor = InkMuted,
                                    uncheckedTrackColor = FieldBackground,
                                    uncheckedBorderColor = Surface2
                                )
                            )
                        }

                        // Drive / saturation (0 to 1)
                        ParamSlider(
                            label = "Drive",
                            valueText = String.format(Locale.UK, "%.0f%%", voiceFxParams.drive * 100f),
                            value = voiceFxParams.drive,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(drive = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = 0f..1f,
                            colour = DriveColour,
                            labelWidth = 76.dp
                        )

                        // Mic gain (0.25 to 4.0)
                        ParamSlider(
                            label = "Mic gain",
                            valueText = String.format(Locale.UK, "%.2f×", voiceFxParams.micGain),
                            value = voiceFxParams.micGain,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(micGain = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = 0.25f..4f,
                            colour = GainColour,
                            labelWidth = 76.dp
                        )

                        // Noise gate threshold (-70 to -30 dBFS)
                        ParamSlider(
                            label = "Noise gate",
                            valueText = String.format(Locale.UK, "%.0f dB", voiceFxParams.gateDb),
                            value = voiceFxParams.gateDb,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(gateDb = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = -70f..-30f,
                            colour = GateColour,
                            labelWidth = 76.dp
                        )
                    }
                }
            }
        }
    }

    // Save dialog for the processed voice clip
    if (showSaveDialog && lastProcessedSamples != null) {
        Dialog(onDismissRequest = { showSaveDialog = false }) {
            Column(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(Surface1)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Save to the Vault",
                    color = InkLight,
                    fontFamily = BricolageCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
                Text("Clip name", color = InkMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                PlayfulField(
                    value = clipTitle,
                    onValueChange = { clipTitle = it },
                    placeholder = "Clip name"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SoftButton(text = "Cancel", onClick = { showSaveDialog = false }, light = false)
                    SoftButton(
                        text = "Save to Vault",
                        onClick = {
                            showSaveDialog = false
                            lastProcessedSamples?.let { onSaveProcessedClip(it, clipTitle.ifBlank { "Voice FX Clip" }) }
                        }
                    )
                }
            }
        }
    }
}

/** One effect: a bright blob in its own colour and shape; the selected one is ringed and a touch bigger. */
@Composable
private fun PresetBlob(
    preset: VoicePreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colour = presetColour(preset)
    val shape = presetShape(preset)
    Box(
        modifier = modifier
            .height(62.dp)
            .scale(if (selected) 1.04f else 1f)
            .then(if (selected) Modifier.border(3.dp, colour, shape) else Modifier)
            .padding(if (selected) 5.dp else 3.dp)
            .gummy(colour, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = if (selected) "${preset.label} effect, selected" else "${preset.label} effect" }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = preset.label,
            color = inkOn(colour),
            fontWeight = FontWeight.Bold,
            fontFamily = BricolageCondensed,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
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
            .gummy(StopRed, shape)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (isRecording) PlayfulIcons.Stop else PlayfulIcons.Mic,
                contentDescription = null,
                tint = InkOnBlob,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = if (isRecording) "Stop" else "Tap to record",
                color = InkOnBlob,
                fontFamily = BricolageCondensed,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
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
                .background(FieldBackground)
                .semantics { contentDescription = String.format(Locale.UK, "Microphone level %.0f percent", clamped * 100f) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(clamped)
                    .clip(RoundedCornerShape(5.dp))
                    .background(SpewYellow)
            )
        }
        Text("Mic level", color = InkMuted, fontSize = 12.sp)
    }
}

/** Preview / Save style pill; dims and ignores taps until there is a recording. */
@Composable
private fun ActionPill(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    shape: Shape,
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (enabled) Surface2 else FieldBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) InkLight else DisabledInk, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

/** A colour-coded parameter slider: label, slider, value. */
@Composable
private fun ParamSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    colour: Color,
    steps: Int = 0,
    labelWidth: Dp = 56.dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(label, color = InkLight, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(labelWidth))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = label },
            colors = SliderDefaults.colors(
                thumbColor = colour,
                activeTrackColor = colour,
                inactiveTrackColor = FieldBackground,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Text(
            valueText,
            color = InkLight,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}
