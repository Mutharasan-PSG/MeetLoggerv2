package com.example.meetloggerv2.ui.audio.fragment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.session.AuthSession
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.util.FileUtils
import com.example.meetloggerv2.ui.audio.util.AudioProcessingDialog
import com.example.meetloggerv2.ui.audio.viewmodel.UploadAudioViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UploadAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private val viewModel: UploadAudioViewModel by viewModels()
    @Inject lateinit var authSession: AuthSession
    private lateinit var networkMonitor: NetworkMonitor

    private var selectedAudioUriState = mutableStateOf<Uri?>(null)
    private var isProcessingState = mutableStateOf(false)
    private var processingStageState = mutableStateOf("Processing...")
    private var showSpeakerSelectionState = mutableStateOf(false)

    private val audioPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAudioUriState.value = uri
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

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        UploadAudioScreen(
                            selectedUri = selectedAudioUriState.value,
                            isProcessing = isProcessingState.value,
                            processingStage = processingStageState.value,
                            onSelectFile = { openAudioPicker() },
                            onProcessFile = { checkAndRequestPermissions() },
                            onDismiss = { dismiss() }
                        )

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
                                    processAudio(speakers, followUp)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setBottomSheetHeight(0.6)
    }

    private fun setBottomSheetHeight(fraction: Double) {
        val dialog = dialog as? BottomSheetDialog ?: return
        val b = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val behavior = BottomSheetBehavior.from(b)
        val dm = resources.displayMetrics
        b.layoutParams.height = (dm.heightPixels * fraction).toInt()
        b.requestLayout()
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        
        isProcessingState.value.let { busy ->
            dialog.setCancelable(!busy)
            dialog.setCanceledOnTouchOutside(!busy)
            behavior.isDraggable = !busy
        }
    }

    private fun openAudioPicker() {
        audioPickerLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        })
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003)
        } else {
            showSpeakerSelection()
        }
    }

    private fun showSpeakerSelection() {
        showSpeakerSelectionState.value = true
    }

    private fun processAudio(speakers: List<String>, followUp: String) {
        selectedAudioUriState.value?.let { uri ->
            val file = FileUtils.uriToFile(requireContext(), uri)
            val uid = authSession.currentUserId()
            if (file != null && uid != null) {
                viewModel.processAudio(uid, file, uri, speakers, followUp)
            }
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    isProcessingState.value = state is UploadAudioViewModel.UploadUiState.Processing
                    val dialog = dialog as? BottomSheetDialog
                    if (dialog != null) {
                        val busy = isProcessingState.value
                        dialog.setCancelable(!busy)
                        dialog.setCanceledOnTouchOutside(!busy)
                        val b = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                        if (b != null) {
                            BottomSheetBehavior.from(b).isDraggable = !busy
                        }
                    }

                    when (state) {
                        is UploadAudioViewModel.UploadUiState.Processing -> {
                            processingStageState.value = state.stage
                        }
                        is UploadAudioViewModel.UploadUiState.Processed -> {
                            Toast.makeText(context, "Processing started", Toast.LENGTH_LONG).show()
                            dismiss()
                        }
                        is UploadAudioViewModel.UploadUiState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                        }
                        else -> {}
                    }
                }
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            if (!isOnline && isProcessingState.value) {
                isProcessingState.value = false
                Toast.makeText(requireContext(), "Aborted: No internet", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun UploadAudioScreen(
    selectedUri: Uri?,
    isProcessing: Boolean,
    processingStage: String,
    onSelectFile: () -> Unit,
    onProcessFile: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileName = remember(selectedUri) {
        selectedUri?.let { FileUtils.getFileNameFromUri(context, it) } ?: ""
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
                    text = "Upload Audio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!isProcessing) {
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File selection area
            Surface(
                onClick = { if (!isProcessing) onSelectFile() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (selectedUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                color = if (selectedUri != null) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.audio_file),
                        contentDescription = null,
                        tint = if (selectedUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (selectedUri != null) fileName else "Tap to choose an audio file",
                        fontWeight = if (selectedUri != null) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        color = if (selectedUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    if (selectedUri == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Supported formats: MP3, M4A, WAV",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onProcessFile,
                enabled = selectedUri != null && !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Process Audio",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // intercept clicks
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = processingStage,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}
