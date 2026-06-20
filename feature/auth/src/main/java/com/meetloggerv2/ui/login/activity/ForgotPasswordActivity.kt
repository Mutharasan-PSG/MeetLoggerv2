package com.meetloggerv2.ui.login.activity

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.meetloggerv2.core.theme.AppStrings
import com.meetloggerv2.core.network.NetworkUtil
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.ui.login.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ForgotPasswordActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeetLoggerTheme {
                val resetState by viewModel.resetPasswordState.collectAsState()
                var submittedEmail by remember { mutableStateOf("") }
                var showSuccessDialog by remember { mutableStateOf(false) }

                LaunchedEffect(resetState) {
                    when (resetState) {
                        is LoginViewModel.ResetPasswordState.Success -> {
                            showSuccessDialog = true
                        }
                        is LoginViewModel.ResetPasswordState.Error -> {
                            // Error is handled inside ForgotPasswordScreen via emailError
                        }
                        else -> {}
                    }
                }

                ForgotPasswordScreen(
                    resetState = resetState,
                    onSendReset = { email ->
                        if (NetworkUtil.isNetworkAvailable(this)) {
                            submittedEmail = email
                            viewModel.sendPasswordReset(email)
                        } else {
                            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBack = { finish() }
                )

                // Password Reset Success Dialog
                if (showSuccessDialog) {
                    Dialog(onDismissRequest = {
                        showSuccessDialog = false
                        viewModel.resetStates()
                        finish()
                    }) {
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
                                Surface(
                                    modifier = Modifier.size(80.dp),
                                    shape = CircleShape,
                                    color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MarkEmailRead,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Reset Link Sent",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 21.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "A password reset link has been sent to",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )

                                if (submittedEmail.contains("@")) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        modifier = Modifier.wrapContentWidth()
                                    ) {
                                        Text(
                                            text = submittedEmail,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Check your inbox (and spam folder) and follow the link to reset your password.",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 20.sp
                                )

                                Spacer(modifier = Modifier.height(28.dp))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .pressScaleClick {
                                            showSuccessDialog = false
                                            viewModel.resetStates()
                                            finish()
                                        },
                                    shape = RoundedCornerShape(25.dp),
                                    color = Color.Transparent
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Back to Login", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    resetState: LoginViewModel.ResetPasswordState,
    onSendReset: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    val isResetLoading = resetState is LoginViewModel.ResetPasswordState.Loading
    val isLoading = isResetLoading

    // Background Reset Logic: Clear fields when the screen is hidden
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (resetState !is LoginViewModel.ResetPasswordState.Loading) {
                    email = ""
                    emailError = null
                    focusManager.clearFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle external errors (like Email not registered)
    LaunchedEffect(resetState) {
        if (resetState is LoginViewModel.ResetPasswordState.Error) {
            val msg = resetState.message
            val msgLower = msg.lowercase()
            emailError = if (msgLower.contains("registered")) {
                msg
            } else {
                "Something went wrong, Try again later"
            }
        }
    }

    fun validateAndSubmit() {
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty()) {
            emailError = "Please enter your email address"
            Toast.makeText(context, "Please enter your email address", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            emailError = "Please enter a valid email address"
            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            return
        }
        emailError = null
        onSendReset(trimmedEmail)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
            // 5% Top Space
            Spacer(modifier = Modifier.height(screenHeight * 0.05f))

            // Modern Back Icon (Circular ball style)
            Surface(
                onClick = { if (!isLoading) onBack() },
                enabled = !isLoading,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .size(44.dp)
                    .pressScale()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Forgot Password",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enter your email address below, and we will send you a password reset link.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    if (it.length <= 150) {
                        email = it
                        if (emailError != null) emailError = null
                    }
                },
                label = { Text("Email Address") },
                isError = emailError != null,
                supportingText = emailError?.let { { Text(text = it) } },
                // Lock the field while the reset request is in flight to prevent
                // accidental retyping until success/failure is reached.
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    autoCorrectEnabled = false
                ),
                visualTransformation = VisualTransformation.None,
                keyboardActions = KeyboardActions(
                    onDone = { validateAndSubmit() }
                ),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLeadingIconColor = MaterialTheme.colorScheme.primary,
                    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                onClick = { 
                    focusManager.clearFocus()
                    validateAndSubmit() 
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScale(),
                shadowElevation = 4.dp,
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GradientStart, GradientEnd)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isResetLoading) {
                        Box(modifier = Modifier.size(24.dp)) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp
                            )
                        }
                    } else {
                        Text(
                            text = "Send Reset Link",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val backToLoginInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { 
                        if (!isLoading) onBack()
                    },
                    enabled = !isLoading,
                    interactionSource = backToLoginInteractionSource,
                    modifier = Modifier.pressScale(backToLoginInteractionSource)
                ) {
                    Text(
                        text = "Back to Login",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
