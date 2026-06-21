package com.meetloggerv2.ui.audio.util

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.ui.components.GradientIconBadge
import com.meetloggerv2.core.config.AppConfig

@Composable
fun PlanLimitDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isDark = MaterialTheme.colorScheme.onSurface == Color.White

                // 1. Icon badge
                GradientIconBadge(
                    icon = Icons.Default.Lock,
                    size = 80.dp,
                    shape = CircleShape,
                    iconSize = 40.dp,
                    shadowElevation = 8.dp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Title
                Text(
                    text = "Plan Limit Reached",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Intro line
                val limit = AppConfig.freePlanLimit
                Text(
                    text = "You've reached the free plan limit of $limit recordings.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Upgrade-benefit chip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Go Pro for unlimited sessions, automated emails and extended file lengths.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 5. Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val dismissInteractionSource = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = onDismiss,
                        interactionSource = dismissInteractionSource,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .pressScale(dismissInteractionSource),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Text("Not Now", fontWeight = FontWeight.Bold)
                    }

                    val upgradeInteractionSource = remember { MutableInteractionSource() }
                    Surface(
                        onClick = onUpgrade,
                        interactionSource = upgradeInteractionSource,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .pressScale(upgradeInteractionSource),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Upgrade", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
