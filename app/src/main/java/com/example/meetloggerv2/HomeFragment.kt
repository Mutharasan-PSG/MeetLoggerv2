package com.example.meetloggerv2

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    private lateinit var audioButton: LinearLayout
    private lateinit var audioOptionsLayout: LinearLayout
    private lateinit var audioOptionsOverlay: FrameLayout
    private lateinit var closeButton: ImageView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var listView: ListView
    private lateinit var searchView: SearchView
    private lateinit var fileList: ArrayList<Pair<String, String>>
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
        audioButton = view.findViewById(R.id.AudioButton)
        audioOptionsLayout = view.findViewById(R.id.AudioOptionsLayout)
        audioOptionsOverlay = view.findViewById(R.id.audioOptionsOverlay)
        closeButton = view.findViewById(R.id.closeButton)

        bottomNavBar.selectedItemId = R.id.menu_home

        listView = view.findViewById(R.id.listView)
        searchView = view.findViewById(R.id.searchView)

        fileList = ArrayList()
        filteredList = ArrayList()
        adapter = FileListAdapter(requireContext(), filteredList)
        listView.adapter = adapter

        fetchFileNamesAndStatus()

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

        // Audio Button Click - Show overlay and options
        audioButton.setOnClickListener {
            audioOptionsOverlay.visibility = View.VISIBLE
            audioOptionsLayout.visibility = View.VISIBLE
            audioButton.visibility = View.GONE
        }

        // Close Button Click - Hide overlay and options, show Audio button
        closeButton.setOnClickListener {
            audioOptionsOverlay.visibility = View.GONE
            audioOptionsLayout.visibility = View.GONE
            audioButton.visibility = View.VISIBLE
        }

        // Handle option clicks
        val recordAudioLayout: LinearLayout = view.findViewById(R.id.RecordAudio)
        val uploadAudioLayout: LinearLayout = view.findViewById(R.id.UploadAudio)

        recordAudioLayout.setOnClickListener {
            audioOptionsOverlay.visibility = View.GONE
            audioOptionsLayout.visibility = View.GONE
            audioButton.visibility = View.VISIBLE
            val recordAudioSheet = RecordAudioBottomsheetFragment()
            recordAudioSheet.show(parentFragmentManager, "RecordAudioSheet")
        }

        uploadAudioLayout.setOnClickListener {
            audioOptionsOverlay.visibility = View.GONE
            audioOptionsLayout.visibility = View.GONE
            audioButton.visibility = View.VISIBLE
            val uploadAudioSheet = UploadAudioBottomsheetFragment()
            uploadAudioSheet.show(parentFragmentManager, "UploadAudioSheet")
        }

        setupBottomNavigation()
        loadUserProfile()

        profilePic.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    private fun setupBottomNavigation() {
        bottomNavBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_home -> true
                R.id.menu_profile -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, ProfileFragment())
                        .addToBackStack(null)
                        .commit()
                    true
                }
                R.id.menu_report -> {
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
    }

    private fun fetchFileNamesAndStatus() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val userFilesRef = firestore.collection("ProcessedDocs").document(userId).collection("UserFiles")

        userFilesRef.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener

            fileList.clear()
            snapshot?.documents?.forEach { document ->
                val fileName = document.getString("fileName") ?: return@forEach
                val status = document.getString("status") ?: "processing"
                val notificationStatus = document.getString("Notification") ?: "Off"
                fileList.add(Pair(fileName, status))

                if (status.equals("processed", ignoreCase = true) && notificationStatus.equals("On", ignoreCase = true)) {
                    triggerNotification(fileName)
                    updateNotificationStatus(document.id)
                }
            }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "File Notifications", NotificationManager.IMPORTANCE_DEFAULT)
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
        firestore.collection("ProcessedDocs").document(userId)
            .collection("UserFiles").document(documentId)
            .update("Notification", "Off")
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