package com.example.meetloggerv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var profilePic: ImageView
    private lateinit var bottomNavBar: BottomNavigationView
    private lateinit var AudioButton: LinearLayout
    private lateinit var AudioOptionsLayout: LinearLayout
    private lateinit var closeButton: ImageView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var fileList: ArrayList<Pair<String, String>>  // Stores (FileName, Status)
    private lateinit var filteredList: ArrayList<Pair<String, String>>
    private lateinit var adapter: FileListAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        profilePic = view.findViewById(R.id.profilePic)
        bottomNavBar = view.findViewById(R.id.bottomNavBar)
        AudioButton = view.findViewById(R.id.AudioButton)
        AudioOptionsLayout = view.findViewById(R.id.AudioOptionsLayout)
        closeButton = view.findViewById(R.id.closeButton)

        // Set Home as the default selected item
        bottomNavBar.selectedItemId = R.id.menu_home // This makes Home selected first and blue

        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)

        fileList = ArrayList()
        filteredList = ArrayList()
        adapter = FileListAdapter(requireContext(), filteredList)
        listView.adapter = adapter

        fetchFileNamesAndStatus()

        // Set up search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterFiles(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterFiles(newText)
                return true
            }
        })

        // Set onClickListener to show or hide the Meet Options popup when Meet button is clicked
        AudioButton.setOnClickListener {
            // If the options are hidden, show it, else hide it
            if (AudioOptionsLayout.visibility == View.GONE) {
                AudioOptionsLayout.visibility = View.VISIBLE
                AudioButton.visibility = View.GONE // Hide Meet button when options are visible
            } else {
                AudioOptionsLayout.visibility = View.GONE
                AudioButton.visibility = View.VISIBLE // Show Meet button when options are hidden
            }
        }

        closeButton.setOnClickListener {
            // Close the Meet options and show the Meet button again
            AudioOptionsLayout.visibility = View.GONE
            AudioButton.visibility = View.VISIBLE // Show Meet button when options are closed
        }

        // Handle the actions of the options buttons
        val RecordAudioLayout: LinearLayout = view.findViewById(R.id.RecordAudio)
        val UploadAudioLayout: LinearLayout= view.findViewById(R.id.UploadAudio)


        RecordAudioLayout.setOnClickListener {
            val RecordAudioSheet = RecordAudioBottomsheetFragment()
            RecordAudioSheet.show(parentFragmentManager, "RecordAudioSheet")
        }

        UploadAudioLayout.setOnClickListener {
            val UploadAudioSheet = UploadAudioBottomsheetFragment()
            UploadAudioSheet.show(parentFragmentManager, "UploadAudioSheet")
        }

        // Setup BottomNavigationView to manage fragment switching
        setupBottomNavigation()

        loadUserProfile() // Load the profile data for the user
        // Set up OnClickListener to navigate to ProfileFragment when the profile image is clicked
        profilePic.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .addToBackStack(null) // Add this transaction to the back stack so the user can press back
                .commit()
        }

        return view
    }

    private fun setupBottomNavigation() {
        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> {
                    // No need to switch fragments, HomeFragment is already here
                    true
                }
                R.id.menu_profile -> {
                    // Switch to ProfileFragment
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ProfileFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.menu_report -> {
                    // Switch to ReportFragment
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ReportFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                else -> false
            }
        }
    }

    private fun loadUserProfile() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("Users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                val profileUrl = document.getString("photoUrl") ?: return@addOnSuccessListener
                Glide.with(this)
                    .load(profileUrl)
                    .circleCrop()
                    .placeholder(R.drawable.default_profile_pic)
                    .into(profilePic)
            }
            .addOnFailureListener {
                // Handle failure to load profile
            }
    }


    private fun fetchFileNamesAndStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userFilesRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles")

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }

            fileList.clear()
            snapshot?.documents?.forEach { document ->
                val fileName = document.getString("fileName") ?: return@forEach
                val status = document.getString("status") ?: "processing"
                val notificationStatus = document.getString("Notification") ?: "Off"

                fileList.add(Pair(fileName, status)) // Store (fileName, status)

                // Check if the status is "processed" and Notification is "On"
                if (status.equals("processed", ignoreCase = true) && notificationStatus.equals("On", ignoreCase = true)) {
                    // Trigger notification
                    triggerNotification(fileName)

                    // Update the Notification field to "Off"
                    updateNotificationStatus(document.id)
                }
            }

            // Update filtered list for UI
            filteredList.clear()
            filteredList.addAll(fileList)

            val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

            if (fileList.isEmpty()) {
                placeholderText?.text = "Audio processing status appear here..."
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

    private fun triggerNotification(fileName: String) {
        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "file_notification_channel"

        // Create notification channel for Android 8 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "File Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setSmallIcon(R.drawable.launchlogo)
            .setContentTitle("File Processed")
            .setContentText("$fileName documented successfully, check it out!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(0, notification)
    }

    private fun updateNotificationStatus(documentId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userFileRef = firestore.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(documentId)

        userFileRef.update("Notification", "Off")
            .addOnSuccessListener {
                // Notification status updated successfully
            }
            .addOnFailureListener {
                // Handle failure
            }
    }



    private fun filterFiles(query: String?) {
        filteredList.clear()

        if (query.isNullOrEmpty()) {
            filteredList.addAll(fileList)
        } else {
            val lowerCaseQuery = query.lowercase(Locale.getDefault())
            fileList.forEach { (fileName, status) ->
                if (fileName.lowercase(Locale.getDefault()).contains(lowerCaseQuery)) {
                    filteredList.add(Pair(fileName, status))
                }
            }
        }

        val placeholderText = view?.findViewById<TextView>(R.id.placeholderText)

        if (filteredList.isEmpty()) {
            placeholderText?.text = if (fileList.isEmpty()) "Audio processing status appear here..." else "No files found"
            placeholderText?.visibility = View.VISIBLE
            listView.visibility = View.GONE
        } else {
            placeholderText?.visibility = View.GONE
            listView.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()
    }


}
