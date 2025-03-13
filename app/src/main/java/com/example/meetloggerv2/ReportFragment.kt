package com.example.meetloggerv2

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.FileProvider
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

class ReportFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var audioListIcon: ImageView
    private lateinit var fileNamesList: ArrayList<String>
    private lateinit var filteredList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var placeholderImage: ImageView
    private lateinit var placeholderText: TextView
    private lateinit var tickIcon: ImageView
    private var isRenaming = false
    private var renamingPosition = -1
    private var isDeleteMode = false
    private val selectedItems = HashSet<Int>()
    private var isDataLoaded = false
    // Temporary storage for export content
    private var pendingExportContent: String? = null
    private var pendingExportFileName: String? = null

    private val TAG = "ReportFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)
        deleteIcon = view.findViewById(R.id.deleteIcon)
        audioListIcon = view.findViewById(R.id.audioListIcon)
        placeholderImage = view.findViewById(R.id.placeholderImage)
        placeholderText = view.findViewById(R.id.placeholderText)
        tickIcon = view.findViewById(R.id.tickIcon)
        listView.setSelector(android.R.color.transparent)

        fileNamesList = ArrayList()
        filteredList = ArrayList()

        adapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item, R.id.textViewFileName, filteredList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                val menuIcon = view.findViewById<ImageView>(R.id.menuIcon)
                val textView = view.findViewById<TextView>(R.id.textViewFileName)
                val editText = view.findViewById<EditText>(R.id.editTextFileName)

                checkbox.visibility = if (isDeleteMode && !isRenaming) View.VISIBLE else View.GONE
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (!isRenaming) {
                        if (isChecked) selectedItems.add(position) else selectedItems.remove(position)
                        updateDeleteIconVisibility()
                    }
                }

                menuIcon.visibility = if (isDeleteMode || isRenaming) View.GONE else View.VISIBLE
                menuIcon.setOnClickListener {
                    if (!isRenaming) {
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

        setInitialVisibility()
        fetchFileNames()

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = filterFiles(query)
            override fun onQueryTextChange(newText: String?) = filterFiles(newText)
        })

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming) {
                val selectedFileName = filteredList[position]
                openFileDetailsFragment(selectedFileName)
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isDeleteMode && !isRenaming) {
                toggleDeleteMode(true)
                selectedItems.add(position)
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                true
            } else false
        }

        deleteIcon.setOnClickListener {
            if (isDeleteMode && selectedItems.isNotEmpty()) {
                showDeleteConfirmationDialog()
            }
        }

        audioListIcon.setOnClickListener {
            openAudioListFragment()
        }

        return view
    }

    private fun setInitialVisibility() {
        placeholderImage.visibility = View.GONE
        placeholderText.visibility = View.GONE
        searchView.visibility = View.GONE
        listView.visibility = View.GONE
        deleteIcon.visibility = View.GONE
        audioListIcon.visibility = View.VISIBLE
    }

    private fun showOptionsPopup(anchorView: View, position: Int) {
        val popup = PopupMenu(requireContext(), anchorView)
        popup.menu.add("Rename")
        popup.menu.add("Export")
        popup.menu.add("Share")

        popup.gravity = Gravity.END

        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "Rename" -> startRenaming(position)
                "Export" -> exportFile(filteredList[position])
                "Share" -> shareFile(filteredList[position])
            }
            true
        }
        popup.show()
    }

    private fun startRenaming(position: Int) {
        Log.d(TAG, "Starting rename for position: $position")
        isRenaming = true
        renamingPosition = position
        deleteIcon.visibility = View.GONE
        audioListIcon.visibility = View.GONE
        tickIcon.visibility = View.VISIBLE

        if (isDeleteMode) {
            toggleDeleteMode(false)
        }

        adapter.notifyDataSetChanged()

        // Ensure cursor is placed at the end after the view is updated
        listView.post {
            val view = listView.getChildAt(position - listView.firstVisiblePosition)
            val editText = view?.findViewById<EditText>(R.id.editTextFileName)
            editText?.let {
                it.setText(filteredList[position]) // Set text
                val textLength = it.text.length
                it.setSelection(textLength) // Move cursor to end
                it.requestFocus() // Request focus first

                // Show keyboard
                val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)

                // Ensure cursor stays at end after keyboard appears
                it.post {
                    it.setSelection(textLength)
                    it.isCursorVisible = true
                    Log.d(TAG, "Cursor position after keyboard: ${it.selectionStart}")
                }
            } ?: Log.w(TAG, "EditText not found for position: $position")
        }

        tickIcon.setOnClickListener {
            finishRenaming(position)
        }
    }

    private fun updateFileNameInDatabase(oldFullName: String, newFullName: String, callback: (Boolean) -> Unit) {
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

        // First get the document data
        userFilesRef.get().addOnSuccessListener { document ->
            if (document.exists()) {
                val documentData = document.data ?: mapOf("fileName" to oldFullName)
                val updatedData = documentData.toMutableMap().apply {
                    put("fileName", newFullName)
                }

                // Handle storage file rename
                oldStorageRef.getBytes(Long.MAX_VALUE).addOnSuccessListener { bytes ->
                    // Upload to new location
                    newStorageRef.putBytes(bytes).addOnSuccessListener {
                        // Delete old file after successful upload
                        oldStorageRef.delete().addOnSuccessListener {
                            Log.d(TAG, "Old file deleted from storage")

                            // Update Firestore in a transaction
                            db.runTransaction { transaction ->
                                transaction.set(newFileRef, updatedData)
                                transaction.delete(userFilesRef)
                            }.addOnSuccessListener {
                                updateLocalLists(oldFullName, newFullName)
                                callback(true)
                            }.addOnFailureListener { e ->
                                Log.e(TAG, "Firestore transaction failed", e)
                                callback(false)
                            }
                        }.addOnFailureListener { e ->
                            Log.e(TAG, "Failed to delete old file", e)
                            callback(false)
                        }
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Failed to upload renamed file", e)
                        callback(false)
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to download original file", e)
                    // If no storage file exists, just update Firestore
                    db.runTransaction { transaction ->
                        transaction.set(newFileRef, updatedData)
                        transaction.delete(userFilesRef)
                    }.addOnSuccessListener {
                        updateLocalLists(oldFullName, newFullName)
                        callback(true)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Firestore transaction failed without storage", e)
                        callback(false)
                    }
                }
            } else {
                Log.w(TAG, "Original document not found")
                callback(false)
            }
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to fetch original document", e)
            callback(false)
        }
    }

    private fun updateFirestore(userId: String, oldFullName: String, newFullName: String, callback: (Boolean) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(oldFullName)
        val newFileRef = db.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(newFullName)

        db.runTransaction { transaction ->
            val documentSnapshot = transaction.get(userFilesRef)
            if (!documentSnapshot.exists()) {
                Log.w(TAG, "Document not found: $oldFullName - Creating new entry")
                val newData = mapOf("fileName" to newFullName)
                transaction.set(newFileRef, newData)
            } else {
                val documentData = documentSnapshot.data ?: throw Exception("Document data is null")
                val updatedData = documentData.toMutableMap()
                updatedData["fileName"] = newFullName
                transaction.set(newFileRef, updatedData)
                transaction.delete(userFilesRef)
            }
        }.addOnSuccessListener {
            Log.d(TAG, "Firestore update successful")
            updateLocalLists(oldFullName, newFullName)
            callback(true)
        }.addOnFailureListener { e ->
            Log.e(TAG, "Firestore update failed", e)
            callback(false)
        }
    }

    private fun updateLocalLists(oldFullName: String, newFullName: String) {
        val indexInFileNames = fileNamesList.indexOf(oldFullName)
        if (indexInFileNames != -1) {
            fileNamesList[indexInFileNames] = newFullName
        }

        val oldNameWithoutExt = oldFullName.substringBefore(".")
        val newNameWithoutExt = newFullName.substringBefore(".")
        val indexInFilteredList = filteredList.indexOf(oldNameWithoutExt)
        if (indexInFilteredList != -1) {
            filteredList[indexInFilteredList] = newNameWithoutExt
        }

        adapter.notifyDataSetChanged()
    }

    private fun finishRenaming(position: Int) {
        val editText = listView.getChildAt(position - listView.firstVisiblePosition)
            ?.findViewById<EditText>(R.id.editTextFileName)

        val newNameWithoutExtension = editText?.text.toString().trim()
        if (newNameWithoutExtension.isEmpty()) {
            Toast.makeText(requireContext(), "File name cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        val oldNameWithoutExtension = filteredList[position]
        val oldFullName = fileNamesList.find { it.startsWith(oldNameWithoutExtension) } ?: return
        val extension = oldFullName.substringAfterLast(".")
        val newFullName = "$newNameWithoutExtension.$extension"

        if (newNameWithoutExtension == oldNameWithoutExtension) {
            Toast.makeText(requireContext(), "Enter a different name", Toast.LENGTH_SHORT).show()
            cleanupRenamingMode()
            return
        }

        if (fileNamesList.any { it == newFullName }) {
            Toast.makeText(requireContext(), "File name already exists", Toast.LENGTH_SHORT).show()
            return
        }

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(listView.windowToken, 0)

        updateFileNameInDatabase(oldFullName, newFullName) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Renamed successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Rename failed", Toast.LENGTH_SHORT).show()
            }
            cleanupRenamingMode()
        }
    }

    private fun fetchFileNames() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Log.e(TAG, "No user ID found in fetchFileNames")
            return
        }
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Snapshot listener error: ${error.message}", error)
                togglePlaceholderOnError()
                return@addSnapshotListener
            }

            Log.d(TAG, "Received snapshot update")
            fileNamesList.clear()
            snapshot?.documents?.forEach { document ->
                val fileName = document.getString("fileName") ?: run {
                    Log.w(TAG, "Null filename in document: ${document.id}")
                    return@forEach
                }
                fileNamesList.add(fileName)
                Log.d(TAG, "Added file: $fileName")
            }

            filteredList.clear()
            filteredList.addAll(fileNamesList.map { it.substringBeforeLast(".") })
            Log.d(TAG, "Updated filteredList with ${filteredList.size} items")
            isDataLoaded = true
            togglePlaceholder()
            adapter.notifyDataSetChanged()
            updateDeleteIconVisibility()
        }
    }

    private fun cleanupRenamingMode() {
        isRenaming = false
        renamingPosition = -1
        tickIcon.visibility = View.GONE
        deleteIcon.visibility = if (isDeleteMode && selectedItems.isNotEmpty()) View.VISIBLE else View.GONE
        audioListIcon.visibility = if (isDeleteMode) View.GONE else View.VISIBLE
        adapter.notifyDataSetChanged()
    }

    private fun exportFile(displayedFileName: String) {
        val fullFileName = fileNamesList.find { it.startsWith(displayedFileName) } ?: return
        fetchAndProcessContent(fullFileName) { formattedContent ->
            showExportDialog(fullFileName, formattedContent)
        }
    }

    private fun shareFile(displayedFileName: String) {
        val fullFileName = fileNamesList.find { it.startsWith(displayedFileName) } ?: return
        fetchAndProcessContent(fullFileName) { formattedContent ->
            exportAndShareContent(fullFileName, formattedContent)
        }
    }

    private fun fetchAndProcessContent(fileName: String, callback: (String) -> Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val fileRef = db.collection("ProcessedDocs")
            .document(userId)
            .collection("UserFiles")
            .document(fileName)

        Log.d(TAG, "Fetching content for file: $fileName")
        fileRef.get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val responseText = document.getString("Response") ?: "No response available"
                    val cleanedText = responseText.replace("*", "").trim()
                    Log.d(TAG, "Content fetched successfully: $cleanedText")
                    callback(cleanedText)
                } else {
                    Log.w(TAG, "Document not found in Firestore for: $fileName")
                    Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to fetch file details: ${e.message}", e)
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

    private fun exportContent(fileName: String, content: String, format: String) {
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "No content to export", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            // Store content and file name temporarily
            pendingExportContent = content
            pendingExportFileName = fileName

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                val cleanFileName = fileName.substringBeforeLast(".")
                putExtra(Intent.EXTRA_TITLE, "$cleanFileName.${if (format == "PDF") "pdf" else "docx"}")
            }
            startActivityForResult(intent, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch export intent: ${e.message}", e)
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
                // Clear pending data after successful save
                pendingExportContent = null
                pendingExportFileName = null
            } else {
                Log.e(TAG, "Pending content or file name is null")
                Toast.makeText(requireContext(), "Failed to export: Content not available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveContentToUri(uri: Uri, content: String) {
        try {
            val outputStream = requireContext().contentResolver.openOutputStream(uri)
            if (outputStream != null) {
                when {
                    uri.toString().endsWith("pdf") -> saveAsPdf(content, outputStream)
                    uri.toString().endsWith("docx") -> saveAsDocx(content, outputStream)
                }
                outputStream.close()
                Toast.makeText(requireContext(), "File saved successfully", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to open output stream for URI: $uri")
                Toast.makeText(requireContext(), "Failed to save file: Output stream unavailable", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file to URI: ${e.message}", e)
            Toast.makeText(requireContext(), "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportAndShareContent(fileName: String, content: String) {
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "No content to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cleanFileName = fileName.substringBeforeLast(".")
            val format = if (content.contains("Speaker")) "PDF" else "DOCX" // Example heuristic; adjust as needed
            val tempFile = File(requireContext().cacheDir, "$cleanFileName.${if (format == "PDF") "pdf" else "docx"}")
            val outputStream = FileOutputStream(tempFile)
            when (format) {
                "PDF" -> saveAsPdf(content, outputStream)
                "DOCX" -> saveAsDocx(content, outputStream)
            }
            outputStream.close()

            val uri = FileProvider.getUriForFile(requireContext(), "com.example.meetloggerv2.fileprovider", tempFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share Document"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error during file sharing: ${e.message}", Toast.LENGTH_SHORT).show()
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

        if (content.startsWith("TRANSCRIPTION OF AUDIO")) {
            canvas.drawText("TRANSCRIPTION OF AUDIO", marginLeft, currentY, boldPaint)
            currentY += lineHeight * 1.5f
        }

        val lines = content.split("\n")
        for (line in lines) {
            if (line.matches(Regex("^Speaker [A-Z]+:.*"))) {
                val parts = line.split(":", limit = 2)
                val speaker = parts[0].trim()
                val text = parts.getOrNull(1)?.trim() ?: ""

                canvas.drawText(speaker, marginLeft, currentY, boldPaint)
                currentY += lineHeight

                val words = text.split(" ")
                val maxLineWidth = pageWidth - marginLeft - marginRight
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        if (wordBuffer.size > 7 && currentY < pageHeight - marginBottom) {
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
                    if (wordBuffer.size > 7) {
                        drawJustifiedText(canvas, wordBuffer, marginLeft, currentY, maxLineWidth, normalPaint)
                    } else {
                        canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    }
                    currentY += lineHeight
                }
            } else {
                val words = line.trim().split(" ")
                val maxLineWidth = pageWidth - marginLeft - marginRight
                val wordBuffer = mutableListOf<String>()

                for (word in words) {
                    val testLine = (wordBuffer + word).joinToString(" ")
                    val textWidth = normalPaint.measureText(testLine)

                    if (textWidth > maxLineWidth) {
                        canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
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
                    canvas.drawText(wordBuffer.joinToString(" "), marginLeft, currentY, normalPaint)
                    currentY += lineHeight
                }
            }
        }

        document.finishPage(page)
        document.writeTo(outputStream)
        document.close()
        outputStream.close()
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

        for (line in lines) {
            if (line.isBlank()) continue

            val para = document.createParagraph()
            para.alignment = ParagraphAlignment.BOTH

            val speakerPattern = Regex("^(Speaker \\w+:)")
            val matchResult = speakerPattern.find(line)

            if (matchResult != null) {
                para.spacingBefore = 200
                val speakerName = matchResult.value
                val remainingText = line.removePrefix(speakerName).trim()

                val speakerRun = para.createRun()
                speakerRun.isBold = true
                speakerRun.setText(speakerName)

                val contentRun = para.createRun()
                contentRun.setText(" $remainingText")
            } else {
                val run = para.createRun()
                run.setText(line)
            }
        }

        document.write(outputStream)
        document.close()
    }

    private fun filterFiles(query: String?): Boolean {
        filteredList.clear()

        if (query.isNullOrEmpty()) {
            filteredList.addAll(fileNamesList.map { it.substringBeforeLast(".") })
        } else {
            val lowerCaseQuery = query.toLowerCase()
            fileNamesList.forEach { fileName ->
                if (fileName.toLowerCase().contains(lowerCaseQuery)) {
                    filteredList.add(fileName.substringBeforeLast("."))
                }
            }
        }

        if (filteredList.isEmpty()) {
            if (fileNamesList.isEmpty()) {
                placeholderText.text = "No files found"
                placeholderImage.visibility = View.GONE
            } else {
                placeholderText.visibility = View.GONE
                placeholderImage.visibility = View.GONE
            }
            placeholderText.visibility = View.VISIBLE
            placeholderText.text = "No files found"
            listView.visibility = View.GONE
        } else {
            placeholderText.visibility = View.GONE
            placeholderImage.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
        updateDeleteIconVisibility()
        return true
    }

    private fun toggleDeleteMode(enable: Boolean) {
        isDeleteMode = enable
        if (!enable) selectedItems.clear()
        adapter.notifyDataSetChanged()
        updateDeleteIconVisibility()
    }

    private fun updateDeleteIconVisibility() {
        val shouldShowDelete = isDeleteMode && selectedItems.isNotEmpty()
        deleteIcon.visibility = if (shouldShowDelete) View.VISIBLE else View.GONE
        audioListIcon.visibility = if (shouldShowDelete) View.GONE else View.VISIBLE
    }

    private fun showDeleteConfirmationDialog() {
        val dialog = AlertDialog.Builder(requireContext())
            .setMessage("Are you sure you want to delete the selected documents?")
            .setPositiveButton("OK") { _, _ -> deleteSelectedItems() }
            .setNegativeButton("Cancel") { _, _ -> toggleDeleteMode(false) }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLUE)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.RED)
    }

    private fun deleteSelectedItems() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        val itemsToDelete = selectedItems.map { filteredList[it] }.toList()
        itemsToDelete.forEach { fileName ->
            val fullFileName = fileNamesList.find { it.startsWith(fileName) } ?: return@forEach
            userFilesRef.document(fullFileName).delete()
        }

        toggleDeleteMode(false)
    }

    private fun togglePlaceholder() {
        if (!isDataLoaded) return

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
        placeholderImage.visibility = View.VISIBLE
        placeholderText.visibility = View.GONE
        searchView.visibility = View.GONE
        listView.visibility = View.GONE
    }

    private fun openFileDetailsFragment(displayedFileName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        val fullFileName = fileNamesList.find { it.startsWith(displayedFileName) } ?: return

        val fileDetailsFragment = FileDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", fullFileName)
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

    override fun onDestroyView() {
        super.onDestroyView()
        if (isRenaming) cleanupRenamingMode()
        // Clear pending export data to avoid stale references
        pendingExportContent = null
        pendingExportFileName = null
    }
}