package com.example.meetloggerv2

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.example.meetloggerv2.databinding.FragmentUploadAudioBottomsheetBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
    private var temporarySpeakerList: List<String>? = null
    private var isProcessing = false
    private var operationStartTime: Long = 0L
    private var hasShownSlowToast = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val internetCheckTask = object : Runnable {
        override fun run() {
            checkInternetStatus()
            handler.postDelayed(this, 2000)
        }
    }

    private val audioPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                selectedAudioUri = uri
                val fileName = getFileNameFromUri(uri)
                binding.selectedAudioTextView.text = fileName
                binding.processAudioButton.isEnabled = true
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentUploadAudioBottomsheetBinding.inflate(inflater, container, false)
        binding.processAudioButton.isEnabled = false
        binding.progressOverlay.visibility = View.GONE // Ensure hidden initially
        setupListeners()
        setupBackPressHandler()
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        setFixedBottomSheetHeight(0.4)
        setupBottomSheetBehavior()
    }

    private fun setupListeners() {
        binding.uploadAudioButton.setOnClickListener {
            openAudioPicker()
        }

        binding.processAudioButton.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            if (!isNetworkAvailable()) {
                Toast.makeText(context, "No internet connection", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showSpeakerSelectionDialog()
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isProcessing) {
                    Toast.makeText(requireContext(), "Processing in progress, please wait", Toast.LENGTH_SHORT).show()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
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
        dialog.setCancelable(!isProcessing)
        dialog.setCanceledOnTouchOutside(!isProcessing)
        bottomSheetBehavior.isDraggable = !isProcessing
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = try {
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } catch (e: IllegalStateException) {
            Log.e("UploadAudioFragment", "Context unavailable, assuming no network: ${e.message}")
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
            Toast.makeText(requireContext(), "Operation aborted due to no internet", Toast.LENGTH_SHORT).show()
        }
        isProcessing = false
        hasShownSlowToast = false
        binding.processAudioButton.isEnabled = true
        binding.processAudioText.text = "Process Audio"
        updateDismissalState()
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
            .setCancelable(false)
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
            val speakers = temporarySpeakerList ?: emptyList()
            alertDialog.dismiss()
            processAudio(speakers, selectedFileName)
            temporarySpeakerList = null
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
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

        userFilesRef.get()
            .addOnSuccessListener { snapshot ->
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                isProcessing = false
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
            updateDismissalState()
            Toast.makeText(context, "Processing audio...", Toast.LENGTH_SHORT).show()
            handler.post(internetCheckTask)
            uploadAudioToFirebase(uri, followUpFileName)
            uploadAudioToBackend(file, userId, filteredSpeakers, followUpFileName)
        } ?: Toast.makeText(context, "No audio file selected.", Toast.LENGTH_SHORT).show()
    }

    private fun uploadAudioToFirebase(fileUri: Uri, followUpFileName: String) {
        if (!isNetworkAvailable()) {
            abortCurrentOperation()
            return
        }
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val fileName = getFileNameFromUri(fileUri)
        val audioRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")

        binding.processAudioButton.isEnabled = false
        binding.processAudioText.text = "Uploading..."

        audioRef.putFile(fileUri)
            .addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                audioRef.downloadUrl.addOnSuccessListener { uri ->
                    if (!isAdded || !isNetworkAvailable()) {
                        abortCurrentOperation()
                        return@addOnSuccessListener
                    }
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
                            if (!isAdded || !isNetworkAvailable()) {
                                abortCurrentOperation()
                                return@addOnSuccessListener
                            }
                            Toast.makeText(context, "Audio metadata saved successfully!", Toast.LENGTH_SHORT).show()
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
                isProcessing = false
                handler.removeCallbacks(internetCheckTask)
                binding.processAudioButton.isEnabled = true
                binding.processAudioText.text = "Process Audio"
                updateDismissalState()
            }
    }

    private fun uploadAudioToBackend(file: File, userId: String, speakers: List<String>, followUpFileName: String) {
        if (!isNetworkAvailable()) {
            abortCurrentOperation()
            return
        }
        val serverUrl = "https://meetloggerserver.onrender.com/upload"
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mpeg".toMediaTypeOrNull()))
            .addFormDataPart("userId", userId)
            .addFormDataPart("fileName", file.name)
            .addFormDataPart("speakers", Gson().toJson(speakers))
            .addFormDataPart("followUpFileName", followUpFileName)
            .build()
        val request = Request.Builder().url(serverUrl).post(requestBody).build()

        Toast.makeText(context, "Sending audio to server...", Toast.LENGTH_SHORT).show()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    if (!isNetworkAvailable()) {
                        abortCurrentOperation()
                    } else {
                        binding.progressOverlay.visibility = View.GONE
                        isProcessing = false
                        handler.removeCallbacks(internetCheckTask)
                        Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        binding.processAudioButton.isEnabled = true
                        binding.processAudioText.text = "Process Audio"
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
                        isProcessing = false
                        handler.removeCallbacks(internetCheckTask)
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            Log.d("UploadAudio", "Upload successful! Response: $responseBody")
                            Toast.makeText(context, "Processing started, you’ll be notified when ready", Toast.LENGTH_LONG).show()
                            Handler(Looper.getMainLooper()).postDelayed({
                                binding.processAudioButton.isEnabled = true
                                binding.processAudioText.text = "Process Audio"
                            }, 2000)
                        } else {
                            Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}")
                            Toast.makeText(context, "Server processing failed: ${response.code}", Toast.LENGTH_SHORT).show()
                            binding.processAudioButton.isEnabled = true
                            binding.processAudioText.text = "Process Audio"
                        }
                        updateDismissalState()
                    }
                }
            }
        })
    }

    private fun uriToFile(uri: Uri): File? {
        val inputStream = requireContext().contentResolver.openInputStream(uri) ?: return null
        val fileName = getFileNameFromUri(uri)
        val fileExtension = ".mp3" // Assuming MP3, adjust if needed
        val baseFileName = fileName.substringBeforeLast(".") + fileExtension
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

    override fun onResume() {
        super.onResume()
        if (isProcessing) {
            Toast.makeText(requireContext(), "Previous operation interrupted", Toast.LENGTH_SHORT).show()
            abortCurrentOperation()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(internetCheckTask)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(internetCheckTask)
        _binding = null
    }
}