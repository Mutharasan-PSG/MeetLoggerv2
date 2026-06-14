package com.example.meetloggerv2.ui.details.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.meetloggerv2.core.R
import com.example.meetloggerv2.core.export.DocumentExportManager
import com.example.meetloggerv2.core.theme.GradientEnd
import com.example.meetloggerv2.core.theme.GradientStart
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.pressScale
import com.example.meetloggerv2.core.theme.pressScaleClick
import com.example.meetloggerv2.core.theme.shimmerBrush
import com.example.meetloggerv2.core.util.ShareHelper
import com.example.meetloggerv2.ui.details.viewmodel.FileDetailsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class FileDetailsFragment : Fragment() {

    private val viewModel: FileDetailsViewModel by viewModels()
    private lateinit var exportManager: DocumentExportManager

    private var fileName: String? = null
    private var pendingExportFormat: String? = null
    private var pendingExportContent: String? = null

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val format = pendingExportFormat ?: return@registerForActivityResult
            saveContentToUri(uri, format)
        }
    }

    private val languages = listOf(
        "English" to "en", "French" to "fr", "German" to "de", "Afrikaans" to "af", "Arabic" to "ar",
        "Belarusian" to "be", "Bulgarian" to "bg", "Bengali" to "bn", "Catalan" to "ca", "Czech" to "cs",
        "Welsh" to "cy", "Danish" to "da", "Greek" to "el", "Spanish" to "es", "Esperanto" to "eo",
        "Estonian" to "et", "Persian" to "fa", "Finnish" to "fi", "Irish" to "ga", "Galician" to "gl",
        "Gujarati" to "gu", "Hebrew" to "he", "Hindi" to "hi", "Croatian" to "hr", "Haitian" to "ht",
        "Hungarian" to "hu", "Indonesian" to "id", "Icelandic" to "is", "Italian" to "it", "Japanese" to "ja",
        "Georgian" to "ka", "Kannada" to "kn", "Korean" to "ko", "Lithuanian" to "lt", "Latvian" to "lv",
        "Macedonian" to "mk", "Marathi" to "mr", "Malay" to "ms", "Malayalam" to "ml", "Maltese" to "mt", "Dutch" to "nl",
        "Norwegian" to "no", "Polish" to "pl", "Portuguese" to "pt", "Romanian" to "ro", "Russian" to "ru",
        "Slovak" to "sk", "Slovenian" to "sl", "Albanian" to "sq", "Swedish" to "sv", "Swahili" to "sw",
        "Tamil" to "ta", "Telugu" to "te", "Thai" to "th", "Tagalog" to "tl", "Turkish" to "tr",
        "Ukrainian" to "uk", "Urdu" to "ur", "Vietnamese" to "vi", "Chinese" to "zh"
    ).sortedBy { it.first }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fileName = arguments?.getString("fileName")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        exportManager = DocumentExportManager(requireContext())
        setupObservers()
        fetchFileDetails()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    val subscription = viewModel.userSubscription
                    val filteredLanguages = if (subscription == "free") {
                        val freeLangs = setOf("ta", "te", "ml", "kn", "hi", "en", "es", "fr", "de", "zh")
                        languages.filter { it.second in freeLangs }
                    } else {
                        languages
                    }
                    FileDetailsScreenContent(
                        viewModel = viewModel,
                        fileName = fileName,
                        languages = filteredLanguages,
                        onBack = { handleBackPressed() },
                        onShare = { content, format -> performShare(content, format) },
                        onExport = { format -> performExport(format) },
                        onNewFileCreated = { newName -> openNewFileDetailsFragment(newName) },
                        onShowFormatSelection = { action, content -> showFormatSelectionBottomSheet(action, content) }
                    )
                }
            }
        }
    }

    private fun showFormatSelectionBottomSheet(action: String, currentContent: String) {
        com.example.meetloggerv2.core.util.FormatSelectionBottomSheetFragment.newInstance(
            title = "Choose format",
            subtitle = if (action == "EXPORT") "Select the format to save your file" else "Select the format to share your file"
        ).setCallback { format ->
            if (action == "EXPORT") {
                pendingExportContent = currentContent
                performExport(format)
            } else {
                performShare(currentContent, format)
            }
        }.show(parentFragmentManager, "FormatSelectionBottomSheet")
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state is FileDetailsViewModel.DetailsUiState.NewFileCreated) {
                        openNewFileDetailsFragment(state.fileName)
                    }
                }
            }
        }
    }

    private fun fetchFileDetails() {
        fileName?.let { viewModel.fetchDetails(it) }
    }

    private fun performExport(format: String) {
        pendingExportFormat = format
        val exporter = exportManager.getExporter(format) ?: return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = exporter.mimeType
            val ext = exporter.fileExtension.removePrefix(".")
            val cleanName = fileName?.substringBeforeLast(".") ?: "export"
            putExtra(Intent.EXTRA_TITLE, "$cleanName.$ext")
        }
        exportLauncher.launch(intent)
    }

    private fun saveContentToUri(uri: Uri, format: String) {
        try {
            val content = pendingExportContent ?: viewModel.fileDetails.value?.get("Response") as? String ?: ""
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                exportManager.export(content, format, os)
            }
            Toast.makeText(requireContext(), R.string.msg_downloaded_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
        } finally {
            pendingExportContent = null
        }
    }

    private fun performShare(content: String, format: String) {
        val cleanName = fileName?.substringBeforeLast(".") ?: "share"
        val exporter = exportManager.getExporter(format) ?: return
        val ext = exporter.fileExtension.removePrefix(".")
        try {
            val temp = File(requireContext().cacheDir, "$cleanName.$ext")
            FileOutputStream(temp).use { os ->
                exportManager.export(content, format, os)
            }
            startActivity(
                Intent.createChooser(
                    ShareHelper.getShareIntent(requireContext(), temp, exporter.mimeType),
                    "Share"
                )
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to share document", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleBackPressed() {
        parentFragmentManager.popBackStack()
    }

    private fun openNewFileDetailsFragment(name: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, FileDetailsFragment().apply {
                arguments = Bundle().apply { putString("fileName", name) }
            })
            .addToBackStack(null)
            .commit()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsScreenContent(
    viewModel: FileDetailsViewModel,
    fileName: String?,
    languages: List<Pair<String, String>>,
    onBack: () -> Unit,
    onShare: (content: String, format: String) -> Unit,
    onExport: (format: String) -> Unit,
    onNewFileCreated: (String) -> Unit,
    onShowFormatSelection: (action: String, content: String) -> Unit
) {
    val context = LocalContext.current

    val fileDetails by viewModel.fileDetails.collectAsState(initial = null)
    val translatedText by viewModel.translatedText.collectAsState(initial = "")
    val uiState by viewModel.uiState.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf("") }

    var selectedLanguageCode by remember { mutableStateOf("en") }
    var originalLanguageCode by remember { mutableStateOf("en") }

    var isContentTranslated by remember { mutableStateOf(false) }

    // Dialog States
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showSaveOptionsDialog by remember { mutableStateOf(false) }
    var showOverwriteWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(fileDetails) {
        fileDetails?.let { data ->
            val responseText = (data["Response"] as? String)?.trim() ?: ""
            editedText = responseText
            val originalLang = data["OriginalLanguage"] as? String ?: "en"
            originalLanguageCode = originalLang
            selectedLanguageCode = originalLang
        }
    }

    LaunchedEffect(translatedText) {
        if (translatedText.isNotEmpty()) {
            editedText = translatedText
            isContentTranslated = true
        }
    }

    androidx.activity.compose.BackHandler(enabled = isEditing) {
        isEditing = false
        editedText = (fileDetails?.get("Response") as? String)?.trim() ?: ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileName?.substringBeforeLast(".") ?: "Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .pressScaleClick {
                                if (isEditing) {
                                    isEditing = false
                                    editedText = (fileDetails?.get("Response") as? String)?.trim() ?: ""
                                } else {
                                    onBack()
                                }
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.height(100.dp)
                ) {
                    if (uiState is FileDetailsViewModel.DetailsUiState.Loading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(4) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(shimmerBrush())
                                )
                            }
                        }
                    } else if (isEditing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressScaleClick {
                                        if (isContentTranslated) {
                                            showSaveOptionsDialog = true
                                        } else {
                                            fileName?.let {
                                                viewModel.updateContent(it, editedText, selectedLanguageCode)
                                            }
                                            isEditing = false
                                        }
                                    },
                                shape = RoundedCornerShape(24.dp),
                                color = Color.Transparent
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(id = R.string.dialog_save), fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .pressScaleClick {
                                        isEditing = false
                                        editedText = (fileDetails?.get("Response") as? String)?.trim() ?: ""
                                    },
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                                color = Color.Transparent
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(text = stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomActionItem(
                                imageVector = Icons.Default.Edit,
                                label = "Edit",
                                onClick = { isEditing = true }
                            )
                            BottomActionItem(
                                imageVector = Icons.Default.Download,
                                label = "Export",
                                onClick = { onShowFormatSelection("EXPORT", editedText) }
                            )
                            BottomActionItem(
                                imageVector = Icons.Default.Share,
                                label = "Share",
                                onClick = { onShowFormatSelection("SHARE", editedText) }
                            )
                            BottomActionItem(
                                imageVector = Icons.Default.Translate,
                                label = "Translate",
                                onClick = { showLanguageDialog = true }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (uiState is FileDetailsViewModel.DetailsUiState.Loading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmerBrush())
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    if (isEditing) {
                         OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 300.dp),
                            shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            ),
                            visualTransformation = VisualTransformation.None,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        )
                    } else {
                        var textLayoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                        val annotatedText = parsePremiumDocument(
                            text = editedText,
                            primaryColor = MaterialTheme.colorScheme.primary,
                            onSurfaceColor = MaterialTheme.colorScheme.onSurface,
                            onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val highlightBgColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

                        Text(
                            text = annotatedText,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Justify,
                            style = LocalTextStyle.current.copy(
                                lineBreak = LineBreak.Paragraph
                            ),
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    val layout = textLayoutResult ?: return@drawBehind
                                    annotatedText.getStringAnnotations(tag = "highlight_tag", start = 0, end = annotatedText.length)
                                        .forEach { annotation ->
                                            if (annotation.start < annotation.end && annotation.end <= annotatedText.length) {
                                                val path = layout.getPathForRange(annotation.start, annotation.end)
                                                // Curvy corner effect: radius of 10.dp converted to pixels
                                                val paint = Paint().apply {
                                                    this.color = highlightBgColor
                                                    this.pathEffect = PathEffect.cornerPathEffect(10.dp.toPx())
                                                }
                                                drawContext.canvas.drawPath(path, paint)
                                            }
                                        }
                                }
                        )
                    }
                }
            }
        }
    }

    // Dialogs Implementation

    // 1. Language Dialog
    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .padding(vertical = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_title_choose_language),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.dialog_language_switch_msg),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    languages.forEach { lang ->
                        val langInteractionSource = remember { MutableInteractionSource() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = langInteractionSource,
                                    indication = androidx.compose.foundation.LocalIndication.current
                                ) {
                                    selectedLanguageCode = lang.second
                                    val currentResp = fileDetails?.get("Response") as? String ?: ""
                                    viewModel.translateContent(
                                        currentResp,
                                        originalLanguageCode,
                                        lang.second
                                    )
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = lang.first,
                                fontSize = 15.sp,
                                color = if (selectedLanguageCode == lang.second)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selectedLanguageCode == lang.second) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { showLanguageDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .pressScaleClick { showLanguageDialog = false },
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(text = stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // 2. Save Options Dialog
    if (showSaveOptionsDialog) {
        Dialog(onDismissRequest = { showSaveOptionsDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Save Options",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "How would you like to save your edits?",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleClick {
                                    showSaveOptionsDialog = false
                                    fileName?.let {
                                        val copyName = "${it.substringBeforeLast(".")} (Copy).mp3"
                                        viewModel.saveAsNewCopy(
                                            copyName,
                                            viewModel.fileDetails.value?.toMutableMap()?.apply {
                                                put("fileName", copyName)
                                                put("Response", editedText)
                                            } ?: emptyMap()
                                        )
                                    }
                                    isEditing = false
                                },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = stringResource(id = R.string.dialog_new_copy), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleClick {
                                    showSaveOptionsDialog = false
                                    showOverwriteWarningDialog = true
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = stringResource(id = R.string.dialog_overwrite), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    // 3. Overwrite Warning Dialog
    if (showOverwriteWarningDialog) {
        val originalLangName = languages.find { it.second == originalLanguageCode }?.first ?: "unknown"
        val currentLangName = languages.find { it.second == selectedLanguageCode }?.first ?: "unknown"
        Dialog(onDismissRequest = { showOverwriteWarningDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_title_warning),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Overwriting will replace the original content ($originalLangName language) with edits made in $currentLangName.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleClick { showOverwriteWarningDialog = false },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text = stringResource(id = R.string.dialog_no), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleClick {
                                    showOverwriteWarningDialog = false
                                    fileName?.let {
                                        viewModel.updateContent(it, editedText, selectedLanguageCode)
                                    }
                                    isEditing = false
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(colors = listOf(GradientStart, GradientEnd))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = stringResource(id = R.string.dialog_yes), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomActionItem(
    imageVector: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

fun parsePremiumDocument(
    text: String,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var isDecisionsSection = false
        var isTranscriptionSection = false
        var consecutiveNewlines = 2 // Start with 2 to prevent leading newlines at index 0
        
        lines.forEachIndexed { index, line ->
            val currentLine = line.trim()
            if (currentLine.isEmpty()) {
                if (consecutiveNewlines < 2) {
                    append("\n")
                    consecutiveNewlines++
                }
                return@forEachIndexed
            }
            
            // Check for Main uppercase titles
            if ((currentLine == "SUMMARY OF THE CONTENT" || currentLine == "TRANSCRIPTION OF SPEAKERS") && !currentLine.contains("*")) {
                // Ensure exactly one blank line before (which means 2 consecutive newlines)
                while (consecutiveNewlines < 2) {
                    append("\n")
                    consecutiveNewlines++
                }
                pushStyle(androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.Start))
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = primaryColor, letterSpacing = 1.2.sp))
                append(currentLine)
                pop() // SpanStyle
                pop() // ParagraphStyle
                append("\n")
                consecutiveNewlines = 2
                isDecisionsSection = false
                isTranscriptionSection = currentLine == "TRANSCRIPTION OF SPEAKERS"
                return@forEachIndexed
            }
            
            // Heading matching
            if (currentLine.startsWith("## ") || currentLine.startsWith("### ")) {
                val cleanHeader = currentLine.substringAfter("##").substringAfter("#").trim()
                val headerText = cleanHeader.replace(Regex("^[0-9]+\\.\\s*"), "").uppercase().trim() // strip numbering & convert to UPPERCASE
                
                isDecisionsSection = headerText.contains("DECISIONS", ignoreCase = true)
                isTranscriptionSection = headerText.contains("TRANSCRIPTION", ignoreCase = true) || headerText.contains("SPEAKERS", ignoreCase = true) || isTranscriptionSection
                
                // Ensure exactly one blank line before (which means 2 consecutive newlines)
                while (consecutiveNewlines < 2) {
                    append("\n")
                    consecutiveNewlines++
                }
                pushStyle(androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.Start))
                
                val icon = when {
                    headerText.contains("SUMMARY", ignoreCase = true) -> "📝 "
                    headerText.contains("DECISIONS", ignoreCase = true) -> "🤝 "
                    headerText.contains("ACTIONS", ignoreCase = true) -> "⚡ "
                    headerText.contains("POINTS", ignoreCase = true) || headerText.contains("TAKEAWAYS", ignoreCase = true) -> "🎯 "
                    headerText.contains("TRANSCRIPTION", ignoreCase = true) || headerText.contains("SPEAKERS", ignoreCase = true) -> "🗣️ "
                    else -> "💡 "
                }
                
                pushStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = primaryColor, letterSpacing = 0.5.sp))
                append(icon + headerText)
                pop() // SpanStyle
                pop() // ParagraphStyle
                append("\n")
                consecutiveNewlines = 1
                return@forEachIndexed
            }
            
            // List item matching
            val isBullet = currentLine.startsWith("* ") || currentLine.startsWith("- ")
            if (isBullet) {
                // Ensure list items immediately follow the previous line (so exactly 1 newline before it)
                while (consecutiveNewlines < 1) {
                    append("\n")
                    consecutiveNewlines++
                }
                val bulletIcon = if (isDecisionsSection) "✓" else "•"
                append("\u00A0\u00A0$bulletIcon\u00A0")
                consecutiveNewlines = 0 // we appended text, so consecutiveNewlines resets to 0
                val content = currentLine.substring(2).trim()
                appendMarkdownInline(content, primaryColor, onSurfaceColor, onSurfaceVariantColor, isTranscriptionSection)
            } else {
                // Detect if this line represents a speaker turn
                val isSpeakerLine = if (isTranscriptionSection) {
                    if (currentLine.startsWith("**")) {
                        val boldEnd = currentLine.indexOf("**", 2)
                        boldEnd != -1 && boldEnd < 80 && (currentLine.substring(2, boldEnd).trim().endsWith(":") || currentLine.startsWith(":", boldEnd + 2))
                    } else {
                        val colonIdx = currentLine.indexOf(":")
                        colonIdx > 0 && colonIdx < 70 && !currentLine.substring(0, colonIdx).contains("[") && !currentLine.substring(0, colonIdx).contains("]") && !currentLine.substring(0, colonIdx).contains("*")
                    }
                } else {
                    false
                }
                
                // For speaker lines, ensure exactly a blank line before it (2 consecutive newlines)
                val requiredNewlines = if (isSpeakerLine) 2 else 1
                while (consecutiveNewlines < requiredNewlines) {
                    append("\n")
                    consecutiveNewlines++
                }
                appendMarkdownInline(currentLine, primaryColor, onSurfaceColor, onSurfaceVariantColor, isTranscriptionSection)
                consecutiveNewlines = 0
            }
            
            // At the end of a non-heading line, we append a newline if it's not the last line
            if (index < lines.size - 1) {
                append("\n")
                consecutiveNewlines = 1
            }
        }
    }
}

private fun AnnotatedString.Builder.appendMarkdownInline(
    content: String,
    primaryColor: Color,
    onSurfaceColor: Color,
    onSurfaceVariantColor: Color,
    isTranscriptionSection: Boolean
) {
    var i = 0
    while (i < content.length) {
        // 0. Check for speaker prefixes at the very start of the line (i == 0) inside the transcription section
        if (i == 0 && isTranscriptionSection) {
            var labelText: String? = null
            var prefixLength = 0
            
            // Format A: starts with bold, e.g., **Speaker Name:** or **Speaker Name**:
            if (content.startsWith("**")) {
                val boldEnd = content.indexOf("**", 2)
                if (boldEnd != -1 && boldEnd < 80) {
                    val inner = content.substring(2, boldEnd).trim()
                    if (inner.endsWith(":")) {
                        labelText = inner.trim()
                        prefixLength = boldEnd + 2
                    } else if (content.startsWith(":", boldEnd + 2)) {
                        labelText = "$inner:"
                        prefixLength = boldEnd + 3
                    }
                }
            }
            
            // Format B: plain text prefix ending with a colon, e.g., Speaker Name:
            if (labelText == null) {
                val colonIdx = content.indexOf(":")
                if (colonIdx > 0 && colonIdx < 70 && !content.substring(0, colonIdx).contains("\n")) {
                    val potential = content.substring(0, colonIdx)
                    // Ensure it doesn't contain bracket symbols or bold markdown characters
                    if (!potential.contains("[") && !potential.contains("]") && !potential.contains("*")) {
                        labelText = "${potential.trim()}:"
                        prefixLength = colonIdx + 1
                    }
                }
            }
            
            if (labelText != null) {
                pushStringAnnotation(tag = "highlight_tag", annotation = "")
                pushStyle(SpanStyle(
                    fontWeight = FontWeight.ExtraBold,
                    color = primaryColor
                ))
                append("\u00A0$labelText\u00A0")
                pop()
                pop()
                
                // Advance pointer
                i = prefixLength
                // Prevent spacing stretching by appending a non-breaking space if the next char is a space
                if (i < content.length && content[i] == ' ') {
                    append("\u00A0")
                    i++
                } else {
                    append("\u00A0")
                }
                continue
            }
        }

        // 1. Check for square bracket tags, e.g. [Action Item]
        if (content.startsWith("[", i)) {
            val endIdx = content.indexOf("]", i + 1)
            if (endIdx != -1) {
                val tagText = content.substring(i, endIdx + 1)
                pushStyle(SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                ))
                append(tagText)
                pop()
                i = endIdx + 1
                continue
            }
        }

        // 2. Check for bold text, e.g. **Bold Text**
        if (content.startsWith("**", i)) {
            val endIdx = content.indexOf("**", i + 2)
            if (endIdx != -1) {
                val boldContent = content.substring(i + 2, endIdx)
                // If it is **[Action Item]**, apply bold primary styling without highlight background
                if (boldContent.startsWith("[") && boldContent.endsWith("]")) {
                    pushStyle(SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    ))
                    append(boldContent)
                    pop()
                } else {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onSurfaceColor))
                    append(boldContent)
                    pop()
                }
                i = endIdx + 2
                continue
            }
        }
        
        if (content[i] == '*') {
            i++
            continue
        }
        
        append(content[i])
        i++
    }
}
