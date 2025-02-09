package com.example.meetloggerv2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ReportFragment : Fragment() {

    private lateinit var listView: ListView
    private lateinit var fileNamesList: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_report, container, false)
        listView = view.findViewById(R.id.listView)  // Ensure ListView exists in XML

        listView.setSelector(android.R.color.transparent)

        fileNamesList = ArrayList()
        // Use the custom layout for list items
        adapter = ArrayAdapter(requireContext(), R.layout.list_item, R.id.textViewFileName, fileNamesList)
        listView.adapter = adapter

        fetchFileNames()

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedFileName = fileNamesList[position]
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
                val fileName = document.getString("fileName")
                fileName?.let {
                    // Keep the full file name (including .mp3) for backend logic
                    val nameWithoutExtension = it.substringBeforeLast(".mp3")
                    fileNamesList.add(nameWithoutExtension)
                }
            }



            // Update ListView and Placeholder visibility
            if (fileNamesList.isEmpty()) {
                view?.findViewById<TextView>(R.id.placeholderText)?.visibility = View.VISIBLE
                listView.visibility = View.GONE
            } else {
                view?.findViewById<TextView>(R.id.placeholderText)?.visibility = View.GONE
                listView.visibility = View.VISIBLE
            }

            adapter.notifyDataSetChanged()
        }
    }



    private fun openFileDetailsFragment(fileName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()
        val userFilesRef = db.collection("ProcessedDocs").document(userId).collection("UserFiles")

        // Fetch full file name with .mp3
        val fullFileName = "$fileName.mp3"

        val fileDetailsFragment = FileDetailsFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", fullFileName)  // Pass full file name with .mp3
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
