package com.jms.pssoundboard.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jms.pssoundboard.ui.theme.ChipBlob
import com.jms.pssoundboard.ui.theme.PadPalette
import com.jms.pssoundboard.ui.theme.PadShape
import com.jms.pssoundboard.ui.theme.gummy
import com.jms.pssoundboard.ui.theme.inkOn
import com.jms.pssoundboard.ui.theme.spew
import com.jms.pssoundboard.ui.theme.toComposeColor
import kotlin.math.abs

/** Soft, slightly lopsided capsule used for list rows and cards. */
val CapsuleShape: Shape = RoundedCornerShape(topStart = 30.dp, topEnd = 24.dp, bottomEnd = 28.dp, bottomStart = 22.dp)

/** A stable bright colour for things that have no colour of their own (library items, vault entries). */
fun blobColourFor(key: String): Color = PadPalette[abs(key.hashCode()) % PadPalette.size].toComposeColor()

/** A stable blob shape for the same purpose. */
fun blobShapeFor(key: String): Shape = PadShape.entries[abs(key.hashCode() / 7) % PadShape.entries.size].shape

/** Screen title: a small muted line above a big squashed title, with optional actions on the right. */
@Composable
fun PlayfulHeader(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * A pill-shaped choice. Selected chips are light on dark; [colour] makes a bright gummy chip
 * instead (used for categories), ringed when selected. [trailingIcon] adds a small round icon
 * after the text, such as the edit button on the open deck.
 */
@Composable
fun BlobChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colour: Color? = null,
    trailingIcon: ImageVector? = null,
    onClickLabel: String? = null,
) {
    val background = when {
        colour != null -> Modifier.gummy(colour, ChipBlob)
        selected -> Modifier.clip(ChipBlob).background(MaterialTheme.colorScheme.onSurface)
        else -> Modifier.clip(ChipBlob).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    val ink = when {
        colour != null -> inkOn(colour)
        selected -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Coloured chips always leave room for the ring, so selecting one doesn't shift the row.
    val ring = if (colour != null) {
        Modifier
            .border(2.5.dp, if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent, ChipBlob)
            .padding(3.dp)
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .then(ring)
            .heightIn(min = 48.dp)
            .then(background)
            .clickable(role = Role.Tab, onClickLabel = onClickLabel, onClick = onClick)
            .semantics { if (selected) contentDescription = "$text, selected" }
            .padding(start = 16.dp, end = if (trailingIcon != null) 7.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            color = ink,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected || colour != null) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1
        )
        if (trailingIcon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(ink.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(trailingIcon, contentDescription = null, tint = ink, modifier = Modifier.size(15.dp))
            }
        }
    }
}

/** The yellow "Spew" action: plays a sound into party chat. */
@Composable
fun SpewButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String = "Spew",
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .defaultMinSize(minWidth = 64.dp)
            .gummy(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 24.dp, topEnd = 18.dp, bottomEnd = 22.dp, bottomStart = 16.dp))
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.spew.onBlob, style = MaterialTheme.typography.labelLarge)
    }
}

/** A round-ish icon button with a 44 dp touch target. */
@Composable
fun IconBlobButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colour: Color? = null,
    shape: Shape = ChipBlob,
    size: Dp = 44.dp,
    iconSize: Dp = 18.dp,
    enabled: Boolean = true,
) {
    val fill = when {
        colour != null -> Modifier.gummy(colour, shape)
        else -> Modifier.clip(shape)
    }
    Box(
        modifier = modifier
            .size(size)
            .then(fill)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                colour != null -> inkOn(colour)
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(iconSize)
        )
    }
}

/** List row container: a soft capsule; [selected] lifts it. */
@Composable
fun CapsuleRow(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Rounded search / text field in the playful style. */
@Composable
fun PlayfulField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 22.dp, bottomEnd = 24.dp, bottomStart = 20.dp))
            .background(MaterialTheme.spew.field)
            .padding(start = 18.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder }
            )
        }
        trailing?.invoke()
    }
}

/** Colours for an `OutlinedTextField`: the accent colour on the border, label and cursor while focused. */
@Composable
fun accentFieldColours(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.spew.accent,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.spew.accent,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.spew.accent
)

/** A friendly status card (offline, empty states): blob art on the left, text, optional action. */
@Composable
fun StatusCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit = {},
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CapsuleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        art()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleSmall)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        action?.invoke()
    }
}

/** Light pill button for secondary actions (Retry, Cancel). */
@Composable
fun SoftButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    light: Boolean = true,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 18.dp, bottomEnd = 20.dp, bottomStart = 16.dp))
            .background(if (light) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (light) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * A pad name that shrinks to fit rather than splitting a word across lines (at large font sizes
 * or on narrow pads). It gets at most one line per word, so a long single word can only shrink.
 */
@Composable
fun PadLabel(
    text: String,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    minFontSize: TextUnit = 9.sp,
) {
    val words = text.trim().split(WhitespaceRegex).size
    BasicText(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.merge(style),
        maxLines = minOf(maxLines, words),
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minFontSize, maxFontSize = style.fontSize, stepSize = 0.5.sp)
    )
}

private val WhitespaceRegex = Regex("\\s+")
