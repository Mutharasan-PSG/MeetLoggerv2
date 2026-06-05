package com.example.meetloggerv2.ui.audio.fragment

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.media.AudioPlayerManager
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.ui.audio.util.AudioProcessingDialog
import com.example.meetloggerv2.ui.audio.viewmodel.RecordAudioViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class RecordAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private val viewModel: RecordAudioViewModel by viewModels()
    private val audioPlayer = AudioPlayerManager()
    private lateinit var networkMonitor: NetworkMonitor
    @Inject lateinit var authSession: AuthSession

    private var audioFile: File? = null
    private var isRecordingState = mutableStateOf(false)
    private var isPausedState = mutableStateOf(false)
    private var isProcessingState = mutableStateOf(false)
    private var isSavingState = mutableStateOf(false)
    private var isSheetLockedState = mutableStateOf(true)
    private var progressMessageState = mutableStateOf("Processing...")
    private var elapsedSecondsState = mutableStateOf(0)
    private var showSpeakerSelectionState = mutableStateOf(false)

    // Preview / Player states
    private var savedFileNameState = mutableStateOf<String?>(null)
    private var isPlaybackPlayingState = mutableStateOf(false)
    private var playbackProgressState = mutableStateOf(0)
    private var playbackMaxState = mutableStateOf(100)
    private var playbackCurrentTimeState = mutableStateOf("00:00")
    private var playbackTotalTimeState = mutableStateOf("00:00")

    private var showSaveDialogState = mutableStateOf(false)
    private var showDeleteConfirmState = mutableStateOf(false)

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
                    val savedFileName = savedFileNameState.value
                    LaunchedEffect(savedFileName) {
                        if (savedFileName != null) {
                            setBottomSheetHeight(0.88)
                        } else {
                            setBottomSheetHeight(0.70)
                        }
                    }
                    RecordAudioScreen(
                        isRecording = isRecordingState.value,
                        isPaused = isPausedState.value,
                        isProcessing = isProcessingState.value,
                        isSaving = isSavingState.value,
                        isSheetLocked = isSheetLockedState.value,
                        progressMessage = progressMessageState.value,
                        elapsedSeconds = elapsedSecondsState.value,
                        savedFileName = savedFileNameState.value,
                        isPlaybackPlaying = isPlaybackPlayingState.value,
                        playbackProgress = playbackProgressState.value,
                        playbackMax = playbackMaxState.value,
                        playbackCurrentTime = playbackCurrentTimeState.value,
                        playbackTotalTime = playbackTotalTimeState.value,
                        viewModel = viewModel,
                        audioPlayer = audioPlayer,
                        onStartRecord = { checkAndRequestPermissions() },
                        onStopRecord = { stopRecording() },
                        onToggleLock = { toggleSheetLock() },
                        onPlayPausePlayback = { togglePlayback() },
                        onStopPlayback = { stopPlayback() },
                        onSeekPlayback = { progress ->
                            audioPlayer.seekTo(progress)
                            playbackProgressState.value = progress
                        },
                        onProcessAudio = { showSpeakerSelection() },
                        onNewRecording = { resetRecordingUI() },
                        onDeleteRecording = { showDeleteConfirmation() },
                        onDismiss = { dismiss() }
                    )

                    if (showSaveDialogState.value) {
                        var fileNameInput by remember { mutableStateOf("") }
                        val saveFocusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            delay(100)
                            saveFocusRequester.requestFocus()
                        }
                        AlertDialog(
                            onDismissRequest = { /* Cannot dismiss by tapping outside */ },
                            title = { Text("Save Recording", fontWeight = FontWeight.Bold) },
                            text = {
                                Column {
                                    Text("Enter a name for your recording:")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = fileNameInput,
                                        onValueChange = { fileNameInput = it },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth().focusRequester(saveFocusRequester),
                                        placeholder = { Text("Meeting name...") }
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val name = fileNameInput.trim()
                                        if (name.isNotEmpty()) {
                                            saveRecording(name)
                                            showSaveDialogState.value = false
                                        } else {
                                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Save")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        audioFile?.delete()
                                        resetRecordingUI()
                                        showSaveDialogState.value = false
                                    }
                                ) {
                                    Text("Discard", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }

                    if (showSpeakerSelectionState.value) {
                        val userFiles by viewModel.userFilesState.collectAsState(emptyList())
                        LaunchedEffect(Unit) {
                            val uid = authSession.currentUserId()
                            if (uid != null) viewModel.fetchUserFiles(uid)
                        }
                        AudioProcessingDialog(
                            userFiles = userFiles,
                            onDismiss = { showSpeakerSelectionState.value = false },
                            onProcessingConfirmed = { speakers, followUp ->
                                showSpeakerSelectionState.value = false
                                proceedToBackendUpload(speakers, followUp)
                            }
                        )
                    }

                    if (showDeleteConfirmState.value) {
                        AlertDialog(
                            onDismissRequest = { showDeleteConfirmState.value = false },
                            title = { Text("Confirm Delete", fontWeight = FontWeight.Bold) },
                            text = { Text("Are you sure you want to delete this recording?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val uid = authSession.currentUserId()
                                        if (uid != null && audioFile != null) {
                                            viewModel.deleteAudio(uid, audioFile!!.name)
                                        }
                                        resetRecordingUI()
                                        showDeleteConfirmState.value = false
                                    },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Delete", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteConfirmState.value = false }) {
                                    Text("Cancel", fontWeight = FontWeight.Bold)
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setBottomSheetHeight(0.76)
    }

    private fun setBottomSheetHeight(fraction: Double) {
        val dialog = dialog as? BottomSheetDialog ?: return
        val b = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = BottomSheetBehavior.from(b)
        val dm = resources.displayMetrics
        b.layoutParams.height = (dm.heightPixels * fraction).toInt()
        b.requestLayout()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        updateDismissalState(dialog, behavior)
    }

    private fun updateDismissalState(d: BottomSheetDialog? = null, b: BottomSheetBehavior<View>? = null) {
        val dialog = d ?: (this.dialog as? BottomSheetDialog) ?: return
        val behavior = b ?: BottomSheetBehavior.from(dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet) ?: return)
        
        val busy = isProcessingState.value || isSavingState.value
        val canDismiss = if (isRecordingState.value) {
            !isSheetLockedState.value && !busy
        } else {
            !busy
        }
        
        dialog.setCancelable(canDismiss)
        dialog.setCanceledOnTouchOutside(canDismiss)
        behavior.isDraggable = canDismiss
    }

    private fun toggleSheetLock() {
        isSheetLockedState.value = !isSheetLockedState.value
        updateDismissalState()
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        } else {
            if (!isRecordingState.value && !isPausedState.value) {
                startRecording()
            } else if (isPausedState.value) {
                viewModel.resumeRecording()
            } else {
                viewModel.pauseRecording()
            }
        }
    }

    private fun startRecording() {
        val file = File(requireContext().externalCacheDir, "temp.mp3")
        audioFile = file
        viewModel.startRecording(file)
    }

    private fun stopRecording() {
        viewModel.stopRecording()
    }

    private fun saveRecording(name: String) {
        val uid = authSession.currentUserId()
        if (uid != null && audioFile != null) {
            val targetFile = File(requireContext().externalCacheDir, "$name.mp3")
            if (audioFile?.renameTo(targetFile) == true) {
                audioFile = targetFile
            }
            viewModel.saveAudio(uid, audioFile!!, Uri.fromFile(audioFile))
        }
    }

    private fun resetRecordingUI() {
        audioPlayer.stop()
        isPlaybackPlayingState.value = false
        playbackProgressState.value = 0
        elapsedSecondsState.value = 0
        viewModel.releaseRecorder()
        audioFile = null
        savedFileNameState.value = null
        isRecordingState.value = false
        isPausedState.value = false
        updateDismissalState()
    }

    private fun togglePlayback() {
        if (isProcessingState.value) return
        if (audioPlayer.isReady()) {
            val playing = audioPlayer.togglePlayPause()
            isPlaybackPlayingState.value = playing
        } else {
            audioFile?.let { file ->
                audioPlayer.play(
                    file.absolutePath,
                    {
                        isPlaybackPlayingState.value = false
                        playbackProgressState.value = 0
                    }
                ) { current, total ->
                    playbackMaxState.value = total
                    playbackProgressState.value = current
                    playbackCurrentTimeState.value = audioPlayer.formatTime(current)
                    playbackTotalTimeState.value = audioPlayer.formatTime(total)
                }
                isPlaybackPlayingState.value = true
            }
        }
    }

    private fun stopPlayback() {
        if (!isProcessingState.value) {
            audioPlayer.stop()
            isPlaybackPlayingState.value = false
            playbackProgressState.value = 0
            playbackCurrentTimeState.value = "00:00"
        }
    }

    private fun showSpeakerSelection() {
        showSpeakerSelectionState.value = true
    }

    private fun proceedToBackendUpload(speakers: List<String>, followUp: String) {
        val uid = authSession.currentUserId()
        if (uid != null && audioFile != null) {
            viewModel.processAudio(uid, audioFile!!, Uri.fromFile(audioFile), speakers, followUp)
        }
    }

    private fun showDeleteConfirmation() {
        showDeleteConfirmState.value = true
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        isSavingState.value = state is RecordAudioViewModel.UiState.Saving
                        isProcessingState.value = state is RecordAudioViewModel.UiState.Processing
                        updateDismissalState()

                        when (state) {
                            is RecordAudioViewModel.UiState.Saving -> {
                                progressMessageState.value = "Saving..."
                            }
                            is RecordAudioViewModel.UiState.Saved -> {
                                savedFileNameState.value = state.fileName
                            }
                            is RecordAudioViewModel.UiState.Processing -> {
                                progressMessageState.value = state.stage
                            }
                            is RecordAudioViewModel.UiState.Processed -> {
                                isProcessingState.value = false
                                Toast.makeText(context, "Ready! You will be notified.", Toast.LENGTH_LONG).show()
                                dismiss()
                            }
                            is RecordAudioViewModel.UiState.Error -> {
                                isProcessingState.value = false
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.recordState.collect { state ->
                        isRecordingState.value = state == RecordAudioViewModel.RecordState.RECORDING
                        isPausedState.value = state == RecordAudioViewModel.RecordState.PAUSED
                        updateDismissalState()

                        if (state == RecordAudioViewModel.RecordState.STOPPED) {
                            showSaveDialogState.value = true
                        }
                    }
                }

                launch {
                    viewModel.elapsedTime.collect { elapsed ->
                        elapsedSecondsState.value = elapsed
                    }
                }
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            if (!isOnline && isProcessingState.value) {
                isProcessingState.value = false
                Toast.makeText(context, "Offline", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCancel(dialog: android.content.DialogInterface) {
        releaseRecorderImmediately()
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        releaseRecorderImmediately()
        audioPlayer.stop()
        super.onDestroyView()
    }

    private fun releaseRecorderImmediately() {
        viewModel.releaseRecorder()
        isRecordingState.value = false
        isPausedState.value = false
        audioFile?.let { if (savedFileNameState.value == null) it.delete() }
    }
}

@Composable
fun RecordAudioScreen(
    isRecording: Boolean,
    isPaused: Boolean,
    isProcessing: Boolean,
    isSaving: Boolean,
    isSheetLocked: Boolean,
    progressMessage: String,
    elapsedSeconds: Int,
    savedFileName: String?,
    isPlaybackPlaying: Boolean,
    playbackProgress: Int,
    playbackMax: Int,
    playbackCurrentTime: String,
    playbackTotalTime: String,
    viewModel: RecordAudioViewModel,
    audioPlayer: AudioPlayerManager,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onToggleLock: () -> Unit,
    onPlayPausePlayback: () -> Unit,
    onStopPlayback: () -> Unit,
    onSeekPlayback: (Int) -> Unit,
    onProcessAudio: () -> Unit,
    onNewRecording: () -> Unit,
    onDeleteRecording: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedElapsed = remember(elapsedSeconds) {
        audioPlayer.formatTime(elapsedSeconds * 1000)
    }

    val bars = remember { mutableStateListOf(1f, 1f, 1f, 1f, 1f) }

    if (isRecording && !isPaused) {
        LaunchedEffect(Unit) {
            val factors = listOf(0.7f, 1.2f, 0.8f, 1.4f, 0.9f)
            while (true) {
                val amp = viewModel.getMaxAmplitude()
                val normalized = (amp.toFloat() / 12000f).coerceIn(0.15f, 1.6f)
                for (i in 0 until 5) {
                    bars[i] = (normalized * factors[i]).coerceIn(0.15f, 1.7f)
                }
                delay(100)
            }
        }
    } else if (isPlaybackPlaying) {
        LaunchedEffect(Unit) {
            val durations = listOf(350L, 250L, 400L, 300L, 320L)
            var time = 0f
            while (true) {
                for (i in 0 until 5) {
                    val wave = 0.8f + 0.6f * kotlin.math.sin(time * (1000f / durations[i]))
                    bars[i] = wave.coerceIn(0.15f, 1.7f)
                }
                time += 0.05f
                delay(50)
            }
        }
    } else {
        LaunchedEffect(Unit) {
            for (i in 0 until 5) {
                bars[i] = 1.0f
            }
        }
    }
    if (isRecording && isSheetLocked) {
        androidx.activity.compose.BackHandler {
            Toast.makeText(context, "Unlock recording before dismissing", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Drag handle / bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record Audio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isRecording) {
                    IconButton(
                        onClick = onToggleLock,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isSheetLocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSheetLocked) R.drawable.ic_lock else R.drawable.ic_unlock),
                            contentDescription = "Toggle Sheet Lock",
                            tint = if (isSheetLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (savedFileName == null) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pulse or Equalizer visualizer
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (isRecording && !isPaused) {
                    val transition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by transition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "scale"
                    )
                    val pulseAlpha by transition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "alpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF4361EE).copy(alpha = pulseAlpha),
                                        Color(0xFF7209B7).copy(alpha = 0f)
                                    )
                                )
                            )
                    )
                }

                if (isRecording || isPlaybackPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(60.dp)
                    ) {
                        bars.forEach { scale ->
                            val animatedScale by animateFloatAsState(targetValue = scale, label = "")
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .fillMaxHeight(animatedScale.coerceIn(0.1f, 1f))
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF7209B7),
                                                Color(0xFF4361EE)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(60.dp)
                    ) {
                        val staticScales = listOf(0.3f, 0.6f, 0.85f, 0.5f, 0.25f)
                        staticScales.forEach { scale ->
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .fillMaxHeight(scale)
                                    .clip(RoundedCornerShape(2.5.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF7209B7).copy(alpha = 0.5f),
                                                Color(0xFF4361EE).copy(alpha = 0.5f)
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Timer or status message
            if (isRecording || isPaused) {
                Text(
                    text = formattedElapsed,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (savedFileName != null) {
                Text(
                    text = savedFileName.substringBeforeLast(".mp3"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Press start to begin recording",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Controls
            if (savedFileName == null) {
                // Recording Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onStartRecord,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRecording) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = if (isRecording) "Pause" else if (isPaused) "Resume" else "Start",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    if (isRecording || isPaused) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = onStopRecord,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "Stop",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            } else {
                // Preview & Process controls
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Mini player seekbar and timings
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = playbackCurrentTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(
                                    value = playbackProgress.toFloat(),
                                    onValueChange = { onSeekPlayback(it.toInt()) },
                                    valueRange = 0f..playbackMax.toFloat().coerceAtLeast(1f),
                                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                    )
                                )
                                Text(text = playbackTotalTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val playPauseInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onPlayPausePlayback,
                                    interactionSource = playPauseInteractionSource,
                                    modifier = Modifier.pressScale(playPauseInteractionSource)
                                ) {
                                    Icon(
                                        painter = painterResource(id = if (isPlaybackPlaying) R.drawable.pause1 else R.drawable.play),
                                        contentDescription = if (isPlaybackPlaying) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                val stopPlaybackInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = onStopPlayback,
                                    interactionSource = stopPlaybackInteractionSource,
                                    modifier = Modifier.pressScale(stopPlaybackInteractionSource)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.stop),
                                        contentDescription = "Stop",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Saved Section action buttons (Vertical Column Stack)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Premium Gradient "Process Audio" Button (Full Width)
                        val processInteractionSource = remember { MutableInteractionSource() }
                        Surface(
                            onClick = onProcessAudio,
                            shape = RoundedCornerShape(25.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .pressScale(processInteractionSource),
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Process Audio",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        // Outlined "New Recording" Button (Full Width)
                        val newInteractionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = onNewRecording,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressScale(newInteractionSource),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            interactionSource = newInteractionSource
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "New Recording",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        // Delete File Button (Full Width)
                        val deleteInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = onDeleteRecording,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .pressScale(deleteInteractionSource),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f),
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            interactionSource = deleteInteractionSource
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Delete File",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (isProcessing || isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable(enabled = false) {}, // Intercept click
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
