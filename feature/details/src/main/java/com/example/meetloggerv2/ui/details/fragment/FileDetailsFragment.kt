package com.example.meetloggerv2.ui.details.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
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
        "Macedonian" to "mk", "Marathi" to "mr", "Malay" to "ms", "Maltese" to "mt", "Dutch" to "nl",
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
                    FileDetailsScreenContent(
                        viewModel = viewModel,
                        fileName = fileName,
                        languages = languages,
                        onBack = { handleBackPressed() },
                        onShare = { content, format -> performShare(content, format) },
                        onExport = { format -> performExport(format) },
                        onNewFileCreated = { newName -> openNewFileDetailsFragment(newName) }
                    )
                }
            }
        }
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
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = if (format == "PDF") "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val ext = if (format == "PDF") "pdf" else "docx"
            val cleanName = fileName?.substringBeforeLast(".") ?: "export"
            putExtra(Intent.EXTRA_TITLE, "$cleanName.$ext")
        }
        exportLauncher.launch(intent)
    }

    private fun saveContentToUri(uri: Uri, format: String) {
        try {
            val content = viewModel.fileDetails.value?.get("Response") as? String ?: ""
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                exportManager.export(content, format, os)
            }
            Toast.makeText(requireContext(), R.string.msg_downloaded_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performShare(content: String, format: String) {
        val cleanName = fileName?.substringBeforeLast(".") ?: "share"
        val exporter = exportManager.getExporter(format) ?: return
        val ext = when (format) {
            "PDF" -> "pdf"
            "DOCX" -> "docx"
            else -> "txt"
        }
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
    onNewFileCreated: (String) -> Unit
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
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var showShareFormatDialog by remember { mutableStateOf(false) }

    LaunchedEffect(fileDetails) {
        fileDetails?.let { data ->
            val responseText = (data["Response"] as? String)?.replace("*", "")?.trim() ?: ""
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

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isEditing) {
                    isEditing = false
                    editedText = (fileDetails?.get("Response") as? String)?.replace("*", "")?.trim() ?: ""
                } else {
                    onBack()
                }
            }
        }
    }

    DisposableEffect(isEditing) {
        backCallback.isEnabled = isEditing
        onDispose {}
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
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    val navInteractionSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                isEditing = false
                                editedText = (fileDetails?.get("Response") as? String)?.replace("*", "")?.trim() ?: ""
                            } else {
                                onBack()
                            }
                        },
                        interactionSource = navInteractionSource,
                        modifier = Modifier.pressScale(navInteractionSource)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
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
                    modifier = Modifier.height(96.dp)
                ) {
                    if (isEditing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val saveInteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = {
                                    if (isContentTranslated) {
                                        showSaveOptionsDialog = true
                                    } else {
                                        fileName?.let {
                                            viewModel.updateContent(it, editedText, selectedLanguageCode)
                                        }
                                        isEditing = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                interactionSource = saveInteractionSource,
                                modifier = Modifier.weight(1f).height(48.dp).pressScale(saveInteractionSource),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(id = R.string.dialog_save))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            val cancelBtnInteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = {
                                    isEditing = false
                                    editedText = (fileDetails?.get("Response") as? String)?.replace("*", "")?.trim() ?: ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                interactionSource = cancelBtnInteractionSource,
                                modifier = Modifier.weight(1f).height(48.dp).pressScale(cancelBtnInteractionSource),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(id = R.string.dialog_cancel))
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
                                iconRes = R.drawable.ic_export_doc,
                                label = "Export",
                                onClick = { showExportFormatDialog = true }
                            )
                            BottomActionItem(
                                imageVector = Icons.Default.Share,
                                label = "Share",
                                onClick = { showShareFormatDialog = true }
                            )
                            BottomActionItem(
                                iconRes = R.drawable.ic_translate,
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                if (isEditing) {
                     OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = editedText,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Progress / Loading HUD
            if (uiState is FileDetailsViewModel.DetailsUiState.Loading) {
                val loadingMsg = (uiState as FileDetailsViewModel.DetailsUiState.Loading).message
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(text = loadingMsg, color = Color.White, fontWeight = FontWeight.Bold)
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_title_choose_language),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.dialog_language_switch_msg),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    languages.forEach { lang ->
                        Text(
                            text = lang.first,
                            fontSize = 15.sp,
                            color = if (selectedLanguageCode == lang.second)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLanguageCode = lang.second
                                    val currentResp = fileDetails?.get("Response") as? String ?: ""
                                    viewModel.translateContent(
                                        currentResp,
                                        originalLanguageCode,
                                        lang.second
                                    )
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            fontWeight = if (selectedLanguageCode == lang.second) FontWeight.Bold else FontWeight.Normal
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    val langCancelInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { showLanguageDialog = false },
                        interactionSource = langCancelInteractionSource,
                        modifier = Modifier.pressScale(langCancelInteractionSource)
                    ) {
                        Text(text = stringResource(id = R.string.dialog_cancel))
                    }
                }
            }
        }
    }

    // 2. Save Options Dialog
    if (showSaveOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showSaveOptionsDialog = false },
            title = { Text(text = "Save Options") },
            text = { Text(text = "How would you like to save your edits?") },
            confirmButton = {
                val confirmInteractionSource = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        showSaveOptionsDialog = false
                        showOverwriteWarningDialog = true
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    interactionSource = confirmInteractionSource,
                    modifier = Modifier.pressScale(confirmInteractionSource)
                ) {
                    Text(text = stringResource(id = R.string.dialog_overwrite), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                val dismissInteractionSource = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = {
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
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    interactionSource = dismissInteractionSource,
                    modifier = Modifier.pressScale(dismissInteractionSource)
                ) {
                    Text(text = stringResource(id = R.string.dialog_new_copy), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // 3. Overwrite Warning Dialog
    if (showOverwriteWarningDialog) {
        val originalLangName = languages.find { it.second == originalLanguageCode }?.first ?: "unknown"
        val currentLangName = languages.find { it.second == selectedLanguageCode }?.first ?: "unknown"
        AlertDialog(
            onDismissRequest = { showOverwriteWarningDialog = false },
            title = { Text(text = stringResource(id = R.string.dialog_title_warning)) },
            text = {
                Text(
                    text = "Overwriting will replace the original content ($originalLangName language) with edits made in $currentLangName."
                )
            },
            confirmButton = {
                val confirmInteractionSource = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        showOverwriteWarningDialog = false
                        fileName?.let {
                            viewModel.updateContent(it, editedText, selectedLanguageCode)
                        }
                        isEditing = false
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    interactionSource = confirmInteractionSource,
                    modifier = Modifier.pressScale(confirmInteractionSource)
                ) {
                    Text(text = stringResource(id = R.string.dialog_yes), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                val dismissInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showOverwriteWarningDialog = false },
                    interactionSource = dismissInteractionSource,
                    modifier = Modifier.pressScale(dismissInteractionSource)
                ) {
                    Text(text = stringResource(id = R.string.dialog_no), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // 4. Export Format Selection
    if (showExportFormatDialog) {
        Dialog(onDismissRequest = { showExportFormatDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_title_choose_format),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(id = R.string.dialog_subtitle_export_format),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val formats = listOf("PDF", "DOCX", "TXT")
                    formats.forEach { format ->
                        val iconRes = when (format) {
                            "PDF" -> R.drawable.pdf
                            "DOCX" -> R.drawable.ic_docx
                            else -> R.drawable.doc_1
                        }
                        val btnInteractionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                onExport(format)
                                showExportFormatDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            interactionSource = btnInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(48.dp)
                                .pressScale(btnInteractionSource)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = format, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val exportCancelInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { showExportFormatDialog = false },
                        interactionSource = exportCancelInteractionSource,
                        modifier = Modifier.pressScale(exportCancelInteractionSource)
                    ) {
                        Text(text = stringResource(id = R.string.dialog_cancel))
                    }
                }
            }
        }
    }

    // 5. Share Format Selection Dialog
    if (showShareFormatDialog) {
        Dialog(onDismissRequest = { showShareFormatDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(id = R.string.dialog_title_choose_format),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Choose format to share this document:",
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    val formats = listOf("PDF", "DOCX", "TXT")
                    formats.forEach { format ->
                        val iconRes = when (format) {
                            "PDF" -> R.drawable.pdf
                            "DOCX" -> R.drawable.ic_docx
                            else -> R.drawable.doc_1
                        }
                        val btnInteractionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                onShare(editedText, format)
                                showShareFormatDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            interactionSource = btnInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(48.dp)
                                .pressScale(btnInteractionSource)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = format, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val shareCancelInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { showShareFormatDialog = false },
                        interactionSource = shareCancelInteractionSource,
                        modifier = Modifier.pressScale(shareCancelInteractionSource)
                    ) {
                        Text(text = stringResource(id = R.string.dialog_cancel))
                    }
                }
            }
        }
    }
}

@Composable
fun BottomActionItem(
    iconRes: Int = 0,
    imageVector: ImageVector? = null,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageVector != null) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            } else if (iconRes != 0) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
