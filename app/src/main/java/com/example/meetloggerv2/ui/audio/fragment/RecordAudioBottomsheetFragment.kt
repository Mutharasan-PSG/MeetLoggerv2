package com.example.meetloggerv2.ui.audio.fragment

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.media.AudioPlayerManager
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.databinding.FragmentRecordAudioBottomsheetBinding
import com.example.meetloggerv2.ui.audio.viewmodel.RecordAudioViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

import com.example.meetloggerv2.ui.audio.util.AudioProcessingDialogHelper

class RecordAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentRecordAudioBottomsheetBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordAudioViewModel by viewModels()
    private val audioPlayer = AudioPlayerManager()
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var audioFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var isProcessing = false
    private var isSaving = false
    private var isSheetLocked = true

    private val handler = Handler(Looper.getMainLooper())
    private val progressTimeoutRunnable = Runnable {
        if (_binding != null && binding.progressOverlay.visibility == View.VISIBLE) {
            Toast.makeText(context, R.string.msg_please_wait, Toast.LENGTH_SHORT).show()
        }
    }
    private var equalizerAnimators = mutableListOf<android.animation.Animator>()
    private var amplitudeHandler: Handler? = null
    private var amplitudeRunnable: Runnable? = null

    // ────────────────────────────────────────────────────────────────────────────
    //  Lifecycle
    // ────────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordAudioBottomsheetBinding.inflate(inflater, container, false)
        networkMonitor = NetworkMonitor(requireContext())
        setupUI()
        setupObservers()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        setFixedBottomSheetHeight(0.76)

        // Set custom click listener on touch_outside to show Toast warning in locked mode
        val d = dialog as? BottomSheetDialog
        val touchOutside = d?.findViewById<View>(com.google.android.material.R.id.touch_outside)
        touchOutside?.setOnClickListener {
            if (isRecording && isSheetLocked) {
                Toast.makeText(context, "Unlock at the top right to dismiss during recording", Toast.LENGTH_SHORT).show()
            } else if (!isProcessing && !isSaving) {
                dismiss()
            }
        }

        // Intercept back presses directly on the dialog window
        d?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    if (isRecording) {
                        if (isSheetLocked) {
                            Toast.makeText(requireContext(), "Unlock at the top right to dismiss during recording", Toast.LENGTH_SHORT).show()
                            true
                        } else {
                            dismiss()
                            true
                        }
                    } else {
                        false
                    }
                } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                    isRecording && isSheetLocked
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    // Called when user swipes down / taps outside the sheet ─ release mic immediately
    override fun onCancel(dialog: android.content.DialogInterface) {
        releaseRecorderImmediately()
        super.onCancel(dialog)
    }

    override fun onDestroyView() {
        stopPulseAnimation()
        stopEqualizerAnimation()
        releaseRecorderImmediately()
        audioPlayer.stop()
        super.onDestroyView()
        _binding = null
    }

    /** Stops + releases MediaRecorder and mic via ViewModel regardless of current state. */
    private fun releaseRecorderImmediately() {
        viewModel.releaseRecorder()
        isRecording = false
        isPaused    = false
        audioFile?.let { if (!isSaving) it.delete() }
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  UI Setup
    // ────────────────────────────────────────────────────────────────────────────

    private fun setupUI() {
        // Start / Pause / Resume button
        binding.startButton.setOnClickListener { checkAndRequestPermissions() }

        // Stop recording → show save dialog
        binding.stopButton.setOnClickListener { stopRecording() }

        // Lock/Unlock toggle button
        binding.lockToggle.setOnClickListener { toggleSheetLock() }

        // Mini-player: play / pause
        binding.playPauseButton.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            if (audioPlayer.isReady()) {
                val nowPlaying = audioPlayer.togglePlayPause()
                binding.playPauseButton.setImageResource(
                    if (nowPlaying) R.drawable.pause1 else R.drawable.play
                )
                if (nowPlaying) {
                    startEqualizerAnimation(false)
                } else {
                    stopEqualizerAnimation()
                }
            } else {
                audioFile?.let { file ->
                    audioPlayer.play(
                        file.absolutePath,
                        {
                            _binding?.playPauseButton?.setImageResource(R.drawable.play)
                            _binding?.seekBar?.progress = 0
                            stopEqualizerAnimation()
                        }
                    ) { current, total ->
                        _binding?.seekBar?.max = total
                        _binding?.seekBar?.progress = current
                        _binding?.currentTime?.text = audioPlayer.formatTime(current)
                        _binding?.totalTime?.text = audioPlayer.formatTime(total)
                    }
                    binding.playPauseButton.setImageResource(R.drawable.pause1)
                    startEqualizerAnimation(false)
                }
            }
        }

        // Mini-player: prev / next (no-op placeholders — AudioPlayerManager manages single file)
        binding.prevButton.setOnClickListener { /* single-file player */ }
        binding.nextButton.setOnClickListener { /* single-file player */ }

        // Mini-player: stop playback
        binding.stopButtonMini.setOnClickListener {
            if (!isProcessing) {
                audioPlayer.stop()
                binding.playPauseButton.setImageResource(R.drawable.play)
                binding.seekBar.progress = 0
                binding.currentTime.text = "00:00"
                stopEqualizerAnimation()
            }
        }

        // Post-save action buttons
        binding.processAudioButton.setOnClickListener {
            if (!isProcessing) {
                AudioProcessingDialogHelper(
                    context = requireContext(),
                    lifecycleOwner = viewLifecycleOwner,
                    userFilesLiveData = viewModel.userFiles,
                    fetchUserFiles = {
                        val uid = com.example.meetloggerv2.core.session.AuthSession().currentUserId()
                        if (uid != null) viewModel.fetchUserFiles(uid)
                    },
                    onProcessingConfirmed = { speakers, followUp ->
                        proceedToBackendUpload(speakers, followUp)
                    }
                ).show()
            }
        }
        binding.newRecordingButton.setOnClickListener { resetRecordingUI() }
        binding.deleteButton.setOnClickListener { deleteCurrentAudio() }

        setupBackPressHandler()
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Observers
    // ────────────────────────────────────────────────────────────────────────────

    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            isSaving     = state is RecordAudioViewModel.UiState.Saving
            isProcessing = state is RecordAudioViewModel.UiState.Processing

            val showOverlay = isSaving || isProcessing
            binding.progressOverlay.visibility = if (showOverlay) View.VISIBLE else View.GONE
            binding.touchBlockOverlay.visibility = if (showOverlay) View.VISIBLE else View.GONE

            if (showOverlay) {
                handler.removeCallbacks(progressTimeoutRunnable)
                handler.postDelayed(progressTimeoutRunnable, 7000)
            } else {
                handler.removeCallbacks(progressTimeoutRunnable)
            }

            updateDismissalState()

            when (state) {
                is RecordAudioViewModel.UiState.Saving -> {
                    binding.progressText.text = "Saving..."
                }

                is RecordAudioViewModel.UiState.Saved -> onAudioSaved(state.fileName)

                is RecordAudioViewModel.UiState.Processing -> {
                    binding.progressText.text = state.stage
                    setProcessButtonState(enabled = false, label = state.stage)
                }

                is RecordAudioViewModel.UiState.Processed -> {
                    isProcessing = false
                    setProcessButtonState(enabled = true, label = "Process")
                    Toast.makeText(context, "Ready! You will be notified.", Toast.LENGTH_LONG).show()
                }

                is RecordAudioViewModel.UiState.Error -> {
                    isProcessing = false
                    setProcessButtonState(enabled = true, label = "Process")
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }

                else -> {}
            }
        }

        viewModel.recordState.observe(viewLifecycleOwner) { state ->
            isRecording = (state == RecordAudioViewModel.RecordState.RECORDING)
            isPaused = (state == RecordAudioViewModel.RecordState.PAUSED)

            when (state) {
                RecordAudioViewModel.RecordState.RECORDING -> {
                    binding.startButton.text = "Pause"
                    binding.stopButton.visibility = View.VISIBLE
                    binding.recordingTimer.visibility = View.VISIBLE
                    binding.timerSpacer.visibility = View.GONE

                    if (::bottomSheetBehavior.isInitialized) bottomSheetBehavior.isDraggable = false

                    isSheetLocked = true
                    binding.lockToggle.visibility = View.VISIBLE
                    updateLockToggleUI()
                    updateDismissalState()

                    startEqualizerAnimation(true)
                    startPulseAnimation()

                    binding.instructionText.text = "Recording in progress — tap Pause to hold, Stop to finish."
                }
                RecordAudioViewModel.RecordState.PAUSED -> {
                    binding.startButton.text = "Resume"
                    stopEqualizerAnimation()
                    stopPulseAnimation()
                    binding.instructionText.text = "Recording paused — tap Resume to continue."
                }
                RecordAudioViewModel.RecordState.STOPPED -> {
                    binding.lockToggle.visibility = View.GONE
                    updateDismissalState()
                    showSaveFileDialog()
                }
                else -> {}
            }
        }

        viewModel.elapsedTime.observe(viewLifecycleOwner) { elapsed ->
            binding.recordingTimer.text = audioPlayer.formatTime(elapsed)
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            if (!isOnline && isProcessing) {
                isProcessing = false
                binding.progressOverlay.visibility = View.GONE
                handler.removeCallbacks(progressTimeoutRunnable)
                Toast.makeText(context, "Offline", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Recording State Machine
    // ────────────────────────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        } else {
            when {
                !isRecording && !isPaused -> startRecording()
                isPaused     -> resumeRecording()
                else         -> pauseRecording()
            }
        }
    }

    private fun startRecording() {
        val file = File(requireContext().externalCacheDir, "temp.mp3")
        audioFile = file
        viewModel.startRecording(file)
    }

    private fun pauseRecording() {
        viewModel.pauseRecording()
    }

    private fun resumeRecording() {
        viewModel.resumeRecording()
    }

    private fun stopRecording() {
        viewModel.stopRecording()
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Save / Reset UI
    // ────────────────────────────────────────────────────────────────────────────

    private fun onAudioSaved(fileName: String) {
        // Hide recording controls
        binding.recordingControls.visibility = View.GONE
        binding.recordingTimer.visibility = View.GONE
        binding.timerSpacer.visibility = View.VISIBLE

        // Show mini-player + action buttons as one unit
        binding.savedSection.visibility = View.VISIBLE

        binding.currentAudioName.text = fileName.substringBeforeLast(".")
        binding.instructionText.text = "Recording saved. Preview or process it below."

        stopEqualizerAnimation()
        binding.lockToggle.visibility = View.GONE
        updateDismissalState()

        // Reset mini-player UI
        binding.playPauseButton.setImageResource(R.drawable.play)
        binding.seekBar.progress = 0
        binding.currentTime.text = "00:00"
        binding.totalTime.text = "00:00"

        // Expand sheet so buttons are always visible
        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isDraggable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun resetRecordingUI() {
        // Reset recording state
        isRecording = false
        isPaused    = false
        viewModel.releaseRecorder()
        stopPulseAnimation()
        audioPlayer.stop()
        audioFile = null

        // Restore recording controls
        binding.recordingControls.visibility = View.VISIBLE
        binding.startButton.text = "Start"
        binding.stopButton.visibility = View.GONE

        // Hide saved section entirely
        binding.savedSection.visibility = View.GONE

        // Hide timer, show spacer
        binding.recordingTimer.text = "00:00"
        binding.recordingTimer.visibility = View.GONE
        binding.timerSpacer.visibility = View.VISIBLE

        binding.instructionText.text = getString(R.string.msg_record_instruction)
        stopEqualizerAnimation()
        binding.lockToggle.visibility = View.GONE
        updateDismissalState()

        if (::bottomSheetBehavior.isInitialized) bottomSheetBehavior.isDraggable = true
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Dialogs
    // ────────────────────────────────────────────────────────────────────────────

    private fun showSaveFileDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_audio, null)
        val edit = v.findViewById<EditText>(R.id.fileNameInput)
        val buttonDiscard = v.findViewById<Button>(R.id.buttonDiscard)
        val buttonSave = v.findViewById<Button>(R.id.buttonSave)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setCancelable(false)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setDimAmount(0.8f)

        buttonDiscard.setOnClickListener {
            audioFile?.delete()
            resetRecordingUI()
            dialog.dismiss()
        }

        buttonSave.setOnClickListener {
            val name = edit.text.toString().trim()
            if (name.isNotEmpty()) {
                val uid = com.example.meetloggerv2.core.session.AuthSession().currentUserId()
                if (uid != null && audioFile != null) {
                    val targetFile = File(requireContext().externalCacheDir, "$name.mp3")
                    if (audioFile?.renameTo(targetFile) == true) audioFile = targetFile
                    viewModel.saveAudio(uid, audioFile!!, Uri.fromFile(audioFile))
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }



    private fun proceedToBackendUpload(speakers: List<String>, followUp: String) {
        val uid = com.example.meetloggerv2.core.session.AuthSession().currentUserId()
        if (uid != null && audioFile != null) {
            isProcessing = true
            viewModel.processAudio(uid, audioFile!!, Uri.fromFile(audioFile), speakers, followUp)
        }
    }

    private fun deleteCurrentAudio() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.msg_delete_this_recording)
            .setPositiveButton(R.string.dialog_delete) { _, _ ->
                val uid = com.example.meetloggerv2.core.session.AuthSession().currentUserId()
                if (uid != null && audioFile != null) viewModel.deleteAudio(uid, audioFile!!.name)
                resetRecordingUI()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Pulse Animation
    // ────────────────────────────────────────────────────────────────────────────

    private fun startPulseAnimation() {
        val ring = _binding?.recordPulseRing ?: return
        ring.visibility = View.VISIBLE
        val anim = AnimatorInflater.loadAnimator(requireContext(), R.animator.record_pulse) as AnimatorSet
        anim.setTarget(ring)
        anim.start()
        ring.tag = anim
    }

    private fun stopPulseAnimation() {
        val ring = _binding?.recordPulseRing ?: return
        (ring.tag as? AnimatorSet)?.cancel()
        ring.visibility = View.INVISIBLE
        ring.tag = null
    }

    /**
     * Enables/disables the process action column (now a LinearLayout icon+label).
     * Dims it visually when disabled and updates the label text.
     */
    private fun setProcessButtonState(enabled: Boolean, label: String) {
        val btn = _binding?.processAudioButton ?: return
        btn.isClickable = enabled
        btn.alpha = if (enabled) 1.0f else 0.45f
        val labelView = btn.getChildAt(1) as? android.widget.TextView
        labelView?.text = label
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  Bottom-sheet height / draggability helpers
    // ────────────────────────────────────────────────────────────────────────────

    private fun setFixedBottomSheetHeight(fraction: Double) {
        val d = dialog as? BottomSheetDialog ?: return
        val sheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheetBehavior = BottomSheetBehavior.from(sheet)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        requireActivity().windowManager.defaultDisplay.getMetrics(dm)
        sheet.layoutParams.height = (dm.heightPixels * fraction).toInt()
        sheet.requestLayout()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheetBehavior.isDraggable = true
    }

    private fun updateDismissalState() {
        val d = dialog as? BottomSheetDialog ?: return
        val busy = isProcessing || isSaving
        val canDismiss = if (isRecording) {
            !isSheetLocked && !busy
        } else {
            !busy
        }
        d.setCancelable(canDismiss)
        d.setCanceledOnTouchOutside(canDismiss)
        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isDraggable = canDismiss
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (isRecording) {
                        if (isSheetLocked) {
                            Toast.makeText(context, "Unlock at the top right to dismiss during recording", Toast.LENGTH_SHORT).show()
                        } else {
                            dismiss()
                        }
                    } else if (!isSaving && !isProcessing) {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    private fun toggleSheetLock() {
        isSheetLocked = !isSheetLocked
        updateLockToggleUI()
        updateDismissalState()
    }

    private fun updateLockToggleUI() {
        if (_binding == null) return
        if (isSheetLocked) {
            binding.lockToggle.setImageResource(R.drawable.ic_lock)
            binding.lockToggle.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.BLUE)
            )
            binding.lockToggle.setBackgroundResource(R.drawable.lock_button_bg)
        } else {
            binding.lockToggle.setImageResource(R.drawable.ic_unlock)
            binding.lockToggle.imageTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.onSurfaceVariant)
            )
            binding.lockToggle.setBackgroundResource(R.drawable.unlocked_button_bg)
        }
    }

    private fun startEqualizerAnimation(useMicAmplitude: Boolean) {
        stopEqualizerAnimation()
        if (_binding == null) return
        binding.recordImageView.visibility = View.GONE
        binding.equalizerContainer.visibility = View.VISIBLE

        val bars = listOf(
            binding.equalizerBar1,
            binding.equalizerBar2,
            binding.equalizerBar3,
            binding.equalizerBar4,
            binding.equalizerBar5
        )

        bars.forEach { bar ->
            bar.post { bar.pivotY = bar.height / 2f }
        }

        if (useMicAmplitude) {
            amplitudeHandler = Handler(Looper.getMainLooper())
            amplitudeRunnable = object : Runnable {
                override fun run() {
                    if (_binding == null) return
                    val amp = viewModel.getMaxAmplitude()
                    val normalized = (amp.toFloat() / 12000f).coerceIn(0.15f, 1.6f)
                    
                    val factors = listOf(0.7f, 1.2f, 0.8f, 1.4f, 0.9f)
                    bars.forEachIndexed { i, bar ->
                        val targetScale = (normalized * factors[i]).coerceIn(0.15f, 1.7f)
                        bar.animate()
                            .scaleY(targetScale)
                            .setDuration(90)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                    
                    amplitudeHandler?.postDelayed(this, 100)
                }
            }
            amplitudeHandler?.post(amplitudeRunnable!!)
        } else {
            // Asynchronous natural wave for playback
            val durations = listOf(350L, 250L, 400L, 300L, 320L)
            val scales = listOf(
                floatArrayOf(0.3f, 1.2f, 0.3f),
                floatArrayOf(0.2f, 1.3f, 0.2f),
                floatArrayOf(0.4f, 1.1f, 0.4f),
                floatArrayOf(0.2f, 1.4f, 0.2f),
                floatArrayOf(0.3f, 1.2f, 0.3f)
            )

            bars.forEachIndexed { index, bar ->
                val animator = android.animation.ObjectAnimator.ofFloat(bar, "scaleY", *scales[index]).apply {
                    duration = durations[index]
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    repeatMode = android.animation.ValueAnimator.REVERSE
                    interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                }
                animator.start()
                equalizerAnimators.add(animator)
            }
        }
    }

    private fun stopEqualizerAnimation() {
        amplitudeHandler?.removeCallbacks(amplitudeRunnable ?: Runnable {})
        amplitudeHandler = null
        amplitudeRunnable = null

        equalizerAnimators.forEach { it.cancel() }
        equalizerAnimators.clear()
        
        if (_binding != null) {
            binding.equalizerContainer.visibility = View.GONE
            binding.recordImageView.visibility = View.VISIBLE
            
            val bars = listOf(
                binding.equalizerBar1,
                binding.equalizerBar2,
                binding.equalizerBar3,
                binding.equalizerBar4,
                binding.equalizerBar5
            )
            bars.forEach { it.scaleY = 1.0f }
        }
    }
}
