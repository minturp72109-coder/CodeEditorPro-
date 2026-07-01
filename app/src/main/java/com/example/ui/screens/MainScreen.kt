package com.example.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.GitBranchEntity
import com.example.data.database.ProjectEntity
import com.example.data.repository.FileNode
import com.example.ui.theme.CodeVisualTransformation
import com.example.ui.viewmodel.EditorViewModel
import kotlinx.coroutines.launch
import java.io.File

// A helper to safely log user actions (no-op after removing AdMob)
fun logAction() {}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EditorViewModel) {
    val currentContext = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- State collections ---
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val fileTree by viewModel.fileTree.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()
    val activeFileContent by viewModel.activeFileContent.collectAsStateWithLifecycle()
    val activePanel by viewModel.activePanel.collectAsStateWithLifecycle()

    // Settings
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val fontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
    val isWordWrap by viewModel.isWordWrapEnabled.collectAsStateWithLifecycle()
    val isAutoSave by viewModel.isAutoSaveEnabled.collectAsStateWithLifecycle()
    val accentColorIndex by viewModel.accentColorIndex.collectAsStateWithLifecycle()

    // --- Color Accents Palette ---
    val accentColors = listOf(
        Color(0xFFFF8E53), // Sunset Orange
        Color(0xFF00BFA5), // Teal Rose
        Color(0xFF2979FF), // Cosmos Blue
        Color(0xFFAA00FF)  // Nebula Purple
    )
    val primaryColor = accentColors[accentColorIndex]

    // Dialog toggles
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showGitBranchDialog by remember { mutableStateOf(false) }
    var showProjectSelectDialog by remember { mutableStateOf(false) }

    // Text field state inside editor (using TextFieldValue to capture selections and cursor indices)
    var editorTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // Synchronize external file edits (tab switches, loading content) with TextFieldState
    LaunchedEffect(activeFileContent) {
        if (editorTextFieldValue.text != activeFileContent) {
            editorTextFieldValue = TextFieldValue(activeFileContent)
        }
    }

    // Capture cursor positions on change
    LaunchedEffect(editorTextFieldValue) {
        val cursor = editorTextFieldValue.selection.start
        val text = editorTextFieldValue.text
        if (cursor in 0..text.length) {
            val pre = text.substring(0, cursor)
            val lines = pre.split("\n")
            val ln = lines.size
            val col = lines.lastOrNull()?.length ?: 0
            viewModel.cursorPositionText.value = "Ln $ln, Col ${col + 1}"
        }
    }

    // Material 3 Dynamic Custom Palette (Professional Polish dark theme is dominant)
    val appColorScheme = if (isDark) {
        darkColorScheme(
            primary = Color(0xFFD0BCFF),
            background = Color(0xFF1C1B1F),
            surface = Color(0xFF25232A),
            surfaceVariant = Color(0xFF49454F),
            onPrimary = Color(0xFF381E72),
            onBackground = Color(0xFFE6E1E5),
            onSurface = Color(0xFFE6E1E5),
            onSurfaceVariant = Color(0xFFCAC4D0),
            outline = Color(0xFF49454F)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color(0xFFFEF7FF),
            surface = Color(0xFFFEF7FF),
            surfaceVariant = Color(0xFFE7E0EC),
            onPrimary = Color.White,
            onBackground = Color(0xFF1D1B20),
            onSurface = Color(0xFF1D1B20),
            onSurfaceVariant = Color(0xFF49454F),
            outline = Color(0xFF79747E)
        )
    }

    MaterialTheme(
        colorScheme = appColorScheme,
        typography = Typography()
    ) {
        val currentClipboardManager = LocalClipboardManager.current
        val windowInfo = LocalWindowInfo.current
        val safeClipboardManager = remember(currentClipboardManager, windowInfo) {
            object : androidx.compose.ui.platform.ClipboardManager {
                override fun getText(): androidx.compose.ui.text.AnnotatedString? {
                    if (!windowInfo.isWindowFocused) return null
                    return try {
                        currentClipboardManager.getText()
                    } catch (e: Exception) {
                        null
                    }
                }

                override fun setText(annotatedString: androidx.compose.ui.text.AnnotatedString) {
                    if (!windowInfo.isWindowFocused) return
                    try {
                        currentClipboardManager.setText(annotatedString)
                    } catch (e: Exception) {
                        // Do nothing
                    }
                }
            }
        }
        CompositionLocalProvider(LocalClipboardManager provides safeClipboardManager) {
            Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Logo icon { }
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFD0BCFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "{ }",
                                        color = Color(0xFF381E72),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Code Editor Pro",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE6E1E5)
                                    )
                                    Text(
                                        text = activeProject?.name ?: "project_stellar_v2",
                                        fontSize = 10.sp,
                                        color = Color(0xFFCAC4D0).copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = { showProjectSelectDialog = true },
                                modifier = Modifier.testTag("project_menu_button")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = "Projects", tint = Color(0xFFD0BCFF))
                            }
                        },
                        actions = {
                            if (activeTabIndex != -1) {
                                IconButton(onClick = {
                                    viewModel.saveActiveFileExplicitly {
                                        Toast.makeText(currentContext, "File saved successfully", Toast.LENGTH_SHORT).show()
                                    }
                                    logAction()
                                }) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Save and Run Code", tint = Color(0xFFD0BCFF))
                                }
                            }
                            IconButton(onClick = {
                                showSettingsDialog = true
                            }) {
                                Icon(Icons.Default.Settings, contentDescription = "Editor Settings", tint = Color(0xFFE6E1E5))
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color(0xFF25232A)
                        )
                    )
                    HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp)
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1C1B1F),
                    tonalElevation = 0.dp
                ) {
                    val items = listOf(
                        Triple(0, "Files", R.drawable.ic_folder),
                        Triple(1, "Editor", Icons.Default.Edit),
                        Triple(2, "Preview", Icons.Default.PlayArrow),
                        Triple(3, "Copilot", Icons.Default.Face),
                        Triple(4, "Git", Icons.Default.Build)
                    )

                    items.forEach { (index, label, icon) ->
                        val isSelected = activePanel == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { 
                                viewModel.setActivePanel(index)
                                logAction()
                            },
                            icon = {
                                when (icon) {
                                    is androidx.compose.ui.graphics.vector.ImageVector -> Icon(icon, contentDescription = label)
                                    is Int -> Icon(painterResource(id = icon), contentDescription = label, modifier = Modifier.size(24.dp))
                                    else -> {}
                                }
                            },
                            label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF1D192B),
                                selectedTextColor = Color(0xFFE6E1E5),
                                indicatorColor = Color(0xFFE8DEF8),
                                unselectedIconColor = Color(0xFFE6E1E5).copy(alpha = 0.6f),
                                unselectedTextColor = Color(0xFFE6E1E5).copy(alpha = 0.6f)
                            )
                        )
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
                // Main Content Switching using animated visibility
                AnimatedContent(
                    targetState = activePanel,
                    transitionSpec = {
                        fadeIn(animationSpec = spring()) togetherWith fadeOut(animationSpec = spring())
                    },
                    label = "main_panel_animation"
                ) { panelIndex ->
                    when (panelIndex) {
                        0 -> ExplorerTab(
                            viewModel = viewModel,
                            onCreateFileClick = { showNewFileDialog = true },
                            onCreateFolderClick = { showNewFolderDialog = true },
                            onNewGitBranchClick = { showGitBranchDialog = true }
                        )
                        1 -> EditorTab(
                            viewModel = viewModel,
                            textFieldValue = editorTextFieldValue,
                            onValueChange = {
                                editorTextFieldValue = it
                                viewModel.updateActiveFileContent(it.text)
                            }
                        )
                        2 -> PreviewTab(viewModel = viewModel)
                        3 -> AiTab(viewModel = viewModel)
                        4 -> TerminalTab(viewModel = viewModel)
                    }
                }
            }
        }

        // --- All Dialog Boxes ---

        if (showSettingsDialog) {
            SettingsDialog(
                viewModel = viewModel,
                accentColors = accentColors,
                onDismiss = { showSettingsDialog = false }
            )
        }

        if (showNewFileDialog) {
            InputDialog(
                title = "Create New File",
                placeholder = "index.html, style.css etc.",
                onConfirm = { name ->
                    viewModel.createNewFile(name)
                    showNewFileDialog = false
                    logAction()
                },
                onDismiss = { showNewFileDialog = false }
            )
        }

        if (showNewFolderDialog) {
            InputDialog(
                title = "Create New Folder",
                placeholder = "css, assets, js etc.",
                onConfirm = { name ->
                    viewModel.createNewFolder(name)
                    showNewFolderDialog = false
                    logAction()
                },
                onDismiss = { showNewFolderDialog = false }
            )
        }

        if (showNewProjectDialog) {
            InputDialog(
                title = "Create New Project",
                placeholder = "My Web App",
                onConfirm = { name ->
                    viewModel.createProject(name)
                    showNewProjectDialog = false
                    logAction()
                },
                onDismiss = { showNewProjectDialog = false }
            )
        }

        if (showGitBranchDialog) {
            InputDialog(
                title = "Create New Git Branch",
                placeholder = "development, feature-login",
                onConfirm = { name ->
                    viewModel.createGitBranch(name)
                    showGitBranchDialog = false
                    logAction()
                },
                onDismiss = { showGitBranchDialog = false }
            )
        }

        if (showProjectSelectDialog) {
            ProjectSelectDialog(
                projects = projects,
                activeProject = activeProject,
                onSelect = {
                    showProjectSelectDialog = false
                    viewModel.selectProject(it)
                    logAction()
                },
                onDelete = { viewModel.deleteProject(it) },
                onFavorite = { viewModel.toggleFavorite(it) },
                onNewProject = {
                    showNewProjectDialog = true
                    showProjectSelectDialog = false
                },
                onDismiss = { showProjectSelectDialog = false }
            )
        }
        }
    }
}

// ==========================================
// PANEL 0: EXPLORER TAB
// ==========================================
@Composable
fun ExplorerTab(
    viewModel: EditorViewModel,
    onCreateFileClick: () -> Unit,
    onCreateFolderClick: () -> Unit,
    onNewGitBranchClick: () -> Unit
) {
    val currentContext = LocalContext.current
    val fileTree by viewModel.fileTree.collectAsStateWithLifecycle()
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    // Git integration states
    val gitBranches by viewModel.gitBranches.collectAsStateWithLifecycle()
    val gitCommits by viewModel.gitCommits.collectAsStateWithLifecycle()
    val currentBranch by viewModel.currentBranchName.collectAsStateWithLifecycle()

    var activeSubSection by remember { mutableStateOf(0) } // 0: Files, 1: Git Repo Status
    var gitCommitMessage by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Toggle Panel between File Tree and Local Git
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            Button(
                onClick = { activeSubSection = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubSection == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (activeSubSection == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(id = R.drawable.ic_folder), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Workspace", fontSize = 12.sp)
                }
            }
            Button(
                onClick = { activeSubSection = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeSubSection == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (activeSubSection == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(36.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Git Repository", fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (activeSubSection == 0) {
            // --- WORKSPACE FILE SYSTEM ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Project Files",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row {
                    IconButton(onClick = onCreateFileClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add File", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onCreateFolderClick) {
                        Icon(painterResource(id = R.drawable.ic_folder), contentDescription = "Add Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.refreshFileTree() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }

            // Search files
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search files & code...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                ),
                textStyle = TextStyle(fontSize = 13.sp)
            )

            Spacer(Modifier.height(12.dp))

            if (searchQuery.isNotEmpty()) {
                // Render Search Results
                Text("Search results for '$searchQuery':", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                if (searchResults.isEmpty()) {
                    Text("No matching files found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(searchResults) { path ->
                            Card(
                                onClick = { viewModel.openFile(path); viewModel.setActivePanel(1) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text(path, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            } else {
                // Render Tree Nodes
                Box(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    fileTree?.let { root ->
                        FileTreeNodeList(root, viewModel, paddingStart = 0)
                    } ?: Text(
                        text = "Loading directory tree...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 14.sp
                    )
                }
            }

            // ZIP Archive Importer / Exporter Footer
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Archive Projects", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(".zip importer/exporter support", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Row {
                        Button(
                            onClick = {
                                // Simulate zip import of standard demo
                                Toast.makeText(currentContext, "Importing project archive...", Toast.LENGTH_SHORT).show()
                                activeProject?.let {
                                    // Make a mock trigger for import/export zip
                                    viewModel.createProject("Imported Zip Project")
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Import", fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                val dest = File(currentContext.cacheDir, "${activeProject?.name ?: "project"}.zip")
                                viewModel.exportActiveProjectToZip(dest) { ok ->
                                    if (ok) {
                                        Toast.makeText(currentContext, "Exported successfully to: ${dest.name}", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(currentContext, "Export failure", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Export", fontSize = 11.sp)
                        }
                    }
                }
            }

        } else {
            // --- LOCAL GIT PANEL ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Current Branch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("⌥ $currentBranch", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Button(
                    onClick = onNewGitBranchClick,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Text("New Branch", fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Commit Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Commit Sandbox Changes", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = gitCommitMessage,
                        onValueChange = { gitCommitMessage = it },
                        placeholder = { Text("Describe changes...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().height(72.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = TextStyle(fontSize = 13.sp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (gitCommitMessage.trim().isNotEmpty()) {
                                viewModel.commitGitChanges(gitCommitMessage)
                                gitCommitMessage = ""
                                Toast.makeText(currentContext, "Changes committed successfully!", Toast.LENGTH_SHORT).show()
                                logAction()
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Commit Snapshot", fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text("Git History", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(gitCommits) { commit ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(commit.message, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Author: ${commit.author}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                Spacer(Modifier.width(8.dp))
                                Text("•", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                Spacer(Modifier.width(8.dp))
                                Text("Branch: ${commit.branchName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FileTreeNodeList(node: FileNode, viewModel: EditorViewModel, paddingStart: Int) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(start = paddingStart.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (node.isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        viewModel.openFile(node.path)
                        viewModel.setActivePanel(1) // switch to editor panel
                    }
                }
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (node.isDirectory) {
                    if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.PlayArrow
                } else {
                    Icons.Default.Edit
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (node.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = node.name,
                fontSize = 14.sp,
                fontFamily = if (node.isDirectory) FontFamily.Default else FontFamily.Monospace,
                fontWeight = if (node.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.weight(1f))

            // Actions for file/folder (Rename, Delete)
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "File Actions", modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            viewModel.deleteFileOrFolder(node.path)
                            showMenu = false
                        }
                    )
                }
            }
        }

        if (node.isDirectory && isExpanded) {
            node.children.forEach { child ->
                FileTreeNodeList(child, viewModel, paddingStart + 16)
            }
        }
    }
}

// ==========================================
// PANEL 1: EDITOR TAB
// ==========================================
@Composable
fun EditorTab(
    viewModel: EditorViewModel,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit
) {
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()

    val fontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
    val isWordWrap by viewModel.isWordWrapEnabled.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()

    // Find and replace
    val isFindReplaceActive by viewModel.isFindReplaceActive.collectAsStateWithLifecycle()
    val findStr by viewModel.findQuery.collectAsStateWithLifecycle()
    val replaceStr by viewModel.replaceQuery.collectAsStateWithLifecycle()

    // Go to line
    val isGoToLineActive by viewModel.isGoToLineActive.collectAsStateWithLifecycle()
    val goToLineStr by viewModel.goToLineTarget.collectAsStateWithLifecycle()

    val cursorText by viewModel.cursorPositionText.collectAsStateWithLifecycle()

    if (activeTabIndex == -1 || openTabs.isEmpty()) {
        // Visual Empty State with Hero Banner
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text("No File Open", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Expand the Workspace folder and click a file inside the explorer to open it here for editing.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.widthIn(max = 280.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.setActivePanel(0) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Go to Workspace", fontSize = 13.sp)
                }
            }
        }
    } else {
        val activePath = openTabs[activeTabIndex]
        val ext = activePath.substringAfterLast('.', "")

        Column(modifier = Modifier.fillMaxSize()) {
            // Horizontal scrollable Tabs
            ScrollableTabRow(
                selectedTabIndex = activeTabIndex,
                containerColor = Color(0xFF25232A),
                edgePadding = 8.dp,
                divider = { HorizontalDivider(color = Color(0xFF49454F), thickness = 1.dp) },
                indicator = {}
            ) {
                openTabs.forEachIndexed { index, path ->
                    val isActive = index == activeTabIndex
                    Card(
                        onClick = { viewModel.selectTab(index) },
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFF1C1B1F) else Color.Transparent
                        ),
                        border = if (isActive) BorderStroke(1.dp, Color(0xFF49454F)) else null,
                        modifier = Modifier
                            .padding(top = 4.dp, start = 2.dp, end = 2.dp)
                            .height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = path.substringAfterLast('/'),
                                fontSize = 11.sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { viewModel.closeTab(index) },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Tab",
                                    modifier = Modifier.size(10.dp),
                                    tint = if (isActive) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }

            // Top Editor Quick Actions Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF25232A))
                    .padding(vertical = 4.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.undo() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Refresh, contentDescription = "Undo", modifier = Modifier.size(18.dp), tint = Color(0xFFE6E1E5))
                    }
                    IconButton(onClick = { viewModel.redo() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Redo", modifier = Modifier.size(18.dp), tint = Color(0xFFE6E1E5))
                    }
                    Spacer(Modifier.width(8.dp))
                    // Find and replace toggle
                    IconButton(onClick = { viewModel.isFindReplaceActive.value = !isFindReplaceActive }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Search, contentDescription = "Find & Replace", modifier = Modifier.size(18.dp), tint = if (isFindReplaceActive) Color(0xFFD0BCFF) else Color(0xFFE6E1E5))
                    }
                    // Word wrap toggle
                    IconButton(onClick = { viewModel.isWordWrapEnabled.value = !isWordWrap }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = "Word Wrap", modifier = Modifier.size(18.dp), tint = if (isWordWrap) Color(0xFFD0BCFF) else Color(0xFFE6E1E5))
                    }
                }

                // AI Assist Action Button inside Toolbar
                Button(
                    onClick = {
                        viewModel.performQuickAiAction("fix") { output ->
                            onValueChange(TextFieldValue(output))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF381E72),
                        contentColor = Color(0xFFD0BCFF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI Copilot Fix", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Find and Replace Box Panel
            if (isFindReplaceActive) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF25232A)),
                    border = BorderStroke(1.dp, Color(0xFF49454F))
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = findStr,
                                onValueChange = { viewModel.findQuery.value = it },
                                textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFFE6E1E5)),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1B1F), RoundedCornerShape(4.dp)).padding(6.dp),
                                decorationBox = { inner -> if (findStr.isEmpty()) Text("Find text...", fontSize = 11.sp, color = Color.Gray) else inner() }
                            )
                            Spacer(Modifier.height(4.dp))
                            BasicTextField(
                                value = replaceStr,
                                onValueChange = { viewModel.replaceQuery.value = it },
                                textStyle = TextStyle(fontSize = 12.sp, color = Color(0xFFE6E1E5)),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF1C1B1F), RoundedCornerShape(4.dp)).padding(6.dp),
                                decorationBox = { inner -> if (replaceStr.isEmpty()) Text("Replace with...", fontSize = 11.sp, color = Color.Gray) else inner() }
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Button(
                                onClick = { viewModel.executeReplaceAll() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72), contentColor = Color(0xFFD0BCFF)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("All", fontSize = 10.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            OutlinedButton(
                                onClick = { viewModel.isFindReplaceActive.value = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE6E1E5)),
                                border = BorderStroke(1.dp, Color(0xFF49454F)),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Hide", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Editor Core: Text Editor viewport with Line Numbers & Syntax Highlighting
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF161519))
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Vertical Line Numbers column (Calculate on the fly)
                    val lines = textFieldValue.text.split("\n")
                    val totalLines = maxOf(1, lines.size)

                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF1C1B1F))
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..totalLines) {
                            Text(
                                text = "$i",
                                color = Color(0xFF938F99),
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.height(with(LocalDensity.current) { (fontSize + 6).sp.toDp() })
                            )
                        }
                    }

                    // A thin vertical divider representing border-[#49454F]
                    Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF49454F)))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = onValueChange,
                            modifier = Modifier.fillMaxWidth().testTag("code_editor_text_field"),
                            textStyle = TextStyle(
                                color = Color(0xFFE6E1E5),
                                fontSize = fontSize.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = (fontSize + 6).sp
                            ),
                            cursorBrush = SolidColor(Color(0xFFD0BCFF)),
                            visualTransformation = CodeVisualTransformation(ext, isDark)
                        )
                    }
                }

                // Floating AI Suggestion Tooltip overlay at the bottom of the editor viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF381E72)),
                        border = BorderStroke(1.dp, Color(0xFF49454F)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("✦", color = Color(0xFFD0BCFF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Explain or optimize this code?", color = Color(0xFFD0BCFF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Button(
                                onClick = {
                                    viewModel.chatInput.value = "Please analyze and explain the active code file in detail."
                                    viewModel.setActivePanel(3) // Switch to AI Chat Panel
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFEADDFF),
                                    contentColor = Color(0xFF21005D)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("ASK AI", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Mobile Keyboard Accessory Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF25232A))
                    .border(width = 1.dp, color = Color(0xFF49454F))
                    .padding(vertical = 4.dp, horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val keys = listOf("TAB", "/", "<", ">", "{", "}", "[", "]")
                keys.forEach { key ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF49454F))
                            .clickable {
                                val toInsert = when (key) {
                                    "TAB" -> "    "
                                    else -> key
                                }
                                val text = textFieldValue.text
                                val start = textFieldValue.selection.start
                                val end = textFieldValue.selection.end
                                val newText = text.substring(0, start) + toInsert + text.substring(end)
                                val newSelectionStart = start + toInsert.length
                                onValueChange(
                                    TextFieldValue(
                                        text = newText,
                                        selection = androidx.compose.ui.text.TextRange(newSelectionStart)
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = key,
                            color = Color(0xFFE6E1E5),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Status Bar Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFD0BCFF))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = cursorText,
                        color = Color(0xFF381E72),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "UTF-8",
                        color = Color(0xFF381E72),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B5E20)) // Dark green
                        )
                        Text(
                            text = "Master",
                            color = Color(0xFF381E72),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = ext.uppercase(),
                        color = Color(0xFF381E72),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ==========================================
// PANEL 2: PREVIEW TAB
// ==========================================
@Composable
fun PreviewTab(viewModel: EditorViewModel) {
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val openTabs by viewModel.openTabs.collectAsStateWithLifecycle()
    val activeTabIndex by viewModel.activeTabIndex.collectAsStateWithLifecycle()

    var activeViewType by remember { mutableStateOf(0) } // 0: HTML Browser WebView, 1: Raw formatted JSON or Code markdown

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Output Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Row {
                IconButton(onClick = { /* trigger refresh */ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload Preview")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (activeProject == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Select a project first to load live preview.", fontSize = 13.sp, color = Color.Gray)
            }
        } else {
            // HTML Webview Renderer
            Card(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                colors = CardDefaults.cardColors(containerColor = Color.White) // Webview canvas background
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            settings.allowContentAccess = true
                        }
                    },
                    update = { webView ->
                        // Load HTML relative layout
                        val currentProjectFile = File(activeProject!!.rootPath, "index.html")
                        if (currentProjectFile.exists()) {
                            val content = currentProjectFile.readText()
                            webView.loadDataWithBaseURL("file://${activeProject!!.rootPath}/", content, "text/html", "UTF-8", null)
                        } else {
                            val defaultContent = """
                                <html>
                                <body style="display:flex;justify-content:center;align-items:center;height:100vh;background:#222;color:#fff;font-family:sans-serif;">
                                    <h3>index.html not found. Click 'Workspace' and add index.html!</h3>
                                </body>
                                </html>
                            """.trimIndent()
                            webView.loadData(defaultContent, "text/html", "UTF-8")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ==========================================
// PANEL 3: GEMINI AI CHAT ASSISTANT
// ==========================================
@Composable
fun AiTab(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val input by viewModel.chatInput.collectAsStateWithLifecycle()
    val isLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("AI Copilot Code Assistant", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { viewModel.clearChat() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Quick Preset Action Chips for AI Suggestions
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.chatInput.value = "Optimize the active file code for lower computation and clean layout style." },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Optimize Code", fontSize = 11.sp)
            }

            Button(
                onClick = { viewModel.chatInput.value = "Explain the active open code line-by-line explaining how the state executes." },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("Explain Logic", fontSize = 11.sp)
            }

            Button(
                onClick = { viewModel.chatInput.value = "Create a modern, sleek registration CSS styling stylesheet." },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text("CSS Template", fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // Chat Conversation Logs
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = false
        ) {
            items(messages) { message ->
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Card(
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isUser) 16.dp else 4.dp,
                            bottomEnd = if (message.isUser) 4.dp else 16.dp
                        ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Text(
                            text = message.text,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("AI Copilot is analyzing...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Chat input text bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { viewModel.chatInput.value = it },
                placeholder = { Text("Ask AI Copilot...", fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                textStyle = TextStyle(fontSize = 13.sp),
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            FloatingActionButton(
                onClick = { 
                    viewModel.sendChatMessage()
                },
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send Message", tint = Color.White)
            }
        }
    }
}

// ==========================================
// PANEL 4: SANDBOX LOCAL TERMINAL
// ==========================================
@Composable
fun TerminalTab(viewModel: EditorViewModel) {
    val context = LocalContext.current
    val terminalInput by viewModel.terminalInput.collectAsStateWithLifecycle()
    val terminalLogs by viewModel.terminalLogs.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Sandbox Terminal", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onClick = { viewModel.clearTerminal() }) {
                Icon(Icons.Default.Delete, contentDescription = "Clear TerminalLogs", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(Modifier.height(8.dp))

        // Black Terminal Box
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1015))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(terminalLogs) { log ->
                        Text(
                            text = log,
                            color = if (log.startsWith("$")) Color(0xFF569CD6) else if (log.startsWith("Error")) Color(0xFFF44336) else Color(0xFFABB2BF),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Interactive terminal prompt line
                Divider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "astro$ ",
                        color = Color(0xFFFF8E53),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    BasicTextField(
                        value = terminalInput,
                        onValueChange = { viewModel.terminalInput.value = it },
                        modifier = Modifier.weight(1f).testTag("terminal_input_text"),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        cursorBrush = SolidColor(Color.White),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (terminalInput.isEmpty()) {
                                Text("Type command...", color = Color.Gray.copy(alpha = 0.5f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            } else {
                                inner()
                            }
                        }
                    )
                    IconButton(
                        onClick = {
                            viewModel.submitTerminalCommand()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// ==========================================
// PRESET COMPOSABLE DIALOG WINDOWS
// ==========================================

@Composable
fun SettingsDialog(
    viewModel: EditorViewModel,
    accentColors: List<Color>,
    onDismiss: () -> Unit
) {
    val isDark by viewModel.isDarkTheme.collectAsStateWithLifecycle()
    val fontSize by viewModel.editorFontSize.collectAsStateWithLifecycle()
    val isWordWrap by viewModel.isWordWrapEnabled.collectAsStateWithLifecycle()
    val isAutoSave by viewModel.isAutoSaveEnabled.collectAsStateWithLifecycle()
    val accentColorIndex by viewModel.accentColorIndex.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Editor Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))

                // Dark/Light Theme Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Mode Theme", fontSize = 14.sp)
                    Switch(checked = isDark, onCheckedChange = { viewModel.isDarkTheme.value = it })
                }

                // Accent color circle dots Row
                Spacer(Modifier.height(8.dp))
                Text("Primary Accent Color", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    accentColors.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (accentColorIndex == index) 3.dp else 0.dp,
                                    color = if (accentColorIndex == index) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.accentColorIndex.value = index }
                        )
                    }
                }

                // Font size slider Row
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Editor Font Size", fontSize = 14.sp)
                    Text("${fontSize}sp", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = fontSize.toFloat(),
                    onValueChange = { viewModel.editorFontSize.value = it.toInt() },
                    valueRange = 10f..24f
                )

                // Auto Save Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto Save Changes", fontSize = 14.sp)
                    Switch(checked = isAutoSave, onCheckedChange = { viewModel.isAutoSaveEnabled.value = it })
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun InputDialog(
    title: String,
    placeholder: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = { Text(placeholder, fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.align(Alignment.End)) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (textValue.trim().isNotEmpty()) {
                                onConfirm(textValue)
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectSelectDialog(
    projects: List<ProjectEntity>,
    activeProject: ProjectEntity?,
    onSelect: (ProjectEntity) -> Unit,
    onDelete: (ProjectEntity) -> Unit,
    onFavorite: (ProjectEntity) -> Unit,
    onNewProject: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Choose Project", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = onNewProject,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("New", fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(projects) { project ->
                        val isCurrent = project.id == activeProject?.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { onSelect(project) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (project.isFavorite) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = Color.Red,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    painterResource(id = R.drawable.ic_folder),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(project.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Last modified: Just now", fontSize = 10.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { onFavorite(project) }) {
                                Icon(
                                    if (project.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (!isCurrent) {
                                IconButton(onClick = { onDelete(project) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Project", modifier = Modifier.size(16.dp), tint = Color.Red)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(10.dp)) {
                    Text("Close")
                }
            }
        }
    }
}
