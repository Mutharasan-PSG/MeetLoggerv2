package com.example.meetloggerv2

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureGoogleSignIn()
        firestore = FirebaseFirestore.getInstance()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeUI(view)

        val user = sessionManager.getUserDetails()
        user?.let {
            populateUserDetails(view, it)
        }
    }

    private fun initializeUI(view: View) {
        val signOutButton: LinearLayout = view.findViewById(R.id.btn_sign_out)

        signOutButton.setOnClickListener {
            signOut()
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Ensure this is correct
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
        sessionManager = SessionManager(requireContext())
    }

    private fun populateUserDetails(view: View, user: User) {
        val nameTextView: TextView = view.findViewById(R.id.profile_name)
        val emailTextView: TextView = view.findViewById(R.id.profile_email)
        val profileImageView: ImageView = view.findViewById(R.id.profile_image)

        nameTextView.text = user.name
        emailTextView.text = user.email

        Glide.with(this)
            .load(user.photoUrl)
            .placeholder(R.drawable.default_profile_pic)
            .error(R.drawable.default_profile_pic)
            .into(profileImageView)
    }

    private fun signOut() {
        FirebaseAuth.getInstance().signOut()

        googleSignInClient.signOut().addOnCompleteListener {
            sessionManager.clearSession()
            // Navigate to LoginActivity
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
}
