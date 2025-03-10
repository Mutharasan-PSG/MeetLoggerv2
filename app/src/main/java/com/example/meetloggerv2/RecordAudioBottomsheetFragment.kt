package com.example.meetloggerv2

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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


class RecordAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentRecordAudioBottomsheetBinding? = null
    private val binding get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var isPlaying = false
    private var isPaused = false
    private var fileName = ""
    private var selectedAudioUri: Uri? = null

    private val REQUEST_MICROPHONE_PERMISSION = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordAudioBottomsheetBinding.inflate(inflater, container, false)
        setupListeners()



        // Make sure the Start button has the image at the start
        binding.startButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start, 0, 0, 0)
        setDrawableSize(binding.startButton, R.drawable.start, 80, 80)  // Start button
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        setFixedBottomSheetHeight(0.65) // Set to 40% of screen height
    }


    private fun setFixedBottomSheetHeight(percentage: Double) {
        val dialog = dialog as? BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)

            val screenHeight = displayMetrics.heightPixels
            val targetHeight = (screenHeight * percentage).toInt()

            // Set explicit height
            it.layoutParams.height = targetHeight
            it.requestLayout()

            // Ensure bottom sheet is properly expanded
            behavior.peekHeight = targetHeight
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }


    private fun setupListeners() {
        binding.startButton.setOnClickListener {
            if (checkMicrophonePermission() && checkNotificationPermission()) {
                if (!isRecording) {
                    startRecording()
                } else {
                    resumeRecording()
                }
            } else {
                requestPermissions()
            }

        }

        binding.stopButton.setOnClickListener {
            stopRecording()
        }

        binding.playButton.setOnClickListener {
            if (!isPlaying) {
                playAudio()
            } else {
                pauseAudio()
            }
        }

        binding.stopPlayButton.setOnClickListener {
            stopAudioPlayback()
        }

        binding.processAudioButton.setOnClickListener {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser == null) {
                Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (audioFile == null || !audioFile!!.exists()) {
                Toast.makeText(context, "No recorded audio file found!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            selectedAudioUri = Uri.fromFile(audioFile)
            showSpeakerSelectionDialog()
        }


        // Add listener for Delete button
        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun startRecording() {
        try {
            fileName = requireContext().externalCacheDir?.absolutePath + "/temp_audio.mp3"
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

            // Show recording GIF
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)

            binding.startButton.text = "Pause"
            setDrawableSize(binding.startButton, R.drawable.pause, 80, 80) // Custom drawable size
            binding.stopButton.isVisible = true
            setDrawableSize(binding.stopButton, R.drawable.stop, 80, 80) // Custom drawable size


        } catch (e: Exception) {
            Toast.makeText(context, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resumeRecording() {
        if (isPaused) {
            mediaRecorder?.resume()
            isPaused = false
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
            binding.startButton.text = "Pause"
            setDrawableSize(binding.startButton, R.drawable.pause, 80, 80) // Custom drawable size
        } else {
            mediaRecorder?.pause()
            isPaused = true
            binding.recordImageView.setImageResource(R.drawable.record) // Static record icon
            binding.startButton.text = "Resume"
            setDrawableSize(binding.startButton, R.drawable.resume, 80, 80) // Custom drawable size
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        showSaveFileDialog()
        mediaRecorder = null
        isRecording = false

        binding.recordImageView.setImageResource(R.drawable.record) // Reset to static icon
        binding.startButton.isVisible = false
        binding.stopButton.isVisible = false

    }

    private fun showSaveFileDialog() {
        // Inflate the custom dialog layout
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_audio, null)

        // Initialize the dialog
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Save Audio File")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        // Get references to the views in the dialog
        val fileNameInput = dialogView.findViewById<EditText>(R.id.fileNameInput)
        val saveFileButton = dialogView.findViewById<Button>(R.id.saveFileButton)

        // Save file when the user clicks the "Save" button
        saveFileButton.setOnClickListener {
            val enteredFileName = fileNameInput.text.toString().trim()

            if (enteredFileName.isNotEmpty()) {
                val newFile = File(requireContext().externalCacheDir, "$enteredFileName.mp3")
                audioFile?.renameTo(newFile)
                audioFile = newFile

               // uploadToFirebaseStorage(newFile, enteredFileName)
                // Update UI after saving the file
                //   binding.fileNameEditText.isVisible = false
                //  binding.saveButton.isVisible = false
                binding.audioPlayerLayout.isVisible = true
                binding.recordedFileNameTextView.text = "Recorded File - " + enteredFileName
                binding.processAudioButton.isVisible = true

                // Set the image for the play button after saving
                binding.playButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.play, 0, 0, 0)
                setDrawableSize(binding.playButton, R.drawable.play, 80, 80)  // Start button
                setDrawableSize(binding.processAudioButton, R.drawable.process, 80, 80) // Custom drawable size

                // Show delete button
                binding.deleteButton.isVisible = true
                binding.deleteButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.delete, 0, 0, 0)
                setDrawableSize(binding.deleteButton, R.drawable.delete, 80, 80)  // Start button

                dialog.dismiss()  // Close the dialog
            } else {
                Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
            }
        }

        // Show the dialog
        dialog.show()
    }

    private fun showSpeakerSelectionDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage("Do you want to include the speaker name?")
            .setPositiveButton("Yes, include speaker name") { _, _ ->
                Log.d("SpeakerSelection", "User selected to include speaker name.")
                showSpeakerInputDialog()
            }
            .setNegativeButton("No, proceed without name") { dialog, _ ->
                Log.d("SpeakerSelection", "User proceeded without speaker name.")
                dialog.dismiss()
                processAudio(emptyList())
            }
            .create()

        dialog.setOnShowListener {
            val poppinsTypeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_regular)
            dialog.findViewById<TextView>(android.R.id.message)?.typeface = poppinsTypeface
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                typeface = poppinsTypeface
                setTextColor(ContextCompat.getColor(requireContext(), R.color.BLUE))
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                typeface = poppinsTypeface
                setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            }
        }

        dialog.show()
    }

    // Speaker Input Dialog
    private fun showSpeakerInputDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_input, null)
        val speakerContainer = dialogView.findViewById<LinearLayout>(R.id.speakerContainer)
        val addSpeakerButton = dialogView.findViewById<Button>(R.id.addSpeakerButton)
        val proceedButton = dialogView.findViewById<Button>(R.id.proceedButton)
        val backButton = dialogView.findViewById<Button>(R.id.backButton)
        val speakerScrollView = dialogView.findViewById<ScrollView>(R.id.speakerScrollView)

        val speakerList = mutableListOf<String>()
        addSpeakerInput(speakerContainer, speakerList)

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

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

            alertDialog.dismiss()
            processAudio(filledSpeakers)
        }

        backButton.setOnClickListener {
            Log.d("SpeakerInput", "User went back to selection dialog.")
            alertDialog.dismiss()
            showSpeakerSelectionDialog()
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

    // Process Audio with Speaker Names
    private fun processAudio(speakerNames: List<String>) {
        val filteredSpeakers = speakerNames.filter { it.isNotBlank() }
        Log.d("ProcessAudio", "Processing audio with speakers: $filteredSpeakers")

        if (filteredSpeakers.isEmpty()) {
            showToast("No valid speaker names provided.")
        }

        selectedAudioUri?.let { uri ->
            Log.d("ProcessAudio", "Uploading audio: $uri")
            binding.processAudioButton.text = "Uploading..."
            uploadAudioToFirebase(uri)

            val file = uriToFile(uri)
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            val userId = firebaseUser?.uid

            if (file != null && userId != null) {
                Log.d("ProcessAudio", "Uploading audio to backend...")
                uploadAudioToBackend(file, userId, filteredSpeakers)
            } else {
                showToast("Failed to get file or User ID is null.")
                Log.e("ProcessAudio", "File is null or User ID is missing")
            }
        } ?: showToast("No audio file selected.")
    }

    private fun uriToFile(uri: Uri): File? {
        return audioFile // Assuming audioFile is already set correctly
    }


    private fun uploadAudioToFirebase(uri: Uri) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = firebaseUser.uid
        val fileName = audioFile?.name ?: "temp_audio.mp3"
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")

        val metadata = storageMetadata {
            contentType = "audio/mpeg"
        }

        storageRef.putFile(uri, metadata)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val audioUrl = downloadUri.toString()
                    val fileData = hashMapOf(
                        "fileName" to fileName,
                        "audioUrl" to audioUrl,
                        "status" to "processing",
                        "timestamp_clientUpload" to FieldValue.serverTimestamp()
                    )

                    FirebaseFirestore.getInstance()
                        .collection("ProcessedDocs")
                        .document(userId)
                        .collection("UserFiles")
                        .document(fileName)
                        .set(fileData)
                }
            }
            .addOnFailureListener {
                showToast("Failed to upload to Firebase: ${it.message}")
            }
    }

    private fun uploadAudioToBackend(file: File, userId: String, speakerNames: List<String> = emptyList()) {

        Log.d("UploadAudio", "Audio File Path: ${file.absolutePath}")
        Log.d("UploadAudio", "File Name: ${file.name}")
        Log.d("UploadAudio", "User ID: $userId")
        Log.d("UploadAudio", "Speakers: $speakerNames")

        val serverUrl = "http://192.168.2.84:5000/upload"

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp3".toMediaTypeOrNull()))
            .addFormDataPart("userId", userId)
            .addFormDataPart("fileName", file.name)
            .addFormDataPart("speakers", Gson().toJson(speakerNames))
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requireActivity().runOnUiThread {
                    showToast("Upload failed: ${e.message}")
                    binding.processAudioButton.text = "Process Audio"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("UploadAudio", "Upload successful! Response: $responseBody")
                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.processAudioButton.text = "Process Audio"
                        }, 2000)
                        showToast("Once the file is ready, you will get notified")
                    } else {
                        Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}")
                        binding.processAudioButton.text = "Process Audio"
                    }
                }
            }
        })
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun playAudio() {
        if (audioFile?.exists() == true) {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile!!.absolutePath)
                prepare()
                start()
            }

            binding.deleteButton.isVisible = false
            isPlaying = true
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)
            binding.playButton.text = "Pause"
            setDrawableSize(binding.playButton, R.drawable.pause, 80, 80) // Custom drawable size
            binding.stopPlayButton.isVisible = true
            setDrawableSize(binding.stopPlayButton, R.drawable.stop, 80, 80) // Custom drawable size

            mediaPlayer?.setOnCompletionListener {
                stopAudioPlayback()
            }
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        binding.recordImageView.setImageResource(R.drawable.record) // Static record icon
        binding.stopPlayButton.isVisible = false
        binding.playButton.text = "Resume"
        setDrawableSize(binding.playButton, R.drawable.resume, 80, 80) // Custom drawable size
    }

    private fun stopAudioPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        binding.recordImageView.setImageResource(R.drawable.record) // Static record icon
        binding.playButton.text = "Play"
        setDrawableSize(binding.playButton, R.drawable.play, 80, 80) // Custom drawable size
        binding.stopPlayButton.isVisible = false
        binding.deleteButton.isVisible = true
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete this audio file?")
            .setPositiveButton("Confirm") { _, _ ->
                deleteAudioFile()
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Get the buttons and apply styles
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Apply custom styles to the positive button (Confirm)
            positiveButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.green))  // Green background
            positiveButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))  // White text
            positiveButton.setPadding(20, 10, 20, 10)  // Add padding for better visibility

            // Apply custom styles to the negative button (Cancel)
            negativeButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red))  // Red background
            negativeButton.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))  // White text
            negativeButton.setPadding(20, 10, 20, 10)  // Add padding for better visibility

            // Add 15dp gap between Cancel and Confirm buttons
            val layoutParams = positiveButton.layoutParams as LinearLayout.LayoutParams
            layoutParams.leftMargin = (15 * resources.displayMetrics.density).toInt()  // Convert dp to pixels
            positiveButton.layoutParams = layoutParams
        }

        dialog.show()
    }



    private fun deleteAudioFile() {
        if (audioFile?.exists() == true) {
            val fileName = audioFile!!.name
            audioFile!!.delete()
            Toast.makeText(context, "Audio deleted locally", Toast.LENGTH_SHORT).show()

            deleteFromFirebaseStorage(fileName)
        }

        // Reset UI to initial state
        binding.recordImageView.setImageResource(R.drawable.record)

        // Reset the Start button
        binding.startButton.isVisible = true
        binding.startButton.text = "Start"
        setDrawableSize(binding.startButton, R.drawable.start, 80, 80)

        // Hide other buttons and layout
        binding.stopButton.isVisible = false
        binding.audioPlayerLayout.isVisible = false
        binding.processAudioButton.isVisible = false
        binding.deleteButton.isVisible = false
    }

    private fun deleteFromFirebaseStorage(fileName: String) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = firebaseUser.uid

        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")

        storageRef.delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Audio deleted from Firebase!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to delete from Firebase: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setDrawableSize(button: Button, drawableResId: Int, width: Int, height: Int) {
        val drawable: Drawable? = ContextCompat.getDrawable(requireContext(), drawableResId)
        drawable?.let {
            // Set the bounds for the drawable (width and height)
            it.setBounds(0, 0, width, height)
            // Apply the drawable to the button's left side (you can adjust for right/top/bottom too if needed)
            button.setCompoundDrawables(it, null, null, null)
        }
    }

    private fun checkMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkNotificationPermission(): Boolean {
        return NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
    }

    private fun requestPermissions() {
        if (!checkMicrophonePermission()) {
            requestMicrophonePermission()
        }
        if (!checkNotificationPermission()) {
            requestNotificationPermission()
        }
    }

    private fun requestMicrophonePermission() {
        requestPermissions(
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_MICROPHONE_PERMISSION
        )
    }

    private fun requestNotificationPermission() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MIC_PERMISSION_REQUEST = 1001
    }
}