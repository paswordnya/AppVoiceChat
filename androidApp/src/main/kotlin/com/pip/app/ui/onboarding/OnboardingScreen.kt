package com.pip.app.ui.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/**
 * First onboarding step, ported 1:1 from iOS's `OnboardingView.swift`
 * ("Hi, I'm Pip"): mascot intro with a skip affordance and a primary CTA.
 * Both "Skip" and "Continue with Pip" lead to Login (see `AppRoot`'s
 * `AppStage`) — the mic-permission/buddy-picker steps are still deferred,
 * but auth is now wired up (`LoginScreen`/`SignupScreen`).
 */
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val breathing by animateFloatAsState(
        targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(1700), RepeatMode.Reverse),
        label = "breathing",
    )
    val blink = rememberBlinkState()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PipTheme.cream)
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 46.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                "Skip",
                style = pipBody(14),
                color = PipTheme.ink.copy(alpha = 0.45f),
                modifier = Modifier.clickable { onFinished() }.padding(8.dp),
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.size((150 * breathing).dp)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .clip(BlobShape())
                            .background(Brush.linearGradient(colors = listOf(PipTheme.buddyFrom, PipTheme.buddyTo))),
                )
                PipBlobFace(modifier = Modifier.fillMaxSize(), eyeOpen = blink.eyeOpen.value)
            }

            Spacer(Modifier.height(22.dp))

            Text("Hi, I'm Pip.", style = pipDisplay(28, FontWeight.SemiBold), color = PipTheme.ink)

            Spacer(Modifier.height(10.dp))

            Text(
                "Your calm little sidekick. Talk to me — I listen, think, and react.",
                style = pipBody(15),
                color = PipTheme.ink.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(PipTheme.accent)
                    .clickable { onFinished() }
                    .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Continue with Pip", style = pipDisplay(16, FontWeight.SemiBold), color = Color.White)
        }
    }
}
