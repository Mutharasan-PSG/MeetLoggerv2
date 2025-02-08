package com.example.meetloggerv2

import android.app.Activity
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.meetloggerv2.databinding.FragmentUploadAudioBottomsheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import okhttp3.*
import okhttp3.MultipartBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException


class UploadAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentUploadAudioBottomsheetBinding? = null
    private val binding get() = _binding!!
    private var selectedAudioUri: Uri? = null

    // Firebase Storage reference
    private val storageReference: StorageReference by lazy {
        FirebaseStorage.getInstance().reference
    }

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAudioUri = uri
                val fileName = getFileNameFromUri(uri)
                binding.selectedAudioTextView.text = fileName
                binding.processAudioButton.isEnabled = true  // Enable button after selection
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadAudioBottomsheetBinding.inflate(inflater, container, false)

        // Initially disable the process audio button
        binding.processAudioButton.isEnabled = false

        binding.uploadAudioButton.setOnClickListener {
            openAudioPicker()
        }

        binding.processAudioButton.setOnClickListener {
            selectedAudioUri?.let { uri ->
                uploadAudioToFirebase(uri) // Upload to Firebase

                // Convert Uri to File for backend upload
                val file = uriToFile(uri)
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                val userId = firebaseUser?.uid
                if (file != null && userId != null) {
                    uploadAudioToBackend(file, userId) // Upload to Flask backend
                } else {
                    showToast("Failed to get file from Uri or User ID is null.")
                }
            } ?: showToast("No audio file selected.")
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        setFixedBottomSheetHeight(0.4) // Set to 40% of screen height
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

    private fun uriToFile(uri: Uri): File? {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
        val fileName = getFileNameFromUri(uri) // Get the original file name
        val fileExtension = ".mp3" // Ensure the extension is .mp3, or change it based on your needs

        // Ensure the file name has the correct extension
        val baseFileName = fileName.substringBeforeLast(".") + fileExtension

        // Create the file with the original name and extension in the cache directory
        val tempFile = File(requireContext().cacheDir, baseFileName)

        inputStream.use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }



    private fun openAudioPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "audio/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        audioPickerLauncher.launch(intent)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        var fileName = "Unknown Audio File"
        val contentResolver: ContentResolver = requireContext().contentResolver
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun uploadAudioToBackend(file: File, userId: String) {

        // Log the audio file, file name, and user ID
        Log.d("UploadAudio", "Audio File: ${file.absolutePath}")
        Log.d("UploadAudio", "File Name: ${file.name}")
        Log.d("UploadAudio", "User ID: $userId")

        val serverUrl = "http://192.168.0.112:5000/upload"

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
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

                        val jsonResponse = JSONObject(responseBody)
                        val text = jsonResponse.optString("text", "Transcription unavailable")

                        // Handle the transcription result if needed
                    } else {
                        Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}, Message: ${response.message}")
                    }
                }
            }
        })
    }




    private fun uploadAudioToFirebase(fileUri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showToast("User not authenticated.")
            return
        }

        val fileName = getFileNameFromUri(fileUri)
        val audioRef = storageReference.child("AudioFiles/$userId/$fileName")

        binding.processAudioButton.isEnabled = false
        binding.processAudioButton.text = "Uploading..."

        audioRef.putFile(fileUri)
            .addOnSuccessListener {
                audioRef.downloadUrl.addOnSuccessListener { uri ->
                    val audioUrl = uri.toString()

                    // Save metadata in Firestore
                    val fileData = hashMapOf(
                        "fileName" to fileName,
                        "audioUrl" to audioUrl,
                       // "timestamp" to FieldValue.serverTimestamp()
                    )

                    FirebaseFirestore.getInstance()
                        .collection("ProcessedDocs")
                        .document(userId)
                        .collection("UserFiles")
                        .document(fileName)  // Using filename as document ID
                        .set(fileData)
                        .addOnSuccessListener {
                            showToast("Audio metadata saved successfully!")
                        }
                        .addOnFailureListener { e ->
                            showToast("Failed to save metadata: ${e.message}")
                        }
                }
            }
            .addOnFailureListener {
                showToast("Failed to upload audio: ${it.message}")
            }
            .addOnCompleteListener {
                binding.processAudioButton.isEnabled = true
                binding.processAudioButton.text = "Process Audio"
            }
    }


    private fun setDrawableSize(button: Button, drawableResId: Int, width: Int, height: Int) {
        val drawable: Drawable? = ContextCompat.getDrawable(requireContext(), drawableResId)
        drawable?.let {
            it.setBounds(0, 0, width, height)
            button.setCompoundDrawables(it, null, null, null)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
