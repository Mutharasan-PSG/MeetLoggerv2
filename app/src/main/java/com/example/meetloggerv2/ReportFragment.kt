package com.example.meetloggerv2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var fileNamesList: ArrayList<String>
    private lateinit var filteredList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        listView = view.findViewById(R.id.listView)  // Ensure ListView exists in XML
        searchView = view.findViewById(R.id.searchView)  // Find the SearchView

        listView.setSelector(android.R.color.transparent)

        fileNamesList = ArrayList()
        filteredList =
            ArrayList(fileNamesList) // Initially, the filtered list is the same as the full list

        // Use the custom layout for list items
        adapter =
            ArrayAdapter(requireContext(), R.layout.list_item, R.id.textViewFileName, filteredList)
        listView.adapter = adapter

        fetchFileNames()

        // Set up the search view
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Handle the query submission (e.g., start filtering the list)
                filterFiles(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Handle text changes (e.g., filter the list live)
                filterFiles(newText)
                return true
            }
        })

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedFileName = filteredList[position]
            openFileDetailsFragment(selectedFileName)
        }

        return view
    }

    private fun fetchFileNames() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }

            fileNamesList.clear()
            snapshot?.documents?.forEach { document ->
                val fileName = document.getString("fileName") ?: return@forEach
                fileNamesList.add(fileName)  // Store full filename with extension in backend list
            }

            // Prepare filteredList for UI (Remove extensions)
            filteredList.clear()
            filteredList.addAll(fileNamesList.map { it.substringBeforeLast(".") }) // Remove extension for UI display

            val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

            if (fileNamesList.isEmpty()) {
                placeholderText?.text = "Your processed files appear here..."
                placeholderText?.visibility = View.VISIBLE
                searchView.visibility = View.GONE
                listView.visibility = View.GONE
            } else {
                placeholderText?.visibility = View.GONE
                searchView.visibility = View.VISIBLE
                listView.visibility = View.VISIBLE
            }

            adapter.notifyDataSetChanged()
        }
    }


    private fun filterFiles(query: String?) {
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
        }

        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

        if (filteredList.isEmpty()) {
            if (fileNamesList.isEmpty()) {
                // No files exist (initial state)
                placeholderText?.text = "Your processed files appear here..."
            } else {
                // Search result empty
                placeholderText?.text = "No files found"
            }
            placeholderText?.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            placeholderText?.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
    }

    private fun openFileDetailsFragment(displayedFileName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        // Find the full filename with extension from the list
        val fullFileName = fileNamesList.find { it.startsWith(displayedFileName) } ?: return

        val fileDetailsFragment = FileDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", fullFileName)  // Pass full file name (with extension)
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

}
