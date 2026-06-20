package com.meetloggerv2.ui.audio.fragment

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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
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
import com.meetloggerv2.core.R
import com.meetloggerv2.core.network.NetworkMonitor
import com.meetloggerv2.core.session.AuthSession
import com.meetloggerv2.core.config.AppConfig
import com.meetloggerv2.core.theme.GradientEnd
import com.meetloggerv2.core.theme.GradientStart
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.theme.pressScale
import com.meetloggerv2.core.theme.pressScaleClick
import com.meetloggerv2.core.ui.components.SheetHeader
import com.meetloggerv2.core.util.FileUtils
import com.meetloggerv2.ui.audio.util.AudioProcessingDialog
import com.meetloggerv2.ui.audio.viewmodel.UploadAudioViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UploadAudioBottomsheetFragment : BottomSheetDialogFragment() {

    companion object {
        // Fragment Result contract used to tell the host screen that backend
        // processing has started, so it can show the confirmation pop-up after
        // this sheet has dismissed (the sheet's own view is gone by then).
        const val RESULT_KEY = "audio_upload_result"
        const val RESULT_STATUS = "status"
        const val STATUS_PROCESSING_STARTED = "processing_started"
    }

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
                viewLifecycleOwner.lifecycleScope.launch {
                    AppConfig.ensureLimitValidated()
                    val subscription = authSession.currentUserSubscription()
                    if (subscription == "free") {
                        val limit = AppConfig.freePlanLimit
                        if (viewModel.historyCountState.value >= limit) {
                            Toast.makeText(context, "Free plan limit: You can only have up to $limit recordings. Please upgrade to Pro.", Toast.LENGTH_LONG).show()
                            dismiss()
                            return@launch
                        }
                        val retriever = android.media.MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(requireContext(), uri)
                            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            val durationMs = durationStr?.toLongOrNull() ?: 0L
                            val audioLimitMins = AppConfig.freePlanAudioLimitMinutes
                            if (durationMs > audioLimitMins * 60 * 1000L) { 
                                Toast.makeText(context, "Free plan limit: Files must be under $audioLimitMins minutes.", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("UploadAudio", "Failed to retrieve audio duration: ${e.message}")
                        } finally {
                            try {
                                retriever.release()
                            } catch (ignored: Exception) {}
                        }
                    }
                    selectedAudioUriState.value = uri
                }
            }
        }
    }

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
                    Box(modifier = Modifier.fillMaxSize()) {
                        UploadAudioScreen(
                            selectedUri = selectedAudioUriState.value,
                            isProcessing = isProcessingState.value,
                            processingStage = processingStageState.value,
                            onSelectFile = { openAudioPicker() },
                            onProcessFile = { checkAndRequestPermissions() },
                            onRecordInstead = {
                                dismiss()
                                RecordAudioBottomsheetFragment().show(parentFragmentManager, "RecordAudioSheet")
                            },
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
        setBottomSheetHeight(0.7)
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
        viewLifecycleOwner.lifecycleScope.launch {
            AppConfig.ensureLimitValidated()
            val subscription = authSession.currentUserSubscription()
            val limit = AppConfig.freePlanLimit
            if (subscription == "free" && viewModel.historyCountState.value >= limit) {
                Toast.makeText(context, "Free plan limit: You can only have up to $limit recordings. Please upgrade to Pro.", Toast.LENGTH_LONG).show()
                dismiss()
                return@launch
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1003)
            } else {
                showSpeakerSelection()
            }
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
                            // Hand off to the host screen so it can show the styled
                            // "Processing Started" pop-up once this sheet is dismissed.
                            parentFragmentManager.setFragmentResult(
                                RESULT_KEY,
                                Bundle().apply { putString(RESULT_STATUS, STATUS_PROCESSING_STARTED) }
                            )
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
    onRecordInstead: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val fileName = remember(selectedUri) {
        selectedUri?.let { FileUtils.getFileNameFromUri(context, it) } ?: ""
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetHeader(
                title = "Upload Audio",
                onDismiss = onDismiss,
                showCloseButton = !isProcessing
            )

            Spacer(modifier = Modifier.height(16.dp))

            // File selection area with Dotted Border
            val borderColor = if (selectedUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            val stroke = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (selectedUri != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .clickable(enabled = !isProcessing) { onSelectFile() }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRoundRect(
                        color = borderColor,
                        style = stroke,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx())
                    )
                }

                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (selectedUri != null) Icons.Default.AudioFile else Icons.Default.Backup,
                                contentDescription = null,
                                tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = if (selectedUri != null) fileName else "Tap to choose a file",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    
                    if (selectedUri == null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "or drag and drop your audio here",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        // Formats
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormatTag("MP3")
                            FormatTag("M4A")
                            FormatTag("WAV")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressScaleClick(enabled = selectedUri != null && !isProcessing) { onProcessFile() },
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (selectedUri != null && !isProcessing) {
                                Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Process Audio",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedUri != null && !isProcessing) Color.White else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // OR Divider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "OR",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Record Instead Button (Profile Style)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClick(enabled = !isProcessing) { onRecordInstead() },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = "Record live audio instead",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {},
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

@Composable
fun FormatTag(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false
        )
    }
}
