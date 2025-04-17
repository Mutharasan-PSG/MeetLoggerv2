package com.example.meetloggerv2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import com.google.firebase.Timestamp

class ReportFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var audioListIcon: ImageView
    private lateinit var selectAllCheckbox: CheckBox
    private lateinit var fileNamesList: ArrayList<Triple<String, Timestamp, String>>
    private lateinit var filteredList: ArrayList<Triple<String, Timestamp, String>>
    private lateinit var adapter: ArrayAdapter<Triple<String, Timestamp, String>>
    private lateinit var placeholderImage: ImageView
    private lateinit var placeholderText: TextView
    private lateinit var tickIcon: ImageView
    private lateinit var progressOverlay: FrameLayout
    private lateinit var mainContent: RelativeLayout
    private lateinit var noInternetContainer: LinearLayout
    private var isRenaming = false
    private var renamingPosition = -1
    private var isDeleteMode = false
    private var isProcessing = false
    private val selectedItems = HashSet<Int>()
    private var isDataLoaded = false
    private var pendingExportContent: String? = null
    private var pendingExportFileName: String? = null
    private var tempShareFile: File? = null
    private var operationStartTime: Long = 0L
    private var hasShownSlowToast = false
    private lateinit var touchBlockOverlay: FrameLayout
    private val handler = Handler(Looper.getMainLooper())
    private val internetCheckTask = object : Runnable {
        override fun run() {
            checkInternetStatus()
            handler.postDelayed(this, 500) // Check every 500ms
        }
    }
    private val fetchDebounceTask = Runnable { fetchFileNames(false) }

    private val TAG = "ReportFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        initializeViews(view)
        setupListView()
        setupBackPressHandler()
        setupSelectAllCheckbox()

        handler.post(internetCheckTask) // Start internet monitoring
        checkInternetAndLoad()

        return view
    }

    private fun initializeViews(view: View) {
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        deleteIcon = view.findViewById(R.id.deleteIcon)
        audioListIcon = view.findViewById(R.id.audioListIcon)
        selectAllCheckbox = view.findViewById(R.id.selectAllCheckbox)
        placeholderImage = view.findViewById(R.id.placeholderImage)
        placeholderText = view.findViewById(R.id.placeholderText)
        tickIcon = view.findViewById(R.id.tickIcon)
        progressOverlay = view.findViewById(R.id.progressOverlay)
        mainContent = view.findViewById(R.id.mainContent)
        noInternetContainer = view.findViewById(R.id.noInternetContainer)
        listView.setSelector(android.R.color.transparent)
        touchBlockOverlay = view.findViewById(R.id.touchBlockOverlay)
        fileNamesList = ArrayList()
        filteredList = ArrayList()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
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
            if (!isProcessing) scheduleFetchDebounce()
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
        if (isNetworkAvailable()) {
            mainContent.visibility = View.VISIBLE
            noInternetContainer.visibility = View.GONE
            fetchFileNames(false) // Silent fetch on load
        } else {
            mainContent.visibility = View.GONE
            noInternetContainer.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_LONG).show()
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
                    toggleDeleteMode(false) // Exit delete mode and stay on fragment
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun abortCurrentOperation() {
        if (isAdded) {
            progressOverlay.visibility = View.GONE
            touchBlockOverlay.visibility = View.GONE
            isProcessing = false
            hasShownSlowToast = false
            cleanupTempFile(tempShareFile)
            Toast.makeText(requireContext(), "Operation aborted due to no internet", Toast.LENGTH_SHORT).show()
            adapter.notifyDataSetChanged()
            togglePlaceholder()
        }
    }

    private fun setupListView() {
        adapter = object : ArrayAdapter<Triple<String, Timestamp, String>>(
            requireContext(),
            R.layout.list_item,
            R.id.textViewFileName,
            filteredList
        ) {
            @RequiresApi(Build.VERSION_CODES.P)
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                val menuIcon = view.findViewById<ImageView>(R.id.menuIcon)
                val textView = view.findViewById<TextView>(R.id.textViewFileName)
                val editText = view.findViewById<EditText>(R.id.editTextFileName)

                // Handle checkbox visibility and state
                checkbox.visibility = if (isDeleteMode && !isRenaming && !isProcessing) View.VISIBLE else View.GONE
                checkbox.setOnCheckedChangeListener(null) // Clear listener to avoid interference
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (!isRenaming && !isProcessing) {
                        if (isChecked) {
                            selectedItems.add(position)
                            Log.d(TAG, "Checked position $position, selectedItems: $selectedItems")
                        } else {
                            selectedItems.remove(position)
                            Log.d(TAG, "Unchecked position $position, selectedItems: $selectedItems")
                        }
                        updateDeleteIconVisibility()
                        updateSelectAllCheckboxState()
                        notifyDataSetChanged() // Refresh adapter to reflect changes
                    }
                }

                menuIcon.visibility = if (isDeleteMode || isRenaming || isProcessing) View.GONE else View.VISIBLE
                menuIcon.setOnClickListener {
                    if (!isRenaming && !isProcessing) {
                        showOptionsPopup(menuIcon, position)
                    }
                }

                val (fileName, _, _) = filteredList[position]
                if (position == renamingPosition && isRenaming) {
                    textView.visibility = View.GONE
                    editText.visibility = View.VISIBLE
                    editText.setText(fileName)
                } else {
                    textView.visibility = View.VISIBLE
                    editText.visibility = View.GONE
                    textView.text = fileName
                }

                return view
            }
        }
        listView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = filterFiles(query)
            override fun onQueryTextChange(newText: String?) = filterFiles(newText)
        })

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming && !isProcessing) {
                val (selectedFileName, _, _) = filteredList[position]
                Log.d(TAG, "ListView clicked at position $position, selected short fileName: $selectedFileName")
                openFileDetailsFragment(selectedFileName)
            } else if (!isRenaming && !isProcessing) {
                // Toggle checkbox state in delete mode
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
                    Log.d(TAG, "Clicked position $position in delete mode, new state: $newCheckedState, selectedItems: $selectedItems")
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
                Log.d(TAG, "Long-clicked position $position, selectedItems: $selectedItems")
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                updateSelectAllCheckboxState()
                true
            } else false
        }

        deleteIcon.setOnClickListener {
            if (isDeleteMode && selectedItems.isNotEmpty() && !isProcessing) {
                showDeleteConfirmationDialog()
            }
        }

        audioListIcon.setOnClickListener {
            if (!isProcessing) openAudioListFragment()
        }
    }

    private fun setupSelectAllCheckbox() {
        selectAllCheckbox.setOnClickListener {
            if (!isRenaming && !isProcessing && isDeleteMode) {
                if (selectAllCheckbox.isChecked) {
                    for (i in 0 until filteredList.size) {
                        selectedItems.add(i)
                    }
                    Log.d(TAG, "Select All checked, selectedItems: $selectedItems")
                } else {
                    selectedItems.clear()
                    Log.d(TAG, "Select All unchecked, selectedItems: $selectedItems")
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
        val layoutParams = listView.layoutParams as RelativeLayout.LayoutParams
        val marginNormal = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 90f, resources.displayMetrics).toInt() // Non-delete mode
        val marginWithCheckbox = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 125f, resources.displayMetrics).toInt() // Delete mode with checkbox

        layoutParams.topMargin = if (selectAllCheckbox.visibility == View.VISIBLE) marginWithCheckbox else marginNormal
        listView.layoutParams = layoutParams
        listView.requestLayout() // Ensure layout updates
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun showOptionsPopup(anchorView: View, position: Int) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.popup_menu_layout, null)
        val listView = popupView.findViewById<ListView>(R.id.popup_list)

        val options = listOf("RENAME", "EXPORT", "SHARE", "COPY")

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
                textView.setPadding(16, 8, 16, 8)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.Grey))
                textView.gravity = Gravity.CENTER_VERTICAL
                textView.text = options[position]
                return view
            }
        }
        listView.adapter = adapter

        val popupWidth = (120 * resources.displayMetrics.density).toInt()
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
        popupWindow.isFocusable = true

        listView.setOnItemClickListener { _, _, index, _ ->
            Log.d("ReportFragment", "Popup option clicked: ${options[index]}")
            val (displayedFileName, _, _) = filteredList[position]
            when (options[index]) {
                "RENAME" -> startRenaming(position)
                "EXPORT" -> exportFile(displayedFileName)
                "SHARE" -> initiateShareFile(displayedFileName)
                "COPY" -> showCopyDialog(position)
            }
            popupWindow.dismiss()
        }


        listView.isClickable = true
        listView.isFocusable = true

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

    private fun showCopyDialog(position: Int) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_copy_file, null)
        val editText = dialogView.findViewById<EditText>(R.id.editTextNewFileName)
        val proceedButton = dialogView.findViewById<Button>(R.id.buttonProceed)
        val cancelButton = dialogView.findViewById<Button>(R.id.buttonCancel)

        val (displayedFileName, _, _) = filteredList[position]
        editText.setText("Copy of $displayedFileName")
        editText.setSelection(editText.text.length)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        proceedButton.setOnClickListener {
            val newNameWithoutExt = editText.text.toString().trim()
            if (newNameWithoutExt.isEmpty()) {
                Toast.makeText(requireContext(), "File name cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fullFileName = fileNamesList.find { it.first.substringBeforeLast(".") == displayedFileName }?.first
            if (fullFileName == null) {
                Log.w(TAG, "Full file name not found for: $displayedFileName")
                Toast.makeText(requireContext(), "Error: File not found", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }

            val extension = fullFileName.substringAfterLast(".", "mp3")
            val newFullName = "$newNameWithoutExt.$extension"

            if (fileNamesList.any { it.first == newFullName }) {
                Toast.makeText(requireContext(), "File name already exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            copyFile(position, newFullName)
        }

        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun copyFile(position: Int, newFullName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val (displayedFileName, _, _) = filteredList[position]
        val oldFullName = fileNamesList.find { it.first.substringBeforeLast(".") == displayedFileName }?.first
        if (oldFullName == null) {
            Log.w(TAG, "Original file not found for copying: $displayedFileName")
            Toast.makeText(requireContext(), "Error: File not found", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "No user ID found for copying")
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
       // Toast.makeText(requireContext(), "Copying file...", Toast.LENGTH_SHORT).show()

        val db = FirebaseFirestore.getInstance()
        val oldDocRef = db.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(oldFullName)
        val newDocRef = db.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(newFullName)

        oldDocRef.get().addOnSuccessListener { document ->
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                return@addOnSuccessListener
            }
            if (document.exists()) {
                val documentData = document.data ?: run {
                    Log.w(TAG, "No data in document: $oldFullName")
                    progressOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Error: File data not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Create new data map, excluding AudioLink
                val newData = documentData.toMutableMap().apply {
                    put("fileName", newFullName)
                    remove("AudioLink") // Ensure no audio reference
                }

                // Copy Firestore document only
                newDocRef.set(newData).addOnSuccessListener {
                    if (!isAdded) return@addOnSuccessListener
                    Log.d(TAG, "File copied successfully: $newFullName")
                    progressOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "File copied successfully", Toast.LENGTH_SHORT).show()
                    scheduleFetchDebounce()
                }.addOnFailureListener { e ->
                    if (!isAdded) return@addOnFailureListener
                    Log.e(TAG, "Failed to copy Firestore document: ${e.message}", e)
                    progressOverlay.visibility = View.GONE
                    isProcessing = false
                    Toast.makeText(requireContext(), "Failed to copy file", Toast.LENGTH_SHORT).show()
                }
            } else {
                if (!isAdded) return@addOnSuccessListener
                Log.w(TAG, "Original document not found: $oldFullName")
                progressOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "Error: File not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener { e ->
            if (!isAdded) return@addOnFailureListener
            Log.e(TAG, "Failed to fetch original document: ${e.message}", e)
            progressOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "Failed to copy file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startRenaming(position: Int) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Starting rename for position: $position, fileName: ${filteredList[position].first}")
        isRenaming = true
        renamingPosition = position
        deleteIcon.visibility = View.GONE
        audioListIcon.visibility = View.GONE
        tickIcon.visibility = View.VISIBLE
        tickIcon.bringToFront()
        tickIcon.isClickable = true
        touchBlockOverlay.visibility = View.VISIBLE
        if (isDeleteMode) {
            toggleDeleteMode(false)
        }

        adapter.notifyDataSetChanged()

        listView.post {
            val view = listView.getChildAt(position - listView.firstVisiblePosition)
            val editText = view?.findViewById<EditText>(R.id.editTextFileName)
            editText?.let {
                it.setText(filteredList[position].first)
                val textLength = it.text.length
                it.setSelection(textLength)
                it.requestFocus()

                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)

                it.post {
                    it.setSelection(textLength)
                    it.isCursorVisible = true
                    Log.d(TAG, "Cursor position after keyboard: ${it.selectionStart}")
                }
            } ?: Log.w(TAG, "EditText not found for position: $position")
        }

        tickIcon.setOnClickListener {
            Log.d(TAG, "Tick icon clicked")
            finishRenaming(position)
        }
    }

    private fun finishRenaming(position: Int) {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No internet connection during finishRenaming")
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            cleanupRenamingMode()
            return
        }

        val view = listView.getChildAt(position - listView.firstVisiblePosition)
        val editText = view?.findViewById<EditText>(R.id.editTextFileName)
        if (editText == null) {
            Log.w(TAG, "EditText is null for position: $position")
            Toast.makeText(requireContext(), "Error: Unable to rename", Toast.LENGTH_SHORT).show()
            cleanupRenamingMode()
            return
        }

        val newNameWithoutExtension = editText.text.toString().trim()
        if (newNameWithoutExtension.isEmpty()) {
            Log.d(TAG, "File name is empty")
            Toast.makeText(requireContext(), "File name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val oldNameWithoutExt = filteredList[position].first
        val oldFullNameEntry = fileNamesList.find { it.first.removeSuffix(".mp3") == oldNameWithoutExt }
        val oldFullName = oldFullNameEntry?.first ?: run {
            Log.w(TAG, "Old full name not found for: $oldNameWithoutExt in fileNamesList")
            Toast.makeText(requireContext(), "Error: File not found", Toast.LENGTH_SHORT).show()
            cleanupRenamingMode()
            return
        }
        val extension = oldFullName.substringAfterLast(".", "mp3")
        val newFullName = "$newNameWithoutExtension.$extension"

        if (newNameWithoutExtension == oldNameWithoutExt) {
            Log.d(TAG, "New name same as old name: $newNameWithoutExtension")
            Toast.makeText(requireContext(), "Enter a different name", Toast.LENGTH_SHORT).show()
            cleanupRenamingMode()
            return
        }

        if (fileNamesList.any { it.first == newFullName }) {
            Log.d(TAG, "File name already exists: $newFullName")
            Toast.makeText(requireContext(), "File name already exists", Toast.LENGTH_SHORT).show()
            return
        }

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(listView.windowToken, 0)

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        Log.d(TAG, "Starting rename from $oldFullName to $newFullName")
       // Toast.makeText(requireContext(), "Renaming file...", Toast.LENGTH_SHORT).show()

        updateFileNameInDatabase(oldFullName, newFullName) { success ->
            if (!isAdded || !isNetworkAvailable()) {
                Log.d(TAG, "Fragment detached or no network during rename callback")
                abortCurrentOperation()
                return@updateFileNameInDatabase
            }
            progressOverlay.visibility = View.GONE
            touchBlockOverlay.visibility = View.GONE
            isProcessing = false
            if (success) {
                Log.d(TAG, "Rename successful: $newFullName")
                Toast.makeText(requireContext(), "Renamed successfully", Toast.LENGTH_SHORT).show()
            } else {
                Log.w(TAG, "Rename failed for: $newFullName")
                Toast.makeText(requireContext(), "Rename failed", Toast.LENGTH_SHORT).show()
            }
            cleanupRenamingMode()
            scheduleFetchDebounce()
        }
    }

    private fun updateFileNameInDatabase(oldFullName: String, newFullName: String, callback: (Boolean) -> Unit) {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No internet in updateFileNameInDatabase")
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "No user ID found")
            callback(false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()
        val oldStorageRef = storage.reference.child("AudioFiles/$userId/$oldFullName")
        val newStorageRef = storage.reference.child("AudioFiles/$userId/$newFullName")
        val userFilesRef = db.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(oldFullName)
        val newFileRef = db.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(newFullName)

        userFilesRef.get().addOnSuccessListener { document ->
            if (!isAdded || !isNetworkAvailable()) {
                Log.d(TAG, "Fragment detached or no network in get")
                callback(false)
                return@addOnSuccessListener
            }
            if (document.exists()) {
                val documentData = document.data ?: mapOf("fileName" to oldFullName)
                val updatedData = documentData.toMutableMap().apply {
                    put("fileName", newFullName)
                }

                oldStorageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                    newStorageRef.putBytes(bytes).addOnSuccessListener {
                        oldStorageRef.delete().addOnSuccessListener {
                            db.runTransaction { transaction ->
                                transaction.set(newFileRef, updatedData)
                                transaction.delete(userFilesRef)
                            }.addOnSuccessListener {
                                if (isAdded) updateLocalLists(oldFullName, newFullName)
                                Log.d(TAG, "Transaction succeeded")
                                callback(true)
                            }.addOnFailureListener { e ->
                                if (isAdded) Log.e(TAG, "Firestore transaction failed", e)
                                callback(false)
                            }
                        }.addOnFailureListener { e ->
                            if (isAdded) Log.e(TAG, "Failed to delete old file", e)
                            callback(false)
                        }
                    }.addOnFailureListener { e ->
                        if (isAdded) Log.e(TAG, "Failed to upload renamed file", e)
                        callback(false)
                    }
                }.addOnFailureListener { e ->
                    if (isAdded) Log.e(TAG, "Failed to download original file, proceeding with Firestore", e)
                    db.runTransaction { transaction ->
                        transaction.set(newFileRef, updatedData)
                        transaction.delete(userFilesRef)
                    }.addOnSuccessListener {
                        if (isAdded) updateLocalLists(oldFullName, newFullName)
                        Log.d(TAG, "Transaction succeeded without storage")
                        callback(true)
                    }.addOnFailureListener { e ->
                        if (isAdded) Log.e(TAG, "Firestore transaction failed without storage", e)
                        callback(false)
                    }
                }
            } else {
                if (isAdded) Log.w(TAG, "Original document not found")
                callback(false)
            }
        }.addOnFailureListener { e ->
            if (isAdded) Log.e(TAG, "Failed to fetch original document", e)
            callback(false)
        }
    }

    private fun updateLocalLists(oldFullName: String, newFullName: String) {
        val oldNameWithoutExt = oldFullName.substringBefore(".")
        val newNameWithoutExt = newFullName.substringBefore(".")
        val indexInFileNames = fileNamesList.indexOfFirst { it.first == oldFullName }
        if (indexInFileNames != -1) {
            val timestamp = fileNamesList[indexInFileNames].second
            val status = fileNamesList[indexInFileNames].third
            fileNamesList[indexInFileNames] = Triple(newFullName, timestamp, status)
        }

        val indexInFilteredList = filteredList.indexOfFirst { it.first == oldNameWithoutExt }
        if (indexInFilteredList != -1) {
            val timestamp = filteredList[indexInFilteredList].second
            val status = filteredList[indexInFilteredList].third
            filteredList[indexInFilteredList] = Triple(newNameWithoutExt, timestamp, status)
        }

        if (isAdded) {
            adapter.notifyDataSetChanged()
            togglePlaceholder()
        }
    }

    private fun cleanupRenamingMode() {
        isRenaming = false
        renamingPosition = -1
        tickIcon.visibility = View.GONE
        deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
        audioListIcon.visibility = if (isDeleteMode) View.GONE else View.VISIBLE
        touchBlockOverlay.visibility = View.GONE
        if (isAdded) {
            adapter.notifyDataSetChanged()
            updateSelectAllCheckboxVisibility()
        }
    }

    private fun exportFile(displayedFileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val fullFileName = fileNamesList.find { it.first.startsWith(displayedFileName) }?.first ?: run {
            Log.w(TAG, "No full file name found for export: $displayedFileName")
            return
        }
        Log.d(TAG, "Exporting file, displayedFileName: $displayedFileName, resolved fullFileName: $fullFileName")
        fetchAndProcessContent(fullFileName) { formattedContent ->
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                return@fetchAndProcessContent
            }
            showExportDialog(fullFileName, formattedContent)
        }
    }

    private fun initiateShareFile(displayedFileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val fullFileName = fileNamesList.find { it.first.startsWith(displayedFileName) }?.first ?: run {
            Log.w(TAG, "No full file name found for share: $displayedFileName")
            return
        }
        Log.d(TAG, "Initiating share for file, displayedFileName: $displayedFileName, resolved fullFileName: $fullFileName")
        fetchAndProcessContent(fullFileName) { formattedContent ->
            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                return@fetchAndProcessContent
            }
            showShareFormatDialog(fullFileName, formattedContent)
        }
    }

    private fun fetchAndProcessContent(fileName: String, callback: (String) -> Unit) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val fileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        Toast.makeText(requireContext(), "Fetching file content...", Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Fetching content for file: $fileName")
        fileRef.get()
            .addOnSuccessListener { document ->
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                progressOverlay.visibility = View.GONE
                isProcessing = false
                if (document.exists()) {
                    val responseText = document.getString("Response") ?: "No response available"
                    val cleanedText = responseText.replace("*", "").trim()
                    Log.d(TAG, "Content fetched successfully for $fileName: $cleanedText")
                    callback(cleanedText)
                } else {
                    Log.w(TAG, "Document not found in Firestore for: $fileName")
                    Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressOverlay.visibility = View.GONE
                isProcessing = false
                Log.e(TAG, "Failed to fetch file details for $fileName: ${e.message}", e)
                Toast.makeText(requireContext(), "Failed to load file details", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showExportDialog(fileName: String, content: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            exportContent(fileName, content, "PDF")
        }

        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            exportContent(fileName, content, "DOCX")
        }

        dialog.show()
    }

    private fun showShareFormatDialog(fileName: String, content: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_export_options, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<LinearLayout>(R.id.pdfButtonLayout).setOnClickListener {
            dialog.dismiss()
            exportAndShareContent(fileName, content, "PDF")
        }

        dialogView.findViewById<LinearLayout>(R.id.docxButtonLayout).setOnClickListener {
            dialog.dismiss()
            exportAndShareContent(fileName, content, "DOCX")
        }

        dialog.show()
    }

    private fun exportContent(fileName: String, content: String, format: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "No content to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            pendingExportContent = content
            pendingExportFileName = fileName

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                val cleanFileName = fileName.substringBeforeLast(".")
                putExtra(Intent.EXTRA_TITLE, "$cleanFileName.${if (format == "PDF") "pdf" else "docx"}")
            }
            Log.d(TAG, "Starting export intent for $fileName in $format format")
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch export intent for $fileName: ${e.message}", e)
            Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == android.app.Activity.RESULT_OK && data != null) {
            val uri: Uri = data.data!!
            val content = pendingExportContent
            val fileName = pendingExportFileName

            if (content != null && fileName != null) {
                Log.d(TAG, "Saving content to URI for file: $fileName")
                saveContentToUri(uri, content)
                pendingExportContent = null
                pendingExportFileName = null
            } else {
                Log.e(TAG, "Pending content or file name is null")
                Toast.makeText(requireContext(), "Failed to export: Content not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveContentToUri(uri: Uri, content: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
      //  Toast.makeText(requireContext(), "Saving file...", Toast.LENGTH_SHORT).show()

        try {
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                when {
                    uri.toString().endsWith("pdf") -> saveAsPdf(content, outputStream)
                    uri.toString().endsWith("docx") -> saveAsDocx(content, outputStream)
                }
                outputStream.close()
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return
                }
                progressOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "File saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                if (!isAdded) return
                progressOverlay.visibility = View.GONE
                isProcessing = false
                Log.e(TAG, "Failed to open output stream for URI: $uri")
                Toast.makeText(requireContext(), "Failed to save file: Output stream unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            if (!isAdded) return
            progressOverlay.visibility = View.GONE
            isProcessing = false
            Log.e(TAG, "Error saving file to URI: ${e.message}", e)
            Toast.makeText(requireContext(), "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAndShareContent(fileName: String, content: String, format: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "No content to share", Toast.LENGTH_SHORT).show()
            return
        }

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
       // Toast.makeText(requireContext(), "Preparing to share...", Toast.LENGTH_SHORT).show()

        try {
            val cleanFileName = fileName.substringBeforeLast(".")
            val tempFile = File(requireContext().cacheDir, "$cleanFileName.${if (format == "PDF") "pdf" else "docx"}")
            tempShareFile = tempFile
            val outputStream = FileOutputStream(tempFile)
            when (format) {
                "PDF" -> saveAsPdf(content, outputStream)
                "DOCX" -> saveAsDocx(content, outputStream)
            }
            outputStream.close()

            if (!isAdded || !isNetworkAvailable()) {
                abortCurrentOperation()
                cleanupTempFile(tempFile)
                return
            }

            val uri = FileProvider.getUriForFile(requireContext(), "com.example.meetloggerv2.fileprovider", tempFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            progressOverlay.visibility = View.GONE
            isProcessing = false
          //  Toast.makeText(requireContext(), "File ready to share", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Starting share intent for $fileName in $format format")
            startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (e: Exception) {
            if (!isAdded) return
            progressOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "Error during file sharing: ${e.message}", Toast.LENGTH_SHORT).show()
            cleanupTempFile(tempShareFile)
        }
    }

    private fun saveAsPdf(content: String, outputStream: OutputStream?) {
        if (outputStream == null) return

        val document = PdfDocument()
        val pageWidth = 500
        val pageHeight = 700
        val marginLeft = 40f
        val marginRight = 40f
        val marginTop = 50f
        val marginBottom = 50f
        val lineHeight = 20f
        val minWordsForJustification = 7

        val normalPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }

        val boldPaint = Paint(normalPaint).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
        }

        var currentY = marginTop
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val lines = content.split("\n")
        val maxLineWidth = pageWidth - marginLeft - marginRight

        for (line in lines) {
            if (line.isBlank()) {
                currentY += lineHeight
                continue
            }

            var lineText = line.trim().replace(Regex("\\s+:\\s+"), ": ")
            lineText = formatSummarization(lineText)
            val colonIndex = lineText.indexOf(":")

            if (lineText.startsWith("SUMMARY OF THE CONTENT") ||
                lineText.startsWith("TRANSCRIPTION OF SPEAKERS")) {
                canvas.drawText(lineText, marginLeft, currentY, boldPaint)
                currentY += lineHeight * 1.5f
                continue
            }

            if (colonIndex > -1) {
                val beforeColon = lineText.substring(0, colonIndex).trim()
                val afterColon = lineText.substring(colonIndex + 1).trim()

                canvas.drawText("$beforeColon:", marginLeft, currentY, boldPaint)

                if (afterColon.isNotEmpty()) {
                    currentY += lineHeight
                    val words = afterColon.split(" ")
                    val wordBuffer = mutableListOf<String>()

                    for (word in words) {
                        val testLine = (wordBuffer + word).joinToString(" ")
                        val textWidth = normalPaint.measureText(testLine)

                        if (textWidth > maxLineWidth) {
                            if (wordBuffer.size >= minWordsForJustification) {
                                drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                            } else {
                                canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                            }
                            currentY += lineHeight
                            wordBuffer.clear()
                        }
                        wordBuffer.add(word)

                        if (currentY > pageHeight - marginBottom) {
                            document.finishPage(page)
                            pageNumber++
                            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                            canvas = page.canvas
                            currentY = marginTop
                        }
                    }

                    if (wordBuffer.isNotEmpty()) {
                        if (wordBuffer.size >= minWordsForJustification) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                    }
                } else {
                    currentY += lineHeight
                }
            } else if (lineText.matches(Regex("^Speaker [A-Z]+:.*"))) {
                val parts = lineText.split(":", limit = 2)
                val speaker = parts[0].trim()
                val text = parts.getOrNull(1)?.trim() ?: ""

                canvas.drawText(speaker, marginLeft, currentY, boldPaint)
                currentY += lineHeight

                if (text.isNotEmpty()) {
                    val words = text.split(" ")
                    val wordBuffer = mutableListOf<String>()

                    for (word in words) {
                        val testLine = (wordBuffer + word).joinToString(" ")
                        val textWidth = normalPaint.measureText(testLine)

                        if (textWidth > maxLineWidth) {
                            if (wordBuffer.size >= minWordsForJustification) {
                                drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                            } else {
                                canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                            }
                            currentY += lineHeight
                            wordBuffer.clear()
                        }
                        wordBuffer.add(word)

                        if (currentY > pageHeight - marginBottom) {
                            document.finishPage(page)
                            pageNumber++
                            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                            canvas = page.canvas
                            currentY = marginTop
                        }
                    }

                    if (wordBuffer.isNotEmpty()) {
                        if (wordBuffer.size >= minWordsForJustification) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                    }
                }
            } else {
                val words = lineText.split(" ")
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        if (wordBuffer.size >= minWordsForJustification) {
                            drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                        } else {
                            canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                        }
                        currentY += lineHeight
                        wordBuffer.clear()
                    }
                    wordBuffer.add(word)

                    if (currentY > pageHeight - marginBottom) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        currentY = marginTop
                    }
                }

                if (wordBuffer.isNotEmpty()) {
                    if (wordBuffer.size >= minWordsForJustification) {
                        drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                    } else {
                        canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    }
                    currentY += lineHeight
                }
            }

            if (currentY > pageHeight - marginBottom) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                currentY = marginTop
            }
        }

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
        outputStream.close()
    }

    private fun formatSummarization(text: String): String {
        var formattedText = text
        formattedText = formattedText.replace(Regex("\\*\\*(.*?)\\*\\*")) { match ->
            "**${match.groupValues[1]}**"
        }
        formattedText = formattedText.replace(Regex("(?<=\\w)\\*(?=\\W)")) { "" }
        formattedText = formattedText.replace(Regex("(?<=\\W)\\*(?=\\w)")) { "" }
        formattedText = formattedText.replace(Regex("\\*(.*?)\\*")) { match ->
            match.groupValues[1]
        }
        return formattedText
    }

    private fun drawJustifiedText(canvas: Canvas, words: List<String>, x: Float, y: Float, maxWidth: Float, paint: Paint) {
        if (words.isEmpty()) return

        val totalWords = words.size
        val textWithoutSpacing = words.joinToString("") { it }
        val textWidth = paint.measureText(textWithoutSpacing)
        val extraSpacing = (maxWidth - textWidth) / (totalWords - 1).coerceAtLeast(1)

        var currentX = x
        for ((index, word) in words.withIndex()) {
            canvas.drawText(word, currentX, y, paint)
            currentX += paint.measureText(word) + if (index < words.lastIndex) extraSpacing else 0f
        }
    }

    private fun saveAsDocx(content: String, outputStream: OutputStream?) {
        if (outputStream == null) return

        val document = XWPFDocument()
        val lines = content.split("\n")
        var isSummarization = true

        for (line in lines) {
            if (line.isBlank()) {
                document.createParagraph()
                continue
            }

            if (line.startsWith("Speaker")) {
                isSummarization = false
            }

            val para = document.createParagraph()
            para.alignment = ParagraphAlignment.BOTH

            if (isSummarization) {
                para.spacingAfter = 200
            } else {
                para.spacingBefore = 200
            }

            val processedLine = line.trim().replace(Regex("\\s+:\\s+"), ": ")
            val colonIndex = processedLine.indexOf(":")

            when {
                processedLine == "SUMMARY OF THE CONTENT" ||
                        processedLine == "TRANSCRIPTION OF SPEAKERS" -> {
                    val boldRun = para.createRun()
                    boldRun.isBold = true
                    boldRun.setText(processedLine)
                    para.spacingAfter = 300
                }
                colonIndex > -1 -> {
                    val beforeColon = processedLine.substring(0, colonIndex).trim()
                    val afterColon = processedLine.substring(colonIndex + 1).trim()

                    val boldRun = para.createRun()
                    boldRun.isBold = true
                    boldRun.setText("$beforeColon:")

                    if (afterColon.isNotEmpty()) {
                        val normalRun = para.createRun()
                        normalRun.setText(" $afterColon")
                    }
                }
                processedLine.matches(Regex("^Speaker [A-Z]+:.*")) -> {
                    val parts = processedLine.split(":", limit = 2)
                    val speakerName = parts[0].trim()
                    val remainingText = parts.getOrNull(1)?.trim() ?: ""

                    val speakerRun = para.createRun()
                    speakerRun.isBold = true
                    speakerRun.setText("$speakerName:")

                    if (remainingText.isNotEmpty()) {
                        val contentRun = para.createRun()
                        contentRun.setText(" $remainingText")
                    }
                }
                else -> {
                    val run = para.createRun()
                    run.setText(processedLine)
                }
            }
        }

        document.write(outputStream)
        document.close()
    }

    private fun filterFiles(query: String?): Boolean {
        filteredList.clear()

        if (query.isNullOrEmpty()) {
            // If fileNamesList is empty, trigger a fetch to ensure data is loaded
            if (fileNamesList.isEmpty() && isNetworkAvailable() && isAdded) {
                Log.d(TAG, "fileNamesList empty on clear search, triggering fetch")
                fetchFileNames(false) // Silent fetch to avoid progress overlay
            }
            // Copy full list, transforming to match displayed names (without extension)
            filteredList.addAll(fileNamesList.map { Triple(it.first.substringBeforeLast("."), it.second, it.third) })
        } else {
            val lowerCaseQuery = query.toLowerCase()
            fileNamesList.forEach { (fileName, timestamp, status) ->
                if (fileName.toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(Triple(fileName.substringBeforeLast("."), timestamp, status))
                }
            }
        }

        if (isAdded) {
            if (filteredList.isEmpty()) {
                if (fileNamesList.isEmpty()) {
                    placeholderText.text = "No files found"
                    placeholderImage.visibility = View.VISIBLE
                    placeholderText.visibility = View.VISIBLE
                } else {
                    placeholderText.text = "No files found"
                    placeholderImage.visibility = View.GONE
                    placeholderText.visibility = View.VISIBLE
                }
                listView.visibility = View.GONE
            } else {
                placeholderText.visibility = View.GONE
                placeholderImage.visibility = View.GONE
                listView.visibility = View.VISIBLE
            }
            adapter.notifyDataSetChanged()
            updateDeleteIconVisibility()
            updateSelectAllCheckboxState()
        }
        Log.d(TAG, "filterFiles: query='$query', filteredList size=${filteredList.size}, items=${filteredList.map { it.first }}")
        return true
    }

    private fun toggleDeleteMode(enable: Boolean) {
        isDeleteMode = enable
        if (!enable) selectedItems.clear()
        if (isAdded) {
            adapter.notifyDataSetChanged()
            updateDeleteIconVisibility()
            updateSelectAllCheckboxVisibility()
            updateSelectAllCheckboxState()
        }
    }

    private fun updateDeleteIconVisibility() {
        val shouldShowDelete = isDeleteMode && selectedItems.isNotEmpty() && !isProcessing
        deleteIcon.visibility = if (shouldShowDelete) View.VISIBLE else View.GONE
        audioListIcon.visibility = if (shouldShowDelete) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmationDialog() {
        val inflater = LayoutInflater.from(requireContext())
        val customView = inflater.inflate(R.layout.dialog_delete_confirm_message, null)

        val messageTextView = customView.findViewById<TextView>(R.id.dialog_message)
        messageTextView.text = "Are you sure, you want to delete the selected files?"
        val poppinsTypeface = ResourcesCompat.getFont(requireContext(), R.font.poppins_medium)
        messageTextView.typeface = poppinsTypeface

        val dialog = AlertDialog.Builder(requireContext())
            .setView(customView)
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

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        positiveButton.setTextColor(Color.BLUE)
        positiveButton.typeface = poppinsTypeface

        negativeButton.setTextColor(Color.RED)
        negativeButton.typeface = poppinsTypeface
    }

    private fun deleteSelectedItems() {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            toggleDeleteMode(false)
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.e(TAG, "No user ID found, cannot delete")
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            toggleDeleteMode(false)
            return
        }

        if (selectedItems.isEmpty()) {
            Log.w(TAG, "No items selected for deletion")
            Toast.makeText(requireContext(), "No files selected", Toast.LENGTH_SHORT).show()
            toggleDeleteMode(false)
            return
        }

        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        isProcessing = true
        operationStartTime = System.currentTimeMillis()
        hasShownSlowToast = false
        progressOverlay.visibility = View.VISIBLE
        selectAllCheckbox.visibility = View.GONE
      //  Toast.makeText(requireContext(), "Deleting files...", Toast.LENGTH_SHORT).show()

        // Use a Set to avoid duplicates
        val itemsToDelete = mutableSetOf<String>()
        synchronized(filteredList) {
            selectedItems.forEach { position ->
                if (position in 0 until filteredList.size) {
                    val (shortName, _, _) = filteredList[position]
                    // Exact match for base name
                    val fullName = fileNamesList.find { it.first.substringBeforeLast(".") == shortName }?.first
                    if (fullName != null) {
                        itemsToDelete.add(fullName)
                    } else {
                        Log.w(TAG, "No full file name found for shortName: $shortName")
                    }
                } else {
                    Log.w(TAG, "Invalid position: $position, filteredList size: ${filteredList.size}")
                }
            }
        }

        if (itemsToDelete.isEmpty()) {
            Log.e(TAG, "No valid files to delete after mapping")
            progressOverlay.visibility = View.GONE
            isProcessing = false
            Toast.makeText(requireContext(), "No valid files to delete", Toast.LENGTH_SHORT).show()
            toggleDeleteMode(false)
            return
        }

        Log.d(TAG, "Items to delete: $itemsToDelete")

        val batch = db.batch()
        itemsToDelete.forEach { fullFileName ->
            val docRef = userFilesRef.document(fullFileName)
            batch.delete(docRef)
            Log.d(TAG, "Queued deletion for: $fullFileName")
        }

        batch.commit()
            .addOnSuccessListener {
                if (!isAdded || !isNetworkAvailable()) {
                    abortCurrentOperation()
                    return@addOnSuccessListener
                }
                Log.d(TAG, "Batch delete successful for ${itemsToDelete.size} files")

                synchronized(fileNamesList) {
                    synchronized(filteredList) {
                        // Remove deleted items from both lists
                        fileNamesList.removeAll { it.first in itemsToDelete }
                        filteredList.removeAll { itemsToDelete.contains(it.first + ".mp3") }
                        progressOverlay.visibility = View.GONE
                        isProcessing = false
                        Toast.makeText(requireContext(), "Files deleted successfully", Toast.LENGTH_SHORT).show()
                        toggleDeleteMode(false)
                        adapter.notifyDataSetChanged()
                        togglePlaceholder()
                    }
                }
                scheduleFetchDebounce()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                Log.e(TAG, "Batch delete failed: ${e.message}", e)
                progressOverlay.visibility = View.GONE
                isProcessing = false
                Toast.makeText(requireContext(), "Failed to delete files: ${e.message}", Toast.LENGTH_SHORT).show()
                toggleDeleteMode(false)
                scheduleFetchDebounce()
            }
    }

    private fun fetchFileNames(showProgress: Boolean = true) {
        if (!isNetworkAvailable()) {
            if (isAdded) {
                mainContent.visibility = View.GONE
                noInternetContainer.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "No user ID found in fetchFileNames")
            return
        }
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        if (showProgress) {
            isProcessing = true
            operationStartTime = System.currentTimeMillis()
            hasShownSlowToast = false
            progressOverlay.visibility = View.VISIBLE
            Toast.makeText(requireContext(), "Fetching documents...", Toast.LENGTH_SHORT).show()
        }

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (!isAdded || !isNetworkAvailable()) {
                if (showProgress) abortCurrentOperation()
                return@addSnapshotListener
            }
            if (showProgress) {
                progressOverlay.visibility = View.GONE
                isProcessing = false
            }
            if (error != null) {
                Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                togglePlaceholderOnError()
                Toast.makeText(requireContext(), "Failed to load documents: ${error.message}", Toast.LENGTH_SHORT).show()
                handler.postDelayed({ fetchFileNames(false) }, 500)
                return@addSnapshotListener
            }

            Log.d(TAG, "Received snapshot update")
            synchronized(fileNamesList) {
                synchronized(filteredList) {
                    fileNamesList.clear()
                    snapshot?.documents?.forEach { document ->
                        val fileName = document.getString("fileName") ?: run {
                            Log.w(TAG, "Null filename in document: ${document.id}")
                            return@forEach
                        }
                        val timestamp = document.getTimestamp("timestamp_clientUpload") ?: Timestamp(0, 0)
                        val status = document.getString("status") ?: "processing"
                        fileNamesList.add(Triple(fileName, timestamp, status))
                        Log.d(TAG, "Added file: $fileName with timestamp: $timestamp")
                    }

                    // Sort files by timestamp, descending
                    fileNamesList.sortByDescending { it.second.seconds + it.second.nanoseconds / 1_000_000_000.0 }

                    // Reapply current search query to filteredList
                    val currentQuery = searchView.query?.toString()
                    filteredList.clear()
                    if (currentQuery.isNullOrEmpty()) {
                        filteredList.addAll(fileNamesList.map { Triple(it.first.substringBeforeLast("."), it.second, it.third) })
                    } else {
                        val lowerCaseQuery = currentQuery.toLowerCase()
                        fileNamesList.forEach { (fileName, timestamp, status) ->
                            if (fileName.toLowerCase().contains(lowerCaseQuery)) {
                                filteredList.add(Triple(fileName.substringBeforeLast("."), timestamp, status))
                            }
                        }
                    }

                    Log.d(TAG, "Updated filteredList with ${filteredList.size} items: ${filteredList.map { it.first }}")
                    isDataLoaded = true
                    togglePlaceholder()
                    adapter.notifyDataSetChanged()
                    updateDeleteIconVisibility()
                    updateSelectAllCheckboxState()
                }
            }
        }
    }

    private fun scheduleFetchDebounce() {
        handler.removeCallbacks(fetchDebounceTask)
        handler.postDelayed(fetchDebounceTask, 500)
    }

    private fun togglePlaceholder() {
        if (!isDataLoaded || !isAdded) return

        if (fileNamesList.isEmpty()) {
            placeholderImage.visibility = View.VISIBLE
            placeholderText.visibility = View.GONE
            searchView.visibility = View.GONE
            listView.visibility = View.GONE
        } else {
            placeholderImage.visibility = View.GONE
            placeholderText.visibility = View.GONE
            searchView.visibility = View.VISIBLE
            listView.visibility = View.VISIBLE
        }
    }

    private fun togglePlaceholderOnError() {
        if (!isAdded) return
        placeholderImage.visibility = View.VISIBLE
        placeholderText.visibility = View.GONE
        searchView.visibility = View.GONE
        listView.visibility = View.GONE
    }

    private fun openFileDetailsFragment(displayedFileName: String) {
        if (!isNetworkAvailable()) {
            Toast.makeText(requireContext(), "No internet connection", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "No user ID found, cannot open FileDetailsFragment")
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Log available file names for debugging
        Log.d(TAG, "Available fileNamesList: ${fileNamesList.map { it.first }}")
        Log.d(TAG, "Searching for displayedFileName: $displayedFileName")

        // Find all matching files
        val matchingFiles = fileNamesList.filter { it.first.substringBeforeLast(".") == displayedFileName }
        if (matchingFiles.isEmpty()) {
            Log.w(TAG, "No full file name found for displayedFileName: $displayedFileName")
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            // Trigger a fetch to refresh data and retry
            if (isNetworkAvailable()) {
                Log.d(TAG, "Triggering fetch due to no matching file")
                fetchFileNames(false) // Silent fetch
            }
            return
        }
        if (matchingFiles.size > 1) {
            Log.w(TAG, "Multiple files match displayedFileName: $displayedFileName, matches: ${matchingFiles.map { it.first }}")
            // Pick the most recent file based on timestamp
        }

        val fullFileName = matchingFiles.maxByOrNull { it.second.seconds + it.second.nanoseconds / 1_000_000_000.0 }?.first
            ?: matchingFiles.first().first
        Log.d(TAG, "Resolved fullFileName: $fullFileName from displayedFileName: $displayedFileName")

        val fileDetailsFragment = FileDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", fullFileName)
                Log.d(TAG, "Bundle prepared with fileName: $fullFileName")
            }
        }

        val transaction: FragmentTransaction = parentFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
        transaction.replace(R.id.fragment_container, fileDetailsFragment)
        transaction.addToBackStack(null)
        Log.d(TAG, "Committing transaction for FileDetailsFragment with fileName: $fullFileName")
        transaction.commit()
    }

    private fun openAudioListFragment() {
        val audioListFragment = AudioListFragment()
        val transaction: FragmentTransaction = parentFragmentManager.beginTransaction()
        transaction.setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right
        )
        transaction.replace(R.id.fragment_container, audioListFragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun cleanupTempFile(file: File?) {
        file?.let {
            if (it.exists()) {
                try {
                    it.delete()
                    Log.d(TAG, "Temp file deleted: ${it.name}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete temp file: ${e.message}")
                }
            }
        }
        tempShareFile = null
    }

    override fun onResume() {
        super.onResume()
        if (isProcessing) {
            Toast.makeText(requireContext(), "Previous operation interrupted", Toast.LENGTH_SHORT).show()
            abortCurrentOperation()
            scheduleFetchDebounce()
        }
        handler.post(internetCheckTask)
        adjustListViewMargin() // Ensure margin is correct on resume
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(internetCheckTask)
        handler.removeCallbacks(fetchDebounceTask)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(internetCheckTask)
        handler.removeCallbacks(fetchDebounceTask)
        if (isRenaming) cleanupRenamingMode()
        pendingExportContent = null
        pendingExportFileName = null
        cleanupTempFile(tempShareFile)
    }
}