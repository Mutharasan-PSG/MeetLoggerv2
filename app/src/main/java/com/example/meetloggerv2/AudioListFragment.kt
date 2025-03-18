package com.example.meetloggerv2

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
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

class AudioListFragment : Fragment() {
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var tickIcon: ImageView
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var progressOverlay: FrameLayout
    private lateinit var mainContent: FrameLayout
    private lateinit var noInternetContainer: LinearLayout
    private lateinit var mainTopic: TextView
    private lateinit var touchBlockOverlay: FrameLayout
    private lateinit var fileNamesList: ArrayList<String>
    private lateinit var filteredList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var miniPlayer: View
    private lateinit var playPauseButton: ImageView
    private lateinit var stopButton: ImageView
    private lateinit var prevButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var currentAudioName: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private var isPlaying = false
    private var currentAudioIndex: Int = -1
    private var isDeleteMode = false
    private var isRenaming = false
    private var renamingPosition = -1
    private var isProcessing = false
    private val selectedItems = HashSet<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBarTask = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                val currentPosition = mediaPlayer.currentPosition
                seekBar.progress = currentPosition
                currentTime.text = formatTime(currentPosition)
            }
            handler.postDelayed(this, 1000)
        }
    }
    private val internetCheckTask = object : Runnable {
        override fun run() {
            checkInternetStatus()
            handler.postDelayed(this, 2000)
        }
    }
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var pendingDownloadFileName: String? = null
    private var temporarySpeakerList: List<String>? = null
    private var tempAudioFile: File? = null
    private var operationStartTime: Long = 0L
    private var hasShownSlowToast = false

    private val downloadFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val uri = result.data!!.data!!
            val fileName = pendingDownloadFileName
            if (fileName != null) {
                saveAudioToUri(fileName, uri)
                pendingDownloadFileName = null
            } else {
                Toast.makeText(requireContext(), "Download failed: No file selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_audio_list, container, false)
        initializeViews(view)

        view.findViewById<ImageView>(R.id.placeholderImage)?.visibility = View.GONE
        view.findViewById<TextView>(R.id.placeholderText)?.visibility = View.GONE

        setupListView()
        setupMediaControls()
        setupDeleteIcon()
        setupMiniPlayerDragging()
        setupBackPressHandler()
        setupSelectAllCheckbox()

        handler.post(internetCheckTask)
        checkInternetAndLoad()

        return view
    }

    private fun initializeViews(view: View) {
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        deleteIcon = view.findViewById(R.id.deleteIcon)
        tickIcon = view.findViewById(R.id.tickIcon)
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox)
        progressOverlay = view.findViewById(R.id.progressOverlay)
        mainContent = view.findViewById(R.id.mainContent)
        noInternetContainer = view.findViewById(R.id.noInternetContainer)
        mainTopic = view.findViewById(R.id.AudioTopic)
        touchBlockOverlay = view.findViewById(R.id.touchBlockOverlay)
        miniPlayer = view.findViewById(R.id.miniPlayer)
        playPauseButton = view.findViewById(R.id.playPauseButton)
        stopButton = view.findViewById(R.id.stopButton)
        prevButton = view.findViewById(R.id.prevButton)
        nextButton = view.findViewById(R.id.nextButton)
        currentAudioName = view.findViewById(R.id.currentAudioName)
        seekBar = view.findViewById(R.id.seekBar)
        currentTime = view.findViewById(R.id.currentTime)
        totalTime = view.findViewById(R.id.totalTime)

        fileNamesList = ArrayList()
        filteredList = ArrayList()
        mediaPlayer = MediaPlayer()
    }

    private fun setupListView() {
        adapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item_3, R.id.textViewFileName, filteredList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item_3, parent, false)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                val menuIcon = view.findViewById<ImageView>(R.id.menuIcon)
                val textView = view.findViewById<TextView>(R.id.textViewFileName)
                val editText = view.findViewById<EditText>(R.id.editTextFileName)

                checkbox.visibility = if (isDeleteMode && !isRenaming && !isProcessing) View.VISIBLE else View.GONE
                checkbox.setOnCheckedChangeListener(null)
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (!isRenaming && !isProcessing) {
                        if (isChecked) {
                            selectedItems.add(position)
                            Log.d("AudioListFragment", "Checked position $position, selectedItems: $selectedItems")
                        } else {
                            selectedItems.remove(position)
                            Log.d("AudioListFragment", "Unchecked position $position, selectedItems: $selectedItems")
                        }
                        updateDeleteIconVisibility()
                        updateSelectAllCheckboxState()
                        notifyDataSetChanged()
                    }
                }

                menuIcon.visibility = if (isDeleteMode || isRenaming || isProcessing) View.GONE else View.VISIBLE
                menuIcon.setOnClickListener {
                    if (!isRenaming && !isProcessing) {
                        showOptionsPopup(menuIcon, position)
                    }
                }

                if (position == renamingPosition && isRenaming) {
                    textView.visibility = View.GONE
                    editText.visibility = View.VISIBLE
                    editText.setText(filteredList[position])
                } else {
                    textView.visibility = View.VISIBLE
                    editText.visibility = View.GONE
                    textView.text = filteredList[position]
                }

                return view
            }
        }
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming && !isProcessing) {
                val selectedFileName = filteredList[position]
                currentAudioIndex = position
                downloadAndPlayAudio(selectedFileName)
            } else if (!isRenaming && !isProcessing) {
                val view = listView.getChildAt(position - listView.firstVisiblePosition)
                val checkbox = view?.findViewById<CheckBox>(R.id.checkbox)
                checkbox?.let {
                    val newCheckedState = !it.isChecked
                    it.isChecked = newCheckedState
                    if (newCheckedState) {
                        selectedItems.add(position)
                    } else {
                        selectedItems.remove(position)
                    }
                    Log.d("AudioListFragment", "Clicked position $position, new state: $newCheckedState, selectedItems: $selectedItems")
                    adapter.notifyDataSetChanged()
                    updateDeleteIconVisibility()
                    updateSelectAllCheckboxState()
                }
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming && !isProcessing) {
                toggleDeleteMode(true)
                selectedItems.add(position)
                Log.d("AudioListFragment", "Long-clicked position $position, selectedItems: $selectedItems")
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                updateSelectAllCheckboxState()
                true
            } else false
        }
    }

    private fun setupSelectAllCheckbox() {
        selectAllCheckbox.setOnClickListener {
            if (!isRenaming && !isProcessing && isDeleteMode) {
                if (selectAllCheckbox.isChecked) {
                    for (i in 0 until filteredList.size) {
                        selectedItems.add(i)
                    }
                    Log.d("AudioListFragment", "Select All checked, selectedItems: $selectedItems")
                } else {
                    selectedItems.clear()
                    Log.d("AudioListFragment", "Select All unchecked, selectedItems: $selectedItems")
                }
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
            }
        }
        updateSelectAllCheckboxVisibility()
    }

    private fun updateSelectAllCheckboxVisibility() {
        selectAllCheckbox.visibility = if (isDeleteMode && !isRenaming && !isProcessing) View.VISIBLE else View.GONE
        adjustListViewMargin()
    }

    private fun updateSelectAllCheckboxState() {
        selectAllCheckbox.isChecked = selectedItems.size == filteredList.size && filteredList.isNotEmpty()
    }

    private fun adjustListViewMargin() {
        val layoutParams = listView.layoutParams as FrameLayout.LayoutParams
        val marginNormal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150f, resources.displayMetrics).toInt() // Non-delete mode
        val marginWithCheckbox = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 190f, resources.displayMetrics).toInt() // Delete mode with checkbox

        layoutParams.topMargin = if (selectAllCheckbox.visibility == View.VISIBLE) marginWithCheckbox else marginNormal
        listView.layoutParams = layoutParams
        listView.requestLayout() // Ensure layout updates
    }

    private fun toggleDeleteMode(enable: Boolean) {
        isDeleteMode = enable
        if (!enable) selectedItems.clear()
        adapter.notifyDataSetChanged()
        updateDeleteIconVisibility()
        updateSelectAllCheckboxVisibility() // This will also adjust the margin
        updateSelectAllCheckboxState()
    }

    private fun updateDeleteIconVisibility() {
        deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty() && !isProcessing) View.VISIBLE else View.GONE
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = try {
            requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        } catch (e: IllegalStateException) {
            Log.e("AudioListFragment", "Context unavailable, assuming no network: ${e.message}")
            return false
        }
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun checkInternetStatus() {
        if (!isAdded) return
        val isOnline = isNetworkAvailable()
        if (!isOnline && !noInternetContainer.isShown) {
            mainContent.visibility = View.GONE
            noInternetContainer.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Internet connection lost", Toast.LENGTH_SHORT).show()
            if (isProcessing) {
                abortCurrentOperation()
            }
        } else if (isOnline && !mainContent.isShown) {
            mainContent.visibility = View.VISIBLE
            noInternetContainer.visibility = View.GONE
            Toast.makeText(requireContext(), "Internet connection restored", Toast.LENGTH_SHORT).show()
            if (!isProcessing) fetchAudioFiles(false)
        }

        if (isProcessing && isOnline) {
            val elapsedTime = System.currentTimeMillis() - operationStartTime
            if (elapsedTime > 5000 && !hasShownSlowToast) {
                Toast.makeText(requireContext(), "Internet is slow, please wait...", Toast.LENGTH_SHORT).show()
                hasShownSlowToast = true
            }
        }
    }

    private fun checkInternetAndLoad() {
        if (!isAdded) return
        if (isNetworkAvailable()) {
            mainContent.visibility = View.VISIBLE
            noInternetContainer.visibility = View.GONE
            fetchAudioFiles(false)
        } else {
            mainContent.visibility = View.GONE
            noInternetContainer.visibility = View.VISIBLE
        }
    }

    private fun setupBackPressHandler() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isProcessing) {
                    Toast.makeText(requireContext(), "Operation in progress, please wait", Toast.LENGTH_SHORT).show()
                } else if (isRenaming) {
                    cleanupRenamingMode()
                } else if (isDeleteMode) {
                    toggleDeleteMode(false)
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun abortCurrentOperation() {
        progressOverlay.visibility = View.GONE
        touchBlockOverlay.visibility = View.GONE
        isProcessing = false
        hasShownSlowToast = false
        cleanupTempFile(tempAudioFile)
        Toast.makeText(requireContext(), "Operation aborted due to no internet", Toast.LENGTH_SHORT).show()
        adapter.notifyDataSetChanged()
    }

    private fun showOptionsPopup(anchorView: View, position: Int) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_menu_layout, null)
        val listView = popupView.findViewById<ListView>(R.id.popup_list)

        val options = listOf("Rename", "Download", "Share", "Summarize")

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.popup_menu_item,
            options
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.popup_menu_item, parent, false)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                val poppinsFont = ResourcesCompat.getFont(requireContext(), R.font.poppins_medium)
                textView.typeface = poppinsFont
                textView.textSize = 16f
                textView.setPadding(16, 8, 16, 2)
                textView.setTextColor(Color.BLACK)
                textView.gravity = Gravity.CENTER_VERTICAL
                textView.text = options[position]
                return view
            }
        }
        listView.adapter = adapter

        val popupWidth = (140 * resources.displayMetrics.density).toInt()
        val popupWindow = PopupWindow(
            popupView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )

        popupView.setPadding(0, 0, 0, 0)
        popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), android.R.drawable.dialog_holo_light_frame))
        popupWindow.elevation = 2f
        popupWindow.isClippingEnabled = false

        listView.setOnItemClickListener { _, _, index, _ ->
            when (options[index]) {
                "Rename" -> startRenaming(position)
                "Download" -> startDownload(filteredList[position])
                "Share" -> shareAudio(filteredList[position])
                "Summarize" -> summarizeAudio(filteredList[position])
            }
            popupWindow.dismiss()
        }

        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        val anchorX = anchorLocation[0]
        val anchorY = anchorLocation[1]
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupHeight = popupView.measuredHeight

        val xOffset = if (anchorX + popupWidth > screenWidth) {
            screenWidth - popupWidth - anchorView.width
        } else {
            anchorX + anchorView.width
        }

        val extraPadding = (64 * resources.displayMetrics.density).toInt()
        val yOffset = if (anchorY + popupHeight > screenHeight) {
            (anchorY - popupHeight - extraPadding).coerceAtLeast(0)
        } else {
            anchorY
        }

        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, xOffset, yOffset)
    }

    private fun summarizeAudio(fileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")
        val tempFile = File(requireContext().cacheDir, "$fileName.mp3")
        tempAudioFile = tempFile

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Downloading audio...", Toast.LENGTH_SHORT).show()

        storageRef.getFile(tempFile).addOnSuccessListener {
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                return@addOnSuccessListener
            }
            progressOverlay.visibility = View.GONE
            isProcessing = false
            touchBlockOverlay.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Audio downloaded", Toast.LENGTH_SHORT).show()
            showSpeakerSelectionDialog(tempFile)
        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            progressOverlay.visibility = View.GONE
            touchBlockOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "Failed to download audio: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanupTempFile(tempFile)
        }
    }

    private fun showSpeakerSelectionDialog(audioFile: File) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_selection, null)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radioGroup)
        val radioYes = dialogView.findViewById<RadioButton>(R.id.radioYes)
        val radioNo = dialogView.findViewById<RadioButton>(R.id.radioNo)
        val proceedButton = dialogView.findViewById<Button>(R.id.proceedButton)
        val cancelButton = dialogView.findViewById<Button>(R.id.cancelButton)

        val alertDialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        touchBlockOverlay.visibility = View.VISIBLE

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
                        showSpeakerInputDialog(audioFile)
                    }, 200)
                }
                R.id.radioNo -> {
                    alertDialog.dismiss()
                    handler.postDelayed({
                        showFollowUpSelectionDialog(audioFile)
                    }, 200)
                }
                else -> Toast.makeText(requireContext(), "Please select an option", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
            handler.postDelayed({
                touchBlockOverlay.visibility = View.GONE
                cleanupTempFile(audioFile)
            }, 200)
        }

        alertDialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay.visibility = View.GONE
            }, 200)
        }

        alertDialog.show()
    }

    private fun showSpeakerInputDialog(audioFile: File) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_speaker_input, null)
        val speakerContainer = dialogView.findViewById<LinearLayout>(R.id.speakerContainer)
        val addSpeakerButton = dialogView.findViewById<Button>(R.id.addSpeakerButton)
        val proceedButton = dialogView.findViewById<Button>(R.id.proceedButton)
        val backButton = dialogView.findViewById<ImageView>(R.id.backButton)
        val speakerScrollView = dialogView.findViewById<ScrollView>(R.id.speakerScrollView)

        val speakerList = mutableListOf<String>()
        addSpeakerInput(speakerContainer, speakerList)

        val alertDialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        touchBlockOverlay.visibility = View.VISIBLE

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
                showFollowUpSelectionDialog(audioFile)
            }, 200)
        }

        backButton.setOnClickListener {
            Log.d("SpeakerInput", "User went back to selection dialog.")
            alertDialog.dismiss()
            handler.postDelayed({
                showSpeakerSelectionDialog(audioFile)
            }, 200)
        }

        alertDialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay.visibility = View.GONE
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

    private fun showFollowUpSelectionDialog(audioFile: File) {
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

        val alertDialog = AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        touchBlockOverlay.visibility = View.VISIBLE

        alertDialog.setOnShowListener {
            val layoutParams = alertDialog.window?.attributes
            layoutParams?.width = (resources.displayMetrics.widthPixels * 0.9).toInt()
            layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
            alertDialog.window?.attributes = layoutParams
        }

        backButton.setOnClickListener {
            alertDialog.dismiss()
            handler.postDelayed({
                showSpeakerInputDialog(audioFile)
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
                processAudio(audioFile, speakers, selectedFileName)
            }, 200)
            temporarySpeakerList = null
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
            handler.postDelayed({
                touchBlockOverlay.visibility = View.GONE
                cleanupTempFile(audioFile)
            }, 200)
        }

        alertDialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay.visibility = View.GONE
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

    private fun processAudio(audioFile: File, speakerNames: List<String>, followUpFileName: String = "") {
        if (!isNetworkAvailable()) {
            touchBlockOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            cleanupTempFile(audioFile)
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            touchBlockOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            cleanupTempFile(audioFile)
            return
        }

        if (!audioFile.exists()) {
            touchBlockOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), "Audio file not found", Toast.LENGTH_SHORT).show()
            cleanupTempFile(audioFile)
            return
        }

        val filteredSpeakers = speakerNames.filter { it.isNotBlank() }
        Log.d("ProcessAudio", "Processing audio with speakers: $filteredSpeakers, followUp: $followUpFileName")

        val fileName = audioFile.name
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName")
        val metadata = storageMetadata { contentType = "audio/mpeg" }

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Uploading audio to Firebase...", Toast.LENGTH_SHORT).show()

        storageRef.putFile(Uri.fromFile(audioFile), metadata)
            .addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    cleanupTempFile(audioFile)
                    return@addOnSuccessListener
                }
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    if (!isAdded || !isNetworkAvailable()) {
                        abortCurrentOperation()
                        cleanupTempFile(audioFile)
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
                                cleanupTempFile(audioFile)
                                return@addOnSuccessListener
                            }
                            Toast.makeText(requireContext(), "Audio metadata saved", Toast.LENGTH_SHORT).show()
                            uploadAudioToBackend(audioFile, userId, filteredSpeakers, followUpFileName)
                        }
                        .addOnFailureListener { e ->
                            if (!isAdded) return@addOnFailureListener
                            progressOverlay.visibility = View.GONE
                            touchBlockOverlay.visibility = View.GONE
                            isProcessing = false
                            Toast.makeText(requireContext(), "Failed to save metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                            cleanupTempFile(audioFile)
                        }
                }.addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    progressOverlay.visibility = View.GONE
                    touchBlockOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Failed to get download URL: ${e.message}", Toast.LENGTH_SHORT).show()
                    cleanupTempFile(audioFile)
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressOverlay.visibility = View.GONE
                touchBlockOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "Failed to upload to Firebase: ${e.message}", Toast.LENGTH_SHORT).show()
                cleanupTempFile(audioFile)
            }
    }

    private fun uploadAudioToBackend(file: File, userId: String, speakerNames: List<String>, followUpFileName: String) {
        if (!isNetworkAvailable()) {
            touchBlockOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            progressOverlay.visibility = View.GONE
            isProcessing = false
            cleanupTempFile(file)
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

        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        Toast.makeText(requireContext(), "Sending audio to server...", Toast.LENGTH_SHORT).show()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    if (!isNetworkAvailable()) {
                        abortCurrentOperation()
                    } else {
                        progressOverlay.visibility = View.GONE
                        touchBlockOverlay.visibility = View.GONE
                        isProcessing = false
                        Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    cleanupTempFile(file)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!isAdded) return
                requireActivity().runOnUiThread {
                    if (!isNetworkAvailable()) {
                        abortCurrentOperation()
                    } else {
                        progressOverlay.visibility = View.GONE
                        touchBlockOverlay.visibility = View.GONE
                        isProcessing = false
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string()
                            Log.d("UploadAudio", "Upload successful! Response: $responseBody")
                            Toast.makeText(requireContext(), "Processing started, you’ll be notified when ready", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.e("UploadAudio", "Upload failed! Response Code: ${response.code}")
                            Toast.makeText(requireContext(), "Server processing failed: ${response.code}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    cleanupTempFile(file)
                }
            }
        })
    }

    private fun cleanupTempFile(file: File?) {
        file?.let {
            if (it.exists()) {
                try {
                    it.delete()
                    Log.d("AudioListFragment", "Temp file deleted: ${it.name}")
                } catch (e: Exception) {
                    Log.e("AudioListFragment", "Failed to delete temp file: ${e.message}")
                }
            }
        }
        tempAudioFile = null
    }

    private fun startRenaming(position: Int) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        isRenaming = true
        renamingPosition = position
        deleteIcon.visibility = View.GONE
        tickIcon.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE

        if (isDeleteMode) {
            toggleDeleteMode(false)
        }

        adapter.notifyDataSetChanged()

        listView.post {
            val view = listView.getChildAt(position - listView.firstVisiblePosition)
            val editText = view?.findViewById<EditText>(R.id.editTextFileName)
            editText?.let {
                it.setText(filteredList[position])
                val textLength = it.text.length
                it.setSelection(textLength)
                it.requestFocus()

                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)

                it.post {
                    it.setSelection(textLength)
                    it.isCursorVisible = true
                }
            }
        }

        tickIcon.setOnClickListener {
            finishRenaming(position)
        }
    }

    private fun finishRenaming(position: Int) {
        val editText = listView.getChildAt(position - listView.firstVisiblePosition)
            ?.findViewById<EditText>(R.id.editTextFileName)

        val newNameWithoutExtension = editText?.text.toString()?.trim()
        if (newNameWithoutExtension.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "File name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val oldNameWithoutExtension = filteredList[position]
        val oldFullName = "$oldNameWithoutExtension.mp3"
        val newFullName = "$newNameWithoutExtension.mp3"

        if (newNameWithoutExtension == oldNameWithoutExtension) {
            Toast.makeText(requireContext(), "Enter a different name", Toast.LENGTH_SHORT).show()
            cleanupRenamingMode()
            return
        }

        if (fileNamesList.any { it == newNameWithoutExtension }) {
            Toast.makeText(requireContext(), "File name already exists", Toast.LENGTH_SHORT).show()
            return
        }

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(listView.windowToken, 0)

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Renaming file...", Toast.LENGTH_SHORT).show()

        renameAudioFile(oldFullName, newFullName, oldNameWithoutExtension, newNameWithoutExtension) { success ->
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                return@renameAudioFile
            }
            progressOverlay.visibility = View.GONE
            isProcessing = false
            if (success) {
                Toast.makeText(requireContext(), "Renamed successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Rename failed", Toast.LENGTH_SHORT).show()
            }
            cleanupRenamingMode()
        }
    }

    private fun renameAudioFile(oldFullName: String, newFullName: String, oldNameWithoutExtension: String, newNameWithoutExtension: String, callback: (Boolean) -> Unit) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            touchBlockOverlay.visibility = View.GONE
            callback(false)
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            touchBlockOverlay.visibility = View.GONE
            callback(false)
            return
        }

        val storage = FirebaseStorage.getInstance()
        val oldStorageRef = storage.reference.child("AudioFiles/$userId/$oldFullName")
        val newStorageRef = storage.reference.child("AudioFiles/$userId/$newFullName")
        val db = FirebaseFirestore.getInstance()
        val oldFileRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles").document(oldFullName)
        val newFileRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles").document(newFullName)

        oldStorageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                callback(false)
                return@addOnSuccessListener
            }
            newStorageRef.putBytes(bytes).addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    callback(false)
                    return@addOnSuccessListener
                }
                oldStorageRef.delete().addOnSuccessListener {
                    if (!isAdded || !isNetworkAvailable()) {
                        abortCurrentOperation()
                        callback(false)
                        return@addOnSuccessListener
                    }
                    oldFileRef.get().addOnSuccessListener { document ->
                        if (!isAdded || !isNetworkAvailable()) {
                            abortCurrentOperation()
                            callback(false)
                            return@addOnSuccessListener
                        }
                        if (document.exists()) {
                            val documentData = document.data ?: mapOf("fileName" to oldFullName)
                            val updatedData = documentData.toMutableMap().apply {
                                put("fileName", newFullName)
                            }
                            db.runTransaction { transaction ->
                                transaction.set(newFileRef, updatedData)
                                transaction.delete(oldFileRef)
                            }.addOnSuccessListener {
                                if (!isAdded || !isNetworkAvailable()) {
                                    abortCurrentOperation()
                                    callback(false)
                                    return@addOnSuccessListener
                                }
                                updateLocalLists(oldNameWithoutExtension, newNameWithoutExtension)
                                callback(true)
                            }.addOnFailureListener { e ->
                                if (!isAdded) return@addOnFailureListener
                                Toast.makeText(requireContext(), "Firestore update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                rollbackRename(newStorageRef, oldStorageRef, bytes) { rollbackSuccess ->
                                    if (!rollbackSuccess) {
                                        Log.e("Rename", "Rollback failed after Firestore error")
                                    }
                                    callback(false)
                                }
                            }
                        } else {
                            updateLocalLists(oldNameWithoutExtension, newNameWithoutExtension)
                            callback(true)
                        }
                    }.addOnFailureListener { e ->
                        if (!isAdded) return@addOnFailureListener
                        Toast.makeText(requireContext(), "Firestore fetch failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        rollbackRename(newStorageRef, oldStorageRef, bytes) { rollbackSuccess ->
                            if (!rollbackSuccess) {
                                Log.e("Rename", "Rollback failed after Firestore fetch error")
                            }
                            callback(false)
                        }
                    }
                }.addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    Toast.makeText(requireContext(), "Storage delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    rollbackRename(newStorageRef, oldStorageRef, bytes) { rollbackSuccess ->
                        if (!rollbackSuccess) {
                            Log.e("Rename", "Rollback failed after delete error")
                        }
                        callback(false)
                    }
                }
            }.addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(requireContext(), "Storage upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            Toast.makeText(requireContext(), "Storage download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            callback(false)
        }
    }

    private fun rollbackRename(newStorageRef: com.google.firebase.storage.StorageReference, oldStorageRef: com.google.firebase.storage.StorageReference, originalBytes: ByteArray, callback: (Boolean) -> Unit) {
        if (!isAdded) return
        newStorageRef.delete().addOnSuccessListener {
            oldStorageRef.putBytes(originalBytes).addOnSuccessListener {
                callback(true)
            }.addOnFailureListener { e ->
                Log.e("Rollback", "Failed to restore original file: ${e.message}")
                callback(false)
            }
        }.addOnFailureListener { e ->
            Log.e("Rollback", "Failed to delete new file during rollback: ${e.message}")
            callback(false)
        }
    }

    private fun updateLocalLists(oldNameWithoutExtension: String, newNameWithoutExtension: String) {
        val indexInFileNames = fileNamesList.indexOf(oldNameWithoutExtension)
        if (indexInFileNames != -1) {
            fileNamesList[indexInFileNames] = newNameWithoutExtension
            fileNamesList.sort()
        }

        val indexInFilteredList = filteredList.indexOf(oldNameWithoutExtension)
        if (indexInFilteredList != -1) {
            filteredList[indexInFilteredList] = newNameWithoutExtension
            filteredList.sort()
        }

        adapter.notifyDataSetChanged()
    }

    private fun cleanupRenamingMode() {
        isRenaming = false
        renamingPosition = -1
        tickIcon.visibility = View.GONE
        deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
        touchBlockOverlay.visibility = View.GONE
        adapter.notifyDataSetChanged()
        updateSelectAllCheckboxVisibility()
        updateSelectAllCheckboxState()
    }

    private fun startDownload(fileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        pendingDownloadFileName = fileName
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_TITLE, "$fileName.mp3")
        }
        downloadFileLauncher.launch(intent)
    }

    private fun saveAudioToUri(fileName: String, uri: android.net.Uri) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Downloading audio...", Toast.LENGTH_SHORT).show()

        storageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                return@addOnSuccessListener
            }
            try {
                val outputStream = requireContext().contentResolver.openOutputStream(uri)
                if (outputStream != null) {
                    outputStream.write(bytes)
                    outputStream.close()
                    progressOverlay.visibility = View.GONE
                    touchBlockOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Audio saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    progressOverlay.visibility = View.GONE
                    touchBlockOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Failed to save audio: Output stream null", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressOverlay.visibility = View.GONE
                touchBlockOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "Error saving audio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            progressOverlay.visibility = View.GONE
            touchBlockOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareAudio(fileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")
        val tempFile = File(requireContext().cacheDir, "$fileName.mp3")
        tempAudioFile = tempFile

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Preparing to share...", Toast.LENGTH_SHORT).show()

        storageRef.getFile(tempFile).addOnSuccessListener {
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                cleanupTempFile(tempFile)
                return@addOnSuccessListener
            }
            val uri = FileProvider.getUriForFile(requireContext(), "com.example.meetloggerv2.fileprovider", tempFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/mpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            progressOverlay.visibility = View.GONE
            touchBlockOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "Audio ready to share", Toast.LENGTH_SHORT).show()
            startActivity(Intent.createChooser(shareIntent, "Share Audio"))
        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            progressOverlay.visibility = View.GONE
            touchBlockOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanupTempFile(tempFile)
        }
    }

    private fun setupMediaControls() {
        playPauseButton.setOnClickListener { if (!isProcessing) togglePlayPause() }
        stopButton.setOnClickListener { if (!isProcessing) stopPlayback() }
        prevButton.setOnClickListener { if (!isProcessing) playPreviousAudio() }
        nextButton.setOnClickListener { if (!isProcessing) playNextAudio() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && !isProcessing) {
                    mediaPlayer.seekTo(progress)
                    currentTime.text = formatTime(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = filterFiles(query)
            override fun onQueryTextChange(newText: String?) = filterFiles(newText)
        })
    }

    private fun setupDeleteIcon() {
        deleteIcon.setOnClickListener {
            if (isDeleteMode && selectedItems.isNotEmpty() && !isProcessing) {
                showDeleteConfirmationDialog()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniPlayerDragging() {
        miniPlayer.setOnTouchListener { v, event ->
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

    private fun showDeleteConfirmationDialog() {
        // Inflate a custom layout for the message
        val inflater = LayoutInflater.from(requireContext())
        val customView = inflater.inflate(R.layout.dialog_delete_confirm_message, null)

        // Find the message TextView and set Poppins font
        val messageTextView = customView.findViewById<TextView>(R.id.dialog_message)
        messageTextView.text = "Are you sure, you want to delete the selected audio files?"

        // Set Poppins font using Typeface (for compatibility)
        val poppinsTypeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_medium)
        messageTextView.typeface = poppinsTypeface

        val dialog = AlertDialog.Builder(requireContext())
            .setView(customView)  // Use custom view instead of setMessage
            .setPositiveButton("Delete") { _, _ -> deleteSelectedItems() }
            .setNegativeButton("Cancel") { _, _ -> toggleDeleteMode(false) }
            .setCancelable(false)
            .create()

        touchBlockOverlay.visibility = View.VISIBLE

        dialog.setOnDismissListener {
            handler.postDelayed({
                touchBlockOverlay.visibility = View.GONE
            }, 200)
        }

        dialog.show()
        // Customize buttons with Poppins font
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        positiveButton.setTextColor(Color.BLUE)
        positiveButton.typeface = poppinsTypeface

        negativeButton.setTextColor(Color.RED)
        negativeButton.typeface = poppinsTypeface
    }

    private fun deleteSelectedItems() {
        if (!isNetworkAvailable()) {
            touchBlockOverlay.visibility = View.GONE
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            toggleDeleteMode(false)
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/")

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        touchBlockOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Deleting files...", Toast.LENGTH_SHORT).show()

        val itemsToDelete = selectedItems.map { filteredList[it] }.toList()
        var deleteCount = 0
        itemsToDelete.forEach { fileName ->
            val fileRef = storageRef.child("$fileName.mp3")
            fileRef.delete()
                .addOnSuccessListener {
                    if (!isAdded || !isNetworkAvailable()) {
                        abortCurrentOperation()
                        return@addOnSuccessListener
                    }
                    deleteCount++
                    if (deleteCount == itemsToDelete.size) {
                        progressOverlay.visibility = View.GONE
                        touchBlockOverlay.visibility = View.GONE
                        isProcessing = false
                        Toast.makeText(requireContext(), "Files deleted successfully", Toast.LENGTH_SHORT).show()
                        fetchAudioFiles(false)
                        toggleDeleteMode(false)
                    }
                }
                .addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    progressOverlay.visibility = View.GONE
                    touchBlockOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Failed to delete $fileName: ${e.message}", Toast.LENGTH_SHORT).show()
                    fetchAudioFiles(false)
                    toggleDeleteMode(false)
                }
        }
    }

    private fun downloadAndPlayAudio(fileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/$fileName.mp3")
        val localDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val localFile = File(localDir, "$fileName.mp3")

        mediaPlayer.reset()

        if (localFile.exists()) {
            playAudio(localFile.absolutePath, fileName)
        } else {
            isProcessing = true
            operationStartTime = System.currentTimeMillis()
            hasShownSlowToast = false
            progressOverlay.visibility = View.VISIBLE
            touchBlockOverlay.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Downloading audio...", Toast.LENGTH_SHORT).show()

            storageRef.getFile(localFile).addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                progressOverlay.visibility = View.GONE
                touchBlockOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "Audio ready to play", Toast.LENGTH_SHORT).show()
                playAudio(localFile.absolutePath, fileName)
            }.addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressOverlay.visibility = View.GONE
                touchBlockOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playAudio(filePath: String, fileName: String) {
        try {
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare()
            mediaPlayer.start()

            miniPlayer.visibility = View.VISIBLE
            currentAudioName.text = fileName
            playPauseButton.setImageResource(R.drawable.pause1)
            isPlaying = true

            seekBar.max = mediaPlayer.duration
            totalTime.text = formatTime(mediaPlayer.duration)
            currentTime.text = "00:00"
            handler.post(updateSeekBarTask)

            mediaPlayer.setOnCompletionListener {
                playNextAudio()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Playback failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            mediaPlayer.pause()
            playPauseButton.setImageResource(R.drawable.play)
            handler.removeCallbacks(updateSeekBarTask)
        } else {
            mediaPlayer.start()
            playPauseButton.setImageResource(R.drawable.pause1)
            handler.post(updateSeekBarTask)
        }
        isPlaying = !isPlaying
    }

    private fun stopPlayback() {
        mediaPlayer.reset()
        miniPlayer.visibility = View.GONE
        playPauseButton.setImageResource(R.drawable.play)
        isPlaying = false
        handler.removeCallbacks(updateSeekBarTask)
        currentTime.text = "00:00"
        seekBar.progress = 0
    }

    private fun playPreviousAudio() {
        if (filteredList.isEmpty()) return

        if (currentAudioIndex > 0) {
            currentAudioIndex--
        } else {
            currentAudioIndex = if (filteredList.size > 1) filteredList.size - 1 else 0
        }
        downloadAndPlayAudio(filteredList[currentAudioIndex])
    }

    private fun playNextAudio() {
        if (filteredList.isEmpty()) return

        if (currentAudioIndex < filteredList.size - 1) {
            currentAudioIndex++
        } else {
            currentAudioIndex = 0
        }
        downloadAndPlayAudio(filteredList[currentAudioIndex])
    }

    private fun fetchAudioFiles(showOverlay: Boolean = false) {
        if (!isNetworkAvailable()) {
            mainContent.visibility = View.GONE
            noInternetContainer.visibility = View.VISIBLE
            touchBlockOverlay.visibility = View.GONE
            if (showOverlay) {
                Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("AudioFiles/$userId/")

        if (showOverlay) {
            isProcessing = true
            operationStartTime = System.currentTimeMillis()
            hasShownSlowToast = false
            progressOverlay.visibility = View.VISIBLE
            touchBlockOverlay.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Fetching audio files...", Toast.LENGTH_SHORT).show()
        }

        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                if (!isAdded || !isNetworkAvailable()) {
                    if (showOverlay) abortCurrentOperation()
                    return@addOnSuccessListener
                }
                fileNamesList.clear()
                for (file in listResult.items) {
                    fileNamesList.add(file.name.substringBeforeLast("."))
                }
                fileNamesList.sort()

                filteredList.clear()
                filteredList.addAll(fileNamesList)
                adapter.notifyDataSetChanged()
                togglePlaceholder()
                updateDeleteIconVisibility()
                updateSelectAllCheckboxState()
                if (showOverlay) {
                    progressOverlay.visibility = View.GONE
                    touchBlockOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Audio files loaded", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                if (showOverlay) {
                    progressOverlay.visibility = View.GONE
                    touchBlockOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Failed to load audio files: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                togglePlaceholder()
            }
    }

    private fun filterFiles(query: String?): Boolean {
        filteredList.clear()
        if (query.isNullOrEmpty()) {
            filteredList.addAll(fileNamesList)
        } else {
            val lowerCaseQuery = query.toLowerCase()
            fileNamesList.forEach { fileName ->
                if (fileName.toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(fileName)
                }
            }
            filteredList.sort()
        }

        adapter.notifyDataSetChanged()

        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)
        if (filteredList.isEmpty()) {
            if (fileNamesList.isEmpty()) {
                placeholderText?.text = "No files found"
                placeholderImage?.visibility = View.GONE
            } else {
                placeholderText?.visibility = View.GONE
                placeholderImage?.visibility = View.GONE
            }
            placeholderText?.visibility = View.VISIBLE
            placeholderText?.text = "No files found"
            listView.visibility = View.GONE
        } else {
            placeholderText?.visibility = View.GONE
            placeholderImage?.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        updateDeleteIconVisibility()
        updateSelectAllCheckboxState()
        return true
    }

    private fun togglePlaceholder() {
        val placeholderImage = view?.findViewById<ImageView>(R.id.placeholderImage)
        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

        if (fileNamesList.isEmpty()) {
            placeholderImage?.visibility = View.VISIBLE
            placeholderText?.visibility = View.GONE
            searchView.visibility = View.GONE
            listView.visibility = View.GONE
        } else {
            placeholderImage?.visibility = View.GONE
            placeholderText?.visibility = View.GONE
            searchView.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
        }
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
            fetchAudioFiles(false)
        }
        handler.post(internetCheckTask)
        adjustListViewMargin() // Ensure margin is correct on resume
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(internetCheckTask)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBarTask)
        handler.removeCallbacks(internetCheckTask)
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.release()
        }
        pendingDownloadFileName = null
        temporarySpeakerList = null
        if (isRenaming) cleanupRenamingMode()
        touchBlockOverlay.visibility = View.GONE
        cleanupTempFile(tempAudioFile)
    }
}