package com.jms.pssoundboard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jms.pssoundboard.audio.ClipRecorder
import com.jms.pssoundboard.audio.VoiceFxParams
import com.jms.pssoundboard.audio.VoicePreset
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
import com.jms.pssoundboard.ui.theme.toComposeColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SlateSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Teal.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🎙️", fontSize = 22.sp)
                        Column {
                            Text(
                                text = "LIVE VOICE CHANGER",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Transform your mic voice in real time and spew into party chat",
                                color = SlateTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Celebrity & Character Voice Search & Presets
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search celebrity & character voices (Arnold, Vader, Batman…)", color = SlateTextMuted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Teal,
                        unfocusedBorderColor = SlateOutline,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                Text(
                    text = "VOICE PRESETS",
                    color = TealBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPresets) { preset ->
                        val isSelected = preset.id == activePresetId
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Teal else SlateSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color.White else SlateOutline
                            ),
                            modifier = Modifier
                                .clickable {
                                    onParamsChanged(preset.params, preset.id)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(preset.emoji, fontSize = 16.sp)
                                Text(
                                    text = preset.label,
                                    color = if (isSelected) Color.White else SlateText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Voice Recording / Processing Control Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SlateSurfaceHigh,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "TEST & RECORD VOICE FX",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isRecording) {
                            Button(
                                onClick = {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) startRecording()
                                    else micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PanicRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("● RECORD VOICE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    stopRecordFlag?.set(true)
                                    isRecording = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("■ STOP RECORDING", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    if (micDenied && !isRecording) {
                        Text(
                            text = "Microphone access is off. Allow it when asked, or in Settings › Apps › PS Party Soundboard › Permissions.",
                            color = WarningAmber,
                            fontSize = 11.sp
                        )
                    }

                    AnimatedVisibility(visible = isRecording) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Recording with Voice FX...", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(String.format("%.1f s / 10.0 s", recordElapsedMs / 1000f), color = SlateTextMuted, fontSize = 11.sp)
                            }
                            LinearProgressIndicator(
                                progress = { (recordElapsedMs / 10000f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = WarningAmber,
                                trackColor = SlateSurface
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Mic Input Level:", color = SlateTextMuted, fontSize = 10.sp)
                                Text(String.format("%.0f%%", currentMicLevel * 100f), color = SlateTextMuted, fontSize = 10.sp)
                            }
                            LinearProgressIndicator(
                                progress = { currentMicLevel.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = Teal,
                                trackColor = SlateSurface
                            )
                        }
                    }

                    // Result actions when a recording is ready
                    lastProcessedSamples?.let { samples ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "READY (${samples.size * 1000L / ClipRecorder.SAMPLE_RATE} ms processed)",
                            color = ReadyGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onAuditionSamples(samples) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealBright)
                            ) {
                                Text("▶ Play", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { onSpewSamples(samples, clipTitle) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Teal)
                            ) {
                                Text("⚡ Spew Now", fontSize = 12.sp)
                            }
                            Button(
                                onClick = { showSaveDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = SlateSurface)
                            ) {
                                Text("💾 Save", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Fine-Tuning Parameters Sliders
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = SlateSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "FINE-TUNE PARAMETERS",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (activePresetId == VoiceFxParams.CUSTOM_ID) {
                            Text(
                                text = "(Preset: Customized)",
                                color = WarningAmber,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Pitch Shift Slider (-12 to +12)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pitch Shift (Semitones)", color = SlateText, fontSize = 12.sp)
                            Text(String.format("%.1f st", voiceFxParams.pitchSemitones), color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = voiceFxParams.pitchSemitones,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(pitchSemitones = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = -12f..12f,
                            steps = 23, // whole semitones: 25 positions from -12 to +12
                            colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = Teal)
                        )
                    }

                    // Robot Ring Mod Slider (0 to 1)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Robot / Dalek Effect", color = SlateText, fontSize = 12.sp)
                            Text(String.format("%.0f%%", voiceFxParams.robot * 100f), color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = voiceFxParams.robot,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(robot = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = Teal)
                        )
                    }

                    // Robot Frequency Slider (20 to 200 Hz)
                    if (voiceFxParams.robot > 0.01f) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Robot Frequency", color = SlateText, fontSize = 12.sp)
                                Text(String.format("%.0f Hz", voiceFxParams.robotHz), color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = voiceFxParams.robotHz,
                                onValueChange = {
                                    onParamsChanged(voiceFxParams.copy(robotHz = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                                },
                                valueRange = 20f..200f,
                                colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = Teal)
                            )
                        }
                    }

                    // Radio Filter Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pit Radio / Walkie-Talkie Filter", color = SlateText, fontSize = 12.sp)
                            Text("Band-pass filter for radio comms", color = SlateTextMuted, fontSize = 10.sp)
                        }
                        Switch(
                            checked = voiceFxParams.radio,
                            onCheckedChange = {
                                onParamsChanged(voiceFxParams.copy(radio = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Teal)
                        )
                    }

                    // Drive / Distortion Slider (0 to 1)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Drive / Saturation", color = SlateText, fontSize = 12.sp)
                            Text(String.format("%.0f%%", voiceFxParams.drive * 100f), color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = voiceFxParams.drive,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(drive = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = Teal)
                        )
                    }

                    // Echo Mix Slider (0 to 1)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Echo / Reverb Mix", color = SlateText, fontSize = 12.sp)
                            Text(String.format("%.0f%%", voiceFxParams.echo * 100f), color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = voiceFxParams.echo,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(echo = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = Teal)
                        )
                    }

                    // Mic Gain Slider (0.25 to 4.0)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Mic Input Gain", color = SlateText, fontSize = 12.sp)
                            Text(String.format("%.2f×", voiceFxParams.micGain), color = TealBright, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = voiceFxParams.micGain,
                            onValueChange = {
                                onParamsChanged(voiceFxParams.copy(micGain = it, presetId = VoiceFxParams.CUSTOM_ID), VoiceFxParams.CUSTOM_ID)
                            },
                            valueRange = 0.25f..4f,
                            colors = SliderDefaults.colors(thumbColor = TealBright, activeTrackColor = Teal)
                        )
                    }
                }
            }
        }
    }

    // Save Dialog for processed voice clip
    if (showSaveDialog && lastProcessedSamples != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showSaveDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SlateSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, Teal)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SAVE VOICE FX CLIP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    OutlinedTextField(
                        value = clipTitle,
                        onValueChange = { clipTitle = it },
                        label = { Text("Clip Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Teal,
                            unfocusedBorderColor = SlateOutline,
                            focusedLabelColor = Teal,
                            unfocusedLabelColor = SlateTextMuted,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { showSaveDialog = false },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SlateTextMuted)
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                showSaveDialog = false
                                lastProcessedSamples?.let { onSaveProcessedClip(it, clipTitle.ifBlank { "Voice FX Clip" }) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Teal)
                        ) {
                            Text("Save to Vault")
                        }
                    }
                }
            }
        }
    }
}
