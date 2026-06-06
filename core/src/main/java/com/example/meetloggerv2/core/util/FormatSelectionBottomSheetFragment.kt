package com.example.meetloggerv2.core.util

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.pressScaleClick
import com.example.meetloggerv2.core.ui.components.SheetHeader
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class FormatSelectionBottomSheetFragment : BottomSheetDialogFragment() {

    private var onFormatSelected: ((String) -> Unit)? = null
    private var sheetTitle: String = "Choose Format"
    private var sheetSubtitle: String = "Select the format to proceed"

    fun setCallback(callback: (String) -> Unit): FormatSelectionBottomSheetFragment {
        this.onFormatSelected = callback
        return this
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialog)
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
                    FormatSelectionScreen(
                        title = sheetTitle,
                        subtitle = sheetSubtitle,
                        onFormatSelected = { format ->
                            onFormatSelected?.invoke(format)
                            dismiss()
                        },
                        onCancel = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance(title: String, subtitle: String): FormatSelectionBottomSheetFragment {
            return FormatSelectionBottomSheetFragment().apply {
                this.sheetTitle = title
                this.sheetSubtitle = subtitle
            }
        }
    }
}

@Composable
fun FormatSelectionScreen(
    title: String,
    subtitle: String,
    onFormatSelected: (String) -> Unit,
    onCancel: () -> Unit
) {
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
                onDismiss = onCancel
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

        // Options
        FormatOptionItem(
            icon = Icons.Default.PictureAsPdf,
            title = "PDF",
            subtitle = "Best for sharing & printing",
            onClick = { onFormatSelected("PDF") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FormatOptionItem(
            icon = Icons.AutoMirrored.Filled.Article,
            title = "WORD",
            subtitle = "Editable Word document",
            onClick = { onFormatSelected("DOCX") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        FormatOptionItem(
            icon = Icons.AutoMirrored.Filled.TextSnippet,
            title = "TXT",
            subtitle = "Plain text, lightweight",
            onClick = { onFormatSelected("TXT") }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Cancel Button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .pressScaleClick { onCancel() },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun FormatOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleClick(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Square Icon Background
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Title and Subtitle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
