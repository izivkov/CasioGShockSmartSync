package org.avmedia.gshockGoogleSync.ui.others

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.avmedia.gshockGoogleSync.data.repository.GShockRepository
import org.avmedia.gshockGoogleSync.data.repository.TranslateRepository
import org.avmedia.gshockGoogleSync.R

@Composable
fun CoverScreen(
    repository: GShockRepository,
    translateApi: TranslateRepository,
    onUnlocked: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var isUnlocked by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    val maxOffset = 200f

    val alpha by animateFloatAsState(if (isUnlocked) 0f else 1f)

    // Check connection status
    LaunchedEffect(Unit) {
        while (true) {
            isConnected = repository.isConnected()
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Connection status pill
        AnimatedVisibility(
            visible = isConnected,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        ) {
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Connected",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        // Sliding content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = translateApi.stringResource(LocalContext.current, R.string.slide_to_unlock),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Slider
            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp, y = 0.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(0f, maxOffset)
                            if (offsetX >= maxOffset) {
                                isUnlocked = true
                                onUnlocked()
                            }
                        }
                    }
            )
        }
    }
}
