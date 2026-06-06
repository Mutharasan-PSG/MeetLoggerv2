package com.example.meetloggerv2.ui.audio.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.media.AudioPlayerManager
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.network.NetworkUtil
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.pressScale
import com.example.meetloggerv2.core.theme.pressScaleClick
import com.example.meetloggerv2.core.ui.components.PremiumAudioPlayer
import com.example.meetloggerv2.ui.audio.util.AudioProcessingDialog
import com.example.meetloggerv2.ui.audio.viewmodel.AudioListViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@AndroidEntryPoint
class AudioListFragment : Fragment() {

    private val viewModel: AudioListViewModel by viewModels()
    private val audioPlayer = AudioPlayerManager()
    private lateinit var networkMonitor: NetworkMonitor

    private val isOnlineState = mutableStateOf(true)
    private var showSpeakerSelectionState = mutableStateOf<String?>(null)

    // Progress/Overlay states
    private var isProcessingState = mutableStateOf(false)
    private var progressMessageState = mutableStateOf("Loading...")

    // Playback state variables mapped to Compose
    private var showMiniPlayerState = mutableStateOf(false)
    private var isPlayingState = mutableStateOf(false)
    private var currentAudioNameState = mutableStateOf("")
    private var playbackProgressState = mutableStateOf(0)
    private var playbackMaxState = mutableStateOf(100)
    private var currentTimeStrState = mutableStateOf("00:00")
    private var totalTimeStrState = mutableStateOf("00:00")

    private var currentAudioIndex = -1

    // Pending file action tags
    private var pendingDownloadFileName: String? = null
    private var pendingPlayFileName: String? = null
    private var pendingShareFileName: String? = null
    private var pendingProcessFileName: String? = null
    private var pendingProcessSpeakers: List<String>? = null
    private var pendingProcessFollowUp: String? = null
    private var pendingDownloadUri: Uri? = null

    private val downloadFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            result.data?.data?.let { uri ->
                saveAudioToUri(pendingDownloadFileName ?: "", uri)
            }
        }
    }

    private var autoPlayNextState = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        networkMonitor = NetworkMonitor(requireContext())
        setupObservers()
        checkInternetAndLoad()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AudioListScreen(
                            viewModel = viewModel,
                            isOnline = isOnlineState.value,
                            isProcessing = isProcessingState.value,
                            progressMessage = progressMessageState.value,
                            showMiniPlayer = showMiniPlayerState.value,
                            isPlaying = isPlayingState.value,
                            currentAudioName = currentAudioNameState.value,
                            playbackProgress = playbackProgressState.value,
                            playbackMax = playbackMaxState.value,
                            currentTimeStr = currentTimeStrState.value,
                            totalTimeStr = totalTimeStrState.value,
                            autoPlayNext = autoPlayNextState.value,
                            onBack = { parentFragmentManager.popBackStack() },
                            onToggleAutoPlay = { autoPlayNextState.value = !autoPlayNextState.value },
                            onPlayItem = { name, index ->
                                currentAudioIndex = index
                                downloadAndPlayAudio(name)
                            },
                            onPlayNext = { playNextAudio() },
                            onPlayPrev = { playPreviousAudio() },
                            onRewind = { audioPlayer.rewind() },
                            onForward = { audioPlayer.fastForward() },
                            onPlayPauseToggle = {
                                if (audioPlayer.isReady()) {
                                    isPlayingState.value = audioPlayer.togglePlayPause()
                                }
                            },
                            onStopPlayback = {
                                audioPlayer.stop()
                                isPlayingState.value = false
                                showMiniPlayerState.value = false
                            },
                            onSeekPlayback = { progress ->
                                audioPlayer.seekTo(progress)
                                playbackProgressState.value = progress
                            },
                            onDownloadItem = { name ->
                                pendingDownloadFileName = name
                                downloadFileLauncher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                    type = "audio/mpeg"
                                    putExtra(Intent.EXTRA_TITLE, "$name.mp3")
                                })
                            },
                            onShareItem = { name ->
                                val temp = File(requireContext().cacheDir, "$name.mp3")
                                showProgress("Loading...")
                                pendingShareFileName = name
                                viewModel.downloadAudioFile("$name.mp3", temp)
                            },
                            onSummarizeItem = { name ->
                                showSpeakerSelectionState.value = name
                            }
                        )

                        showSpeakerSelectionState.value?.let { name ->
                            val userFiles by viewModel.userFilesState.collectAsState(emptyList())
                            LaunchedEffect(Unit) {
                                viewModel.fetchUserFiles()
                            }
                            AudioProcessingDialog(
                                userFiles = userFiles,
                                onDismiss = { showSpeakerSelectionState.value = null },
                                onProcessingConfirmed = { speakers, followUp ->
                                    showSpeakerSelectionState.value = null
                                    startAudioProcessing(name, speakers, followUp)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun showProgress(msg: String) {
        progressMessageState.value = msg
        isProcessingState.value = true
    }

    private fun hideProgress() {
        isProcessingState.value = false
    }

    private fun checkInternetAndLoad() {
        if (NetworkUtil.isNetworkAvailable(requireContext())) {
            isOnlineState.value = true
            viewModel.fetchAudioFiles()
        } else {
            isOnlineState.value = false
        }
    }

    private fun downloadAndPlayAudio(fileName: String) {
        if (!NetworkUtil.isNetworkAvailable(requireContext())) return
        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$fileName.mp3")
        if (localFile.exists()) {
            startPlayback(localFile.absolutePath, fileName)
        } else {
            showProgress("Downloading...")
            pendingPlayFileName = fileName
            viewModel.downloadAudioFile("$fileName.mp3", localFile)
        }
    }

    private fun startPlayback(path: String, name: String) {
        currentAudioNameState.value = name
        showMiniPlayerState.value = true
        isPlayingState.value = true
        audioPlayer.play(path, {
            if (autoPlayNextState.value) {
                playNextAudio()
            } else {
                isPlayingState.value = false
                playbackProgressState.value = 0
                currentTimeStrState.value = "00:00"
            }
        }) { curr, dur ->
            playbackProgressState.value = curr
            playbackMaxState.value = dur
            currentTimeStrState.value = audioPlayer.formatTime(curr)
            totalTimeStrState.value = audioPlayer.formatTime(dur)
        }
    }

    private fun playNextAudio() {
        val filesList = viewModel.filteredAudioFiles.value
        if (filesList.isNotEmpty()) {
            currentAudioIndex = (currentAudioIndex + 1) % filesList.size
            downloadAndPlayAudio(filesList[currentAudioIndex])
        }
    }

    private fun playPreviousAudio() {
        val filesList = viewModel.filteredAudioFiles.value
        if (filesList.isNotEmpty()) {
            currentAudioIndex = if (currentAudioIndex > 0) currentAudioIndex - 1 else filesList.size - 1
            downloadAndPlayAudio(filesList[currentAudioIndex])
        }
    }

    private fun startAudioProcessing(name: String, speakers: List<String>, followUp: String) {
        val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), "$name.mp3")
        if (localFile.exists()) {
            showProgress("Processing...")
            processAudioWithDownloadUrl(localFile, name, speakers, followUp)
        } else {
            showProgress("Downloading...")
            pendingProcessFileName = name
            pendingProcessSpeakers = speakers
            pendingProcessFollowUp = followUp
            viewModel.downloadAudioFile("$name.mp3", localFile)
        }
    }

    private fun processAudioWithDownloadUrl(localFile: File, name: String, speakers: List<String>, followUp: String) {
        pendingProcessFileName = name
        pendingProcessSpeakers = speakers
        pendingProcessFollowUp = followUp
        viewModel.getAudioDownloadUrl("$name.mp3")
    }

    private fun saveAudioToUri(name: String, uri: Uri) {
        showProgress("Downloading...")
        pendingDownloadUri = uri
        pendingDownloadFileName = name
        viewModel.downloadAudioBytes("$name.mp3")
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is AudioListViewModel.AudioUiState.Loading -> {
                                showProgress(state.message)
                            }
                            is AudioListViewModel.AudioUiState.Idle -> {
                                hideProgress()
                            }
                            is AudioListViewModel.AudioUiState.Error -> {
                                hideProgress()
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            }
                            is AudioListViewModel.AudioUiState.Processed -> {
                                hideProgress()
                                Toast.makeText(context, "Ready!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.audioEvent.collect { event ->
                        val content = event.getContentIfNotHandled() ?: return@collect
                        when (content) {
                            is AudioListViewModel.AudioEvent.DownloadFileSuccess -> {
                                hideProgress()
                                val fileName = content.fileName.substringBeforeLast(".mp3")
                                if (pendingPlayFileName == fileName) {
                                    pendingPlayFileName = null
                                    startPlayback(content.localFile.absolutePath, fileName)
                                }
                                if (pendingShareFileName == fileName) {
                                    pendingShareFileName = null
                                    val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", content.localFile)
                                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/mpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }, "Share"))
                                }
                                if (pendingProcessFileName == fileName) {
                                    val speakers = pendingProcessSpeakers ?: emptyList()
                                    val followUp = pendingProcessFollowUp ?: ""
                                    processAudioWithDownloadUrl(content.localFile, fileName, speakers, followUp)
                                }
                            }
                            is AudioListViewModel.AudioEvent.DownloadFileError -> {
                                hideProgress()
                                pendingPlayFileName = null
                                pendingShareFileName = null
                                pendingProcessFileName = null
                                Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                            }
                            is AudioListViewModel.AudioEvent.DownloadUrlSuccess -> {
                                val speakers = pendingProcessSpeakers ?: emptyList()
                                val followUp = pendingProcessFollowUp ?: ""
                                val fileName = content.fileName
                                if (pendingProcessFileName == fileName.substringBeforeLast(".mp3")) {
                                    pendingProcessFileName = null
                                    pendingProcessSpeakers = null
                                    pendingProcessFollowUp = null
                                    val localFile = File(requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC), fileName)
                                    viewModel.processAudio(localFile, speakers, followUp, fileName, content.url)
                                }
                            }
                            is AudioListViewModel.AudioEvent.DownloadUrlError -> {
                                hideProgress()
                                pendingProcessFileName = null
                                pendingProcessSpeakers = null
                                pendingProcessFollowUp = null
                                Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                            }
                            is AudioListViewModel.AudioEvent.DownloadBytesSuccess -> {
                                hideProgress()
                                val uri = pendingDownloadUri
                                if (uri != null) {
                                    pendingDownloadUri = null
                                    pendingDownloadFileName = null
                                    requireContext().contentResolver.openOutputStream(uri)?.use { it.write(content.bytes) }
                                    Toast.makeText(requireContext(), "Downloaded successfully", Toast.LENGTH_SHORT).show()
                                }
                            }
                            is AudioListViewModel.AudioEvent.DownloadBytesError -> {
                                hideProgress()
                                pendingDownloadUri = null
                                pendingDownloadFileName = null
                                Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            isOnlineState.value = isOnline
            if (isOnline) {
                viewModel.fetchAudioFiles()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioListScreen(
    viewModel: AudioListViewModel,
    isOnline: Boolean,
    isProcessing: Boolean,
    progressMessage: String,
    showMiniPlayer: Boolean,
    isPlaying: Boolean,
    currentAudioName: String,
    playbackProgress: Int,
    playbackMax: Int,
    currentTimeStr: String,
    totalTimeStr: String,
    autoPlayNext: Boolean,
    onBack: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onPlayItem: (String, Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekPlayback: (Int) -> Unit,
    onDownloadItem: (String) -> Unit,
    onShareItem: (String) -> Unit,
    onSummarizeItem: (String) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is AudioListViewModel.AudioUiState.Loading

    val files by viewModel.filteredAudioFiles.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<String>() }

    var renamingPosition by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    var renameTextValue by remember { mutableStateOf(TextFieldValue("")) }

    var expandedMenuPosition by remember { mutableStateOf(-1) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Intercept back presses for modes
    androidx.activity.compose.BackHandler(enabled = isDeleteMode || renamingPosition != -1) {
        if (renamingPosition != -1) {
            renamingPosition = -1
        } else if (isDeleteMode) {
            isDeleteMode = false
            selectedFiles.clear()
        }
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
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Audio Recordings",
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
                                    .pressScaleClick(enabled = !isLoading) {
                                        if (isDeleteMode) {
                                            isDeleteMode = false
                                            selectedFiles.clear()
                                        } else if (renamingPosition != -1) {
                                            renamingPosition = -1
                                        } else {
                                            onBack()
                                        }
                                    },
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
                        actions = {
                            if (renamingPosition != -1) {
                                IconButton(
                                    onClick = {
                                        val item = files.getOrNull(renamingPosition)
                                        if (item != null) {
                                            val newName = renameText.trim()
                                            if (newName.isNotEmpty() && newName != item) {
                                                viewModel.renameAudioFile(item, newName)
                                            } else {
                                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        renamingPosition = -1
                                    },
                                    enabled = !isLoading,
                                    modifier = Modifier.pressScale().padding(end = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Confirm", tint = MaterialTheme.colorScheme.secondary)
                                }
                            } else if (isDeleteMode) {
                                if (selectedFiles.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = true },
                                        enabled = !isLoading,
                                        modifier = Modifier.pressScaleClick { showDeleteConfirmDialog = true }.padding(end = 8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
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
                        .padding(horizontal = 24.dp)
                ) {
                    // Search Bar
                    if (files.isNotEmpty() || searchQuery.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.setQuery(it)
                            },
                            placeholder = { Text("Search recordings...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    val clearInteractionSource = remember { MutableInteractionSource() }
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            viewModel.setQuery("")
                                        },
                                        interactionSource = clearInteractionSource,
                                        modifier = Modifier.pressScale(clearInteractionSource)
                                    ) {
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

                    if (isDeleteMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedFiles.size == files.size && files.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    selectedFiles.clear()
                                    if (checked) {
                                        files.forEach { selectedFiles.add(it) }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Select All",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (files.isEmpty() && !isProcessing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (searchQuery.isEmpty()) {
                                EmptyStatePlaceholder(
                                    icon = Icons.Default.Mic,
                                    title = "No recordings yet",
                                    subtitle = "Recorded or uploaded audio files will appear here"
                                )
                            } else {
                                Text(
                                    text = "No matching recordings found",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
                            itemsIndexed(files) { index, item ->
                                val isSelected = selectedFiles.contains(item)
                                val isRenamingThis = renamingPosition == index

                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                if (isDeleteMode) {
                                                    if (isSelected) selectedFiles.remove(item) else selectedFiles.add(item)
                                                } else if (renamingPosition == -1) {
                                                    onPlayItem(item, index)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isDeleteMode && renamingPosition == -1) {
                                                    isDeleteMode = true
                                                    selectedFiles.add(item)
                                                }
                                            }
                                        )
                                        .pressScale(interactionSource)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                                        if (isDeleteMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null // Click is handled by parent Card
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                        } else {
                                            Surface(
                                                modifier = Modifier.size(44.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.AudioFile,
                                                        contentDescription = null,
                                                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            if (isRenamingThis) {
                                                val focusRequester = remember { FocusRequester() }
                                                LaunchedEffect(Unit) {
                                                    focusRequester.requestFocus()
                                                }
                                                TextField(
                                                    value = renameTextValue,
                                                    onValueChange = {
                                                        renameTextValue = it
                                                        renameText = it.text
                                                    },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Password,
                                                        imeAction = ImeAction.Done,
                                                        autoCorrectEnabled = false
                                                    ),
                                                    visualTransformation = VisualTransformation.None,
                                                    keyboardActions = KeyboardActions(
                                                        onDone = {
                                                            val newName = renameText.trim()
                                                            if (newName.isNotEmpty() && newName != item) {
                                                                viewModel.renameAudioFile(item, newName)
                                                            } else {
                                                                Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                                            }
                                                            renamingPosition = -1
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .focusRequester(focusRequester),
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        disabledIndicatorColor = Color.Transparent,
                                                        cursorColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    textStyle = LocalTextStyle.current.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            } else {
                                                Text(
                                                    text = item,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        if (!isDeleteMode && renamingPosition == -1) {
                                            Box {
                                                IconButton(
                                                    onClick = { expandedMenuPosition = index },
                                                    modifier = Modifier.pressScaleClick { expandedMenuPosition = index }
                                                ) {
                                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                                                }

                                                MaterialTheme(
                                                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(28.dp))
                                                ) {
                                                    DropdownMenu(
                                                        expanded = expandedMenuPosition == index,
                                                        onDismissRequest = { expandedMenuPosition = -1 },
                                                        modifier = Modifier
                                                            .widthIn(min = 150.dp, max = 220.dp)
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .border(
                                                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                                                RoundedCornerShape(28.dp)
                                                            )
                                                    ) {
                                                        val isDarkMenu = MaterialTheme.colorScheme.onSurface == Color.White
                                                        val menuIconTint = if (isDarkMenu) Color.White else MaterialTheme.colorScheme.primary
                                                        val menuSummarizeTint = if (isDarkMenu) Color.White else MaterialTheme.colorScheme.secondary

                                                        DropdownMenuItem(
                                                            text = { Text("Rename", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuIconTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                renameTextValue = TextFieldValue(text = item, selection = TextRange(item.length))
                                                                renameText = item
                                                                renamingPosition = index
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                renameTextValue = TextFieldValue(text = item, selection = TextRange(item.length))
                                                                renameText = item
                                                                renamingPosition = index
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Download", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuIconTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                onDownloadItem(item)
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                onDownloadItem(item)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuIconTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                onShareItem(item)
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                onShareItem(item)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Summarize", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuSummarizeTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                onSummarizeItem(item)
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                onSummarizeItem(item)
                                                            }
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
            }

            // Draggable Mini Player
            if (showMiniPlayer) {
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                            }
                        }
                        .fillMaxWidth(0.9f)
                ) {
                    PremiumAudioPlayer(
                        title = currentAudioName,
                        isPlaying = isPlaying,
                        playbackProgress = playbackProgress,
                        playbackMax = playbackMax,
                        currentTimeStr = currentTimeStr,
                        totalTimeStr = totalTimeStr,
                        onPlayPause = onPlayPauseToggle,
                        onSeek = onSeekPlayback,
                        onRewind = onRewind,
                        onForward = onForward,
                        onSkipNext = onPlayNext,
                        onSkipPrev = onPlayPrev,
                        onStop = onStopPlayback,
                        autoPlayNext = autoPlayNext,
                        onToggleAutoPlay = onToggleAutoPlay,
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }
            }

            if (showDeleteConfirmDialog) {
                Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
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
                                text = "Confirm Delete",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(id = R.string.msg_delete_selected_audio),
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
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScaleClick { showDeleteConfirmDialog = false },
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    color = Color.Transparent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScaleClick {
                                            viewModel.deleteAudioFiles(selectedFiles.toList())
                                            isDeleteMode = false
                                            selectedFiles.clear()
                                            showDeleteConfirmDialog = false
                                        },
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.Transparent
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Brush.linearGradient(colors = listOf(Color(0xFFEF5350), Color(0xFFD32F2F)))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(stringResource(id = R.string.dialog_delete), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = progressMessage,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
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
        val isDark = MaterialTheme.colorScheme.onSurface == Color.White
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
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
