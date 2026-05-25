package com.example.meetloggerv2.ui.audio.fragment

import android.Manifest
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.meetloggerv2.R
import android.content.pm.PackageManager
import android.media.MediaRecorder
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
import com.bumptech.glide.Glide
import com.example.meetloggerv2.databinding.FragmentRecordAudioBottomsheetBinding
import com.example.meetloggerv2.core.media.AudioPlayerManager
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.util.ViewDragHelper
import com.example.meetloggerv2.ui.audio.viewmodel.RecordAudioViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.example.meetloggerv2.core.session.AuthSession
import java.io.File

class RecordAudioBottomsheetFragment : BottomSheetDialogFragment() {
    private var _binding: FragmentRecordAudioBottomsheetBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RecordAudioViewModel by viewModels()
    private val audioPlayer = AudioPlayerManager()
    private val authSession = AuthSession()
    private lateinit var networkMonitor: NetworkMonitor

    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var isProcessing = false
    private var isSaving = false
    private var temporarySpeakerList: List<String>? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var recordingStartTime: Long = 0L
    private var elapsedTimeBeforePause: Long = 0L

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                val time = System.currentTimeMillis() - recordingStartTime + elapsedTimeBeforePause
                binding.recordingTimer.text = audioPlayer.formatTime(time.toInt())
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordAudioBottomsheetBinding.inflate(inflater, container, false)
        networkMonitor = NetworkMonitor(requireContext())
        setupUI()
        setupObservers()
        return binding.root
    }

    private fun setupUI() {
        binding.startButton.setOnClickListener { checkAndRequestPermissions() }
        binding.stopButton.setOnClickListener { stopRecording() }
        
        binding.playPauseButton.setOnClickListener { 
            if (isProcessing) return@setOnClickListener
            
            if (audioPlayer.isReady()) {
                val isNowPlaying = audioPlayer.togglePlayPause()
                if (isNowPlaying) {
                    binding.playPauseButton.setImageResource(R.drawable.pause1)
                    Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
                } else {
                    binding.playPauseButton.setImageResource(R.drawable.play)
                    Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
                }
            } else {
                // Not started, try to start from file
                audioFile?.let { file ->
                    audioPlayer.play(file.absolutePath, {
                        binding.playPauseButton.setImageResource(R.drawable.play)
                        binding.seekBar.progress = 0
                        Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
                    }) { current, total ->
                        binding.seekBar.max = total
                        binding.seekBar.progress = current
                        binding.currentTime.text = audioPlayer.formatTime(current)
                        binding.totalTime.text = audioPlayer.formatTime(total)
                    }
                    binding.playPauseButton.setImageResource(R.drawable.pause1)
                    Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
                }
            }
        }
        
        binding.stopButtonMini.setOnClickListener { 
            if (!isProcessing) { 
                audioPlayer.stop()
                binding.playPauseButton.setImageResource(R.drawable.play)
                binding.seekBar.progress = 0
                binding.currentTime.text = "00:00"
                Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
            } 
        }

        binding.processAudioButton.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            showSpeakerSelectionDialog()
        }
        binding.newRecordingButton.setOnClickListener { resetRecordingUI() }
        binding.deleteButton.setOnClickListener { deleteCurrentAudio() }
        setupBackPressHandler()
        setupMiniPlayerDragging()
    }

    private fun showSpeakerSelectionDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_selection, null)
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        v.findViewById<Button>(R.id.proceedButton).setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, R.string.error_selection_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (checkedId == R.id.radioYes) { d.dismiss(); showSpeakerInputDialog() }
            else { d.dismiss(); showFollowUpSelectionDialog(emptyList()) }
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        d.show()
    }

    private fun showSpeakerInputDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_input, null)
        v.setBackgroundResource(R.drawable.dialog_background_rounded)
        val container = v.findViewById<LinearLayout>(R.id.speakerContainer)
        val addSpeakerBtn = v.findViewById<Button>(R.id.addSpeakerButton)
        val proceedBtn = v.findViewById<Button>(R.id.proceedButton)
        val speakerList = mutableListOf<String>()

        val updateButtons = {
            val allFilled = speakerList.all { it.isNotBlank() } && speakerList.isNotEmpty()
            proceedBtn.isEnabled = allFilled
            addSpeakerBtn.isEnabled = allFilled && speakerList.size < 10
        }

        val addInput = {
            val item = LayoutInflater.from(requireContext()).inflate(R.layout.item_speaker_input, container, false)
            val idx = speakerList.size
            speakerList.add("")
            val input = item.findViewById<EditText>(R.id.speakerNameInput)
            item.findViewById<TextView>(R.id.speakerLabel).text = "Speaker ${('A' + idx)}"
            input.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    speakerList[idx] = s.toString().trim()
                    updateButtons()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            container.addView(item)
            input.post {
                input.requestFocus()
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
            updateButtons()
        }

        addInput()
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        addSpeakerBtn.setOnClickListener { addInput() }
        proceedBtn.setOnClickListener {
            val filtered = speakerList.filter { it.isNotBlank() }
            d.dismiss()
            showFollowUpSelectionDialog(filtered)
        }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener { 
            d.dismiss()
            showSpeakerSelectionDialog()
        }
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        d.show()
    }

    private fun showFollowUpSelectionDialog(speakers: List<String>) {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_follow_up_selection, null)
        val spinner = v.findViewById<Spinner>(R.id.spinnerFiles)
        val proceed = v.findViewById<Button>(R.id.proceedButton)
        v.findViewById<RadioGroup>(R.id.radioGroup).setOnCheckedChangeListener { _, id ->
            spinner.visibility = if (id == R.id.radioYes) View.VISIBLE else View.GONE
            if (id == R.id.radioYes) {
                proceed.isEnabled = false
                authSession.currentUserId()?.let { viewModel.fetchUserFiles(it) }
            } else proceed.isEnabled = true
        }
        viewModel.userFiles.observe(viewLifecycleOwner) { files ->
            spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, files.map { it.substringBeforeLast(".") })
            spinner.setTag(files)
            proceed.isEnabled = true
        }
        val d = MaterialAlertDialogBuilder(requireContext()).setView(v).setCancelable(false).create()
        proceed.setOnClickListener {
            val checkedId = v.findViewById<RadioGroup>(R.id.radioGroup).checkedRadioButtonId
            if (checkedId == -1) {
                Toast.makeText(context, "Please make a selection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val followUp = if (checkedId == R.id.radioYes) {
                (spinner.tag as? List<String>)?.getOrNull(spinner.selectedItemPosition) ?: ""
            } else ""
            d.dismiss(); proceedToBackendUpload(speakers, followUp)
        }
        v.findViewById<Button>(R.id.cancelButton).setOnClickListener { d.dismiss() }
        v.findViewById<ImageView>(R.id.backButton).setOnClickListener { 
            d.dismiss()
            showSpeakerSelectionDialog()
        }
        d.show()
    }

    private fun proceedToBackendUpload(speakers: List<String>, followUp: String) {
        val uid = authSession.currentUserId()
        if (uid != null && audioFile != null) {
            isProcessing = true
            viewModel.processAudio(uid, audioFile!!, Uri.fromFile(audioFile), speakers, followUp)
        }
    }

    private fun setupObservers() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            isSaving = state is RecordAudioViewModel.UiState.Saving
            isProcessing = state is RecordAudioViewModel.UiState.Processing
            binding.progressOverlay.visibility = if (isSaving || isProcessing) View.VISIBLE else View.GONE
            binding.touchBlockOverlay.visibility = if (isSaving || isProcessing) View.VISIBLE else View.GONE
            updateDismissalState()
            
            when (state) {
                is RecordAudioViewModel.UiState.Saved -> { 
                    binding.miniPlayer.visibility = View.VISIBLE
                    binding.startButton.visibility = View.GONE
                    binding.stopButton.visibility = View.GONE
                    binding.recordingTimer.visibility = View.GONE
                    
                    binding.currentAudioName.text = state.fileName.substringBeforeLast(".")
                    binding.instructionText.text = "Recording saved. Preview or process it below."
                    
                    binding.processAudioButton.visibility = View.VISIBLE
                    binding.newRecordingButton.visibility = View.VISIBLE
                    binding.deleteButton.visibility = View.VISIBLE
                    Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
                }
                is RecordAudioViewModel.UiState.Processing -> {
                    binding.processAudioButton.text = state.stage
                    binding.processAudioButton.isEnabled = false
                }
                is RecordAudioViewModel.UiState.Processed -> {
                    isProcessing = false
                    binding.processAudioButton.isEnabled = true
                    binding.processAudioButton.text = "Process"
                    Toast.makeText(context, "Ready! You will be notified.", Toast.LENGTH_LONG).show()
                }
                is RecordAudioViewModel.UiState.Error -> {
                    isProcessing = false
                    binding.processAudioButton.isEnabled = true
                    binding.processAudioButton.text = "Process"
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
        networkMonitor.observe(viewLifecycleOwner) { isOnline -> if (!isOnline && isProcessing) { isProcessing = false; binding.progressOverlay.visibility = View.GONE; Toast.makeText(context, "Offline", Toast.LENGTH_SHORT).show() } }
    }

    override fun onStart() { super.onStart(); setFixedBottomSheetHeight(0.65) }

    private fun setFixedBottomSheetHeight(p: Double) {
        val d = dialog as? BottomSheetDialog ?: return
        val b = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        bottomSheetBehavior = BottomSheetBehavior.from(b)
        val dm = DisplayMetrics()
        requireActivity().windowManager.defaultDisplay.getMetrics(dm)
        b.layoutParams.height = (dm.heightPixels * p).toInt()
        b.requestLayout()
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun updateDismissalState() {
        val d = dialog as? BottomSheetDialog ?: return
        d.setCancelable(!isProcessing && !isSaving)
        d.setCanceledOnTouchOutside(!isProcessing && !isSaving)
        if (::bottomSheetBehavior.isInitialized) bottomSheetBehavior.isDraggable = !isProcessing && !isSaving
    }

    private fun startRecording() {
        try {
            audioFile = File(requireContext().externalCacheDir, "temp.mp3")
            mediaRecorder = MediaRecorder().apply { 
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start() 
            }
            isRecording = true
            binding.startButton.text = "Pause"
            binding.stopButton.visibility = View.VISIBLE
            binding.recordingTimer.visibility = View.VISIBLE
            recordingStartTime = System.currentTimeMillis()
            handler.post(timerRunnable)
            bottomSheetBehavior.isDraggable = false
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
        } catch (e: Exception) {}
    }

    private fun stopRecording() { 
        try { 
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            handler.removeCallbacks(timerRunnable)
            Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
            showSaveFileDialog() 
        } catch (e: Exception) {} 
    }

    private fun showSaveFileDialog() {
        val v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_audio, null)
        val edit = v.findViewById<EditText>(R.id.fileNameInput)
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(v)
            .setCancelable(false)
            .setPositiveButton(R.string.dialog_save, null) // Set to null to override later
            .setNegativeButton(R.string.dialog_discard) { _, _ ->
                audioFile?.delete()
                resetRecordingUI()
            }
            .create()

        dialog.show()

        // Override positive button to prevent auto-dismissal when validation fails
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val name = edit.text.toString().trim()
            if (name.isNotEmpty()) {
                val uid = authSession.currentUserId()
                if (uid != null && audioFile != null) {
                    val targetFile = File(requireContext().externalCacheDir, "$name.mp3")
                    if (audioFile?.renameTo(targetFile) == true) {
                        audioFile = targetFile
                    }
                    viewModel.saveAudio(uid, audioFile!!, Uri.fromFile(audioFile))
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1002)
        else if (!isRecording) startRecording() else resumeRecording()
    }

    private fun resumeRecording() {
        if (isPaused) { 
            mediaRecorder?.resume()
            isPaused = false
            binding.startButton.text = "Pause"
            recordingStartTime = System.currentTimeMillis()
            handler.post(timerRunnable)
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
        } else { 
            mediaRecorder?.pause()
            isPaused = true
            binding.startButton.text = "Resume"
            elapsedTimeBeforePause += System.currentTimeMillis() - recordingStartTime
            handler.removeCallbacks(timerRunnable)
            Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
        }
    }

    private fun resetRecordingUI() {
        binding.miniPlayer.visibility = View.GONE
        binding.processAudioButton.visibility = View.GONE
        binding.newRecordingButton.visibility = View.GONE
        binding.deleteButton.visibility = View.GONE
        binding.startButton.text = "Start"
        binding.startButton.visibility = View.VISIBLE
        binding.stopButton.visibility = View.GONE
        binding.recordingTimer.text = "00:00"
        binding.instructionText.text = "Begin recording audio by selecting the Start button."
        audioPlayer.stop()
        audioFile = null
        Glide.with(this).load(R.drawable.record).into(binding.recordImageView)
    }

    private fun deleteCurrentAudio() {
        MaterialAlertDialogBuilder(requireContext()).setMessage(R.string.msg_delete_this_recording).setPositiveButton(R.string.dialog_delete) { _, _ ->
            val uid = authSession.currentUserId()
            if (uid != null && audioFile != null) viewModel.deleteAudio(uid, audioFile!!.name)
            resetRecordingUI()
        }.setNegativeButton(R.string.dialog_cancel, null).show()
    }

    private fun setupMiniPlayerDragging() {
        ViewDragHelper().attach(binding.miniPlayer)
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!isRecording && !isSaving && !isProcessing) { isEnabled = false; requireActivity().onBackPressedDispatcher.onBackPressed() } }
        })
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
