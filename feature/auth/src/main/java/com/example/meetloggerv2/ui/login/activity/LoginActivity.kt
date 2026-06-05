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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
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
                }
            }
        }

        setContent {
            MeetLoggerTheme {
                val loginState by viewModel.loginState.collectAsState()
                val resendVerificationState by viewModel.resendVerificationState.collectAsState()
                var verificationMessage by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(loginState) {
                    when (loginState) {
                        is LoginViewModel.LoginState.Success -> {
                            navigateToHome((loginState as LoginViewModel.LoginState.Success).user)
                        }
                        is LoginViewModel.LoginState.EmailNotVerified -> {
                            verificationMessage = (loginState as LoginViewModel.LoginState.EmailNotVerified).message
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
                    onLogin = { email, password ->
                        viewModel.signInWithEmail(email, password)
                    },
                    onGoogleLogin = {
                        signInWithGoogle()
                    },
                    onForgotPassword = {
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
                    AlertDialog(
                        onDismissRequest = {
                            verificationMessage = null
                            viewModel.resetStates()
                        },
                        title = {
                            Text(
                                text = "Email Verification Required",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(text = verificationMessage.orEmpty())
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    verificationMessage = null
                                    viewModel.resendVerificationEmail()
                                }
                            ) {
                                Text("Resend Email")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    verificationMessage = null
                                    viewModel.resetStates()
                                }
                            ) {
                                Text("Dismiss")
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
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
    val scrollState = rememberScrollState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }

    val isLoading = loginState is LoginViewModel.LoginState.Loading

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
            passwordError = context.getString(R.string.error_password_rules)
            if (isValid) Toast.makeText(context, context.getString(R.string.error_password_rules), Toast.LENGTH_LONG).show()
            isValid = false
        } else {
            passwordError = null
        }

        if (isValid) {
            focusManager.clearFocus()
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.launchlogo),
                    contentDescription = null,
                    modifier = Modifier.size(90.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.app_name),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Log back into your meeting archive",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (emailError != null) emailError = null
                    },
                    label = { Text("Email Address") },
                    isError = emailError != null,
                    supportingText = emailError?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (passwordError != null) passwordError = null
                    },
                    label = { Text("Password") },
                    isError = passwordError != null,
                    supportingText = passwordError?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
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
                                painter = painterResource(id = if (passwordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    singleLine = true,
                    enabled = !isLoading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
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
                            .clickable(enabled = !isLoading, onClick = onForgotPassword)
                            .padding(vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Premium Gradient Submit Button
                Surface(
                    onClick = { validateAndSubmit() },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shadowElevation = 4.dp,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF4361EE), Color(0xFF7209B7))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
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
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                    Text(
                        text = "OR",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Premium Google Sign In Button with subtle border and elevation
                Surface(
                    onClick = onGoogleLogin,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
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

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    TextButton(onClick = onSignUp, enabled = !isLoading) {
                        Text(
                            text = "Sign Up",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}
