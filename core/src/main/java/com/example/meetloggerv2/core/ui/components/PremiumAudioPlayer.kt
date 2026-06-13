package com.example.meetloggerv2.core.ui.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.theme.pressScaleClick

@Composable
fun PremiumAudioPlayer(
    title: String? = null,
    isPlaying: Boolean,
    playbackProgress: Int,
    playbackMax: Int,
    currentTimeStr: String,
    totalTimeStr: String,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onSkipNext: (() -> Unit)? = null,
    onSkipPrev: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    autoPlayNext: Boolean = false,
    onToggleAutoPlay: (() -> Unit)? = null,
    containerColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier.fillMaxWidth(),
        tonalElevation = if (containerColor != null) 8.dp else 1.dp,
        shadowElevation = if (containerColor != null) 12.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (title != null || onStop != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (title != null) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                                .basicMarquee()
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    
                    if (onStop != null) {
                        IconButton(
                            onClick = onStop, 
                            modifier = Modifier.size(28.dp).pressScaleClick { onStop() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close, 
                                contentDescription = "Close", 
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Timeline / Seeker
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = playbackProgress.toFloat(),
                    onValueChange = { onSeek(it.toInt()) },
                    valueRange = 0f..playbackMax.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.height(20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = currentTimeStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = totalTimeStr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Auto Play / Settings (Optional)
                if (onToggleAutoPlay != null) {
                    IconButton(
                        onClick = onToggleAutoPlay, 
                        modifier = Modifier.size(40.dp).pressScaleClick { onToggleAutoPlay() }
                    ) {
                        val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = "Auto Play",
                            tint = if (autoPlayNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDark) 0.6f else 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (onSkipPrev != null || onSkipNext != null) {
                     // Spacing helper if some items are null
                     Spacer(modifier = Modifier.size(40.dp))
                }

                // Skip Previous
                if (onSkipPrev != null) {
                    IconButton(
                        onClick = onSkipPrev, 
                        modifier = Modifier.size(44.dp).pressScaleClick { onSkipPrev() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious, 
                            contentDescription = "Previous", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Rewind 10s
                IconButton(
                    onClick = onRewind, 
                    modifier = Modifier.size(44.dp).pressScaleClick { onRewind() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10, 
                        contentDescription = "Rewind 10s", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Play / Pause (Compact Premium)
                Surface(
                    modifier = Modifier
                        .size(56.dp)
                        .pressScaleClick { onPlayPause() },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    tonalElevation = 4.dp,
                    shadowElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Forward 10s
                IconButton(
                    onClick = onForward, 
                    modifier = Modifier.size(44.dp).pressScaleClick { onForward() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10, 
                        contentDescription = "Forward 10s", 
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Skip Next
                if (onSkipNext != null) {
                    IconButton(
                        onClick = onSkipNext, 
                        modifier = Modifier.size(44.dp).pressScaleClick { onSkipNext() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext, 
                            contentDescription = "Next", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Spacing helper for balance
                if (onToggleAutoPlay != null) {
                    Spacer(modifier = Modifier.size(40.dp))
                } else if (onSkipPrev != null || onSkipNext != null) {
                    Spacer(modifier = Modifier.size(40.dp))
                }
            }
        }
    }
}
