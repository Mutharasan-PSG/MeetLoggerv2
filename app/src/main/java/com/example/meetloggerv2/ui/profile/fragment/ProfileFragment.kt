package com.example.meetloggerv2.ui.profile.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.meetloggerv2.R
import com.example.meetloggerv2.core.session.SessionManager
import com.example.meetloggerv2.ui.home.viewmodel.HomeViewModel
import com.example.meetloggerv2.ui.login.activity.LoginActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.example.meetloggerv2.core.session.AuthSession

class ProfileFragment : Fragment() {

    private lateinit var sessionManager: SessionManager
    private lateinit var googleSignInClient: GoogleSignInClient
    private val viewModel: HomeViewModel by viewModels()
    private val authSession = AuthSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureGoogleSignIn()
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
        setupObservers(view)

        val userId = sessionManager.getUserId()
        if (userId != null) {
            viewModel.loadUserProfile(userId)
        }
    }

    private fun setupObservers(view: View) {
        viewModel.userProfile.observe(viewLifecycleOwner) { data ->
            if (data != null) {
                populateUserDetails(view, data)
            }
        }
    }

    private fun initializeUI(view: View) {
        val signOutButton: LinearLayout = view.findViewById(R.id.btn_sign_out)
        signOutButton.setOnClickListener {
            signOut()
        }

        val versionTextView: TextView = view.findViewById(R.id.version)
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionTextView.text = "version ${pInfo.versionName}"
        } catch (_: Exception) {
            versionTextView.text = "version 1.0"
        }
    }

    private fun configureGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
        sessionManager = SessionManager(requireContext())
    }

    private fun populateUserDetails(view: View, data: Map<String, Any>) {
        val nameTextView: TextView = view.findViewById(R.id.profile_name)
        val emailTextView: TextView = view.findViewById(R.id.profile_email)
        val profileImageView: ImageView = view.findViewById(R.id.profile_image)

        nameTextView.text = data["name"] as? String ?: ""
        emailTextView.text = data["email"] as? String ?: ""

        Glide.with(this)
            .load(data["photoUrl"] as? String)
            .placeholder(R.drawable.default_profile_pic)
            .error(R.drawable.default_profile_pic)
            .into(profileImageView)
    }

    private fun signOut() {
        authSession.signOut()
        googleSignInClient.signOut().addOnCompleteListener {
            sessionManager.clearSession()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
}
