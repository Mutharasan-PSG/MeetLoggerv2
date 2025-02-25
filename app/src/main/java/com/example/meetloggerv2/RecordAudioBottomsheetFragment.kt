package com.example.meetloggerv2

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
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
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import org.json.JSONObject
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

            val userId = firebaseUser.uid
            binding.processAudioButton.text="Uploading..."
            uploadAudioToBackend(audioFile!!, userId)
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
        /* binding.fileNameEditText.isVisible = true
         binding.saveButton.isVisible = true
         setDrawableSize(binding.saveButton, R.drawable.save, 80, 80) // Custom drawable size

         */
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

                uploadToFirebaseStorage(newFile, enteredFileName)
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


    private fun uploadAudioToBackend(file: File, userId: String) {
        val serverUrl = "http://192.168.0.112:5000/upload"

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp3".toMediaTypeOrNull()))
            .addFormDataPart("userId", userId)
            .addFormDataPart("fileName", file.name)
            .build()

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("UploadAudio", "Upload failed: ${e.message}", e)
                requireActivity().runOnUiThread {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("UploadAudio", "Upload successful! Response: $responseBody")


                        Handler(Looper.getMainLooper()).postDelayed({
                            binding.processAudioButton.text="Process Audio"
                        }, 2_000) // 10 seconds

                        Toast.makeText(context, "Once the file ready, you will get notified", Toast.LENGTH_SHORT).show()

                   /*     // ✅ **Trigger the notification after 15 seconds**
                        Handler(Looper.getMainLooper()).postDelayed({
                            sendNotification(requireContext(), file.name)
                        }, 30_000) // 10 seconds

                    */

                        // Handle the transcription result if needed
                    } else {
                        Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}, Message: ${response.message}")
                    }
                }
            }
        })
    }


    private fun uploadToFirebaseStorage(file: File, fileName: String) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = firebaseUser.uid
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")
        val fileUri = Uri.fromFile(file)

        val metadata = storageMetadata {
            contentType = "audio/mpeg"  // Explicitly set MIME type
        }

        storageRef.putFile(fileUri, metadata)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val audioUrl = uri.toString()

                    // Save metadata in Firestore
                    val fileData = hashMapOf(
                        "fileName" to "$fileName.mp3",
                        "audioUrl" to audioUrl,
                        "status" to "processing",
                         "timestamp_clientUpload" to FieldValue.serverTimestamp()
                    )

                    FirebaseFirestore.getInstance()
                        .collection("ProcessedDocs")
                        .document(userId)
                        .collection("UserFiles")
                        .document("$fileName.mp3") // Using filename as document ID
                        .set(fileData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Audio metadata saved!", Toast.LENGTH_SHORT).show()

                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(context, "Failed to save metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to upload: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun sendNotification(context: Context, fileName: String) {
        val channelId = "audio_upload_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ensure filename has an extension
        val displayName = fileName.substringBeforeLast(".") // Default to .mp3 if no extension

        // Create a PendingIntent to open ReportFragment
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Audio Upload Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.launchlogo)  // Change to your app's icon
            .setContentText("$displayName audio file successfully documented.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // Set the PendingIntent
            .setAutoCancel(true) // Remove notification when clicked
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
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