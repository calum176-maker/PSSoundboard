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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jms.pssoundboard.data.WhatsNew
import com.jms.pssoundboard.ui.components.SpewButton
import com.jms.pssoundboard.ui.components.blobColourFor
import com.jms.pssoundboard.ui.components.blobShapeFor
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkMuted
import com.jms.pssoundboard.ui.theme.Surface1
import com.jms.pssoundboard.ui.theme.gummy

/** The changes in this build: shown once after an update, and from Setup any time. */
@Composable
fun WhatsNewDialog(notes: WhatsNew, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = Surface1
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column {
                    Text(
                        text = "What's new",
                        color = InkLight,
                        fontFamily = BricolageCondensed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                    Text("Spewboard v${notes.version}", color = InkMuted, fontSize = 13.sp)
                }
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    notes.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // A small gummy blob for a bullet, in a colour picked from the text
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(width = 14.dp, height = 13.dp)
                                    .gummy(blobColourFor(item), blobShapeFor(item))
                            )
                            Text(item, color = InkLight, fontSize = 15.sp, lineHeight = 20.sp)
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
