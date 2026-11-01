package com.jms.pssoundboard.ui.screens

import com.jms.pssoundboard.BuildConfig
import com.jms.pssoundboard.ui.components.CapsuleShape
import com.jms.pssoundboard.ui.components.SoftButton
import com.jms.pssoundboard.ui.components.StatusCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jms.pssoundboard.ui.components.PlayfulHeader
import com.jms.pssoundboard.ui.theme.BeepCyan
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.PlayfulIcons
import com.jms.pssoundboard.ui.theme.RingMuted
import com.jms.pssoundboard.ui.theme.SleevePink
import com.jms.pssoundboard.ui.theme.TipAmber
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.spew

private val ConnectionShape = RoundedCornerShape(topStart = 30.dp, topEnd = 24.dp, bottomEnd = 28.dp, bottomStart = 22.dp)
private val TestShape = RoundedCornerShape(topStart = 24.dp, topEnd = 30.dp, bottomEnd = 22.dp, bottomStart = 28.dp)
private val CableShape = RoundedCornerShape(topStart = 28.dp, topEnd = 22.dp, bottomEnd = 30.dp, bottomStart = 24.dp)

@Composable
fun HardwareGuideScreen(
    loopbackConnected: Boolean,
    headsetMicDetected: Boolean,
    outputDeviceName: String?,
    isTestTonePlaying: Boolean,
    onToggleTestTone: () -> Unit,
    onSendFeedback: () -> Unit,
    onExportDecks: () -> Unit,
    onImportDecks: () -> Unit,
    onShowWhatsNew: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PlayfulHeader(eyebrow = "Get your spews into voice chat", title = "Setup")

        // Connection check
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ConnectionShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .semantics { contentDescription = "Connection check" },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatusLine(
                label = "Loopback cable",
                value = if (loopbackConnected) "Plugged in" else "Not plugged in",
                colour = if (loopbackConnected) MaterialTheme.spew.ready else MaterialTheme.colorScheme.tertiary
            )
            StatusLine(
                label = "Headset mic line",
                value = if (headsetMicDetected) "Found" else "Not found",
                colour = if (headsetMicDetected) MaterialTheme.spew.ready else MaterialTheme.colorScheme.tertiary
            )
            StatusLine(
                label = "Sound is coming out of",
                value = outputDeviceName ?: if (loopbackConnected) "Wired or USB audio" else "Phone speaker",
                colour = MaterialTheme.colorScheme.onSurfaceVariant,
                valueColour = MaterialTheme.colorScheme.onSurface
            )
            StatusLine(
                label = "Sounds go to the left channel only",
                value = "Ready",
                colour = MaterialTheme.spew.ready
            )
        }

        // Test beep
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TestShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val beepLabel = if (isTestTonePlaying) "Stop beep" else "Test beep"
            val ink = inkOn(BeepCyan)
            Column(
                modifier = Modifier
                    .width(96.dp)
                    .height(80.dp)
                    .gummy(BeepCyan, PadShape.PEBBLE.shape)
                    .clickable(role = Role.Button, onClickLabel = beepLabel, onClick = onToggleTestTone)
                    .clearAndSetSemantics { contentDescription = beepLabel },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isTestTonePlaying) PlayfulIcons.Stop else PlayfulIcons.Play,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = beepLabel,
                    color = ink,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = "Plays a steady beep. If the cable works, your mic lights up in your voice chat app.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }

        // Make the cable
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CableShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Make the cable",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            TrrsPlug()

            NumberedStep(1, TipAmber) {
                Text("Get a 3.5 mm plug with four sections (TRRS).", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
            NumberedStep(2, SleevePink) {
                Text(
                    text = buildAnnotatedString {
                        append("Join the ")
                        withStyle(SpanStyle(color = TipAmber, fontWeight = FontWeight.Bold)) { append("Tip") }
                        append(" to the ")
                        withStyle(SpanStyle(color = SleevePink, fontWeight = FontWeight.Bold)) { append("Sleeve") }
                        append(" through a 10 kΩ resistor.")
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            NumberedStep(3, BeepCyan) {
                Text("Plug it in, join a voice chat on this phone and tap Test beep.", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                HintLine("Beep too loud or crackly? Add a 1 kΩ resistor from the Sleeve to the second Ring.")
                HintLine("No headphone socket? Use a USB-C to 3.5 mm adapter with a built-in DAC.")
                HintLine("While you play, tap the mini-deck button (top right, or bottom of the side bar when the phone is sideways) to keep your pads over your voice chat app. Tap its × or drag it to the bottom of the screen to close it.")
            }
        }

        // Backups: everything in one file, to keep or move to another phone
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CapsuleShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Your decks", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
            Text(
                "Save every deck, pad, sound and your Spew Vault to one file. Keep it safe, or open it on another phone to bring everything back.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftButton(text = "Export decks", onClick = onExportDecks)
                SoftButton(text = "Import decks", onClick = onImportDecks, light = false)
            }
        }

        // Tester feedback: goes to the team
        StatusCard(
            title = "Found a bug or got an idea?",
            body = "Tell the team. The speech bubble at the top does the same from any screen.",
            art = {
                Icon(PlayfulIcons.Feedback, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(28.dp))
            },
            action = { SoftButton(text = "Send feedback", onClick = onSendFeedback) }
        )

        // Version, and this build's changes (GitHub builds only)
        StatusCard(
            title = "Spewboard v${BuildConfig.VERSION_NAME}",
            body = if (onShowWhatsNew != null) "See what changed in this version." else "Test build.",
            action = onShowWhatsNew?.let { show -> { SoftButton(text = "What's new", onClick = show, light = false) } }
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, colour: Color, valueColour: Color = colour) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 13.dp)
                .clip(ChipBlob)
                .background(colour)
        )
        Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColour, style = MaterialTheme.typography.labelLarge)
    }
}

private val PlugGap = 6.dp

/** A 3.5 mm TRRS plug drawn as coloured sections, labelled underneath. */
@Composable
private fun TrrsPlug() {
    val sections: List<Triple<String, Color, Dp>> = listOf(
        Triple("Tip", TipAmber, 54.dp),
        Triple("Ring", RingMuted, 54.dp),
        Triple("Ring", RingMuted, 54.dp),
        Triple("Sleeve (mic)", SleevePink, 70.dp),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "3.5 mm TRRS plug: Tip, Ring, Ring, Sleeve (mic)" },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            sections.forEachIndexed { index, (_, colour, width) ->
                if (index > 0) Box(Modifier.width(PlugGap).fillMaxHeight())
                val shape: Shape =
                    if (index == 0) RoundedCornerShape(topStart = 13.dp, bottomStart = 13.dp) else RoundedCornerShape(0.dp)
                Box(
                    Modifier
                        .width(width)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(colour)
                )
            }
            // Plug body
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            sections.forEachIndexed { index, (name, _, width) ->
                Text(
                    text = name,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                    modifier = if (index == sections.lastIndex) Modifier.weight(1f) else Modifier.width(width + PlugGap)
                )
            }
        }
    }
}

@Composable
private fun NumberedStep(number: Int, colour: Color, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .gummy(colour, PadShape.entries[(number - 1) % PadShape.entries.size].shape),
            contentAlignment = Alignment.Center
        ) {
            Text(number.toString(), color = inkOn(colour), style = MaterialTheme.typography.labelLarge)
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun HintLine(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}
