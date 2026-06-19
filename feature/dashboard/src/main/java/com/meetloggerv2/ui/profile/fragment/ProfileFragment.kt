package com.meetloggerv2.ui.profile.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.meetloggerv2.core.R
import com.meetloggerv2.core.navigation.findNavigationRouter
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.theme.ShimmerProfile
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.ui.profile.viewmodel.ProfileViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        setupObservers()

        val userId = com.meetloggerv2.core.session.SessionManager(requireContext()).getUserId()
        if (userId != null) {
            viewModel.loadUserProfile(userId)
        }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    ProfileScreen(
                        viewModel = viewModel,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onNavigateToLegal = { type -> findNavigationRouter()?.navigateToLegal(type) },
                        onNavigateToHelpSupport = { findNavigationRouter()?.navigateToHelpSupport() },
                        onNavigateToSettings = { findNavigationRouter()?.navigateToSettings() },
                        onNavigateToSubscriptions = { findNavigationRouter()?.navigateToSubscriptions() }
                    )
                }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.signOutState.collect { state ->
                    when (state) {
                        is ProfileViewModel.SignOutState.Loading -> {
                            // Can show loading indicator if desired
                        }
                        is ProfileViewModel.SignOutState.Success -> {
                            findNavigationRouter()?.navigateToLogin()
                        }
                        is ProfileViewModel.SignOutState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                        is ProfileViewModel.SignOutState.Idle -> {
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
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToHelpSupport: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSubscriptions: () -> Unit
) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val signOutState by viewModel.signOutState.collectAsState()
    val isSigningOut = signOutState is ProfileViewModel.SignOutState.Loading
    val scrollState = rememberScrollState()

    var showSignOutConfirmDialog by remember { mutableStateOf(false) }

    // Show the shimmer until the profile data is actually loaded. This is tied
    // to real load state (not a fixed timer), so it behaves the same on every
    // visit and never reveals a half-rendered screen (e.g. the Sign Out button
    // floating at the top) before the profile content is ready.
    val showShimmer = userProfile == null

    val appVersion = remember {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "version ${pInfo.versionName}"
        } catch (_: Exception) {
            "version 1.0"
        }
    }

    val stopRed = Color(0xFFEF5350)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Crossfade(
                targetState = showShimmer,
                animationSpec = tween(durationMillis = 350),
                modifier = Modifier.weight(1f),
                label = "profileContent"
            ) { shimmering ->
            if (shimmering) {
                ShimmerProfile()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    userProfile?.let { profile ->
                        val name = profile["name"] as? String ?: "User"
                        val email = profile["email"] as? String ?: ""
                        val photoUrl = profile["photoUrl"] as? String

                        // Minimized space between title and profile pic
                        Spacer(modifier = Modifier.height(4.dp))

                        // Profile Image wrapper with Glide
                        val avatarDrawable = remember(name) {
                            com.meetloggerv2.core.util.AvatarGenerator.getAvatar(context, name)
                        }

                        Card(
                            shape = CircleShape,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    ImageView(ctx).apply {
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { imageView ->
                                    Glide.with(context)
                                        .load(photoUrl)
                                        .placeholder(avatarDrawable)
                                        .error(avatarDrawable)
                                        .fallback(avatarDrawable)
                                        .circleCrop()
                                        .into(imageView)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = email,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Profile Options
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProfileOptionItem(
                                icon = Icons.Default.Settings,
                                title = "Settings",
                                onClick = { if (!isSigningOut) onNavigateToSettings() }
                            )
                            ProfileOptionItem(
                                icon = Icons.Default.CardMembership,
                                title = "Subscriptions",
                                onClick = { if (!isSigningOut) onNavigateToSubscriptions() }
                            )
                            ProfileOptionItem(
                                icon = Icons.Default.Security,
                                title = "Privacy & security",
                                onClick = { if (!isSigningOut) onNavigateToLegal("policy") }
                            )
                            ProfileOptionItem(
                                icon = Icons.AutoMirrored.Filled.Help,
                                title = "Help & support",
                                onClick = { if (!isSigningOut) onNavigateToHelpSupport() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Premium Sign Out Button
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .pressScaleClick(enabled = !isSigningOut) {
                                showSignOutConfirmDialog = true
                            },
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, stopRed.copy(alpha = 0.2f)),
                        colors = CardDefaults.cardColors(
                            containerColor = stopRed.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSigningOut) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = stopRed
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign Out",
                                    tint = stopRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Sign Out",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = stopRed
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
            }

            // Version text stuck to the absolute bottom - fades in with the content
            androidx.compose.animation.AnimatedVisibility(
                visible = !showShimmer,
                enter = fadeIn(animationSpec = tween(350)),
                exit = fadeOut(animationSpec = tween(150))
            ) {
                Text(
                    text = appVersion,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    if (showSignOutConfirmDialog) {
        Dialog(onDismissRequest = { showSignOutConfirmDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Confirmation",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Are you sure you want to sign out? You will need to log back in to access your logs.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val cancelInteractionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = { showSignOutConfirmDialog = false },
                            interactionSource = cancelInteractionSource,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScale(cancelInteractionSource),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }

                        val signOutInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            onClick = {
                                showSignOutConfirmDialog = false
                                viewModel.signOut()
                            },
                            interactionSource = signOutInteractionSource,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScale(signOutInteractionSource),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(colors = listOf(Color(0xFFEF5350), Color(0xFFD32F2F)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Sign Out", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileOptionItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Square Icon Background
            val isDark = MaterialTheme.colorScheme.onSurface == Color.White
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
