package com.jms.pssoundboard.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jms.pssoundboard.ui.theme.PinStar
import com.jms.pssoundboard.ui.theme.PlayfulIcons

// The pieces shared by the Library and Spew Vault lists: the sound row, the bar shown while
// several sounds are selected, and the menus both of them open.

/** How a menu entry reads: plain, the yellow Spew action, or red for deleting. */
enum class MenuTone { Plain, Spew, Danger }

/** One entry in an [ItemMenu]. The menu closes itself before [onClick] runs. */
class MenuEntry(
    val text: String,
    val icon: ImageVector,
    val tone: MenuTone = MenuTone.Plain,
    val onClick: () -> Unit,
)

/** An icon button that opens a dropdown of [entries]; the ⋮ "more options" button by default. */
@Composable
fun ItemMenu(
    contentDescription: String,
    entries: List<MenuEntry>,
    modifier: Modifier = Modifier,
    icon: ImageVector = PlayfulIcons.More,
    colour: Color? = null,
    size: Dp = 44.dp,
    iconSize: Dp = 18.dp,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconBlobButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = { expanded = true },
            colour = colour,
            size = size,
            iconSize = iconSize
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            entries.forEach { entry ->
                MenuEntryItem(entry) {
                    expanded = false
                    entry.onClick()
                }
            }
        }
    }
}

@Composable
private fun MenuEntryItem(entry: MenuEntry, onClick: () -> Unit) {
    val ink = when (entry.tone) {
        MenuTone.Plain -> MaterialTheme.colorScheme.onSurface
        MenuTone.Spew -> MaterialTheme.colorScheme.primary
        MenuTone.Danger -> MaterialTheme.colorScheme.error
    }
    DropdownMenuItem(
        text = {
            Text(
                text = entry.text,
                color = ink,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (entry.tone == MenuTone.Plain) FontWeight.Medium else FontWeight.Bold
            )
        },
        leadingIcon = {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = if (entry.tone == MenuTone.Plain) MaterialTheme.colorScheme.onSurfaceVariant else ink,
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = onClick
    )
}

/**
 * The bar shown while sounds are selected: the count, the screen's [actions], a ⋮ menu of
 * [menuEntries] and a button that clears the selection. Hidden while [count] is 0.
 */
@Composable
fun SelectionBar(
    count: Int,
    menuDescription: String,
    menuEntries: List<MenuEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    AnimatedVisibility(
        visible = count > 0,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, MaterialTheme.colorScheme.primary, CapsuleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CapsuleShape)
                .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$count selected",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f)
            )
            actions()
            ItemMenu(contentDescription = menuDescription, entries = menuEntries)
            IconBlobButton(
                icon = PlayfulIcons.Close,
                contentDescription = "Clear selection",
                onClick = onClear
            )
        }
    }
}

/**
 * A sound in a list: the screen's [leading] blob, the title (tap it to scroll a long one) over
 * [subtitle], then Spew, Share and a ⋮ menu of [menuEntries].
 *
 * [key] identifies the sound, so a scrolling title stops when the row is reused for another one.
 */
@Composable
fun SoundRow(
    key: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    onSpew: () -> Unit,
    spewDescription: String,
    onShare: () -> Unit,
    shareDescription: String,
    menuEntries: List<MenuEntry>,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    leading: @Composable RowScope.() -> Unit,
) {
    CapsuleRow(
        selected = selected,
        modifier = modifier.then(
            if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CapsuleShape) else Modifier
        )
    ) {
        leading()

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            var isScrollingTitle by remember(key) { mutableStateOf(false) }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (pinned) {
                    Icon(
                        imageVector = PlayfulIcons.Star,
                        contentDescription = "Pinned",
                        tint = PinStar,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = if (isScrollingTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isScrollingTitle = !isScrollingTitle
                        }
                        .then(
                            if (isScrollingTitle) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 150,
                                    repeatDelayMillis = 1200,
                                    velocity = 45.dp
                                )
                            } else {
                                Modifier
                            }
                        )
                )
            }
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        SpewButton(onClick = onSpew, contentDescription = spewDescription)

        IconBlobButton(
            icon = PlayfulIcons.Share,
            contentDescription = shareDescription,
            onClick = onShare,
            size = 40.dp,
            iconSize = 18.dp
        )

        ItemMenu(contentDescription = "More options for $title", entries = menuEntries)
    }
}
