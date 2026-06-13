package com.example.meetloggerv2.ui.home.fragment

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
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.navigation.findNavigationRouter
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.theme.GradientEnd
import com.example.meetloggerv2.core.theme.GradientStart
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.ShimmerItem
import com.example.meetloggerv2.core.theme.pressScale
import com.example.meetloggerv2.core.theme.pressScaleClick
import com.example.meetloggerv2.ui.audio.fragment.RecordAudioBottomsheetFragment
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.ui.audio.fragment.UploadAudioBottomsheetFragment
import com.example.meetloggerv2.ui.home.viewmodel.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
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
                                Toast.makeText(requireContext(), "Free plan limit: You can only have up to 7 recordings. Please upgrade to Pro.", Toast.LENGTH_LONG).show()
                            } else {
                                RecordAudioBottomsheetFragment().show(parentFragmentManager, "RecordAudioSheet")
                            }
                        },
                        onShowUploadSheet = {
                            val subscription = authSession.currentUserSubscription()
                            val fileCount = viewModel.files.value.size
                            if (subscription == "free" && fileCount >= 7) {
                                Toast.makeText(requireContext(), "Free plan limit: You can only have up to 7 recordings. Please upgrade to Pro.", Toast.LENGTH_LONG).show()
                            } else {
                                UploadAudioBottomsheetFragment().show(parentFragmentManager, "UploadAudioSheet")
                            }
                        }
                    )
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
    val files by viewModel.files.collectAsState(initial = emptyList())
    val userProfile by viewModel.userProfile.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAudioOptions by remember { mutableStateOf(false) }

    // Shimmer states to avoid flicker and support smooth updates
    val previousFiles = remember { mutableStateOf<List<Triple<String, String, com.google.firebase.Timestamp>>?>(null) }
    var showUpdateShimmer by remember { mutableStateOf(false) }

    LaunchedEffect(files) {
        if (previousFiles.value != null && previousFiles.value != files) {
            showUpdateShimmer = true
            delay(300)
            showUpdateShimmer = false
        }
        previousFiles.value = files
    }

    var forceShimmer by remember { mutableStateOf(files.isEmpty()) }
    LaunchedEffect(Unit) {
        if (files.isEmpty()) {
            delay(300)
            forceShimmer = false
        } else {
            forceShimmer = false
        }
    }

    val showInitialShimmer = files.isEmpty() && (isRefreshing || forceShimmer)

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
                                com.example.meetloggerv2.core.util.AvatarGenerator.getAvatar(context, username)
                            }

                            Card(
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .pressScaleClick { onProfileClick() },
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
                        }
                    }

                    // Search View - Hidden during initial premium shimmer
                    if (files.isNotEmpty() && !showInitialShimmer) {
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

                    // File List or Placeholder - Use shimmer during updates to avoid flicker
                    if (showInitialShimmer || showUpdateShimmer) {
                        Column {
                            repeat(6) {
                                ShimmerItem()
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    } else if (files.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(id = R.string.empty_home_search_message),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
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

            // Sleek Custom Floating Bottom Navigation Bar
            CustomFloatingBottomBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
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
        modifier = modifier
            .fillMaxWidth(0.9f)
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
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
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1.0f,
        animationSpec = tween(durationMillis = 250),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight() // Fill full height for vertical centering
            .pressScaleClick(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Extra top spacing as requested
        Spacer(modifier = Modifier.height(6.dp))
        
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color,
            maxLines = 1
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        // Smooth scaling indicator dot
        Box(
            modifier = Modifier
                .size(if (selected) 4.dp else 0.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        
        // Bottom spacer to ensure the dot isn't touching the edge
        Spacer(modifier = Modifier.height(4.dp))
    }
}
