package com.example.meetloggerv2

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.meetloggerv2.databinding.FragmentUploadAudioBottomsheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
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


class UploadAudioBottomsheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentUploadAudioBottomsheetBinding? = null
    private val binding get() = _binding!!
    private var selectedAudioUri: Uri? = null
    private var temporarySpeakerList: List<String>? = null // Temporary storage for speakers

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
            showSpeakerSelectionDialog()
        }

        return binding.root
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
                showFollowUpSelectionDialog()
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

    private fun showFollowUpSelectionDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_follow_up_selection, null)
        val backButton = dialogView.findViewById<ImageButton>(R.id.backButton)
        val followUpQuestion = dialogView.findViewById<TextView>(R.id.followUpQuestion)
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

        alertDialog.setOnShowListener {
            val layoutParams = alertDialog.window?.attributes
            layoutParams?.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
            alertDialog.window?.attributes = layoutParams
        }

        backButton.setOnClickListener {
            alertDialog.dismiss()
            showSpeakerSelectionDialog()
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

            // Retrieve the saved speaker list and proceed with processing
            val speakers = temporarySpeakerList ?: emptyList()
            alertDialog.dismiss()
            processAudio(speakers, selectedFileName)
            // Clear the temporary speaker list after use
            temporarySpeakerList = null
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun fetchUserFiles(
        userFilesRef: CollectionReference,
        spinner: Spinner,
        proceedButton: Button
    ) {
        userFilesRef.get()
            .addOnSuccessListener { snapshot ->
                val fileNames = snapshot.documents.mapNotNull { it.getString("fileName") }
                if (fileNames.isEmpty()) {
                    spinner.visibility = View.GONE
                    Toast.makeText(requireContext(), "No previous files found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Create a list for display without extensions
                val displayFileNames = fileNames.map { fileName ->
                    fileName.substringBeforeLast(".") // Remove everything after the last "."
                }

                val adapter = object : ArrayAdapter<String>(
                    requireContext(),
                    R.layout.spinner_dropdown_item,
                    R.id.spinner_dropdown_text,
                    displayFileNames
                ) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        view.setSingleLine(false)
                        view.maxLines = 2
                        view.ellipsize = TextUtils.TruncateAt.END
                        view.setPadding(15, 15, 15, 15) // Internal padding for selected item
                        return view
                    }

                    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getDropDownView(position, convertView, parent)
                        val textView = view.findViewById<TextView>(R.id.spinner_dropdown_text)
                        textView.setSingleLine(false)
                        textView.maxLines = 3
                        textView.ellipsize = TextUtils.TruncateAt.END
                        textView.setPadding(15, 15, 15, 15) // Internal padding for dropdown items

                        // Adjust dropdown item width to account for the 15dp padding on spinner
                        val effectiveWidth = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            250f, // Subtract padding from both sides (15dp * 2)
                            resources.displayMetrics
                        ).toInt()
                        val layoutParams = ViewGroup.LayoutParams(
                            effectiveWidth,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        view.layoutParams = layoutParams
                        return view
                    }
                }

                spinner.adapter = adapter
                spinner.dropDownWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    250f,
                    resources.displayMetrics
                ).toInt()

                // Store the original filenames for backend use
                spinner.setTag(fileNames)

                proceedButton.isEnabled = true
            }
            .addOnFailureListener { e ->
                Log.e("FetchFiles", "Error fetching files: ${e.message}")
                Toast.makeText(requireContext(), "Failed to load files", Toast.LENGTH_SHORT).show()
                spinner.visibility = View.GONE
            }
    }

    private fun processAudio(speakerNames: List<String>, followUpFileName: String = "") {
        val filteredSpeakers = speakerNames.filter { it.isNotBlank() }
        Log.d("ProcessAudio", "Processing audio with speakers: $filteredSpeakers, followUp: $followUpFileName")

        selectedAudioUri?.let { uri ->
            Log.d("ProcessAudio", "Uploading audio: $uri")
            uploadAudioToFirebase(uri, followUpFileName)

            val file = uriToFile(uri)
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            val userId = firebaseUser?.uid

            if (file != null && userId != null) {
                Log.d("ProcessAudio", "Uploading audio to backend...")
                uploadAudioToBackend(file, userId, filteredSpeakers, followUpFileName)
            } else {
                showToast("Failed to get file or User ID is null.")
                Log.e("ProcessAudio", "File is null or User ID is missing")
            }
        } ?: showToast("No audio file selected.")
    }

    private fun uploadAudioToFirebase(fileUri: Uri, followUpFileName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = getFileNameFromUri(fileUri)
        val audioRef = storageReference.child("AudioFiles/$userId/$fileName")

        binding.processAudioButton.isEnabled = false
        binding.processAudioText.text = "Uploading..."

        audioRef.putFile(fileUri)
            .addOnSuccessListener {
                audioRef.downloadUrl.addOnSuccessListener { uri ->
                    val audioUrl = uri.toString()
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
                binding.processAudioText.text = "Process Audio"
            }
    }

    private fun uploadAudioToBackend(file: File, userId: String, speakers: List<String>, followUpFileName: String) {

        Log.d("UploadAudio", "Audio File Path: ${file.absolutePath}")
        Log.d("UploadAudio", "File Name: ${file.name}")
        Log.d("UploadAudio", "User ID: $userId")
        Log.d("UploadAudio", "Speakers: $speakers")
        Log.d("UploadAudio", "followUp_Filename: $followUpFileName")

        val serverUrl = "https://meetloggerserver.onrender.com/upload"

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
            .addFormDataPart("userId", userId)
            .addFormDataPart("fileName", file.name)
            .addFormDataPart("speakers", Gson().toJson(speakers))
            .addFormDataPart("followUpFileName", followUpFileName)
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
                    showToast("Upload failed: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        Log.d("UploadAudio", "Server Response: $responseBody")
                        showToast("File processing started. You'll be notified once ready.")
                    } else {
                        Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}")
                    }
                }
            }
        })
    }

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

            // Save the speaker list temporarily
            temporarySpeakerList = filledSpeakers
            alertDialog.dismiss()
            // Open the follow-up selection dialog
            showFollowUpSelectionDialog()
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

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
