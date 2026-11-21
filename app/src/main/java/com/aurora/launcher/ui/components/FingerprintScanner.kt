@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.aurora.launcher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aurora.launcher.R
import kotlinx.coroutines.delay

/**
 * Декоративный сканер отпечатка — «экран блокировки» поверх стола.
 *
 * Ничего не разблокирует по-настоящему: это визуальный эффект. После касания датчик «сканирует»,
 * показывает зелёную галочку и вызывает [onUnlock] (реальная блокировка экрана лаунчером).
 */
@Composable
fun FingerprintScannerOverlay(
    visible: Boolean,
    vibration: Boolean,
    onUnlock: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 0 — ждём касания, 1 — сканируем, 2 — распознали
    var phase by remember { mutableStateOf(0) }
    var scanRequested by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) { phase = 0; scanRequested = false } }
    // Ключ эффекта — scanRequested, а не phase: смена phase не обрывает последовательность,
    // а отмена (visible = false) её корректно останавливает
    LaunchedEffect(scanRequested, visible) {
        if (!scanRequested || !visible) return@LaunchedEffect
        delay(900)
        if (!visible) return@LaunchedEffect
        phase = 2
        if (vibration) {
            runCatching {
                @Suppress("MissingPermission")
                (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(35L)
            }
        }
        delay(430)
        if (!visible) return@LaunchedEffect
        onUnlock()
        onCancel()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        val cs = MaterialTheme.colorScheme
        val pulse by rememberInfiniteTransition(label = "fingerprintPulse").animateFloat(
            initialValue = 0.92f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse",
        )
        val sweep by animateFloatAsState(if (phase >= 1) 1f else 0f, tween(900), label = "scanSweep")

        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).clickable(interactionSource = null, indication = null) { },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.fingerprint_title),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (phase) {
                        0 -> stringResource(R.string.fingerprint_tap)
                        1 -> stringResource(R.string.fingerprint_scanning)
                        else -> stringResource(R.string.fingerprint_unlocked)
                    },
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))

                Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
                    for (i in 0..2) {
                        Box(
                            Modifier
                                .size(176.dp - (i * 26).dp * pulse)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    (if (phase == 2) Color(0xFF69F0AE) else cs.primary).copy(alpha = 0.34f - i * 0.08f),
                                    CircleShape,
                                ),
                        )
                    }
                    Box(
                        Modifier
                            .size(104.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainer.copy(alpha = 0.28f))
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable { if (phase == 0) { phase = 1; scanRequested = true } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Fingerprint,
                            null,
                            tint = if (phase == 2) Color(0xFF69F0AE) else Color.White,
                            modifier = Modifier.size(64.dp),
                        )
                        if (phase == 1) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(cs.primary)
                                    .graphicsLayer { translationY = (sweep * 76f) - 38f },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(36.dp))
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.fingerprint_cancel), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/** Компактный датчик, который висит в углу рабочего стола и открывает сканер по нажатию. */
@Composable
fun FingerprintHomeButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Тёмная плашка с белой окантовкой читается и на светлых, и на тёмных обоях
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.34f))
            .border(1.dp, Color.White.copy(alpha = 0.32f), CircleShape)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Fingerprint, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}
