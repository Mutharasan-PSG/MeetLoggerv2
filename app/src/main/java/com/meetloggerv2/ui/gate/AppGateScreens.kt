package com.meetloggerv2.ui.gate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.core.ui.components.GradientIconBadge

/**
 * Full-screen blocking surfaces shown by the Remote Config access gates
 * (force-update / maintenance / per-user block). Styled to match the existing
 * AppLockScreen so the blocked states feel native to the app.
 */

@Composable
fun ForceUpdateScreen(onUpdate: () -> Unit) {
    GateScaffold(
        icon = Icons.Default.SystemUpdateAlt,
        title = "Update Required",
        message = "A new version of MeetLogger is required to continue. " +
            "Please update to the latest version to keep using the app.",
        primaryLabel = "Update Now",
        onPrimary = onUpdate,
    )
}

@Composable
fun MaintenanceScreen(message: String, onRetry: () -> Unit) {
    GateScaffold(
        icon = Icons.Default.Engineering,
        title = "Under Maintenance",
        message = message,
        primaryLabel = "Retry",
        onPrimary = onRetry,
    )
}

@Composable
fun BlockedScreen(onSignOut: () -> Unit) {
    GateScaffold(
        icon = Icons.Default.Block,
        title = "Account Blocked",
        message = "Your account has been blocked and can no longer access MeetLogger. " +
            "If you believe this is a mistake, please contact support.",
        primaryLabel = "Sign Out",
        onPrimary = onSignOut,
    )
}

@Composable
private fun GateScaffold(
    icon: ImageVector,
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GradientIconBadge(
                icon = icon,
                contentDescription = title,
                size = 100.dp,
                shape = CircleShape,
                iconSize = 52.dp,
                shadowElevation = 8.dp,
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(48.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScaleClick { onPrimary() },
                shape = RoundedCornerShape(24.dp),
                color = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(GradientStart, GradientEnd))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = primaryLabel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
