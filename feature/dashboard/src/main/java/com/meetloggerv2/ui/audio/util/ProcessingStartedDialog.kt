package com.meetloggerv2.ui.audio.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.meetloggerv2.core.theme.pressScaleClick

/**
 * Confirmation popup shown once an upload/recording kicks off backend
 * processing. Matches the app's richer dialog language (account deletion,
 * email verification, account-exists): circle icon badge, title, a short
 * intro, a single time-expectation chip, and one primary action — so the user
 * understands work is happening in the background, rather than a vague toast.
 *
 * Hosted by the screen behind the (now dismissed) bottom sheet so it persists
 * after the sheet closes.
 */
@Composable
fun ProcessingStartedDialog(
    onDismiss: () -> Unit
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
                Surface(
                    modifier = Modifier.size(80.dp),
                    shape = CircleShape,
                    color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Autorenew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Title
                Text(
                    text = "Processing Started",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Intro line
                Text(
                    text = "Your audio was uploaded successfully and is now being processed. We'll notify you the moment your meeting minutes are ready.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Time-expectation chip
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
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "This usually takes a few minutes — you can safely close the app.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // 5. Primary action
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .pressScaleClick { onDismiss() },
                    shape = RoundedCornerShape(25.dp),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Got It", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
