package com.meetloggerv2.ui.audio.fragment

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.meetloggerv2.core.theme.pressScale
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.meetloggerv2.core.R
import com.meetloggerv2.core.media.AudioPlayerManager
import com.meetloggerv2.core.network.NetworkMonitor
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.config.AppConfig
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.core.ui.components.PremiumAudioPlayer
import com.meetloggerv2.core.ui.components.SheetHeader
import com.meetloggerv2.ui.audio.util.AudioProcessingDialog
import com.meetloggerv2.ui.audio.viewmodel.RecordAudioViewModel
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, com.meetloggerv2.core.R.style.CustomBottomSheetDialog)
        val uid = authSession.currentUserId()
        if (uid != null) {
            viewModel.fetchUserFiles(uid)
        }
    }

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
                            setBottomSheetHeight(0.85)
                        } else {
                            setBottomSheetHeight(0.65)
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
                        onSeekPlayback = { progress ->
                            audioPlayer.seekTo(progress)
                            playbackProgressState.value = progress
                        },
                        onRewind = { audioPlayer.rewind() },
                        onForward = { audioPlayer.fastForward() },
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
                        Dialog(onDismissRequest = { }) {
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
                                        text = "Save Recording",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Start
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Enter a name for your recording:",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    OutlinedTextField(
                                        value = fileNameInput,
                                        onValueChange = { fileNameInput = it },
                                        singleLine = true,
                                        shape = RoundedCornerShape(24.dp),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Password,
                                            autoCorrectEnabled = false
                                        ),
                                        visualTransformation = VisualTransformation.None,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = Color.Transparent,
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth().focusRequester(saveFocusRequester),
                                        placeholder = { Text("Meeting name...") }
                                    )
                                    Spacer(modifier = Modifier.height(32.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val discardInteractionSource = remember { MutableInteractionSource() }
                                        OutlinedButton(
                                            onClick = {
                                                audioFile?.delete()
                                                resetRecordingUI()
                                                showSaveDialogState.value = false
                                            },
                                            interactionSource = discardInteractionSource,
                                            modifier = Modifier.weight(1f).height(48.dp).pressScale(discardInteractionSource),
                                            shape = RoundedCornerShape(24.dp),
                                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                        ) {
                                            Text("Discard", fontWeight = FontWeight.Bold)
                                        }

                                        val saveInteractionSource = remember { MutableInteractionSource() }
                                        Surface(
                                            onClick = {
                                                val name = fileNameInput.trim()
                                                if (name.isNotEmpty()) {
                                                    saveRecording(name)
                                                    showSaveDialogState.value = false
                                                } else {
                                                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp).pressScale(saveInteractionSource),
                                            shape = RoundedCornerShape(24.dp),
                                            color = Color.Transparent
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (isSavingState.value) {
                                                    Box(modifier = Modifier.size(24.dp)) {
                                                        CircularProgressIndicator(
                                                            color = Color.White,
                                                            strokeWidth = 2.5.dp
                                                        )
                                                    }
                                                } else {
                                                    Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
                        Dialog(onDismissRequest = { showDeleteConfirmState.value = false }) {
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
                                        text = "Are you sure you want to delete this recording?",
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
                                            onClick = { showDeleteConfirmState.value = false },
                                            interactionSource = cancelInteractionSource,
                                            modifier = Modifier.weight(1f).height(48.dp).pressScale(cancelInteractionSource),
                                            shape = RoundedCornerShape(24.dp),
                                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                                        ) {
                                            Text("Cancel", fontWeight = FontWeight.Bold)
                                        }

                                        val deleteInteractionSource = remember { MutableInteractionSource() }
                                        Surface(
                                            onClick = {
                                                val uid = authSession.currentUserId()
                                                if (uid != null && audioFile != null) {
                                                    viewModel.deleteAudio(uid, audioFile!!.name)
                                                }
                                                resetRecordingUI()
                                                showDeleteConfirmState.value = false
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp).pressScale(deleteInteractionSource),
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
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setBottomSheetHeight(0.70)
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
        viewLifecycleOwner.lifecycleScope.launch {
            AppConfig.ensureLimitValidated()
            val subscription = authSession.currentUserSubscription()
            val limit = AppConfig.freePlanLimit
            if (subscription == "free" && viewModel.historyCountState.value >= limit) {
                Toast.makeText(context, "Free plan limit: You can only have up to $limit recordings. Please upgrade to Pro.", Toast.LENGTH_LONG).show()
                dismiss()
                return@launch
            }

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
                                playbackMaxState.value = elapsedSecondsState.value * 1000
                                playbackTotalTimeState.value = audioPlayer.formatTime(playbackMaxState.value)
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onSeekPlayback: (Int) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHeader(
                title = if (isRecording && !isPaused) "Recording..." else "Record Audio",
                onDismiss = onDismiss,
                showCloseButton = !isRecording && !isPaused && !isProcessing && !isSaving,
                trailingContent = {
                    if ((isRecording || isPaused) && !isProcessing && !isSaving) {
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .pressScaleClick(onClick = onToggleLock),
                            shape = CircleShape,
                            color = if (isSheetLocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isSheetLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Toggle Sheet Lock",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isSheetLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            )

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
                                        GradientStart.copy(alpha = pulseAlpha),
                                        GradientEnd.copy(alpha = 0f)
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
                                                GradientEnd,
                                                GradientStart
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
                                                GradientEnd.copy(alpha = 0.5f),
                                                GradientStart.copy(alpha = 0.5f)
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Recording in progress",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .pressScaleClick(enabled = !isProcessing && !isSaving) { onStartRecord() },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (isRecording) {
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFFFA726),
                                                Color(0xFFFB8C00)
                                            )
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            colors = listOf(GradientStart, GradientEnd)
                                        )
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isRecording) "Pause" else if (isPaused) "Resume" else "Start",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    if (isRecording || isPaused) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .pressScaleClick(enabled = !isProcessing && !isSaving) { onStopRecord() },
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFFEF5350),
                                                Color(0xFFD32F2F)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Stop",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // Premium Integrated Player
                PremiumAudioPlayer(
                    isPlaying = isPlaybackPlaying,
                    playbackProgress = playbackProgress,
                    playbackMax = playbackMax,
                    currentTimeStr = playbackCurrentTime,
                    totalTimeStr = playbackTotalTime,
                    onPlayPause = onPlayPausePlayback,
                    onSeek = onSeekPlayback,
                    onRewind = onRewind,
                    onForward = onForward
                )

                Spacer(modifier = Modifier.height(36.dp))

                    // Saved Section action buttons (Vertical Column Stack)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Outlined "New Recording" Button (Full Width)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .pressScaleClick(enabled = !isProcessing && !isSaving) { onNewRecording() },
                            shape = RoundedCornerShape(25.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "New Recording",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Premium Gradient "Process Audio" Button (Full Width)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .pressScaleClick(enabled = !isProcessing && !isSaving) { onProcessAudio() },
                            shape = RoundedCornerShape(25.dp),
                            shadowElevation = 4.dp,
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(GradientStart, GradientEnd)
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
                                        modifier = Modifier.size(20.dp)
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
}
