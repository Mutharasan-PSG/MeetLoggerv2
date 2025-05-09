package com.example.meetloggerv2

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.example.meetloggerv2.databinding.FragmentRecordAudioBottomsheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storageMetadata
import com.google.gson.Gson
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class RecordAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentRecordAudioBottomsheetBinding? = null
    private val binding get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var isPlaying = false
    private var isPaused = false
    private var isProcessing = false
    private var isSaving = false
    private var fileName = ""
    private var selectedAudioUri: Uri? = null
    private var temporarySpeakerList: List<String>? = null
    private var operationStartTime: Long = 0L
    private var hasShownSlowToast = false
    private val handler = Handler(Looper.getMainLooper())
    private val internetCheckTask = object : Runnable {
        override fun run() {
            checkInternetStatus()
            handler.postDelayed(this, 2000)
        }
    }
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var touchBlockOverlay: FrameLayout? = null
    // Timer variables for recording
    private var recordingStartTime: Long = 0L
    private var elapsedTimeBeforePause: Long = 0L
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRecording && !isPaused) {
                val elapsedTime = System.currentTimeMillis() - recordingStartTime + elapsedTimeBeforePause
                updateTimerDisplay(elapsedTime)
                handler.postDelayed(this, 1000)
            }
        }
    }
    // SeekBar update for playback
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            if (mediaPlayer?.isPlaying == true) {
                val currentPosition = mediaPlayer!!.currentPosition
                binding.seekBar.progress = currentPosition
                binding.currentTime.text = formatTime(currentPosition)
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val PERMISSION_REQUEST_CODE = 1002

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordAudioBottomsheetBinding.inflate(inflater, container, false)
        touchBlockOverlay = binding.touchBlockOverlay
        setupListeners()
        setupBackPressHandler()
        setupMiniPlayerDragging()

        binding.startButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start, 0, 0, 0)
        setDrawableSize(binding.startButton, R.drawable.start, 80, 80)
        binding.progressOverlay.visibility = View.GONE
        binding.recordingTimer.visibility = View.GONE
        binding.recordImageView.setImageResource(R.drawable.record)
        setInstructionText(InstructionState.INITIAL) // Set initial instruction
        return binding.root
    }

    // Enum to manage instruction states
    private enum class InstructionState {
        INITIAL, RECORDING, SAVED
    }

    // Helper function to set instruction text
    private fun setInstructionText(state: InstructionState) {
        val instruction = when (state) {
            InstructionState.INITIAL -> "Begin recording audio by selecting the Start button."
            InstructionState.RECORDING -> "Control the recording using the Pause/Resume and Stop buttons."
            InstructionState.SAVED -> "Play the recorded audio, process it, or start a new recording."
        }
        binding.instructionText.text = instruction
        Log.d("RecordAudioFragment", "Instruction set to: $instruction")
    }

    override fun onStart() {
        super.onStart()
        setFixedBottomSheetHeight(0.65)
        setupBottomSheetBehavior()
    }

    private fun setFixedBottomSheetHeight(percentage: Double) {
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            bottomSheetBehavior = BottomSheetBehavior.from(it)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            val screenHeight = displayMetrics.heightPixels
            val targetHeight = (screenHeight * percentage).toInt()
            it.layoutParams.height = targetHeight
            it.requestLayout()
            bottomSheetBehavior.peekHeight = targetHeight
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupBottomSheetBehavior() {
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        bottomSheetBehavior.isDraggable = true
        updateDismissalState()
    }

    private fun updateDismissalState() {
        val dialog = dialog as? BottomSheetDialog ?: return
        dialog.setCancelable(!isProcessing && !isSaving)
        dialog.setCanceledOnTouchOutside(!isProcessing && !isSaving)
        bottomSheetBehavior.isDraggable = !isProcessing && !isSaving
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRecording || isSaving) {
                    Toast.makeText(requireContext(), "Cannot exit while recording or saving", Toast.LENGTH_SHORT).show()
                } else if (isProcessing) {
                    Toast.makeText(requireContext(), "Operation in progress, please wait", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = try {
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } catch (e: IllegalStateException) {
            Log.e("RecordAudioFragment", "Context unavailable, assuming no network: ${e.message}")
            return false
        }
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkInternetStatus() {
        if (!isAdded || !isProcessing) return
        val isOnline = isNetworkAvailable()
        if (!isOnline) {
            abortCurrentOperation()
        } else {
            val elapsedTime = System.currentTimeMillis() - operationStartTime
            if (elapsedTime > 5000 && !hasShownSlowToast) {
                Toast.makeText(requireContext(), "Internet is slow, please wait...", Toast.LENGTH_SHORT).show()
                hasShownSlowToast = true
            }
        }
    }

    private fun abortCurrentOperation() {
        if (isAdded) {
            binding.progressOverlay.visibility = View.GONE
            touchBlockOverlay?.visibility = View.GONE
            Toast.makeText(requireContext(), "Operation aborted due to no internet", Toast.LENGTH_SHORT).show()
        }
        isProcessing = false
        hasShownSlowToast = false
        cleanupTempFile(audioFile)
        binding.processAudioButton.isEnabled = true
        binding.processAudioButton.text = "PROCESS"
        updateDismissalState()
    }

    private fun setupListeners() {
        binding.startButton.setOnClickListener {
            checkAndRequestPermissions()
        }

        binding.stopButton.setOnClickListener {
            stopRecording()
        }

        binding.playPauseButton.setOnClickListener { if (!isProcessing) togglePlayPause() }
        binding.stopButtonMini.setOnClickListener { if (!isProcessing) stopPlayback() }
        binding.prevButton.setOnClickListener { if (!isProcessing) rewindAudio() }
        binding.nextButton.setOnClickListener { if (!isProcessing) fastForwardAudio() }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isProcessing) {
                    mediaPlayer?.seekTo(progress)
                    binding.currentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.processAudioButton.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (audioFile == null || !audioFile!!.exists()) {
                Toast.makeText(context, "No recorded audio file found!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isNetworkAvailable()) {
                Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            selectedAudioUri = Uri.fromFile(audioFile)
            showSpeakerSelectionDialog()
        }

        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        binding.newRecordingButton.setOnClickListener {
            cleanupTempFile(audioFile)
            resetUI()
            setInstructionText(InstructionState.INITIAL) // Reset instruction on new recording
            Toast.makeText(context, "Ready for new recording", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniPlayerDragging() {
        binding.miniPlayer.setOnTouchListener { v, event ->
            if (isProcessing) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = v.x
                    initialY = v.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    v.x = initialX + deltaX
                    v.y = initialY + deltaY

                    val parent = v.parent as View
                    v.x = v.x.coerceIn(0f, parent.width - v.width.toFloat())
                    v.y = v.y.coerceIn(0f, parent.height - v.height.toFloat())
                    true
                }
                else -> false
            }
        }
    }

    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private fun startRecording() {
        try {
            fileName = "${requireContext().externalCacheDir?.absolutePath}/temp_audio.mp3"
            audioFile = File(fileName)
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(fileName)
                prepare()
                start()
            }
            isRecording = true
            isPaused = false
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
            binding.startButton.text = "Pause"
            setDrawableSize(binding.startButton, R.drawable.pause, 80, 80)
            binding.stopButton.isVisible = true
            setDrawableSize(binding.stopButton, R.drawable.stop, 80, 80)

            binding.recordingTimer.visibility = View.VISIBLE
            binding.recordingTimer.text = "00:00"
            recordingStartTime = System.currentTimeMillis()
            elapsedTimeBeforePause = 0L
            handler.post(timerRunnable)
            lockUIDuringRecording()
            setInstructionText(InstructionState.RECORDING) // Update instruction
        } catch (e: Exception) {
            Toast.makeText(context, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun lockUIDuringRecording() {
        touchBlockOverlay?.visibility = View.VISIBLE
        val dialog = dialog as? BottomSheetDialog
        dialog?.setCancelable(false)
        dialog?.setCanceledOnTouchOutside(false)
        bottomSheetBehavior.isDraggable = false
    }

    private fun resumeRecording() {
        if (isPaused) {
            mediaRecorder?.resume()
            isPaused = false
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
            binding.startButton.text = "Pause"
            setDrawableSize(binding.startButton, R.drawable.pause, 80, 80)
            recordingStartTime = System.currentTimeMillis()
            binding.recordingTimer.visibility = View.VISIBLE
            handler.post(timerRunnable)
            setInstructionText(InstructionState.RECORDING) // Update instruction
        } else {
            mediaRecorder?.pause()
            isPaused = true
            binding.recordImageView.setImageResource(R.drawable.record)
            binding.startButton.text = "Resume"
            setDrawableSize(binding.startButton, R.drawable.resume, 80, 80)
            elapsedTimeBeforePause += System.currentTimeMillis() - recordingStartTime
            handler.removeCallbacks(timerRunnable)
            setInstructionText(InstructionState.RECORDING) // Keep recording instruction during pause
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            binding.recordImageView.setImageResource(R.drawable.record)
            binding.startButton.isVisible = false
            binding.stopButton.isVisible = false
            handler.removeCallbacks(timerRunnable)
            binding.recordingTimer.text = "00:00"
            binding.recordingTimer.visibility = View.GONE
            elapsedTimeBeforePause = 0L
            showSaveFileDialog()
        } catch (e: IllegalStateException) {
            Toast.makeText(context, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
            unlockUIAfterRecording()
            binding.recordingTimer.visibility = View.GONE
        }
    }

    private fun updateTimerDisplay(elapsedTime: Long) {
        val seconds = (elapsedTime / 1000) % 60
        val minutes = (elapsedTime / (1000 * 60)) % 60
        binding.recordingTimer.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun unlockUIAfterRecording() {
        touchBlockOverlay?.visibility = View.GONE
        val dialog = dialog as? BottomSheetDialog
        dialog?.setCancelable(true)
        dialog?.setCanceledOnTouchOutside(true)
        bottomSheetBehavior.isDraggable = true
    }

    private fun showSaveFileDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_audio, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Save Audio File")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .create()
        val fileNameInput = dialogView.findViewById<EditText>(R.id.fileNameInput)
        val saveFileButton = dialogView.findViewById<Button>(R.id.saveFileButton)

        touchBlockOverlay?.visibility = View.VISIBLE
        lockUIDuringRecording()

        saveFileButton.setOnClickListener {
            val enteredFileName = fileNameInput.text.toString().trim()
            if (enteredFileName.isNotEmpty()) {
                val newFile = File(requireContext().externalCacheDir, "$enteredFileName.mp3")
                audioFile?.renameTo(newFile)
                audioFile = newFile
                isSaving = true
                binding.progressOverlay.visibility = View.VISIBLE
                dialog.dismiss()
                saveAudioToFirebase(Uri.fromFile(audioFile)) {
                    if (isAdded) {
                        binding.progressOverlay.visibility = View.GONE
                        touchBlockOverlay?.visibility = View.GONE
                        isSaving = false
                        unlockUIAfterRecording()
                        binding.miniPlayer.visibility = View.VISIBLE
                        binding.currentAudioName.text = enteredFileName
                        binding.processAudioButton.isVisible = true
                        binding.processAudioButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.process, 0, 0, 0)
                        setDrawableSize(binding.processAudioButton, R.drawable.process, 80, 80)
                        binding.newRecordingButton.isVisible = true
                        binding.newRecordingButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start, 0, 0, 0)
                        setDrawableSize(binding.newRecordingButton, R.drawable.start, 80, 80)
                        binding.deleteButton.isVisible = true
                        binding.deleteButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.delete, 0, 0, 0)
                        setDrawableSize(binding.deleteButton, R.drawable.delete, 80, 80)
                        setInstructionText(InstructionState.SAVED) // Update instruction after save
                    }
                }
            } else {
                Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setOnDismissListener {
            if (!isSaving) {
                handler.postDelayed({
                    touchBlockOverlay?.visibility = View.GONE
                    unlockUIAfterRecording()
                    setInstructionText(InstructionState.RECORDING) // Keep recording instruction if dialog canceled
                }, 200)
            }
        }

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                Toast.makeText(requireContext(), "Cannot exit while saving is pending", Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
        }

        dialog.show()
    }

    private fun saveAudioToFirebase(uri: Uri, onComplete: () -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show()
            onComplete()
            unlockUIAfterRecording()
            return
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(context, "No internet connection, saved locally only", Toast.LENGTH_SHORT).show()
            onComplete()
            unlockUIAfterRecording()
            return
        }

        val fileName = audioFile?.name ?: "temp_audio.mp3"
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")
        val metadata = storageMetadata { contentType = "audio/mpeg" }

       // Toast.makeText(context, "Saving audio to Firebase...", Toast.LENGTH_SHORT).show()
        storageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    onComplete()
                    unlockUIAfterRecording()
                    return@addOnSuccessListener
                }
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    if (!isAdded || !isNetworkAvailable()) {
                        onComplete()
                        unlockUIAfterRecording()
                        return@addOnSuccessListener
                    }
                    val audioUrl = downloadUri.toString()
                    val fileData = hashMapOf(
                        "fileName" to fileName,
                        "audioUrl" to audioUrl,
                        "status" to "saved",
                        "OriginalLanguage" to "en",
                        "timestamp_clientUpload" to FieldValue.serverTimestamp()
                    )
                    FirebaseFirestore.getInstance()
                        .collection("ProcessedDocs")
                        .document(userId)
                        .collection("UserFiles")
                        .document(fileName)
                        .set(fileData)
                        .addOnSuccessListener {
                            if (!isAdded || !isNetworkAvailable()) {
                                onComplete()
                                unlockUIAfterRecording()
                                return@addOnSuccessListener
                            }
                           // Toast.makeText(context, "Audio saved to Firebase!", Toast.LENGTH_SHORT).show()
                            onComplete()
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded) {
                                onComplete()
                                unlockUIAfterRecording()
                                return@addOnFailureListener
                            }
                            Toast.makeText(context, "Failed to save metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                            onComplete()
                            unlockUIAfterRecording()
                        }
                }.addOnFailureListener { e ->
                    if (!isAdded) {
                        onComplete()
                        unlockUIAfterRecording()
                        return@addOnFailureListener
                    }
                    Toast.makeText(context, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete()
                    unlockUIAfterRecording()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) {
                    onComplete()
                    unlockUIAfterRecording()
                    return@addOnFailureListener
                }
                Toast.makeText(context, "Failed to save to Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
                onComplete()
                unlockUIAfterRecording()
            }
    }

    private fun showSpeakerSelectionDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_selection, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val radioYes = dialogView.findViewById<RadioButton>(R.id.radioYes)
        val radioNo = dialogView.findViewById<RadioButton>(R.id.radioNo)
        val proceedButton = dialogView.findViewById<Button>(R.id.proceedButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        touchBlockOverlay?.visibility = View.VISIBLE

        alertDialog.setOnShowListener {
            val messageText = dialogView.findViewById<TextView>(R.id.messageText)
            messageText.typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
            proceedButton.typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
            cancelButton.typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
            proceedButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.BLUE))
            cancelButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            radioYes.typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
            radioNo.typeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)

            val layoutParams = alertDialog.window?.attributes
            layoutParams?.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
            alertDialog.window?.attributes = layoutParams
        }

        proceedButton.setOnClickListener {
            when (radioGroup.checkedRadioButtonId) {
                R.id.radioYes -> {
                    alertDialog.dismiss()
                    handler.postDelayed({
                        showSpeakerInputDialog()
                    }, 200)
                }
                R.id.radioNo -> {
                    alertDialog.dismiss()
                    handler.postDelayed({
                        showFollowUpSelectionDialog()
                    }, 200)
                }
                else -> Toast.makeText(requireContext(), "Please select an option", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
            handler.postDelayed({
                touchBlockOverlay?.visibility = View.GONE
                cleanupTempFile(audioFile)
            }, 200)
        }

        alertDialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay?.visibility = View.GONE
            }, 200)
        }

        alertDialog.show()
    }

    private fun showSpeakerInputDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_input, null)
        val speakerContainer = dialogView.findViewById<LinearLayout>(R.id.speakerContainer)
        val addSpeakerButton = dialogView.findViewById<Button>(R.id.addSpeakerButton)
        val proceedButton = dialogView.findViewById<Button>(R.id.proceedButton)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)
        val speakerScrollView = dialogView.findViewById<ScrollView>(R.id.speakerScrollView)

        val speakerList = mutableListOf<String>()
        addSpeakerInput(speakerContainer, speakerList)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        touchBlockOverlay?.visibility = View.VISIBLE

        fun updateDialogHeight() {
            speakerScrollView.post {
                val params = alertDialog.window?.attributes
                params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
                alertDialog.window?.attributes = params
            }
        }

        addSpeakerButton.setOnClickListener {
            if (speakerList.isNotEmpty() && speakerList.last().isBlank()) {
                Toast.makeText(requireContext(), "Enter a name before adding another.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (speakerList.size < 10) {
                addSpeakerInput(speakerContainer, speakerList)
                updateDialogHeight()
            } else {
                Toast.makeText(requireContext(), "Maximum 10 speakers allowed", Toast.LENGTH_SHORT).show()
            }
        }

        proceedButton.setOnClickListener {
            val filledSpeakers = speakerList.filter { it.isNotBlank() }
            Log.d("SpeakerInput", "Proceed clicked with speakers: $filledSpeakers")
            if (filledSpeakers.isEmpty()) {
                Toast.makeText(requireContext(), "Enter at least one speaker name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            temporarySpeakerList = filledSpeakers
            alertDialog.dismiss()
            handler.postDelayed({
                showFollowUpSelectionDialog()
            }, 200)
        }

        backButton.setOnClickListener {
            Log.d("SpeakerInput", "User went back to selection dialog.")
            alertDialog.dismiss()
            handler.postDelayed({
                showSpeakerSelectionDialog()
            }, 200)
        }

        alertDialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay?.visibility = View.GONE
            }, 200)
        }

        alertDialog.show()
        updateDialogHeight()
    }

    private fun addSpeakerInput(container: LinearLayout, speakerList: MutableList<String>) {
        val speakerIndex = speakerList.size
        val speakerView = LayoutInflater.from(requireContext()).inflate(R.layout.item_speaker_input, container, false)
        val speakerLabel = speakerView.findViewById<TextView>(R.id.speakerLabel)
        val speakerNameInput = speakerView.findViewById<EditText>(R.id.speakerNameInput)

        speakerLabel.text = "Speaker ${'A' + speakerIndex} -"
        speakerList.add("")
        speakerNameInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                speakerList[speakerIndex] = s.toString().trim()
                Log.d("SpeakerInput", "Speaker $speakerIndex updated: ${speakerList[speakerIndex]}")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        container.addView(speakerView)
    }

    private fun showFollowUpSelectionDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_follow_up_selection, null)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)
        val radioYes = dialogView.findViewById<RadioButton>(R.id.radioYes)
        val radioNo = dialogView.findViewById<RadioButton>(R.id.radioNo)
        val spinnerFiles = dialogView.findViewById<Spinner>(R.id.spinnerFiles)
        val proceedButton = dialogView.findViewById<Button>(R.id.proceedButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        proceedButton.isEnabled = false
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userFilesRef = FirebaseFirestore.getInstance()
            .collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        touchBlockOverlay?.visibility = View.VISIBLE

        alertDialog.setOnShowListener {
            val layoutParams = alertDialog.window?.attributes
            layoutParams?.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
            alertDialog.window?.attributes = layoutParams
        }

        backButton.setOnClickListener {
            alertDialog.dismiss()
            handler.postDelayed({
                showSpeakerInputDialog()
            }, 200)
        }

        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioYes -> {
                    spinnerFiles.visibility = View.VISIBLE
                    fetchUserFiles(userFilesRef, spinnerFiles, proceedButton)
                }
                R.id.radioNo -> {
                    spinnerFiles.visibility = View.GONE
                    proceedButton.isEnabled = true
                }
            }
        }

        proceedButton.setOnClickListener {
            val selectedFileName = if (radioYes.isChecked) {
                val selectedPosition = spinnerFiles.selectedItemPosition
                val originalFileNames = spinnerFiles.getTag() as? List<String>
                originalFileNames?.getOrNull(selectedPosition) ?: ""
            } else ""
            val speakers = temporarySpeakerList ?: emptyList()
            alertDialog.dismiss()
            handler.postDelayed({
                processAudio(speakers, selectedFileName)
            }, 200)
            temporarySpeakerList = null
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
            handler.postDelayed({
                touchBlockOverlay?.visibility = View.GONE
                cleanupTempFile(audioFile)
            }, 200)
        }

        alertDialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay?.visibility = View.GONE
            }, 200)
        }

        alertDialog.show()
    }

    private fun fetchUserFiles(userFilesRef: CollectionReference, spinner: Spinner, proceedButton: Button) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            spinner.visibility = View.GONE
            return
        }

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        touchBlockOverlay?.visibility = View.VISIBLE

        userFilesRef.get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                isProcessing = false
                touchBlockOverlay?.visibility = View.GONE
                val fileNames = snapshot.documents.mapNotNull { it.getString("fileName") }
                if (fileNames.isEmpty()) {
                    spinner.visibility = View.GONE
                    Toast.makeText(requireContext(), "No previous files found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                val displayFileNames = fileNames.map { it.substringBeforeLast(".") }
                val adapter = object : ArrayAdapter<String>(
                    requireContext(), R.layout.spinner_dropdown_item, R.id.spinner_dropdown_text, displayFileNames
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        view.setSingleLine(false)
                        view.maxLines = 2
                        view.ellipsize = TextUtils.TruncateAt.END
                        view.setPadding(15, 15, 15, 15)
                        return view
                    }
                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getDropDownView(position, convertView, parent)
                        val textView = view.findViewById<TextView>(R.id.spinner_dropdown_text)
                        textView.setSingleLine(false)
                        textView.maxLines = 3
                        textView.ellipsize = TextUtils.TruncateAt.END
                        textView.setPadding(15, 15, 15, 15)
                        val layoutParams = ViewGroup.LayoutParams(
                            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 250f, resources.displayMetrics).toInt(),
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        view.layoutParams = layoutParams
                        return view
                    }
                }
                spinner.adapter = adapter
                spinner.dropDownWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 250f, resources.displayMetrics).toInt()
                spinner.setTag(fileNames)
                proceedButton.isEnabled = true
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                isProcessing = false
                touchBlockOverlay?.visibility = View.GONE
                Log.e("FetchFiles", "Error fetching files: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load files: ${e.message}", Toast.LENGTH_SHORT).show()
                spinner.visibility = View.GONE
            }
    }

    private fun processAudio(speakerNames: List<String>, followUpFileName: String = "") {
        val filteredSpeakers = speakerNames.filter { it.isNotBlank() }
        Log.d("ProcessAudio", "Processing audio with speakers: $filteredSpeakers, followUp: $followUpFileName")

        selectedAudioUri?.let { uri ->
            val file = uriToFile(uri)
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            if (file == null || userId == null) {
                Toast.makeText(context, "Failed to get file or User ID is null.", Toast.LENGTH_SHORT).show()
                Log.e("ProcessAudio", "File is null or User ID is missing")
                return
            }
            isProcessing = true
            operationStartTime = System.currentTimeMillis()
            hasShownSlowToast = false
            binding.progressOverlay.visibility = View.VISIBLE
            touchBlockOverlay?.visibility = View.VISIBLE
            binding.processAudioButton.text = "Processing..."
            binding.processAudioButton.isEnabled = false
            updateDismissalState()
         //   Toast.makeText(context, "Processing audio...", Toast.LENGTH_SHORT).show()
            handler.post(internetCheckTask)
            uploadAudioToFirebase(uri, followUpFileName)
            uploadAudioToBackend(file, userId, filteredSpeakers, followUpFileName)
        } ?: Toast.makeText(context, "No audio file selected.", Toast.LENGTH_SHORT).show()
    }

    private fun uriToFile(uri: Uri): File? {
        return audioFile
    }

    private fun uploadAudioToFirebase(uri: Uri, followUpFileName: String) {
        if (!isNetworkAvailable()) {
            abortCurrentOperation()
            return
        }
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = audioFile?.name ?: "temp_audio.mp3"
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")
        val metadata = storageMetadata { contentType = "audio/mpeg" }

        binding.processAudioButton.isEnabled = false
        binding.processAudioButton.text = "Uploading..."

        storageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    if (!isAdded || !isNetworkAvailable()) {
                        abortCurrentOperation()
                        return@addOnSuccessListener
                    }
                    val audioUrl = downloadUri.toString()
                    val fileData = hashMapOf(
                        "fileName" to fileName,
                        "audioUrl" to audioUrl,
                        "status" to "processing",
                        "timestamp_clientUpload" to FieldValue.serverTimestamp(),
                        "followUpFileName" to followUpFileName
                    )
                    FirebaseFirestore.getInstance()
                        .collection("ProcessedDocs")
                        .document(userId)
                        .collection("UserFiles")
                        .document(fileName)
                        .set(fileData)
                        .addOnSuccessListener {
                            if (!isAdded || !isNetworkAvailable()) {
                                abortCurrentOperation()
                                return@addOnSuccessListener
                            }
                          //  Toast.makeText(context, "Audio metadata saved successfully!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded) return@addOnFailureListener
                            Toast.makeText(context, "Failed to save metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }.addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    Toast.makeText(context, "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Failed to upload to Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                if (!isAdded) return@addOnCompleteListener
                if (isProcessing && isNetworkAvailable()) {
                    return@addOnCompleteListener
                }
                binding.progressOverlay.visibility = View.GONE
                touchBlockOverlay?.visibility = View.GONE
                isProcessing = false
                handler.removeCallbacks(internetCheckTask)
                binding.processAudioButton.isEnabled = true
                binding.processAudioButton.text = "PROCESS"
                updateDismissalState()
            }
    }

    private fun uploadAudioToBackend(file: File, userId: String, speakerNames: List<String>, followUpFileName: String) {
        if (!isNetworkAvailable()) {
            abortCurrentOperation()
            return
        }
        val serverUrl = "https://meetloggerserver.onrender.com/upload"
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp3".toMediaTypeOrNull()))
            .addFormDataPart("userId", userId)
            .addFormDataPart("fileName", file.name)
            .addFormDataPart("speakers", Gson().toJson(speakerNames))
            .addFormDataPart("followUpFileName", followUpFileName)
            .build()
        val request = Request.Builder().url(serverUrl).post(requestBody).build()

      //  Toast.makeText(context, "Sending audio to server...", Toast.LENGTH_SHORT).show()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    if (!isNetworkAvailable()) {
                        abortCurrentOperation()
                    } else {
                        binding.progressOverlay.visibility = View.GONE
                        touchBlockOverlay?.visibility = View.GONE
                        isProcessing = false
                        handler.removeCallbacks(internetCheckTask)
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.processAudioButton.isEnabled = true
                        binding.processAudioButton.text = "PROCESS"
                        updateDismissalState()
                    }
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    if (!isNetworkAvailable()) {
                        abortCurrentOperation()
                    } else {
                        binding.progressOverlay.visibility = View.GONE
                        touchBlockOverlay?.visibility = View.GONE
                        isProcessing = false
                        handler.removeCallbacks(internetCheckTask)
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            Log.d("UploadAudio", "Upload successful! Response: $responseBody")
                            Toast.makeText(context, "Processing started, you’ll be notified when ready-scheduler", Toast.LENGTH_LONG).show()
                            Handler(Looper.getMainLooper()).postDelayed({
                                binding.processAudioButton.isEnabled = true
                                binding.processAudioButton.text = "PROCESS"
                            }, 2000)
                        } else {
                            Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}")
                            Toast.makeText(context, "Server processing failed: ${response.code}", Toast.LENGTH_SHORT).show()
                            binding.processAudioButton.isEnabled = true
                            binding.processAudioButton.text = "PROCESS"
                        }
                        updateDismissalState()
                    }
                }
            }
        })
    }

    private fun playAudio() {
        if (audioFile?.exists() != true) {
            Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile!!.absolutePath)
                prepare()
                start()
            }
            binding.deleteButton.visibility = View.GONE
            isPlaying = true
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
            binding.playPauseButton.setImageResource(R.drawable.pause1)
            binding.seekBar.max = mediaPlayer!!.duration
            binding.totalTime.text = formatTime(mediaPlayer!!.duration)
            binding.currentTime.text = "00:00"
            handler.post(updateSeekBarTask)
            mediaPlayer?.setOnCompletionListener { stopPlayback() }
        } catch (e: Exception) {
            Toast.makeText(context, "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            mediaPlayer?.pause()
            binding.playPauseButton.setImageResource(R.drawable.play)
            binding.recordImageView.setImageResource(R.drawable.record)
            handler.removeCallbacks(updateSeekBarTask)
        } else {
            if (mediaPlayer == null) {
                playAudio()
            } else {
                mediaPlayer?.start()
                binding.playPauseButton.setImageResource(R.drawable.pause1)
                Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
                handler.post(updateSeekBarTask)
            }
        }
        isPlaying = !isPlaying
    }

    private fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        binding.recordImageView.setImageResource(R.drawable.record)
        binding.playPauseButton.setImageResource(R.drawable.play)
        binding.deleteButton.visibility = View.VISIBLE
        handler.removeCallbacks(updateSeekBarTask)
        binding.currentTime.text = "00:00"
        binding.seekBar.progress = 0
    }

    private fun rewindAudio() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition - 10000).coerceAtLeast(0)
            it.seekTo(newPosition)
            binding.seekBar.progress = newPosition
            binding.currentTime.text = formatTime(newPosition)
        }
    }

    private fun fastForwardAudio() {
        mediaPlayer?.let {
            val newPosition = (it.currentPosition + 10000).coerceAtMost(it.duration)
            it.seekTo(newPosition)
            binding.seekBar.progress = newPosition
            binding.currentTime.text = formatTime(newPosition)
        }
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete this audio file?")
            .setPositiveButton("Confirm") { _, _ -> deleteAudioFile() }
            .setNegativeButton("Cancel", null)
            .create()

        touchBlockOverlay?.visibility = View.VISIBLE

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            positiveButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green))
            positiveButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            positiveButton.setPadding(20, 10, 20, 10)
            negativeButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))
            negativeButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            negativeButton.setPadding(20, 10, 20, 10)
            val layoutParams = positiveButton.layoutParams as LinearLayout.LayoutParams
            layoutParams.leftMargin = (15 * resources.displayMetrics.density).toInt()
            positiveButton.layoutParams = layoutParams
        }

        dialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay?.visibility = View.GONE
            }, 200)
        }

        dialog.show()
    }

    private fun deleteAudioFile() {
        if (audioFile?.exists() != true) {
            Toast.makeText(context, "Audio file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = audioFile!!.name
        audioFile!!.delete()
        Toast.makeText(context, "Audio deleted locally", Toast.LENGTH_SHORT).show()
        deleteFromFirebaseStorage(fileName)
        resetUI()
        setInstructionText(InstructionState.INITIAL) // Reset instruction on delete
    }

    private fun deleteFromFirebaseStorage(fileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(context, "No internet connection, Firebase sync skipped", Toast.LENGTH_SHORT).show()
            return
        }
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")
        storageRef.delete()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(context, "Audio deleted from Firebase!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Failed to delete from Firebase: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun resetUI() {
        binding.recordImageView.setImageResource(R.drawable.record)
        binding.startButton.isVisible = true
        binding.startButton.text = "Start"
        setDrawableSize(binding.startButton, R.drawable.start, 80, 80)
        binding.stopButton.isVisible = false
        binding.miniPlayer.visibility = View.GONE
        binding.processAudioButton.isVisible = false
        binding.newRecordingButton.isVisible = false
        binding.deleteButton.isVisible = false
        binding.recordingTimer.visibility = View.GONE
        binding.recordingTimer.text = "00:00"
        elapsedTimeBeforePause = 0L
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(updateSeekBarTask)
    }

    private fun setDrawableSize(button: TextView, drawableResId: Int, width: Int, height: Int) {
        val drawable: Drawable? = ContextCompat.getDrawable(requireContext(), drawableResId)
        drawable?.let {
            it.setBounds(0, 0, width, height)
            button.setCompoundDrawables(it, null, null, null)
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check microphone permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // Both permissions are granted, proceed with recording
            if (!isRecording) {
                startRecording()
            } else {
                resumeRecording()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var microphoneGranted = true
            var notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU // Assume true for pre-Android 13

            for (i in permissions.indices) {
                when (permissions[i]) {
                    Manifest.permission.RECORD_AUDIO -> {
                        if (grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED) {
                          /*  Toast.makeText(
                                requireContext(),
                                "Microphone permission granted",
                                Toast.LENGTH_SHORT
                            ).show()

                           */
                        } else {
                            microphoneGranted = false
                            Toast.makeText(
                                requireContext(),
                                "Enable the microphone permission",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    Manifest.permission.POST_NOTIFICATIONS -> {
                        if (grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED) {
                           /* Toast.makeText(
                                requireContext(),
                                "Notification permission granted",
                                Toast.LENGTH_SHORT
                            ).show()

                            */
                        } else {
                            notificationGranted = false
                            Toast.makeText(
                                requireContext(),
                                "Enable the notification permission",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            // Proceed with recording only if both permissions are granted
            if (microphoneGranted && notificationGranted) {
                if (!isRecording) {
                    startRecording()
                } else {
                    resumeRecording()
                }
            }
        }
    }

    private fun cleanupTempFile(file: File?) {
        file?.let {
            if (it.exists()) {
                try {
                    it.delete()
                    Log.d("RecordAudioFragment", "Temp file deleted: ${it.name}")
                } catch (e: Exception) {
                    Log.e("RecordAudioFragment", "Failed to delete temp file: ${e.message}")
                }
            }
        }
        audioFile = null
    }

    @SuppressLint("DefaultLocale")
    private fun formatTime(milliseconds: Int): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds.toLong())
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.toLong()) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        if (isProcessing) {
            Toast.makeText(requireContext(), "Previous operation interrupted", Toast.LENGTH_SHORT).show()
            abortCurrentOperation()
        } else if (isSaving) {
            Toast.makeText(requireContext(), "Previous save interrupted", Toast.LENGTH_SHORT).show()
            binding.progressOverlay.visibility = View.GONE
            touchBlockOverlay?.visibility = View.GONE
            isSaving = false
            unlockUIAfterRecording()
        }
        handler.post(internetCheckTask)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(internetCheckTask)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaRecorder?.release()
        mediaRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
        cleanupTempFile(audioFile)
        handler.removeCallbacks(timerRunnable)
        handler.removeCallbacks(updateSeekBarTask)
        touchBlockOverlay?.visibility = View.GONE
        _binding = null
    }
}

