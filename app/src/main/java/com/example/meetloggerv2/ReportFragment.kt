package com.example.meetloggerv2

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var deleteIcon: ImageView
    private lateinit var audioListIcon: ImageView // Add this
    private lateinit var fileNamesList: ArrayList<String>
    private lateinit var filteredList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var placeholderImage: ImageView
    private lateinit var placeholderText: TextView
    private var isDeleteMode = false
    private val selectedItems = HashSet<Int>()
    private var isDataLoaded = false // Flag to track data loading

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

        listView.setSelector(android.R.color.transparent)

        fileNamesList = ArrayList()
        filteredList = ArrayList()

        // Use the same custom layout as AudioListFragment (assuming list_item_3.xml has a checkbox)
        adapter = object : ArrayAdapter<String>(requireContext(), R.layout.list_item, R.id.textViewFileName, filteredList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val checkbox = view.findViewById<CheckBox>(R.id.checkbox)
                checkbox.visibility = if (isDeleteMode) View.VISIBLE else View.GONE
                checkbox.isChecked = selectedItems.contains(position)
                checkbox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedItems.add(position) else selectedItems.remove(position)
                    updateDeleteIconVisibility()
                }
                return view
            }
        }
        listView.adapter = adapter

        // Initially hide all UI elements until data is loaded
        setInitialVisibility()

        fetchFileNames()

        // Set up the search view
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = filterFiles(query)
            override fun onQueryTextChange(newText: String?) = filterFiles(newText)
        })

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            if (!isDeleteMode) {
                val selectedFileName = filteredList[position]
                openFileDetailsFragment(selectedFileName)
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            if (!isDeleteMode) {
                toggleDeleteMode(true)
                selectedItems.add(position)
                adapter.notifyDataSetChanged()
                updateDeleteIconVisibility()
                true
            } else false
        }

        // Set up delete icon click listener
        deleteIcon.setOnClickListener {
            if (isDeleteMode && selectedItems.isNotEmpty()) {
                showDeleteConfirmationDialog()
            }
        }

        // Initialize the audio list icon
        val audioListIcon: ImageView = view.findViewById(R.id.audioListIcon)
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
        audioListIcon.visibility = View.VISIBLE // Keep this visible initially
    }

    private fun fetchFileNames() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                togglePlaceholderOnError()
                return@addSnapshotListener
            }

            fileNamesList.clear()
            snapshot?.documents?.forEach { document ->
                val fileName = document.getString("fileName") ?: return@forEach
                fileNamesList.add(fileName)
            }

            filteredList.clear()
            filteredList.addAll(fileNamesList.map { it.substringBeforeLast(".") })
            isDataLoaded = true // Mark data as loaded
            togglePlaceholder()
            adapter.notifyDataSetChanged()
            updateDeleteIconVisibility()
        }
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
                // No files exist (initial state)
                placeholderText.text = "No files found"
                placeholderImage.visibility = View.GONE
            } else {
                // Search result empty
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
        audioListIcon.visibility = if (shouldShowDelete) View.GONE else View.VISIBLE // Hide audioListIcon when deleteIcon shows
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
            // Find the full filename with extension
            val fullFileName = fileNamesList.find { it.startsWith(fileName) } ?: return@forEach
            userFilesRef.document(fullFileName).delete()
        }

        toggleDeleteMode(false) // Refresh happens via snapshot listener
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
}