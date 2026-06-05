package com.example.meetloggerv2.ui.login.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.ui.login.fragment.TermsPolicyBottomSheetFragment

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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.launchlogo),
                    contentDescription = null,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.app_slogan),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 26.sp
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.btn_get_started),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                val annotatedString = buildAnnotatedString {
                    append("By signing in, you agree to our ")
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

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
