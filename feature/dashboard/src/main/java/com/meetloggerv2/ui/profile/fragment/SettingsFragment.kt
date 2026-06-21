package com.meetloggerv2.ui.profile.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.meetloggerv2.core.util.ToastHelper
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.ui.components.GradientIconBadge
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.ui.profile.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.navigation.findNavigationRouter
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    @Inject
    lateinit var authSession: AuthSession

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupObservers()

        val userId = authSession.currentUserId() ?: ""

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    SettingsScreen(
                        viewModel = viewModel,
                        userId = userId,
                        onBack = { parentFragmentManager.popBackStack() },
                        onToggleBiometric = { enabled ->
                            checkAndVerifyBiometric(enabled)
                        }
                    )
                }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteAccountState.collect { state ->
                    when (state) {
                        is SettingsViewModel.DeleteAccountState.Loading -> {
                            // Handled in Compose loader
                        }
                        is SettingsViewModel.DeleteAccountState.Success -> {
                            ToastHelper.showShort(requireContext(), "Account permanently deleted.")
                            findNavigationRouter()?.navigateToLogin()
                        }
                        is SettingsViewModel.DeleteAccountState.Error -> {
                            ToastHelper.showLong(requireContext(), state.message)
                        }
                        is SettingsViewModel.DeleteAccountState.Idle -> {
                            // Do nothing
                        }
                    }
                }
            }
        }
    }

    private fun checkAndVerifyBiometric(enable: Boolean) {
        val context = requireContext()
        val biometricManager = BiometricManager.from(context)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        
        when (biometricManager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                showBiometricPrompt(enable)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                ToastHelper.showLong(context, "No biometric features available on this device.")
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                ToastHelper.showLong(context, "Biometric features are currently unavailable.")
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                ToastHelper.showLong(context, "Please set up fingerprint or screen lock in your system settings first.")
            }
            else -> {
                ToastHelper.showLong(context, "Biometric lock is not supported on this device.")
            }
        }
    }

    private fun showBiometricPrompt(enable: Boolean) {
        val executor = ContextCompat.getMainExecutor(requireContext())
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                ToastHelper.showShort(requireContext(), "Authentication error: $errString")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                viewModel.setBiometricLock(enable)
                val statusText = if (enable) "App Lock enabled successfully!" else "App Lock disabled!"
                ToastHelper.showShort(requireContext(), statusText)
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                ToastHelper.showShort(requireContext(), "Authentication failed.")
            }
        })

        val titleText = if (enable) "Enable App Lock" else "Disable App Lock"
        val subtitleText = if (enable) 
            "Scan your fingerprint to enable App Lock protection" 
        else 
            "Scan your fingerprint to confirm disabling App Lock"

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titleText)
            .setSubtitle(subtitleText)
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    userId: String,
    onBack: () -> Unit,
    onToggleBiometric: (Boolean) -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val autoSend by viewModel.autoSendEmail.collectAsState()
    val quality by viewModel.recordingQuality.collectAsState()
    val biometric by viewModel.biometricLock.collectAsState()
    val deleteState by viewModel.deleteAccountState.collectAsState()

    var showDeleteAccountConfirmDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .pressScaleClick { onBack() },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsGroup(title = "Appearance") {
                ThemeSelectionRow(
                    currentMode = themeMode,
                    onModeSelected = { viewModel.setThemeMode(it) }
                )
            }

            SettingsGroup(title = "Automation") {
                ToggleSettingRow(
                    icon = Icons.Default.Email,
                    title = "Auto-send PDF",
                    subtitle = "Email PDF files automatically",
                    checked = autoSend,
                    onCheckedChange = { viewModel.setAutoSendEmail(enabled = it) }
                )
                SelectionSettingRow(
                    icon = Icons.Default.Language,
                    title = "Default Language",
                    value = "English (US)",
                    onClick = { /* TODO */ }
                )
            }

            SettingsGroup(title = "Recording & Privacy") {
                SelectionSettingRow(
                    icon = Icons.Default.Mic,
                    title = "Recording Quality",
                    value = quality,
                    onClick = { /* TODO: Show quality dialog */ }
                )
                ToggleSettingRow(
                    icon = Icons.Default.Notifications,
                    title = "Processing Alerts",
                    subtitle = "Notify when summarization is done",
                    checked = true,
                    onCheckedChange = { /* TODO */ }
                )
                ToggleSettingRow(
                    icon = Icons.Default.Lock,
                    title = "App Lock",
                    subtitle = "Secure app with biometrics",
                    checked = biometric,
                    onCheckedChange = { onToggleBiometric(it) }
                )
            }

            Column {
                Text(
                    text = "Account Actions",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                ActionSettingRow(
                    icon = Icons.Default.Delete,
                    title = "Delete Account",
                    subtitle = "Permanently delete all data",
                    isDestructive = true,
                    onClick = { showDeleteAccountConfirmDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showDeleteAccountConfirmDialog) {
        Dialog(onDismissRequest = { showDeleteAccountConfirmDialog = false }) {
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
                    // 1. Icon Badge
                    val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PersonOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. Title
                    Text(
                        text = "Delete Your Account?",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 3. Intro sentence
                    Text(
                        text = "Your account and all associated data will be permanently removed from our servers.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 4. Warning chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "This action is irreversible and cannot be undone.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5. Data list card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "The following will be deleted:",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            DeleteDataItem(icon = Icons.Default.CloudOff, text = "Audio recordings & cloud files")
                            Spacer(modifier = Modifier.height(8.dp))
                            DeleteDataItem(icon = Icons.Default.SpeakerNotesOff, text = "Transcripts, summaries & AI insights")
                            Spacer(modifier = Modifier.height(8.dp))
                            DeleteDataItem(icon = Icons.Default.PersonRemove, text = "Profile, settings & activity history")
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // 6. Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val cancelInteractionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = { showDeleteAccountConfirmDialog = false },
                            interactionSource = cancelInteractionSource,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .pressScale(cancelInteractionSource),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }

                        val deleteInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                showDeleteAccountConfirmDialog = false
                                viewModel.deleteAccount(userId)
                            },
                            interactionSource = deleteInteractionSource,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .pressScale(deleteInteractionSource),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(colors = listOf(Color(0xFFEF5350), Color(0xFFD32F2F)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Delete", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteState is SettingsViewModel.DeleteAccountState.Loading) {
        Dialog(onDismissRequest = {}) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                content()
            }
        }
    }
}

@Composable
fun ThemeSelectionRow(currentMode: Int, onModeSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeOption(
            text = "System",
            selected = currentMode == 0,
            onClick = { onModeSelected(0) },
            modifier = Modifier.weight(1f)
        )
        ThemeOption(
            text = "Light",
            selected = currentMode == 1,
            onClick = { onModeSelected(1) },
            modifier = Modifier.weight(1f)
        )
        ThemeOption(
            text = "Dark",
            selected = currentMode == 2,
            onClick = { onModeSelected(2) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ThemeOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .pressScaleClick(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ToggleSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientIconBadge(
            icon = icon,
            size = 44.dp,
            cornerRadius = 12.dp,
            iconSize = 24.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun SelectionSettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GradientIconBadge(
            icon = icon,
            size = 44.dp,
            cornerRadius = 12.dp,
            iconSize = 24.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = value, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActionSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    if (isDestructive) {
        val errorBgColor = MaterialTheme.colorScheme.error.copy(alpha = 0.05f)
        val errorBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
        val errorTintColor = MaterialTheme.colorScheme.error
        
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .pressScaleClick(onClick = onClick),
            shape = RoundedCornerShape(24.dp),
            color = errorBgColor,
            border = BorderStroke(1.dp, errorBorderColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = errorTintColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = errorTintColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = errorTintColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    } else {
        val titleColor = MaterialTheme.colorScheme.onSurface
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScaleClick(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientIconBadge(
                icon = icon,
                size = 44.dp,
                cornerRadius = 12.dp,
                iconSize = 24.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun DeleteDataItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

