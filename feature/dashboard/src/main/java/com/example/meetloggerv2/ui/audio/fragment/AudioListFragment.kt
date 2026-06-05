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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                            onBack = { parentFragmentManager.popBackStack() },
                            onPlayItem = { name, index ->
                                currentAudioIndex = index
                                downloadAndPlayAudio(name)
                            },
                            onPlayNext = { playNextAudio() },
                            onPlayPrev = { playPreviousAudio() },
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
        audioPlayer.play(path, { playNextAudio() }) { curr, dur ->
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
    onBack: () -> Unit,
    onPlayItem: (String, Int) -> Unit,
    onPlayNext: () -> Unit,
    onPlayPrev: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekPlayback: (Int) -> Unit,
    onDownloadItem: (String) -> Unit,
    onShareItem: (String) -> Unit,
    onSummarizeItem: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val files by viewModel.filteredAudioFiles.collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<Int>() }

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
            selectedItems.clear()
        }
    }

    if (!isOnline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.no_internet),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Internet Connection",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.no_internet_message),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
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
                                fontSize = 20.sp
                            )
                        },
                        navigationIcon = {
                            val navInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = {
                                    if (isDeleteMode) {
                                        isDeleteMode = false
                                        selectedItems.clear()
                                    } else if (renamingPosition != -1) {
                                        renamingPosition = -1
                                    } else {
                                        onBack()
                                    }
                                },
                                interactionSource = navInteractionSource,
                                modifier = Modifier.pressScale(navInteractionSource)
                            ) {
                                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (renamingPosition != -1) {
                                val confirmInteractionSource = remember { MutableInteractionSource() }
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
                                    interactionSource = confirmInteractionSource,
                                    modifier = Modifier.pressScale(confirmInteractionSource).padding(end = 8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Check, contentDescription = "Confirm", tint = MaterialTheme.colorScheme.secondary)
                                }
                            } else if (isDeleteMode) {
                                if (selectedItems.isNotEmpty()) {
                                    val deleteInteractionSource = remember { MutableInteractionSource() }
                                    IconButton(
                                        onClick = { showDeleteConfirmDialog = true },
                                        interactionSource = deleteInteractionSource,
                                        modifier = Modifier.pressScale(deleteInteractionSource).padding(end = 8.dp)
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
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.setQuery(it)
                        },
                        placeholder = { Text("Search recordings...") },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDeleteMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedItems.size == files.size && files.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    selectedItems.clear()
                                    if (checked) {
                                        files.indices.forEach { selectedItems.add(it) }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                            Text(
                                text = if (searchQuery.isEmpty()) "Recorded or uploaded audio files appear here" else "No matching recordings found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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
                                val isSelected = selectedItems.contains(index)
                                val isRenamingThis = renamingPosition == index

                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressScale(interactionSource)
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                if (isDeleteMode) {
                                                    if (isSelected) selectedItems.remove(index) else selectedItems.add(index)
                                                } else if (renamingPosition == -1) {
                                                    onPlayItem(item, index)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isDeleteMode && renamingPosition == -1) {
                                                    isDeleteMode = true
                                                    selectedItems.add(index)
                                                }
                                            }
                                        )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        if (isDeleteMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null // Click is handled by parent Card
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                        } else {
                                            Icon(
                                                painter = painterResource(id = R.drawable.audioo),
                                                contentDescription = null,
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(32.dp)
                                            )
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
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                                                val menuInteractionSource = remember { MutableInteractionSource() }
                                                IconButton(
                                                    onClick = { expandedMenuPosition = index },
                                                    interactionSource = menuInteractionSource,
                                                    modifier = Modifier.pressScale(menuInteractionSource)
                                                ) {
                                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                                                }

                                                DropdownMenu(
                                                    expanded = expandedMenuPosition == index,
                                                    onDismissRequest = { expandedMenuPosition = -1 },
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                                                        .background(MaterialTheme.colorScheme.surface)
                                                ) {
                                                    val renameInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Rename", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = renameInteractionSource,
                                                        modifier = Modifier.pressScale(renameInteractionSource),
                                                        onClick = {
                                                            expandedMenuPosition = -1
                                                            renameTextValue = TextFieldValue(text = item, selection = TextRange(item.length))
                                                            renameText = item
                                                            renamingPosition = index
                                                        }
                                                    )
                                                    val downloadInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Download", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.save), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = downloadInteractionSource,
                                                        modifier = Modifier.pressScale(downloadInteractionSource),
                                                        onClick = {
                                                            expandedMenuPosition = -1
                                                            onDownloadItem(item)
                                                        }
                                                    )
                                                    val shareInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Share", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = shareInteractionSource,
                                                        modifier = Modifier.pressScale(shareInteractionSource),
                                                        onClick = {
                                                            expandedMenuPosition = -1
                                                            onShareItem(item)
                                                        }
                                                    )
                                                    val summarizeInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Summarize", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.summarize), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary) },
                                                        interactionSource = summarizeInteractionSource,
                                                        modifier = Modifier.pressScale(summarizeInteractionSource),
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
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentAudioName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                val stopInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onStopPlayback,
                                    interactionSource = stopInteractionSource,
                                    modifier = Modifier.pressScale(stopInteractionSource)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Stop",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Seekbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentTimeStr,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = playbackProgress.toFloat(),
                                    onValueChange = { onSeekPlayback(it.toInt()) },
                                    valueRange = 0f..playbackMax.toFloat().coerceAtLeast(1f),
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                    )
                                )
                                Text(
                                    text = totalTimeStr,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Playback action controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val prevInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onPlayPrev,
                                    interactionSource = prevInteractionSource,
                                    modifier = Modifier.pressScale(prevInteractionSource)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.previous),
                                        contentDescription = "Previous",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                val playPauseInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onPlayPauseToggle,
                                    interactionSource = playPauseInteractionSource,
                                    modifier = Modifier.pressScale(playPauseInteractionSource)
                                ) {
                                    Icon(
                                        painter = painterResource(id = if (isPlaying) R.drawable.pause1 else R.drawable.play),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                val nextInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onPlayNext,
                                    interactionSource = nextInteractionSource,
                                    modifier = Modifier.pressScale(nextInteractionSource)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.next),
                                        contentDescription = "Next",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showDeleteConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirmDialog = false },
                    title = { Text(text = "Confirm Delete") },
                    text = { Text(text = stringResource(id = R.string.msg_delete_selected_audio)) },
                    confirmButton = {
                        val confirmBtnInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                val selectedFiles = selectedItems.mapNotNull { files.getOrNull(it) }
                                viewModel.deleteAudioFiles(selectedFiles)
                                isDeleteMode = false
                                selectedItems.clear()
                                showDeleteConfirmDialog = false
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            interactionSource = confirmBtnInteractionSource,
                            modifier = Modifier.pressScale(confirmBtnInteractionSource)
                        ) {
                            Text(stringResource(id = R.string.dialog_delete), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        val dismissBtnInteractionSource = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = { showDeleteConfirmDialog = false },
                            interactionSource = dismissBtnInteractionSource,
                            modifier = Modifier.pressScale(dismissBtnInteractionSource)
                        ) {
                            Text(stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold)
                        }
                    }
                )
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
