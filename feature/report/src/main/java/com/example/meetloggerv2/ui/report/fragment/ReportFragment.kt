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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import com.example.meetloggerv2.core.theme.pressScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.meetloggerv2.core.theme.MeetLoggerTheme
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
                        onShareAction = { name, format -> performShare(name, format) }
                    )
                }
            }
        }
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
    onShareAction: (name: String, format: String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    val files by viewModel.filteredFiles.collectAsState(initial = emptyList())
    val uiState by viewModel.uiState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isDeleteMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<Int>() }

    var renamingPosition by remember { mutableStateOf(-1) }
    var renameText by remember { mutableStateOf("") }
    var renameTextValue by remember { mutableStateOf(TextFieldValue("")) }

    var expandedMenuPosition by remember { mutableStateOf(-1) }

    // Dialog States
    var showCopyDialogForPos by remember { mutableStateOf(-1) }
    var copyNewName by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showFormatSelectionDialogForAction by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(ActionType, name)

    // Backpress handler for delete mode and rename mode
    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (renamingPosition != -1) {
                    renamingPosition = -1
                } else if (isDeleteMode) {
                    isDeleteMode = false
                    selectedItems.clear()
                } else {
                    onBack()
                }
            }
        }
    }

    DisposableEffect(isDeleteMode, renamingPosition) {
        backCallback.isEnabled = isDeleteMode || renamingPosition != -1
        onDispose {}
    }

    if (!isOnline) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(id = R.string.content_desc_no_internet),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(id = R.string.no_internet_message),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(id = R.string.summarized_files),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    },
                    navigationIcon = {
                        val navInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = {
                                if (isDeleteMode) {
                                    isDeleteMode = false
                                    selectedItems.clear()
                                } else if (renamingPosition != -1) {
                                    renamingPosition = -1
                                } else {
                                    onBack()
                                }
                            },
                            interactionSource = navInteractionSource,
                            modifier = Modifier.pressScale(navInteractionSource)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (renamingPosition != -1) {
                            val confirmInteractionSource = remember { MutableInteractionSource() }
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
                                interactionSource = confirmInteractionSource,
                                modifier = Modifier.pressScale(confirmInteractionSource).padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Confirm",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else if (isDeleteMode) {
                            if (selectedItems.isNotEmpty()) {
                                val deleteInteractionSource = remember { MutableInteractionSource() }
                                IconButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    interactionSource = deleteInteractionSource,
                                    modifier = Modifier.pressScale(deleteInteractionSource).padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Selected",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        } else {
                            val listInteractionSource = remember { MutableInteractionSource() }
                            IconButton(
                                onClick = onOpenAudioList,
                                interactionSource = listInteractionSource,
                                modifier = Modifier.pressScale(listInteractionSource).padding(end = 8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.audioo),
                                    contentDescription = "Audio List",
                                    tint = Color.Unspecified,
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
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.setQuery(it)
                        },
                        placeholder = { Text("Search summaries...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isDeleteMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Checkbox(
                                checked = selectedItems.size == files.size && files.isNotEmpty(),
                                onCheckedChange = { checked ->
                                    selectedItems.clear()
                                    if (checked) {
                                        files.indices.forEach { selectedItems.add(it) }
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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
                            Text(
                                text = if (searchQuery.isEmpty())
                                    stringResource(id = R.string.empty_report_message)
                                else
                                    stringResource(id = R.string.empty_report_search_message),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
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

                                val isSelected = selectedItems.contains(index)
                                val isRenamingThis = renamingPosition == index

                                val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pressScale(interactionSource)
                                        .clip(RoundedCornerShape(16.dp))
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                if (isDeleteMode) {
                                                    if (isSelected) selectedItems.remove(index) else selectedItems.add(index)
                                                } else if (renamingPosition == -1) {
                                                    onOpenDetails(name)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isDeleteMode && renamingPosition == -1) {
                                                    isDeleteMode = true
                                                    selectedItems.add(index)
                                                }
                                            }
                                        ),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp)
                                    ) {
                                        if (isDeleteMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                        } else {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_docx),
                                                contentDescription = "docImage",
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(32.dp)
                                            )
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
                                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
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
                                                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
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
                                                val menuInteractionSource = remember { MutableInteractionSource() }
                                                IconButton(
                                                    onClick = { expandedMenuPosition = index },
                                                    interactionSource = menuInteractionSource,
                                                    modifier = Modifier.pressScale(menuInteractionSource)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.MoreVert,
                                                        contentDescription = "Options"
                                                    )
                                                }

                                                DropdownMenu(
                                                    expanded = expandedMenuPosition == index,
                                                    onDismissRequest = { expandedMenuPosition = -1 },
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(16.dp))
                                                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)), RoundedCornerShape(16.dp))
                                                        .background(MaterialTheme.colorScheme.surface)
                                                ) {
                                                    val renameInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Rename", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(imageVector = Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = renameInteractionSource,
                                                        modifier = Modifier.pressScale(renameInteractionSource),
                                                        onClick = {
                                                            expandedMenuPosition = -1
                                                            renameTextValue = TextFieldValue(text = name, selection = TextRange(name.length))
                                                            renamingPosition = index
                                                        }
                                                    )
                                                    val exportInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Export", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(painterResource(id = R.drawable.ic_export_doc), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = exportInteractionSource,
                                                        modifier = Modifier.pressScale(exportInteractionSource),
                                                        onClick = {
                                                            expandedMenuPosition = -1
                                                            showFormatSelectionDialogForAction = Pair("EXPORT", name)
                                                        }
                                                    )
                                                    val shareInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Share", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = shareInteractionSource,
                                                        modifier = Modifier.pressScale(shareInteractionSource),
                                                        onClick = {
                                                            expandedMenuPosition = -1
                                                            showFormatSelectionDialogForAction = Pair("SHARE", name)
                                                        }
                                                    )
                                                    val copyInteractionSource = remember { MutableInteractionSource() }
                                                    DropdownMenuItem(
                                                        text = { Text("Copy", fontWeight = FontWeight.Medium) },
                                                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_docx), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                                                        interactionSource = copyInteractionSource,
                                                        modifier = Modifier.pressScale(copyInteractionSource),
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
                AlertDialog(
                    onDismissRequest = { showCopyDialogForPos = -1 },
                    title = { Text(text = stringResource(id = R.string.dialog_title_copy_report)) },
                    text = {
                        Column {
                            Text(text = "Enter new filename:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = copyNewName,
                                onValueChange = { copyNewName = it },
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth().focusRequester(copyFocusRequester)
                            )
                        }
                    },
                    confirmButton = {
                        val proceedInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                val trimmed = copyNewName.trim()
                                if (trimmed.isNotEmpty()) {
                                    val ext = full.substringAfterLast(".", "mp3")
                                    viewModel.copyFile(full, "$trimmed.$ext")
                                    showCopyDialogForPos = -1
                                } else {
                                    Toast.makeText(context, R.string.error_name_empty, Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            interactionSource = proceedInteractionSource,
                            modifier = Modifier.pressScale(proceedInteractionSource)
                        ) {
                            Text(stringResource(id = R.string.dialog_proceed), fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        val cancelInteractionSource = remember { MutableInteractionSource() }
                        TextButton(
                            onClick = { showCopyDialogForPos = -1 },
                            interactionSource = cancelInteractionSource,
                            modifier = Modifier.pressScale(cancelInteractionSource)
                        ) {
                            Text(stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }
        }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(text = "Confirm Delete") },
            text = { Text(text = stringResource(id = R.string.msg_delete_selected_reports)) },
            confirmButton = {
                val confirmInteractionSource = remember { MutableInteractionSource() }
                Button(
                    onClick = {
                        val filesToDelete = selectedItems.mapNotNull {
                            val shortName = files.getOrNull(it)?.first
                            if (shortName != null) viewModel.getFullFileName(shortName) else null
                        }
                        viewModel.deleteFiles(filesToDelete)
                        isDeleteMode = false
                        selectedItems.clear()
                        showDeleteConfirmDialog = false
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    interactionSource = confirmInteractionSource,
                    modifier = Modifier.pressScale(confirmInteractionSource)
                ) {
                    Text(stringResource(id = R.string.dialog_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                val dismissInteractionSource = remember { MutableInteractionSource() }
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    interactionSource = dismissInteractionSource,
                    modifier = Modifier.pressScale(dismissInteractionSource)
                ) {
                    Text(stringResource(id = R.string.dialog_cancel), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    showFormatSelectionDialogForAction?.let { pair ->
        val action = pair.first
        val reportName = pair.second
        Dialog(onDismissRequest = { showFormatSelectionDialogForAction = null }) {
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
                        val formatBtnInteractionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = {
                                if (action == "EXPORT") {
                                    onExportAction(reportName, format)
                                } else {
                                    onShareAction(reportName, format)
                                }
                                showFormatSelectionDialogForAction = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            interactionSource = formatBtnInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(48.dp)
                                .pressScale(formatBtnInteractionSource)
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
                    val formatCancelInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = { showFormatSelectionDialogForAction = null },
                        interactionSource = formatCancelInteractionSource,
                        modifier = Modifier.pressScale(formatCancelInteractionSource)
                    ) {
                        Text(stringResource(id = R.string.dialog_cancel))
                    }
                }
            }
        }
    }
}
