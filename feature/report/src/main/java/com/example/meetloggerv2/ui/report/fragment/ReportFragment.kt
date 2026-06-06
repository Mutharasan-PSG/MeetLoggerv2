package com.example.meetloggerv2.ui.report.fragment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.example.meetloggerv2.core.navigation.findNavigationRouter
import com.example.meetloggerv2.core.network.NetworkMonitor
import com.example.meetloggerv2.core.theme.GradientEnd
import com.example.meetloggerv2.core.theme.GradientStart
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
import com.example.meetloggerv2.core.theme.pressScale
import com.example.meetloggerv2.core.theme.pressScaleClick
import com.example.meetloggerv2.core.util.ShareHelper
import com.example.meetloggerv2.ui.report.viewmodel.ReportViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class ReportFragment : Fragment() {

    private val viewModel: ReportViewModel by viewModels()
    private lateinit var exportManager: DocumentExportManager
    private lateinit var networkMonitor: NetworkMonitor

    private var pendingContent: String? = null
    private var pendingFormat: String? = null
    private var pendingReportAction: String? = null
    private var pendingReportName: String? = null

    private val isOnlineState = mutableStateOf(true)

    private val exportFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val content = pendingContent ?: return@registerForActivityResult
            saveContentToUri(uri, content, pendingFormat ?: "PDF")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        exportManager = DocumentExportManager(requireContext())
        networkMonitor = NetworkMonitor(requireContext())

        setupObservers()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MeetLoggerTheme {
                    ReportScreenContent(
                        viewModel = viewModel,
                        isOnline = isOnlineState.value,
                        onBack = { handleBackPressed() },
                        onOpenDetails = { shortName -> openFileDetailsFragment(shortName) },
                        onOpenAudioList = { openAudioListFragment() },
                        onExportAction = { name, format -> performExport(name, format) },
                        onShareAction = { name, format -> performShare(name, format) },
                        onShowFormatSelection = { action, name -> showFormatSelectionBottomSheet(action, name) }
                    )
                }
            }
        }
    }

    private fun showFormatSelectionBottomSheet(action: String, name: String) {
        com.example.meetloggerv2.core.util.FormatSelectionBottomSheetFragment.newInstance(
            title = "Choose format",
            subtitle = if (action == "EXPORT") "Select the format to save your file" else "Select the format to share your file"
        ).setCallback { format ->
            if (action == "EXPORT") {
                performExport(name, format)
            } else {
                performShare(name, format)
            }
        }.show(parentFragmentManager, "FormatSelectionBottomSheet")
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.reportEvent.collect { event ->
                    val content = event.getContentIfNotHandled() ?: return@collect
                    when (content) {
                        is ReportViewModel.ReportEvent.FetchDetailsSuccess -> {
                            val name = pendingReportName ?: ""
                            val format = pendingFormat ?: "PDF"
                            val reportContent = content.content

                            val exporter = exportManager.getExporter(format)
                            if (exporter != null) {
                                if (pendingReportAction == "EXPORT") {
                                    pendingReportAction = null
                                    pendingReportName = null
                                    pendingContent = reportContent
                                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                        type = exporter.mimeType
                                        putExtra(Intent.EXTRA_TITLE, "$name${exporter.fileExtension}")
                                    }
                                    exportFileLauncher.launch(intent)
                                } else if (pendingReportAction == "SHARE") {
                                    pendingReportAction = null
                                    pendingReportName = null
                                    val ext = exporter.fileExtension.removePrefix(".")
                                    val temp = File(requireContext().cacheDir, "$name.$ext")
                                    FileOutputStream(temp).use { os ->
                                        exportManager.export(reportContent, format, os)
                                    }
                                    startActivity(
                                        Intent.createChooser(
                                            ShareHelper.getShareIntent(requireContext(), temp, exporter.mimeType),
                                            "Share"
                                        )
                                    )
                                }
                            }
                        }
                        is ReportViewModel.ReportEvent.FetchDetailsError -> {
                            pendingReportAction = null
                            pendingReportName = null
                            Toast.makeText(requireContext(), content.errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        networkMonitor.observe(viewLifecycleOwner) { isOnline ->
            isOnlineState.value = isOnline
            if (isOnline) {
                viewModel.fetchFiles()
            }
        }
    }

    private fun performExport(name: String, format: String) {
        val full = viewModel.getFullFileName(name) ?: return
        pendingReportAction = "EXPORT"
        pendingReportName = name
        pendingFormat = format
        viewModel.fetchFileDetails(full)
    }

    private fun performShare(name: String, format: String) {
        val full = viewModel.getFullFileName(name) ?: return
        pendingReportAction = "SHARE"
        pendingReportName = name
        pendingFormat = format
        viewModel.fetchFileDetails(full)
    }

    private fun saveContentToUri(uri: Uri, content: String, format: String) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { os ->
                exportManager.export(content, format, os)
            }
            Toast.makeText(requireContext(), R.string.msg_downloaded_success, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFileDetailsFragment(name: String) {
        val full = viewModel.getFullFileName(name) ?: return
        findNavigationRouter()?.navigateToFileDetails(full)
    }

    private fun openAudioListFragment() {
        findNavigationRouter()?.navigateToAudioList()
    }

    private fun handleBackPressed() {
        parentFragmentManager.popBackStack()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReportScreenContent(
    viewModel: ReportViewModel,
    isOnline: Boolean,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenAudioList: () -> Unit,
    onExportAction: (name: String, format: String) -> Unit,
    onShareAction: (name: String, format: String) -> Unit,
    onShowFormatSelection: (action: String, name: String) -> Unit
) {
    val context = LocalContext.current

    val files by viewModel.filteredFiles.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()
    val isLoading = uiState is ReportViewModel.ReportUiState.Loading

    var searchQuery by remember { mutableStateOf("") }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedFiles = remember { mutableStateListOf<String>() }

    var renamingPosition by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    var renameTextValue by remember { mutableStateOf(TextFieldValue("")) }

    var expandedMenuPosition by remember { mutableStateOf(-1) }

    // Dialog States
    var showCopyDialogForPos by remember { mutableStateOf(-1) }
    var copyNewName by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    androidx.activity.compose.BackHandler(enabled = isDeleteMode || renamingPosition != -1) {
        if (renamingPosition != -1) {
            renamingPosition = -1
        } else if (isDeleteMode) {
            isDeleteMode = false
            selectedFiles.clear()
        }
    }

    if (!isOnline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            EmptyStatePlaceholder(
                icon = Icons.Default.WifiOff,
                title = "No Internet Connection",
                subtitle = "Check your network settings and try again"
            )
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.summarized_files),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.padding(start = 5.dp)
                        )
                    },
                    navigationIcon = {
                            Surface(
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(40.dp)
                                    .pressScaleClick(enabled = !isLoading) {
                                        if (isDeleteMode) {
                                            isDeleteMode = false
                                            selectedFiles.clear()
                                        } else if (renamingPosition != -1) {
                                            renamingPosition = -1
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
                    actions = {
                        if (renamingPosition != -1) {
                            IconButton(
                                onClick = {
                                    val item = files.getOrNull(renamingPosition)
                                    if (item != null) {
                                        val oldFull = viewModel.getFullFileName(item.first)
                                        val newNameTrimmed = renameText.trim()
                                        if (newNameTrimmed.isNotEmpty() && oldFull != null) {
                                            val ext = oldFull.substringAfterLast(".")
                                            viewModel.renameFile(oldFull, "$newNameTrimmed.$ext")
                                        } else {
                                            Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    renamingPosition = -1
                                },
                                enabled = !isLoading,
                                modifier = Modifier.pressScale().padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else if (isDeleteMode) {
                            if (selectedFiles.isNotEmpty()) {
                                IconButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    enabled = !isLoading,
                                    modifier = Modifier.pressScale().padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Selected",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
else {
                            IconButton(
                                onClick = onOpenAudioList,
                                modifier = Modifier.pressScaleClick { onOpenAudioList() }.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Audio List",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
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
                        .padding(horizontal = 16.dp)
                ) {
                    // Search Bar
                    if (files.isNotEmpty() || searchQuery.isNotEmpty()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.setQuery(it)
                            },
                            placeholder = {
                                Text(
                                    "Search summaries...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    val clearInteractionSource = remember { MutableInteractionSource() }
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            viewModel.setQuery("")
                                        },
                                        interactionSource = clearInteractionSource,
                                        modifier = Modifier.pressScale(clearInteractionSource)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            ),
                            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDeleteMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedFiles.size == files.size && files.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    selectedFiles.clear()
                                    if (checked) {
                                        files.forEach { selectedFiles.add(it.first) }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.select_all),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (files.isEmpty() && uiState !is ReportViewModel.ReportUiState.Loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (searchQuery.isEmpty()) {
                                EmptyStatePlaceholder(
                                    icon = Icons.Default.Description,
                                    title = "No summaries yet",
                                    subtitle = "Summarized files will show up here once processed"
                                )
                            } else {
                                Text(
                                    text = stringResource(id = R.string.empty_report_search_message),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            itemsIndexed(files) { index, item ->
                                val name = item.first
                                val timestamp = item.second

                                val isSelected = selectedFiles.contains(name)
                                val isRenamingThis = renamingPosition == index

                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                if (isDeleteMode) {
                                                    if (isSelected) selectedFiles.remove(name) else selectedFiles.add(name)
                                                } else if (renamingPosition == -1) {
                                                    onOpenDetails(name)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isDeleteMode && renamingPosition == -1) {
                                                    isDeleteMode = true
                                                    selectedFiles.add(name)
                                                }
                                            }
                                        )
                                        .pressScale(interactionSource),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                )
{
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        val isDark = MaterialTheme.colorScheme.onSurface == Color.White
                                        if (isDeleteMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                        } else {
                                            Surface(
                                                modifier = Modifier.size(44.dp),
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Description,
                                                        contentDescription = "docImage",
                                                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (isRenamingThis) {
                                                val focusRequester = remember { FocusRequester() }
                                                LaunchedEffect(Unit) {
                                                    focusRequester.requestFocus()
                                                }
                                                TextField(
                                                    value = renameTextValue,
                                                    onValueChange = {
                                                        renameTextValue = it
                                                        renameText = it.text
                                                    },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(
                                                        keyboardType = KeyboardType.Password,
                                                        imeAction = ImeAction.Done,
                                                        autoCorrectEnabled = false
                                                    ),
                                                    visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                                                    keyboardActions = KeyboardActions(
                                                        onDone = {
                                                            val oldFull = viewModel.getFullFileName(name)
                                                            val newNameTrimmed = renameText.trim()
                                                            if (newNameTrimmed.isNotEmpty() && oldFull != null) {
                                                                val ext = oldFull.substringAfterLast(".")
                                                                viewModel.renameFile(oldFull, "$newNameTrimmed.$ext")
                                                            } else {
                                                                Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
                                                            }
                                                            renamingPosition = -1
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .focusRequester(focusRequester),
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                        disabledIndicatorColor = Color.Transparent,
                                                        cursorColor = MaterialTheme.colorScheme.primary
                                                    ),
                                                    textStyle = LocalTextStyle.current.copy(
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                )
                                            } else {
                                                Text(
                                                    text = name,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 15.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
                                                val dateStr = sdf.format(timestamp.toDate())
                                                Text(
                                                    text = dateStr,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (!isDeleteMode && renamingPosition == -1) {
                                            Box {
                                                IconButton(
                                                    onClick = { expandedMenuPosition = index },
                                                    modifier = Modifier.pressScaleClick { expandedMenuPosition = index }
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Options"
                                                    )
                                                }

                                                MaterialTheme(
                                                    shapes = MaterialTheme.shapes.copy(extraSmall = RoundedCornerShape(28.dp))
                                                ) {
                                                    DropdownMenu(
                                                        expanded = expandedMenuPosition == index,
                                                        onDismissRequest = { expandedMenuPosition = -1 },
                                                        modifier = Modifier
                                                            .widthIn(min = 140.dp, max = 220.dp)
                                                            .background(MaterialTheme.colorScheme.surface)
                                                            .border(
                                                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                                                RoundedCornerShape(28.dp)
                                                            )
                                                    ) {
                                                        val isDarkMenu = MaterialTheme.colorScheme.onSurface == Color.White
                                                        val menuIconTint = if (isDarkMenu) Color.White else MaterialTheme.colorScheme.primary
                                                        val menuSecondaryTint = if (isDarkMenu) Color.White else MaterialTheme.colorScheme.secondary

                                                        DropdownMenuItem(
                                                            text = { Text("Rename", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuIconTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                renameTextValue = TextFieldValue(text = name, selection = TextRange(name.length))
                                                                renamingPosition = index
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                renameTextValue = TextFieldValue(text = name, selection = TextRange(name.length))
                                                                renamingPosition = index
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Export", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuIconTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                onShowFormatSelection("EXPORT", name)
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                onShowFormatSelection("EXPORT", name)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Share", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuIconTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                onShowFormatSelection("SHARE", name)
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                onShowFormatSelection("SHARE", name)
                                                            }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text("Copy", fontWeight = FontWeight.SemiBold, fontSize = 17.sp) },
                                                            leadingIcon = { Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(24.dp), tint = menuSecondaryTint) },
                                                            contentPadding = PaddingValues(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
                                                            modifier = Modifier.pressScaleClick {
                                                                expandedMenuPosition = -1
                                                                copyNewName = "copy of $name"
                                                                showCopyDialogForPos = index
                                                            },
                                                            onClick = {
                                                                expandedMenuPosition = -1
                                                                copyNewName = "copy of $name"
                                                                showCopyDialogForPos = index
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState is ReportViewModel.ReportUiState.Loading) {
                    val msg = (uiState as ReportViewModel.ReportUiState.Loading).message
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f))
                            .clickable(enabled = false) {},
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(text = msg, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showCopyDialogForPos != -1) {
        val item = files.getOrNull(showCopyDialogForPos)
        if (item != null) {
            val name = item.first
            val full = viewModel.getFullFileName(name)
            if (full != null) {
                val copyFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(100)
                    copyFocusRequester.requestFocus()
                }
                Dialog(onDismissRequest = { showCopyDialogForPos = -1 }) {
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
                                text = stringResource(id = R.string.dialog_title_copy_report),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Enter a name for your copy:",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedTextField(
                                value = copyNewName,
                                onValueChange = { copyNewName = it },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    autoCorrectEnabled = false
                                ),
                                visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(copyFocusRequester)
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
                                        .pressScaleClick { showCopyDialogForPos = -1 },
                                    shape = RoundedCornerShape(24.dp),
                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                    color = Color.Transparent
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScaleClick {
                                            val trimmed = copyNewName.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val ext = full.substringAfterLast(".", "mp3")
                                                viewModel.copyFile(full, "$trimmed.$ext")
                                                showCopyDialogForPos = -1
                                            } else {
                                                Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
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
                                        val isCopying = uiState is ReportViewModel.ReportUiState.Loading && (uiState as ReportViewModel.ReportUiState.Loading).message == "Copying..."
                                        if (isCopying) {
                                            Box(modifier = Modifier.size(24.dp)) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    strokeWidth = 2.5.dp
                                                )
                                            }
                                        } else {
                                            Text(stringResource(id = R.string.dialog_proceed), fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirmDialog) {
        Dialog(onDismissRequest = { showDeleteConfirmDialog = false }) {
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
                        text = "Confirm Delete",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(id = R.string.msg_delete_selected_reports),
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
                                .pressScaleClick { showDeleteConfirmDialog = false },
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            color = Color.Transparent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .pressScaleClick {
                                    val filesToDelete = selectedFiles.mapNotNull { shortName ->
                                        viewModel.getFullFileName(shortName)
                                    }
                                    viewModel.deleteFiles(filesToDelete)
                                    isDeleteMode = false
                                    selectedFiles.clear()
                                    showDeleteConfirmDialog = false
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.linearGradient(colors = listOf(Color(0xFFEF5350), Color(0xFFD32F2F)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(stringResource(id = R.string.dialog_delete), fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStatePlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val isDark = MaterialTheme.colorScheme.onSurface == Color.White
        Surface(
            modifier = Modifier.size(100.dp),
            shape = RoundedCornerShape(24.dp),
            color = if (isDark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
