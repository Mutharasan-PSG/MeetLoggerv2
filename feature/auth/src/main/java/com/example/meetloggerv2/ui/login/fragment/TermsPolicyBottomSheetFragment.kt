package com.example.meetloggerv2.ui.login.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TermsPolicyBottomSheetFragment : BottomSheetDialogFragment() {

    private var type: String = "policy"

    companion object {
        private const val ARG_TYPE = "type"

        fun newInstance(type: String): TermsPolicyBottomSheetFragment {
            val fragment = TermsPolicyBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getString(ARG_TYPE) ?: "policy"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    TermsPolicyScreen(
                        type = type,
                        onClose = { dismiss() }
                    )
                }
            }
        }
    }

    @Composable
    private fun TermsPolicyScreen(type: String, onClose: () -> Unit) {
        val title = if (type == "terms") "Terms of Service" else "Privacy Policy"
        val contentText = if (type == "terms") getTermsOfServiceText() else getPrivacyPolicyText()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = contentText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    private fun getPrivacyPolicyText(): String {
        return """
            Last Updated: June 2026

            Welcome to MeetLogger. We are committed to protecting your privacy and ensuring a secure experience. This Privacy Policy describes how we collect, use, and safeguard your personal information when you use our application.

            1. Information We Collect
            - Audio Data: We process audio recordings that you record or upload within the application solely to generate transcripts, summaries, and translations.
            - Account Information: When you sign in using your account, we access your basic profile information (such as name, email address, and profile picture) to manage your user profile.
            - Generated Documents: We save your transcripts, summaries, and translations under your profile so that you can search, view, and export them at your convenience.

            2. How We Use Your Information
            - To transcribe, translate, and summarize your meeting audios.
            - To synchronize your notes and files securely across your devices.
            - To display and manage your profile inside the application.

            3. Third-Party Services
            We partner with secure cloud infrastructure and service providers to host data, verify user identities, and perform text translation. Your audio and generated documents are processed securely, and we do not sell or share your data with third parties for marketing purposes.

            4. Data Retention & Control
            You retain complete ownership and control of your content:
            - You can view, search, export, or share your documents and audio files at any time.
            - You can permanently delete any document or audio file. Deleting an item removes it from our secure cloud storage immediately.

            5. Security
            We implement industry-standard administrative and technical security measures to protect your account and data.

            6. Changes to This Policy
            We may update this privacy policy from time to time. Your continued use of the application constitutes acceptance of any updates.
        """.trimIndent()
    }

    private fun getTermsOfServiceText(): String {
        return """
            Last Updated: June 2026

            Please read these Terms of Service ("Terms") carefully before using the MeetLogger application.

            1. Agreement to Terms
            By signing in and using MeetLogger, you agree to be bound by these Terms. If you do not agree to these Terms, you may not use the application.

            2. Permitted Use & Consent
            - You represent and warrant that you have obtained all necessary consents under applicable local laws from all participants before recording any conversations or meetings.
            - You agree not to upload or record any illegal, defamatory, or infringing content.

            3. User Content & Accountability
            - You retain all ownership rights to the audio recordings and reports generated using MeetLogger.
            - MeetLogger acts as a tool for audio recording, transcription, translation, and document compilation. You are solely responsible for how you distribute or share the exported files.

            4. Intellectual Property
            MeetLogger and its original design, logos, and features are and remain the exclusive property of our team. You may not copy, modify, or reverse-engineer any part of the application.

            5. Disclaimer of Warranties
            MeetLogger is provided "as is" and "as available". We do not guarantee that the transcription, summary generation, or translations will be 100% accurate, error-free, or uninterrupted.

            6. Limitation of Liability
            To the maximum extent permitted by law, MeetLogger shall not be liable for any indirect, incidental, or consequential damages resulting from the loss of data, audio recordings, or incorrect transcriptions.

            7. Termination
            We reserve the right to suspend or terminate your access to the application if you violate these Terms or engage in unauthorized use of our services.
        """.trimIndent()
    }
}
