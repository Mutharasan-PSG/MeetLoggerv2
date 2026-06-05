package com.example.meetloggerv2.ui.login.activity

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.ui.login.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SignUpActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeetLoggerTheme {
                val signUpState by viewModel.signUpState.collectAsState()
                var registeredEmail by remember { mutableStateOf("") }
                var showSuccessDialog by remember { mutableStateOf(false) }

                LaunchedEffect(signUpState) {
                    when (signUpState) {
                        is LoginViewModel.SignUpState.Success -> {
                            showSuccessDialog = true
                        }
                        is LoginViewModel.SignUpState.Error -> {
                            Toast.makeText(this@SignUpActivity, (signUpState as LoginViewModel.SignUpState.Error).message, Toast.LENGTH_LONG).show()
                            viewModel.resetStates()
                        }
                        else -> {}
                    }
                }

                SignUpScreen(
                    signUpState = signUpState,
                    onSignUp = { name, email, password ->
                        registeredEmail = email
                        viewModel.signUpWithEmail(name, email, password)
                    },
                    onBack = { finish() }
                )

                if (showSuccessDialog) {
                    val rawMessage = stringResource(id = R.string.dialog_msg_signup_success, registeredEmail)
                    val formattedMessage = remember(rawMessage) {
                        androidx.core.text.HtmlCompat.fromHtml(rawMessage, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                    }

                    AlertDialog(
                        onDismissRequest = { /* Cannot dismiss */ },
                        title = {
                            Text(
                                text = stringResource(id = R.string.dialog_title_signup_success),
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(text = formattedMessage)
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showSuccessDialog = false
                                    viewModel.resetStates()
                                    finish()
                                }
                            ) {
                                Text("OK")
                            }
                        },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    signUpState: LoginViewModel.SignUpState,
    onSignUp: (String, String, String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val isLoading = signUpState is LoginViewModel.SignUpState.Loading

    fun validateAndSubmit() {
        val trimmedName = name.trim()
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        val trimmedConfirmPassword = confirmPassword.trim()

        var isValid = true

        if (trimmedName.isEmpty()) {
            nameError = context.getString(R.string.error_name_required)
            Toast.makeText(context, context.getString(R.string.error_name_required), Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            nameError = null
        }

        if (trimmedEmail.isEmpty()) {
            emailError = "Please enter your email address"
            if (isValid) Toast.makeText(context, "Please enter your email address", Toast.LENGTH_SHORT).show()
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            emailError = "Please enter a valid email address"
            if (isValid) Toast.makeText(context, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            emailError = null
        }

        val passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!_\\-]).{8,}$".toRegex()
        if (trimmedPassword.isEmpty()) {
            passwordError = "Please enter a password"
            if (isValid) Toast.makeText(context, "Please enter a password", Toast.LENGTH_SHORT).show()
            isValid = false
        } else if (!trimmedPassword.matches(passwordRegex)) {
            passwordError = context.getString(R.string.error_password_rules)
            if (isValid) Toast.makeText(context, context.getString(R.string.error_password_rules), Toast.LENGTH_LONG).show()
            isValid = false
        } else {
            passwordError = null
        }

        if (trimmedConfirmPassword.isEmpty()) {
            confirmPasswordError = "Please confirm your password"
            if (isValid) Toast.makeText(context, "Please confirm your password", Toast.LENGTH_SHORT).show()
            isValid = false
        } else if (trimmedPassword != trimmedConfirmPassword) {
            confirmPasswordError = context.getString(R.string.error_passwords_do_not_match)
            if (isValid) Toast.makeText(context, context.getString(R.string.error_passwords_do_not_match), Toast.LENGTH_SHORT).show()
            isValid = false
        } else {
            confirmPasswordError = null
        }

        if (isValid) {
            focusManager.clearFocus()
            if (NetworkUtil.isNetworkAvailable(context)) {
                onSignUp(trimmedName, trimmedEmail, trimmedPassword)
            } else {
                Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Create Account",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, enabled = !isLoading) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sign up to get started and manage your meetings contextually",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (nameError != null) nameError = null
                    },
                    label = { Text("Full Name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

                Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(8.dp))

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
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
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

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        if (confirmPasswordError != null) confirmPasswordError = null
                    },
                    label = { Text("Confirm Password") },
                    isError = confirmPasswordError != null,
                    supportingText = confirmPasswordError?.let { { Text(text = it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                        IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                            Icon(
                                painter = painterResource(id = if (confirmPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
                                contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password"
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

                Spacer(modifier = Modifier.height(24.dp))

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
                                text = "Sign Up",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    TextButton(onClick = onBack, enabled = !isLoading) {
                        Text(
                            text = "Sign In",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
