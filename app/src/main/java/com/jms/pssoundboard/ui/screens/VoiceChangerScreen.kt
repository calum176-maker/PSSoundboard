package com.jms.pssoundboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.audio.ClipRecorder
import com.jms.pssoundboard.audio.VoiceFxParams
import com.jms.pssoundboard.audio.VoicePreset
import com.jms.pssoundboard.ui.components.CapsuleShape
import com.jms.pssoundboard.ui.components.PadLabel
import com.jms.pssoundboard.ui.components.PlayfulField
import com.jms.pssoundboard.ui.components.PlayfulHeader
import com.jms.pssoundboard.ui.components.RecordPanel
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.theme.DriveColour
import com.jms.pssoundboard.ui.theme.EchoColour
import com.jms.pssoundboard.ui.theme.GainColour
import com.jms.pssoundboard.ui.theme.GateColour
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PitchColour
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.PresetColours
import com.jms.pssoundboard.ui.theme.RobotColour
import com.jms.pssoundboard.ui.theme.condensed
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.spew
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private fun presetColour(preset: VoicePreset): Color = PresetColours[preset.id] ?: blobColourFor(preset.id)

private fun presetShape(preset: VoicePreset): Shape =
    if (preset == VoicePreset.NATURAL) PadShape.ROUND.shape
    else PadShape.entries[preset.ordinal % PadShape.entries.size].shape

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

    val effectName = VoicePreset.byId(activePresetId)?.label
        ?: if (activePresetId == VoiceFxParams.CUSTOM_ID) "custom" else "current"

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    Text("No effects match \"$searchQuery\".", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
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
                RecordPanel(
                    isRecording = isRecording,
                    elapsedMs = recordElapsedMs,
                    micLevel = currentMicLevel,
                    maxSeconds = MAX_RECORD_SECONDS,
                    startDescription = "Start recording with the $effectName effect",
                    onStart = { startRecording() },
                    onStop = {
                        stopRecordFlag?.set(true)
                        isRecording = false
                    },
                    idleHint = "Records up to 10 seconds, then adds the effect. It is not live."
                )

                val samples = lastProcessedSamples
                if (samples != null && !isRecording) {
                    Text(
                        text = String.format(Locale.UK, "Ready: %.1f seconds with the effect", samples.size.toFloat() / ClipRecorder.SAMPLE_RATE),
                        color = MaterialTheme.spew.ready,
                        style = MaterialTheme.typography.labelMedium
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
                            contentDescription = "Spew the recording into voice chat",
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fine-tune", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    if (activePresetId == VoiceFxParams.CUSTOM_ID) {
                        Text(text = "(Preset: Customized)", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
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
                    Text(if (showMore) "Less" else "More", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    Icon(
                        imageVector = PlayfulIcons.ChevronDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                Text("Radio filter", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
                                Text("Walkie-talkie band-pass", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(
                                checked = voiceFxParams.radio,
                                onCheckedChange = {
                                    onParamsChanged(voiceFxParams.copy(radio = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.spew.onBlob,
                                    checkedTrackColor = EchoColour,
                                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    uncheckedTrackColor = MaterialTheme.spew.field,
                                    uncheckedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Save to the Vault",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text("Clip name", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
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
        PadLabel(
            text = preset.label,
            style = MaterialTheme.typography.labelMedium.condensed(FontWeight.Bold)
                .copy(color = inkOn(colour), textAlign = TextAlign.Center),
            maxLines = 2,
            minFontSize = 9.sp
        )
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
            .background(if (enabled) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.spew.field)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.spew.disabled, style = MaterialTheme.typography.labelMedium)
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
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(labelWidth))
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
                inactiveTrackColor = MaterialTheme.spew.field,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Text(
            valueText,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(56.dp)
        )
    }
}
