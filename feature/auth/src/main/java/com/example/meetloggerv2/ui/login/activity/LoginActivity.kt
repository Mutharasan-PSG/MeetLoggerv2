package com.example.meetloggerv2.ui.login.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.example.meetloggerv2.core.theme.pressScale
import com.example.meetloggerv2.core.theme.pressScaleClick
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.core.theme.GradientEnd
import com.example.meetloggerv2.core.theme.GradientStart
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.pressScale
import com.example.meetloggerv2.core.theme.pressScaleClick
import com.example.meetloggerv2.data.model.User
import com.example.meetloggerv2.ui.login.fragment.TermsPolicyBottomSheetFragment
import com.example.meetloggerv2.ui.login.viewmodel.LoginViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var signInResultLauncher: ActivityResultLauncher<Intent>
    private val viewModel: LoginViewModel by viewModels()
    private val isGoogleLoading = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        signInResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    handleSignInResult(account)
                } catch (e: ApiException) {
                    Log.e("LoginActivity", "Google sign-in failed: ${e.message}")
                    isGoogleLoading.value = false
                }
            } else {
                isGoogleLoading.value = false
            }
        }

        setContent {
            MeetLoggerTheme {
                val loginState by viewModel.loginState.collectAsState()
                val resendVerificationState by viewModel.resendVerificationState.collectAsState()
                var verificationMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(loginState) {
                    if (loginState !is LoginViewModel.LoginState.Loading) {
                        isGoogleLoading.value = false
                    }
                    when (loginState) {
                        is LoginViewModel.LoginState.Success -> {
                            navigateToHome((loginState as LoginViewModel.LoginState.Success).user)
                        }
                        is LoginViewModel.LoginState.EmailNotVerified -> {
                            verificationMessage = (loginState as LoginViewModel.LoginState.EmailNotVerified).message
                        }
                        is LoginViewModel.LoginState.UserNotFound -> {
                            // Automatically navigate to Sign Up
                            Toast.makeText(this@LoginActivity, (loginState as LoginViewModel.LoginState.UserNotFound).message, Toast.LENGTH_LONG).show()
                            startActivity(Intent(this@LoginActivity, SignUpActivity::class.java))
                            viewModel.resetStates()
                        }
                        is LoginViewModel.LoginState.Error -> {
                            Toast.makeText(this@LoginActivity, (loginState as LoginViewModel.LoginState.Error).message, Toast.LENGTH_LONG).show()
                            viewModel.resetStates()
                        }
                        else -> {}
                    }
                }

                LaunchedEffect(resendVerificationState) {
                    when (resendVerificationState) {
                        is LoginViewModel.VerificationResendState.Loading -> {
                            Toast.makeText(this@LoginActivity, "Resending verification email...", Toast.LENGTH_SHORT).show()
                        }
                        is LoginViewModel.VerificationResendState.Success -> {
                            Toast.makeText(this@LoginActivity, (resendVerificationState as LoginViewModel.VerificationResendState.Success).message, Toast.LENGTH_LONG).show()
                            viewModel.resetStates()
                        }
                        is LoginViewModel.VerificationResendState.Error -> {
                            Toast.makeText(this@LoginActivity, (resendVerificationState as LoginViewModel.VerificationResendState.Error).message, Toast.LENGTH_LONG).show()
                            viewModel.resetStates()
                        }
                        else -> {}
                    }
                }

                LoginScreen(
                    loginState = loginState,
                    isGoogleLoading = isGoogleLoading.value,
                    onLogin = { email, password ->
                        viewModel.signInWithEmail(email, password)
                    },
                    onGoogleLogin = {
                        isGoogleLoading.value = true
                        viewModel.resetStates() // Reset any previous errors
                        signInWithGoogle()
                    },
                    onForgotPassword = {
                        // startActivity handled in LoginScreen via callback
                        startActivity(Intent(this, ForgotPasswordActivity::class.java))
                    },
                    onSignUp = {
                        startActivity(Intent(this, SignUpActivity::class.java))
                    },
                    onShowTerms = { showPolicyDialog("terms") },
                    onShowPolicy = { showPolicyDialog("policy") },
                    onBack = {
                        handleBackPressed()
                    }
                )

                if (verificationMessage != null) {
                    val resendState by viewModel.resendVerificationState.collectAsState()
                    val isResending = resendState is LoginViewModel.VerificationResendState.Loading
                    
                    Dialog(onDismissRequest = {
                        if (!isResending) {
                            verificationMessage = null
                            viewModel.resetStates()
                        }
                    }) {
                        Card(
                            shape = RoundedCornerShape(32.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                            modifier = Modifier.fillMaxWidth(0.95f)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 1. Icon with square-round background (Premium Style)
                                val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                                Surface(
                                    modifier = Modifier.size(100.dp),
                                    shape = RoundedCornerShape(24.dp),
                                    color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.MarkEmailRead,
                                            contentDescription = null,
                                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(52.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // 2. Title
                                Text(
                                    text = "Verify Your Email",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // 3. Message Content
                                val emailText = verificationMessage?.substringAfter("link to:\n")?.substringBefore("\n\nPlease") ?: "your inbox"
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Your account is almost ready! Please verify your email address to continue.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 20.sp
                                    )
                                    
                                    if (emailText.contains("@")) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                            modifier = Modifier.wrapContentWidth()
                                        ) {
                                            Text(
                                                text = emailText,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(32.dp))

                                // 4. Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Dismiss Button (Outlined style)
                                    Surface(
                                        onClick = {
                                            if (!isResending) {
                                                verificationMessage = null
                                                viewModel.resetStates()
                                            }
                                        },
                                        enabled = !isResending,
                                        shape = RoundedCornerShape(24.dp),
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                        color = Color.Transparent,
                                        modifier = Modifier.weight(1f).height(50.dp).pressScale()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                "Dismiss", 
                                                fontWeight = FontWeight.Bold, 
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Resend Button (Gradient style)
                                    Surface(
                                        onClick = {
                                            viewModel.resendVerificationEmail()
                                        },
                                        enabled = !isResending,
                                        shape = RoundedCornerShape(24.dp),
                                        color = Color.Transparent,
                                        modifier = Modifier.weight(1f).height(50.dp).pressScale()
                                    ) {
                                        val resendBrush = Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    if (isResending) Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                                                    else resendBrush
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isResending) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                            } else {
                                                Text("Resend", fontWeight = FontWeight.Bold, color = Color.White)
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
    }

    override fun onResume() {
        super.onResume()
        isGoogleLoading.value = false
        viewModel.resetStates()
    }

    private fun handleBackPressed() {
        val intent = Intent(this, IntroActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun showPolicyDialog(type: String) {
        val bottomSheet = TermsPolicyBottomSheetFragment.newInstance(type)
        bottomSheet.show(supportFragmentManager, "TermsPolicyBottomSheet")
    }

    private fun signInWithGoogle() {
        if (NetworkUtil.isNetworkAvailable(this)) {
            signInResultLauncher.launch(googleSignInClient.signInIntent)
        } else {
            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        if (account != null) {
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            viewModel.signInWithCredential(credential)
        }
    }

    private fun navigateToHome(user: User) {
        sessionManager.setLoggedIn(true)
        sessionManager.saveUserDetails(user)
        val intent = Intent().setClassName(this, "com.example.meetloggerv2.ui.main.activity.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}

@Composable
fun LoginScreen(
    loginState: LoginViewModel.LoginState,
    isGoogleLoading: Boolean,
    onLogin: (String, String) -> Unit,
    onGoogleLogin: () -> Unit,
    onForgotPassword: () -> Unit,
    onSignUp: () -> Unit,
    onShowTerms: () -> Unit,
    onShowPolicy: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Background Reset Logic: Clear fields when the screen is hidden
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Reset states while hidden so there's no flicker when returning
                if (loginState !is LoginViewModel.LoginState.Loading && !isGoogleLoading) {
                    email = ""
                    password = ""
                    emailError = null
                    passwordError = null
                    focusManager.clearFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isEmailLoading = loginState is LoginViewModel.LoginState.Loading
    val isLoading = isEmailLoading || isGoogleLoading

    // Handle external errors (like Incorrect Password)
    LaunchedEffect(loginState) {
        if (loginState is LoginViewModel.LoginState.Error) {
            val msg = loginState.message.lowercase()
            if (msg.contains("password")) {
                passwordError = loginState.message
            }
            if (msg.contains("email") || msg.contains("registered")) {
                emailError = loginState.message
            }
        }
    }

    BackHandler(onBack = onBack)

    fun validateAndSubmit() {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        var isValid = true

        if (trimmedEmail.isEmpty()) {
            emailError = "Please enter your email"
            Toast.makeText(context, "Please enter your email", Toast.LENGTH_SHORT).show()
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            emailError = "Please enter a valid email address"
            Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            emailError = null
        }

        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!_\\-]).{8,}$".toRegex()
        if (trimmedPassword.isEmpty()) {
            passwordError = "Please enter your password"
            if (isValid) Toast.makeText(context, "Please enter your password", Toast.LENGTH_SHORT).show()
            isValid = false
        } else if (!trimmedPassword.matches(passwordRegex)) {
            passwordError = "Invalid password"
            if (isValid) Toast.makeText(context, "Invalid password", Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            passwordError = null
        }

        if (isValid) {
            if (NetworkUtil.isNetworkAvailable(context)) {
                onLogin(trimmedEmail, trimmedPassword)
            } else {
                Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(
                    text = "Welcome back",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Log back into your meeting archive",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                        autoCorrectEnabled = false
                    ),
                    visualTransformation = VisualTransformation.None,
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
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

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        if (it.length <= 128) {
                            password = it
                            if (passwordError != null) passwordError = null
                        }
                    },
                    label = { Text("Password") },
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { validateAndSubmit() }
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
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

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "Forgot Password?",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .pressScaleClick(enabled = !isLoading) { 
                                if (!isLoading) onForgotPassword() 
                            }
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Premium Gradient Submit Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressScaleClick(enabled = !isLoading) { 
                            focusManager.clearFocus()
                            validateAndSubmit() 
                        },
                    shape = RoundedCornerShape(16.dp),
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
                                if (isEmailLoading) {
                                    Box(modifier = Modifier.size(24.dp)) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.5.dp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Login",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 1f),
                        thickness = 2.5.dp
                    )
                    @Suppress("DEPRECATION")
                    Text(
                        text = "OR",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 1f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 1f),
                        thickness = 2.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Premium Google Sign In Button with subtle border and elevation
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .pressScaleClick(enabled = !isLoading) { 
                            onGoogleLogin() 
                        },
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isGoogleLoading) {
                            Box(modifier = Modifier.size(24.dp)) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.google_logo),
                                contentDescription = "Google Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign Up",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        modifier = Modifier.pressScaleClick(enabled = !isLoading) { 
                            if (!isLoading) onSignUp()
                        }
                    )
                }
            }
        }
    }
}
