package com.jms.pssoundboard.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun HardwareGuideScreen(
    loopbackConnected: Boolean,
    headsetMicDetected: Boolean,
    outputDeviceName: String?,
    isTestTonePlaying: Boolean,
    onToggleTestTone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Test Tone Calibrator Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp,
                if (isTestTonePlaying) PanicRed else Teal.copy(alpha = 0.8f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIVE LOOPBACK SIGNAL CALIBRATION",
                            color = if (isTestTonePlaying) PanicRed else TealBright,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Fires a 1 kHz sine wave hard-panned to Tip (Left Out)",
                            color = SlateTextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isTestTonePlaying) PanicRed else SlateOutline)
                    )
                }

                Button(
                    onClick = onToggleTestTone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTestTonePlaying) PanicRed else Teal
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text(
                        text = if (isTestTonePlaying) "■ STOP 1 kHz TEST TONE" else "▶ START 1 kHz CALIBRATION TONE",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "When active, your avatar's microphone ring in the PlayStation App party should light up steadily, confirming the resistor loopback bridge is feeding audio into the party.",
                    color = SlateTextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Live Hardware Status Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DETECTED AUDIO HARDWARE",
                    color = SlateTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                HardwareStatusRow(
                    label = "USB-C DAC / Headset Plug:",
                    value = if (loopbackConnected) "CONNECTED" else "UNPLUGGED",
                    color = if (loopbackConnected) ReadyGreen else WarningAmber
                )
                HardwareStatusRow(
                    label = "TRRS Headset Mic Line:",
                    value = if (headsetMicDetected) "ACTIVE (Sleeve Input)" else "NOT DETECTED",
                    color = if (headsetMicDetected) ReadyGreen else WarningAmber
                )
                HardwareStatusRow(
                    label = "Active Audio Route:",
                    value = outputDeviceName ?: (if (loopbackConnected) "Wired/USB Audio" else "Phone Speaker"),
                    color = Color.White
                )
                HardwareStatusRow(
                    label = "Panning Isolation:",
                    value = "100% HARD-LEFT (Right = 0.0f)",
                    color = TealBright
                )
            }
        }

        // TRRS Plug Wiring Diagram Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "CTIA TRRS HARDWARE LOOPBACK SCHEMATIC",
                    color = Teal,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )

                Text(
                    text = "Custom 3.5mm plug pinout bridging Left Out to Mic In:",
                    color = SlateTextMuted,
                    fontSize = 11.sp
                )

                // Visual Pinout Canvas
                TrrsPlugSchematic(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SlateBackground)
                )

                // Resistor divider breakdown
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SlateSurfaceHigh)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "RESISTOR DIVIDER ATTENUATION FORMULA",
                        color = TealBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "V_mic = V_left × [ R2 / (R1 + R2) ] = 1.0V × [ 1kΩ / (10kΩ + 1kΩ) ] ≈ 90 mV (-20.8 dB)",
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• Drops phone line output (~1V RMS) to safe electret mic level (~90mV RMS)\n" +
                            "• Protects DAC output from dead shorts\n" +
                            "• 100% zero distortion / clipping in PS App party codec",
                        color = SlateTextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // How To Run PS Party Soundboard Guide
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SlateSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, SlateOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "HOW TO USE WITH PLAYSTATION APP",
                    color = Teal,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )

                val steps = listOf(
                    "1. Plug the USB-C to 3.5mm DAC adapter into your Android phone.",
                    "2. Insert your custom TRRS loopback plug into the 3.5mm jack.",
                    "3. Verify the top pill turns GREEN (\"LOOPBACK PLUG READY\").",
                    "4. Tap \"▶ START 1 kHz CALIBRATION TONE\" to verify the mic indicator.",
                    "5. Open the PlayStation App and join your friends' Voice Party.",
                    "6. Tap \"Floating Mini-Deck\" in the top bar to drop a 3x2 overlay over the PS App and fire clips instantly during gameplay!"
                )

                steps.forEach { step ->
                    Text(
                        text = step,
                        color = SlateText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareStatusRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = SlateTextMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TrrsPlugSchematic(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Draw 3.5mm TRRS plug cylinder on the left side
        val plugLeft = 20f
        val plugTop = 30f
        val plugWidth = 90f
        val sectionHeight = 24f
        val gap = 4f

        // Tip (Left Out) - Golden
        val tipColor = Color(0xFFF59E0B)
        drawRoundRect(
            color = tipColor,
            topLeft = Offset(plugLeft, plugTop),
            size = Size(plugWidth, sectionHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )

        // Ring 1 (Right Out - Isolated) - Dark Slate
        val ring1Color = Color(0xFF64748B)
        drawRect(
            color = ring1Color,
            topLeft = Offset(plugLeft, plugTop + (sectionHeight + gap)),
            size = Size(plugWidth, sectionHeight)
        )

        // Ring 2 (Ground) - Greenish
        val ring2Color = Color(0xFF10B981)
        drawRect(
            color = ring2Color,
            topLeft = Offset(plugLeft, plugTop + (sectionHeight + gap) * 2),
            size = Size(plugWidth, sectionHeight)
        )

        // Sleeve (Mic In) - Pink/Red
        val sleeveColor = Color(0xFFEC4899)
        drawRect(
            color = sleeveColor,
            topLeft = Offset(plugLeft, plugTop + (sectionHeight + gap) * 3),
            size = Size(plugWidth, sectionHeight)
        )

        // Labels for plug contacts
        val textStartX = plugLeft + plugWidth + 14f

        // Trace from Tip (Left Out) -> 10k resistor -> Sleeve (Mic In)
        val tipY = plugTop + sectionHeight / 2f
        val ring1Y = plugTop + (sectionHeight + gap) + sectionHeight / 2f
        val ring2Y = plugTop + (sectionHeight + gap) * 2 + sectionHeight / 2f
        val sleeveY = plugTop + (sectionHeight + gap) * 3 + sectionHeight / 2f

        // Wire 1: Tip -> 10k resistor
        val resistor1X = textStartX + 70f
        drawLine(
            color = tipColor,
            start = Offset(plugLeft + plugWidth, tipY),
            end = Offset(resistor1X, tipY),
            strokeWidth = 3f
        )

        // Resistor 1 box: 10kΩ
        drawRoundRect(
            color = Teal,
            topLeft = Offset(resistor1X, tipY - 10f),
            size = Size(50f, 20f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Trace from Resistor 1 to Junction
        val junctionX = resistor1X + 80f
        drawLine(
            color = Color.White,
            start = Offset(resistor1X + 50f, tipY),
            end = Offset(junctionX, tipY),
            strokeWidth = 3f
        )
        drawLine(
            color = Color.White,
            start = Offset(junctionX, tipY),
            end = Offset(junctionX, sleeveY),
            strokeWidth = 3f
        )

        // Trace to Sleeve (Mic In)
        drawLine(
            color = sleeveColor,
            start = Offset(plugLeft + plugWidth, sleeveY),
            end = Offset(junctionX, sleeveY),
            strokeWidth = 3f
        )

        // Resistor 2: 1kΩ from Sleeve line to Ground (Ring 2)
        val r2X = junctionX + 40f
        drawLine(
            color = Color.White,
            start = Offset(junctionX, sleeveY),
            end = Offset(r2X, sleeveY),
            strokeWidth = 3f
        )
        drawRoundRect(
            color = TealBright,
            topLeft = Offset(r2X, sleeveY - 26f),
            size = Size(20f, 32f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
        )

        // Wire to Ring 2 Ground
        drawLine(
            color = ring2Color,
            start = Offset(plugLeft + plugWidth, ring2Y),
            end = Offset(r2X + 10f, ring2Y),
            strokeWidth = 3f
        )
        drawLine(
            color = ring2Color,
            start = Offset(r2X + 10f, ring2Y),
            end = Offset(r2X + 10f, sleeveY - 26f),
            strokeWidth = 3f
        )

        // Ring 1 (Right Out) - Disconnected (NC)
        drawLine(
            color = ring1Color,
            start = Offset(plugLeft + plugWidth, ring1Y),
            end = Offset(plugLeft + plugWidth + 30f, ring1Y),
            strokeWidth = 2f
        )
        // Draw NC cross
        drawLine(
            color = PanicRed,
            start = Offset(plugLeft + plugWidth + 30f, ring1Y - 6f),
            end = Offset(plugLeft + plugWidth + 42f, ring1Y + 6f),
            strokeWidth = 2f
        )
        drawLine(
            color = PanicRed,
            start = Offset(plugLeft + plugWidth + 30f, ring1Y + 6f),
            end = Offset(plugLeft + plugWidth + 42f, ring1Y - 6f),
            strokeWidth = 2f
        )
    }
}
