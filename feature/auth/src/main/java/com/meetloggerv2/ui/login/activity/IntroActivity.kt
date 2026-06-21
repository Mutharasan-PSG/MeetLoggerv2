package com.meetloggerv2.ui.login.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetloggerv2.core.R
import com.meetloggerv2.core.theme.AppStrings
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.ui.login.fragment.TermsPolicyBottomSheetFragment

class IntroActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeetLoggerTheme {
                IntroScreen(
                    onGetStarted = {
                        startActivity(Intent(this, LoginActivity::class.java))
                    },
                    onShowTerms = { showPolicyDialog("terms") },
                    onShowPolicy = { showPolicyDialog("policy") }
                )
            }
        }
    }

    private fun showPolicyDialog(type: String) {
        val bottomSheet = TermsPolicyBottomSheetFragment.newInstance(type)
        bottomSheet.show(supportFragmentManager, "TermsPolicyBottomSheet")
    }
}

@Composable
fun IntroScreen(
    onGetStarted: () -> Unit,
    onShowTerms: () -> Unit,
    onShowPolicy: () -> Unit
) {
    val brandGradient = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))

    // Entrance animation: content fades/slides up, logo scales in with a soft bounce.
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }

    val contentAlpha by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
        label = "contentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (startAnim) 0f else 40f,
        animationSpec = tween(durationMillis = 700, easing = EaseOutCubic),
        label = "contentOffset"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.7f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1.1f))

            // Hero logo
            Image(
                painter = painterResource(id = R.drawable.launchlogo),
                contentDescription = null,
                modifier = Modifier
                    .size(118.dp)
                    .scale(logoScale)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // App name with brand gradient
            Text(
                text = AppStrings.APP_NAME,
                style = TextStyle(
                    brush = brandGradient,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier
                    .alpha(contentAlpha)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Slogan / tagline
            Text(
                text = AppStrings.APP_SLOGAN.replace("\n", " "),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .alpha(contentAlpha)
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Feature highlights — tells a first-time user what the app does
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeatureItem(icon = Icons.Filled.Mic, label = "Record")
                FeatureItem(icon = Icons.Filled.AutoAwesome, label = "AI Minutes")
                FeatureItem(icon = Icons.Filled.Description, label = "Documents")
            }

            Spacer(modifier = Modifier.weight(1f))

            // CTA
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .absoluteOffset(y = contentOffset.dp)
                    .alpha(contentAlpha)
                    .pressScaleClick { onGetStarted() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                shadowElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(brandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = AppStrings.BTN_GET_STARTED,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val annotatedString = buildAnnotatedString {
                append("By continuing in, you agree to our ")
                pushStringAnnotation(tag = "TERMS", annotation = "terms")
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                    append("Terms")
                }
                pop()
                append(" & ")
                pushStringAnnotation(tag = "POLICY", annotation = "policy")
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                    append("Privacy Policy")
                }
                pop()
                append(".")
            }

            ClickableText(
                text = annotatedString,
                modifier = Modifier.alpha(contentAlpha),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                ),
                onClick = { offset ->
                    annotatedString.getStringAnnotations(tag = "TERMS", start = offset, end = offset)
                        .firstOrNull()?.let {
                            onShowTerms()
                        }
                    annotatedString.getStringAnnotations(tag = "POLICY", start = offset, end = offset)
                        .firstOrNull()?.let {
                            onShowPolicy()
                        }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.Transparent,
            shadowElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(GradientStart, GradientEnd))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
