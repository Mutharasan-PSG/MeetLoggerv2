package com.example.meetloggerv2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.textview.MaterialTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var profilePic: ImageView
    private lateinit var bottomNavBar: BottomNavigationView
    private lateinit var meetButton: LinearLayout // The LinearLayout you click to open the options
    private lateinit var meetOptionsLayout: LinearLayout // The options popup menu
    private lateinit var closeButton: ImageView // Close button (X)
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var meetingsListView: ListView
    private lateinit var meetingsAdapter: ArrayAdapter<String>
    private val meetingsList = mutableListOf<String>() // Store meeting details

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        meetingsListView = view.findViewById(R.id.meetingsListView)

        profilePic = view.findViewById(R.id.profilePic)
        bottomNavBar = view.findViewById(R.id.bottomNavBar)
        meetButton = view.findViewById(R.id.meetButton) // Assuming there's a "Meet" button
        meetOptionsLayout = view.findViewById(R.id.meetOptionsLayout)
        closeButton = view.findViewById(R.id.closeButton)

        // Set Home as the default selected item
        bottomNavBar.selectedItemId = R.id.menu_home // This makes Home selected first and blue

        meetingsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_2, android.R.id.text1, meetingsList)
        meetingsListView.adapter = meetingsAdapter

       // fetchUserMeetings() // Fetch meetings when fragment is opened

        // Set onClickListener to show or hide the Meet Options popup when Meet button is clicked
        meetButton.setOnClickListener {
            // If the options are hidden, show it, else hide it
            if (meetOptionsLayout.visibility == View.GONE) {
                meetOptionsLayout.visibility = View.VISIBLE
                meetButton.visibility = View.GONE // Hide Meet button when options are visible
            } else {
                meetOptionsLayout.visibility = View.GONE
                meetButton.visibility = View.VISIBLE // Show Meet button when options are hidden
            }
        }

        closeButton.setOnClickListener {
            // Close the Meet options and show the Meet button again
            meetOptionsLayout.visibility = View.GONE
            meetButton.visibility = View.VISIBLE // Show Meet button when options are closed
        }
/*
        // Handle the actions of the options buttons
        val joinMeetLayout: LinearLayout = view.findViewById(R.id.RecordAudio)
        val createMeetLayout: LinearLayout= view.findViewById(R.id.UploadAudio)
       // val scheduleMeetLayout: LinearLayout= view.findViewById(R.id.ScheduleMeet)

        joinMeetLayout.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, JoinMeetFragment())
                .addToBackStack(null) // Allows users to press back to return
                .commit()
        }

        createMeetLayout.setOnClickListener {
            val createMeetBottomSheet = CreateMeetBottomSheetFragment()
            createMeetBottomSheet.show(parentFragmentManager, "CreateMeetBottomSheet")
        }

        scheduleMeetLayout.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScheduleMeetFragment())
                .addToBackStack(null)
                .commit()
        }

 */

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


    private fun fetchUserMeetings() {
        val currentUser = auth.currentUser
        val userId = currentUser?.uid ?: return

        firestore.collection("MeetingInfo")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                meetingsList.clear()
                for (document in documents) {
                    val meetingId = document.getString("meetingId") ?: continue
                    val createdAtTimestamp = document.getTimestamp("createdAt") ?: continue
                    val createdAt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(createdAtTimestamp.toDate())

                    val creatorId = document.getString("creatorId") ?: ""
                    val participants = document.get("participants") as? List<Map<String, Any>> ?: emptyList()

                    val isParticipant = participants.any { it["userId"] == userId }
                    val isCreator = creatorId == userId

                    if (isParticipant || isCreator) {
                        meetingsList.add("Meeting ID: $meetingId\nCreated: $createdAt")
                    }
                }
                if (meetingsList.isEmpty()) {
                    meetingsListView.visibility = View.GONE
                    view?.findViewById<MaterialTextView>(R.id.placeholderText)?.visibility = View.VISIBLE
                } else {
                    meetingsListView.visibility = View.VISIBLE
                    view?.findViewById<MaterialTextView>(R.id.placeholderText)?.visibility = View.GONE
                }

                meetingsAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to fetch meetings", Toast.LENGTH_SHORT).show()
            }
    }
}