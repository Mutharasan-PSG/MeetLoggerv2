package com.example.meetloggerv2.ui.profile.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.example.meetloggerv2.R
import com.example.meetloggerv2.ui.login.activity.LoginActivity
import com.example.meetloggerv2.ui.profile.viewmodel.ProfileViewModel

class ProfileFragment : Fragment() {

    private val viewModel: ProfileViewModel by viewModels()

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

        // ViewModel handles SessionManager / AuthSession internally or via injected components
        val userId = com.example.meetloggerv2.core.session.SessionManager(requireContext()).getUserId()
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

        viewModel.signOutState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ProfileViewModel.SignOutState.Loading -> {
                    // Show progress / loading toast if desired
                }
                is ProfileViewModel.SignOutState.Success -> {
                    startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    requireActivity().finish()
                }
                is ProfileViewModel.SignOutState.Error -> {
                    Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                }
                is ProfileViewModel.SignOutState.Idle -> {
                    // Do nothing
                }
            }
        }
    }

    private fun initializeUI(view: View) {
        view.findViewById<ImageView>(R.id.backButton).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val signOutButton: LinearLayout = view.findViewById(R.id.btn_sign_out)
        signOutButton.setOnClickListener {
            viewModel.signOut()
        }

        val versionTextView: TextView = view.findViewById(R.id.version)
        try {
            val pInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            versionTextView.text = "version ${pInfo.versionName}"
        } catch (_: Exception) {
            versionTextView.text = "version 1.0"
        }
    }

    private fun populateUserDetails(view: View, data: Map<String, Any>) {
        val nameTextView: TextView = view.findViewById(R.id.profile_name)
        val emailTextView: TextView = view.findViewById(R.id.profile_email)
        val profileImageView: ImageView = view.findViewById(R.id.profile_image)

        val name = data["name"] as? String ?: "User"
        nameTextView.text = name
        emailTextView.text = data["email"] as? String ?: ""

        val avatarDrawable = com.example.meetloggerv2.core.util.AvatarGenerator.getAvatar(requireContext(), name)

        Glide.with(this)
            .load(data["photoUrl"] as? String)
            .placeholder(avatarDrawable)
            .error(avatarDrawable)
            .fallback(avatarDrawable)
            .circleCrop()
            .into(profileImageView)
    }
}
