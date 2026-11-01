package com.jms.pssoundboard.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jms.pssoundboard.BuildConfig
import com.jms.pssoundboard.R
import com.jms.pssoundboard.ui.theme.BricolageCondensed
import com.jms.pssoundboard.ui.theme.Ground
import com.jms.pssoundboard.ui.theme.InkLight
import com.jms.pssoundboard.ui.theme.InkMuted
import com.jms.pssoundboard.ui.theme.SpewYellow
import com.jms.pssoundboard.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay

/**
 * Brief branded screen after the system launch screen: the icon stays exactly where the launch
 * screen drew it (288 dp, centred), and the name and version rise in below. Tap to skip.
 */
@Composable
fun IntroSplash(onFinished: () -> Unit) {
    val reducedMotion = rememberReducedMotion()
    var showText by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showText = true
        delay(if (reducedMotion) 600 else 1100)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onFinished)
            .semantics { contentDescription = "Spewboard, version ${BuildConfig.VERSION_NAME}" },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(288.dp)
        )
        AnimatedVisibility(
            visible = showText,
            enter = if (reducedMotion) fadeIn(tween(150))
                    else fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 },
            modifier = Modifier.offset(y = 170.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Spewboard",
                    color = InkLight,
                    fontFamily = BricolageCondensed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 46.sp
                )
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    color = InkMuted,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Spew into any voice chat",
                    color = SpewYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
