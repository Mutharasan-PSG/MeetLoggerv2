package com.meetloggerv2.ui.login.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.meetloggerv2.core.theme.MeetLoggerTheme
import com.meetloggerv2.core.ui.components.SheetHeader
import com.meetloggerv2.core.util.LegalContentProvider
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
        setStyle(STYLE_NORMAL, com.meetloggerv2.core.R.style.CustomBottomSheetDialog)
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
        val contentText = if (type == "terms") {
            LegalContentProvider.getTermsOfServiceText()
        } else {
            LegalContentProvider.getPrivacyPolicyText()
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                SheetHeader(
                    title = title,
                    onDismiss = onClose
                )

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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Justify,
                        style = LocalTextStyle.current.copy(
                            lineBreak = LineBreak.Paragraph
                        )
                    )
                }
            }
        }
    }
}
