package com.meetloggerv2.ui.home.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.meetloggerv2.core.theme.AppStrings
import com.meetloggerv2.core.navigation.findNavigationRouter
import com.meetloggerv2.core.network.NetworkMonitor
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.theme.ShimmerItem
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.ui.audio.fragment.RecordAudioBottomsheetFragment
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.ui.audio.fragment.UploadAudioBottomsheetFragment
import com.meetloggerv2.ui.audio.util.PlanLimitDialog
import com.meetloggerv2.ui.home.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class HomeFragment : Fragment() {

    @Inject lateinit var authSession: AuthSession
    private val viewModel: HomeViewModel by viewModels()
    private lateinit var networkMonitor: NetworkMonitor
    private val isOnlineState = mutableStateOf(true)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        networkMonitor = NetworkMonitor(requireContext())
        setupObservers()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    var showLimitDialog by remember { mutableStateOf(false) }

                    HomeScreenContent(
                        viewModel = viewModel,
                        isOnline = isOnlineState.value,
                        onProfileClick = { findNavigationRouter()?.navigateToProfile() },
                        onNavigateToAudio = { findNavigationRouter()?.navigateToAudioList() },
                        onNavigateToReport = { findNavigationRouter()?.navigateToReportList() },
                        onOpenFileDetails = { name -> findNavigationRouter()?.navigateToFileDetails(name) },
                        onRefresh = { viewModel.fetchFiles() },
                        onShowRecordSheet = {
                            val subscription = authSession.currentUserSubscription()
                            val fileCount = viewModel.files.value.size
                            if (subscription == "free" && fileCount >= 7) {
                                showLimitDialog = true
                            } else {
                                RecordAudioBottomsheetFragment().show(parentFragmentManager, "RecordAudioSheet")
                            }
                        },
                        onShowUploadSheet = {
                            val subscription = authSession.currentUserSubscription()
                            val fileCount = viewModel.files.value.size
                            if (subscription == "free" && fileCount >= 7) {
                                showLimitDialog = true
                            } else {
                                UploadAudioBottomsheetFragment().show(parentFragmentManager, "UploadAudioSheet")
                            }
                        }
                    )

                    if (showLimitDialog) {
                        PlanLimitDialog(
                            onDismiss = { showLimitDialog = false },
                            onUpgrade = {
                                showLimitDialog = false
                                findNavigationRouter()?.navigateToSubscriptions()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.error.collect { err ->
                    err?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            isOnlineState.value = isOnline
            if (isOnline) {
                viewModel.fetchFiles()
            }
        }

        checkNotificationAndMicrophonePermissions()
        viewModel.loadUserProfile()
    }

    private fun checkNotificationAndMicrophonePermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO)
        }
        if (perms.isNotEmpty()) {
            requestPermissions(perms.toTypedArray(), 1001)
        }
    }
}

@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel,
    isOnline: Boolean,
    onProfileClick: () -> Unit,
    onNavigateToAudio: () -> Unit,
    onNavigateToReport: () -> Unit,
    onOpenFileDetails: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowRecordSheet: () -> Unit,
    onShowUploadSheet: () -> Unit
) {
    val context = LocalContext.current
    // No `initial` override: files is a StateFlow that already holds its current
    // value, so on back-press the cached list shows on the first frame instead of
    // flashing empty (which would replay the shimmer/crossfade — the "hit" feel).
    val files by viewModel.files.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isInitialLoading by viewModel.isInitialLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAudioOptions by remember { mutableStateOf(false) }

    // Show the shimmer whenever the list is empty AND an update is in flight
    // (first load or refresh). This guarantees we never flash the empty-state
    // placeholder during an update — the empty state is shown only once it's
    // *confirmed* empty, i.e. nothing is loading.
    val isUpdating = isInitialLoading || isRefreshing
    val showShimmer = files.isEmpty() && isUpdating
    val showEmptyState = files.isEmpty() && !isUpdating

    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isEmpty()) files
        else {
            val q = searchQuery.lowercase(Locale.getDefault())
            files.filter { it.first.lowercase(Locale.getDefault()).contains(q) }
        }
    }

    val username = remember(userProfile) {
        userProfile?.get("name") as? String ?: "User"
    }
    val photoUrl = remember(userProfile) {
        userProfile?.get("photoUrl") as? String
    }

    androidx.activity.compose.BackHandler(enabled = showAudioOptions) {
        showAudioOptions = false
    }

    if (!isOnline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            EmptyStatePlaceholder(
                icon = Icons.Default.WifiOff,
                title = "No Internet Connection",
                subtitle = "Check your network settings and try again"
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Scaffold(
                containerColor = Color.Transparent
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 24.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MeetLogger",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {

                            // Profile Pic
                            val avatarDrawable = remember(username) {
                                com.meetloggerv2.core.util.AvatarGenerator.getAvatar(context, username)
                            }

                            Card(
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .pressScaleClick { onProfileClick() },
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        }
                    }

                    // Search View - Hidden during initial premium shimmer
                    if (files.isNotEmpty() && !showShimmer) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search documents...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            ),
                            visualTransformation = VisualTransformation.None,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // File List or Placeholder - crossfade so content fades in
                    // smoothly instead of snapping when the list resolves.
                    Crossfade(
                        targetState = showShimmer,
                        animationSpec = tween(durationMillis = 350),
                        modifier = Modifier.weight(1f),
                        label = "homeContent"
                    ) { shimmering ->
                    if (shimmering) {
                        Column {
                            repeat(6) {
                                ShimmerItem()
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    } else if (showEmptyState) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStatePlaceholder(
                                icon = Icons.Default.HourglassEmpty,
                                title = "No activity yet",
                                subtitle = "Status of recorded or uploaded files will appear here"
                            )
                        }
                    } else if (filteredFiles.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = AppStrings.EMPTY_HOME_SEARCH_MESSAGE,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp)
                        ) {
                            items(filteredFiles, key = { it.first }) { item ->
                                val name = item.first
                                val status = item.second

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .pressScaleClick { /* No action, status indication only */ }
                                        .clip(RoundedCornerShape(16.dp))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AudioFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(34.dp)
                                        )

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = name.substringBeforeLast("."),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val displayStatus = when (status.lowercase(Locale.ROOT)) {
                                                "processed" -> "Processed"
                                                "processing" -> "Processing..."
                                                "saved" -> "File Uploaded"
                                                else -> status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                                            }
                                            Text(
                                                text = displayStatus,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        val statusIcon = when (status.lowercase(Locale.ROOT)) {
                                            "processed" -> Icons.Default.CheckCircle
                                            "processing" -> Icons.Default.Autorenew
                                            "saved" -> Icons.Default.CloudDone
                                            else -> Icons.Default.Cloud
                                        }
                                        val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                                        val statusColor = when (status.lowercase(Locale.ROOT)) {
                                            "processed" -> MaterialTheme.colorScheme.secondary
                                            "processing" -> MaterialTheme.colorScheme.primary
                                            "saved" -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(if (isDark) statusColor else statusColor.copy(alpha = 0.12f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (status.lowercase(Locale.ROOT) == "processing") {
                                                val infiniteTransition = rememberInfiniteTransition(label = "rotation")
                                                val rotationAngle by infiniteTransition.animateFloat(
                                                    initialValue = 0f,
                                                    targetValue = 360f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = tween(1500, easing = LinearEasing),
                                                        repeatMode = RepeatMode.Restart
                                                    ),
                                                    label = "angle"
                                                )
                                                Icon(
                                                    imageVector = statusIcon,
                                                    contentDescription = status,
                                                    tint = if (isDark) Color.White else statusColor,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .rotate(rotationAngle)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = statusIcon,
                                                    contentDescription = status,
                                                    tint = if (isDark) Color.White else statusColor,
                                                    modifier = Modifier.size(20.dp)
                                                )
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

            // Legacy bottom navigation bar stuck to the bottom edge with a top
            // highlighter line indicating the active section.
            CustomFloatingBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter),
                onNavigateToReport = onNavigateToReport,
                onNavigateToAudio = onNavigateToAudio
            )

            // Audio Action FAB Button (Premium Rounded Card with Horizontal Gradient)
            val openAlpha by animateFloatAsState(targetValue = if (showAudioOptions) 1f else 0f, label = "openAlpha")
            val closedAlpha by animateFloatAsState(targetValue = if (showAudioOptions) 0f else 1f, label = "closedAlpha")
            val fabWidth by animateDpAsState(targetValue = if (showAudioOptions) 50.dp else 110.dp, label = "fabWidth")
            val fabRotation by animateFloatAsState(targetValue = if (showAudioOptions) 180f else 0f, label = "fabRotation")

            // Audio Action FAB Button (Premium Rounded Card with Horizontal Gradient)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 96.dp, end = 24.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .height(50.dp)
                        .width(fabWidth)
                        .pressScaleClick { showAudioOptions = !showAudioOptions },
                    shape = RoundedCornerShape(25.dp),
                    shadowElevation = 6.dp,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (showAudioOptions) {
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.surface,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(GradientStart, GradientEnd)
                                    )
                                }
                            )
                            .border(
                                width = if (showAudioOptions) 1.dp else 0.dp,
                                color = if (showAudioOptions) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(25.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Closed Content
                            Row(
                                modifier = Modifier
                                    .alpha(closedAlpha)
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio Action",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (!showAudioOptions) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Audio",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            // Open Content
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Options",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .size(24.dp)
                                    .alpha(openAlpha)
                                    .rotate(fabRotation)
                            )
                        }
                    }
                }
            }

            // Options Overlay
            AnimatedVisibility(
                visible = showAudioOptions,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable { showAudioOptions = false }
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 162.dp, end = 24.dp)
                            .clickable(enabled = false) {}, // Intercept click
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Record Live Audio option row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .pressScaleClick {
                                    showAudioOptions = false
                                    onShowRecordSheet()
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Record Audio",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(GradientStart, GradientEnd)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Upload Audio File option row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier
                                .pressScaleClick {
                                    showAudioOptions = false
                                    onShowUploadSheet()
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Upload Audio",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(GradientStart, GradientEnd)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStatePlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun CustomFloatingBottomBar(
    modifier: Modifier = Modifier,
    onNavigateToReport: () -> Unit,
    onNavigateToAudio: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle divider separating the bar from the content above it.
            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = true,
                    onClick = {}
                )
                BottomNavItem(
                    icon = Icons.Default.Description,
                    label = "Files",
                    selected = false,
                    onClick = onNavigateToReport
                )
                BottomNavItem(
                    icon = Icons.Default.GraphicEq,
                    label = "Audio",
                    selected = false,
                    onClick = onNavigateToAudio
                )
            }
        }
    }
}

@Composable
fun RowScope.BottomNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        label = "color"
    )
    // Animated fraction of the item width covered by the top highlighter line.
    val indicatorFraction by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "indicatorFraction"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .pressScaleClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Top highlighter line spanning the section width (modern style).
        Box(
            modifier = Modifier
                .fillMaxWidth(indicatorFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Spacer(modifier = Modifier.weight(1f))

        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(28.dp)
        )

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1
        )

        Spacer(modifier = Modifier.weight(1f))
    }
}
