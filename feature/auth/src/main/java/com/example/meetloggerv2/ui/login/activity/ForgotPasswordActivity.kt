package com.example.meetloggerv2.ui.login.activity

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.ui.login.viewmodel.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ForgotPasswordActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupObservers()

        setContent {
            MeetLoggerTheme {
                ForgotPasswordScreen(
                    onSendReset = { email ->
                        if (NetworkUtil.isNetworkAvailable(this)) {
                            viewModel.sendPasswordReset(email)
                        } else {
                            Toast.makeText(this, "No internet connection", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun setupObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.resetPasswordState.collect { state ->
                    when (state) {
                        is LoginViewModel.ResetPasswordState.Loading -> {
                            Toast.makeText(this@ForgotPasswordActivity, "Sending reset link...", Toast.LENGTH_SHORT).show()
                        }
                        is LoginViewModel.ResetPasswordState.Success -> {
                            Toast.makeText(this@ForgotPasswordActivity, getString(R.string.toast_password_reset_sent), Toast.LENGTH_LONG).show()
                            finish()
                        }
                        is LoginViewModel.ResetPasswordState.Error -> {
                            Toast.makeText(this@ForgotPasswordActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                        is LoginViewModel.ResetPasswordState.Idle -> {
                            // Do nothing
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
    onSendReset: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

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
        focusManager.clearFocus()
        onSendReset(trimmedEmail)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.title_forgot_password),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(id = R.string.desc_forgot_password),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

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
                        imeAction = ImeAction.Done
                    ),
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
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Surface(
                    onClick = { validateAndSubmit() },
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
                        Text(
                            text = stringResource(id = R.string.btn_send_reset_link),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                TextButton(onClick = onBack) {
                    Text(
                        text = stringResource(id = R.string.label_back_to_login),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
