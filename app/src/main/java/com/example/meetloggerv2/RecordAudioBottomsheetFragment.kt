package com.example.meetloggerv2

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.example.meetloggerv2.databinding.FragmentRecordAudioBottomsheetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecordAudioBottomsheetBinding.inflate(inflater, container, false)
        setupListeners()



        // Make sure the Start button has the image at the start
        binding.startButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.start, 0, 0, 0)
        setDrawableSize(binding.startButton, R.drawable.start, 70, 70)  // Start button
        return binding.root
    }

    private fun setupListeners() {
        binding.startButton.setOnClickListener {
            if (checkMicrophonePermission()) {
                if (!isRecording) {
                    startRecording()
                } else {
                    resumeRecording()
                }
            } else {
                requestMicrophonePermission()
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
            // Handle Process Audio action
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
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(fileName)
                prepare()
                start()
            }

            isRecording = true
            isPaused = false

            // Show recording GIF
            Glide.with(this).asGif().load(R.drawable.recording).into(binding.recordImageView)

            binding.startButton.text = "Pause"
            setDrawableSize(binding.startButton, R.drawable.pause, 70, 70) // Custom drawable size
            binding.stopButton.isVisible = true
            setDrawableSize(binding.stopButton, R.drawable.stop, 70, 70) // Custom drawable size


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
            setDrawableSize(binding.startButton, R.drawable.pause, 70, 70) // Custom drawable size
        } else {
            mediaRecorder?.pause()
            isPaused = true
            binding.recordImageView.setImageResource(R.drawable.record) // Static record icon
            binding.startButton.text = "Resume"
            setDrawableSize(binding.startButton, R.drawable.start, 70, 70) // Custom drawable size
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
        setDrawableSize(binding.saveButton, R.drawable.save, 70, 70) // Custom drawable size

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
                binding.recordedFileNameTextView.text = enteredFileName
                binding.processAudioButton.isVisible = true

                // Set the image for the play button after saving
                binding.playButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.play, 0, 0, 0)
                setDrawableSize(binding.playButton, R.drawable.play, 70, 70)  // Start button
                setDrawableSize(binding.processAudioButton, R.drawable.process, 70, 70) // Custom drawable size

                // Show delete button
                binding.deleteButton.isVisible = true
                binding.deleteButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.delete, 0, 0, 0)
                setDrawableSize(binding.deleteButton, R.drawable.delete, 70, 70)  // Start button

                dialog.dismiss()  // Close the dialog
            } else {
                Toast.makeText(context, "Please enter a file name", Toast.LENGTH_SHORT).show()
            }
        }

        // Show the dialog
        dialog.show()
    }

    private fun uploadToFirebaseStorage(file: File, fileName: String) {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser == null) {
            Toast.makeText(context, "User not logged in!", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = firebaseUser.uid
        val userName = firebaseUser.displayName ?: "Unknown"
        val userEmail = firebaseUser.email ?: "Unknown"

        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")
        val fileUri = Uri.fromFile(file)

        storageRef.putFile(fileUri)
            .addOnSuccessListener {
                Toast.makeText(context, "Audio uploaded to Firebase!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to upload: ${it.message}", Toast.LENGTH_SHORT).show()
            }
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
            binding.playButton.text = "Pause"
            setDrawableSize(binding.playButton, R.drawable.pause, 70, 70) // Custom drawable size
            binding.stopPlayButton.isVisible = true
            setDrawableSize(binding.stopPlayButton, R.drawable.stop, 70, 70) // Custom drawable size

            mediaPlayer?.setOnCompletionListener {
                stopAudioPlayback()
            }
        }
    }

    private fun pauseAudio() {
        mediaPlayer?.pause()
        isPlaying = false
        binding.stopPlayButton.isVisible = false
        binding.playButton.text = "Resume"
        setDrawableSize(binding.playButton, R.drawable.start, 70, 70) // Custom drawable size
    }

    private fun stopAudioPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        binding.playButton.text = "Play"
        setDrawableSize(binding.playButton, R.drawable.play, 70, 70) // Custom drawable size
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
        setDrawableSize(binding.startButton, R.drawable.start, 70, 70)

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
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestMicrophonePermission() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(Manifest.permission.RECORD_AUDIO),
            MIC_PERMISSION_REQUEST
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MIC_PERMISSION_REQUEST = 1001
    }
}


