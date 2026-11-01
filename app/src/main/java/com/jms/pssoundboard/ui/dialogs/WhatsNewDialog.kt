package com.jms.pssoundboard.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.WhatsNewRelease
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.components.blobShapeFor
import com.jms.pssoundboard.ui.theme.gummy

/**
 * What changed, one section per release (newest first): after an update it covers every release
 * since the one last seen; from Setup, the recent history.
 */
@Composable
fun WhatsNewDialog(version: String, releases: List<WhatsNewRelease>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        text = "What's new",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text("Spewboard v$version", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    releases.forEachIndexed { index, release ->
                        // With more than one release, label each section with its version
                        if (releases.size > 1) {
                            Text(
                                text = "v${release.version}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = if (index == 0) 0.dp else 6.dp)
                            )
                        }
                        release.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // A small gummy blob for a bullet, in a colour picked from the text
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(width = 14.dp, height = 13.dp)
                                        .gummy(blobColourFor(item), blobShapeFor(item))
                                )
                                Text(item, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                SpewButton(
                    label = "Got it",
                    contentDescription = "Close what's new",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
