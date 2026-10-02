package com.pip.app.ui.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pip.app.ui.components.BlobShape
import com.pip.app.ui.components.PipBlobFace
import com.pip.app.ui.components.rememberBlinkState
import com.pip.app.ui.theme.PipTheme
import com.pip.app.ui.theme.pipBody
import com.pip.app.ui.theme.pipDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Floater(val topFrac: Float?, val leftFrac: Float?, val bottomFrac: Float?, val rightFrac: Float?, val size: Int)

private val floaters =
    listOf(
        Floater(topFrac = 0.14f, leftFrac = 0.12f, bottomFrac = null, rightFrac = null, size = 46),
        Floater(topFrac = 0.20f, leftFrac = null, bottomFrac = null, rightFrac = 0.14f, size = 30),
        Floater(topFrac = null, leftFrac = 0.10f, bottomFrac = 0.22f, rightFrac = null, size = 34),
        Floater(topFrac = null, leftFrac = null, bottomFrac = 0.16f, rightFrac = 0.10f, size = 52),
    )

private val loadingMessages =
    listOf(
        "Getting everything ready…",
        "Preparing your AI assistant…",
        "Almost there…",
    )

/**
 * Launch splash, ported 1:1 from iOS's `SplashView.swift`: gradient
 * background, floating dots, a breathing mascot blob, wordmark + welcome
 * copy, and a bouncing-dot loader with rotating status text. Auto-advances
 * after ~2.7s; tapping anywhere skips immediately (PRD §13a).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var messageIndex by remember { mutableIntStateOf(0) }
    var hasFinished by remember { mutableStateOf(false) }

    val breathing by animateFloatAsState(
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "breathing",
    )
    val bounce by animateFloatAsState(
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "bounce",
    )

    fun finish() {
        if (hasFinished) return
        hasFinished = true
        onFinished()
    }

    LaunchedEffect(Unit) {
        launch {
            while (!hasFinished) {
                delay(900)
                messageIndex = (messageIndex + 1) % loadingMessages.size
            }
        }
        delay(2700)
        finish()
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = listOf(PipTheme.accent, PipTheme.accentDeep)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { finish() },
    ) {
        floaters.forEach { floater ->
            val x = floater.leftFrac?.let { maxWidth * it } ?: (maxWidth - maxWidth * (floater.rightFrac ?: 0f) - floater.size.dp)
            val y = floater.topFrac?.let { maxHeight * it } ?: (maxHeight - maxHeight * (floater.bottomFrac ?: 0f) - floater.size.dp)
            Box(
                modifier =
                    Modifier
                        .offset(x = x, y = y)
                        .size(floater.size.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.16f)),
            )
        }

        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 260.dp)
                    .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val blink = rememberBlinkState()
            Box(
                modifier =
                    Modifier
                        .size((120 * breathing).dp)
                        .shadow(elevation = 25.dp, shape = BlobShape(), clip = false),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(BlobShape())
                            .background(Color.White.copy(alpha = 0.24f)),
                )
                PipBlobFace(modifier = Modifier.fillMaxSize(), color = Color.White, eyeOpen = blink.eyeOpen.value)
            }

            Spacer(Modifier.height(14.dp))

            Text("Pip", style = pipDisplay(32, FontWeight.Bold), color = Color.White)

            Spacer(Modifier.height(8.dp))

            Text("Welcome! 👋", style = pipDisplay(20, FontWeight.SemiBold), color = Color.White)

            Spacer(Modifier.height(6.dp))

            Text(
                "Your AI assistant is ready to help you chat, create, and get things done.",
                style = pipBody(14),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) {
                    Box(
                        modifier =
                            Modifier
                                .size((9 * bounce).dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.5f + bounce * 0.5f)),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(loadingMessages[messageIndex], style = pipBody(13), color = Color.White.copy(alpha = 0.75f))
        }
    }
}
