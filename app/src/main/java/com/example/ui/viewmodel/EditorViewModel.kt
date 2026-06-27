package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ProjectEntity
import com.example.data.database.GitCommitEntity
import com.example.data.database.GitBranchEntity
import com.example.data.api.GeminiApiClient
import com.example.data.repository.FileNode
import com.example.data.repository.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ProjectRepository(application, db.projectDao())

    // --- State Observables ---
    val projects: StateFlow<List<ProjectEntity>> = repository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeProject: StateFlow<ProjectEntity?> = _activeProject.asStateFlow()

    private val _fileTree = MutableStateFlow<FileNode?>(null)
    val fileTree: StateFlow<FileNode?> = _fileTree.asStateFlow()

    // Editor Tabs: List of relative file paths currently open
    private val _openTabs = MutableStateFlow<List<String>>(emptyList())
    val openTabs: StateFlow<List<String>> = _openTabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(-1)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    // Active File Content
    private val _activeFileContent = MutableStateFlow("")
    val activeFileContent: StateFlow<String> = _activeFileContent.asStateFlow()

    // Undo/Redo History Stacks: Map of relativePath -> List of text states
    private val undoHistory = mutableMapOf<String, MutableList<String>>()
    private val redoHistory = mutableMapOf<String, MutableList<String>>()

    // Settings
    val isDarkTheme = MutableStateFlow(true)
    val editorFontSize = MutableStateFlow(14)
    val isWordWrapEnabled = MutableStateFlow(true)
    val isAutoSaveEnabled = MutableStateFlow(true)
    val tabSize = MutableStateFlow(4)
    val accentColorIndex = MutableStateFlow(0) // 0: Orange/Red, 1: Teal, 2: Blue, 3: Purple

    // UI Panel / Navigation
    // Panel type: 0 = Explorer, 1 = Terminal, 2 = AI Chat, 3 = Git, 4 = Preview, 5 = Settings
    private val _activePanel = MutableStateFlow(0)
    val activePanel: StateFlow<Int> = _activePanel.asStateFlow()

    // Find and Replace
    val findQuery = MutableStateFlow("")
    val replaceQuery = MutableStateFlow("")
    val isFindReplaceActive = MutableStateFlow(false)

    // Go to line
    val isGoToLineActive = MutableStateFlow(false)
    val goToLineTarget = MutableStateFlow("")

    // Cursor position display
    val cursorPositionText = MutableStateFlow("Ln 1, Col 1")

    // Terminal History
    private val _terminalLogs = MutableStateFlow<List<String>>(listOf("AstroEdit Terminal v1.0.0 Ready.", "Type 'help' for available local commands.", ""))
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()
    val terminalInput = MutableStateFlow("")

    // AI Chat History
    data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(ChatMessage("Hello! I am your Gemini AI coding companion. Ask me to complete code, optimize, explain, or generate new templates!", false))
    )
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()
    val chatInput = MutableStateFlow("")
    val isAiLoading = MutableStateFlow(false)

    // Git Status / Branches / History
    private val _gitBranches = MutableStateFlow<List<GitBranchEntity>>(emptyList())
    val gitBranches: StateFlow<List<GitBranchEntity>> = _gitBranches.asStateFlow()

    private val _gitCommits = MutableStateFlow<List<GitCommitEntity>>(emptyList())
    val gitCommits: StateFlow<List<GitCommitEntity>> = _gitCommits.asStateFlow()

    private val _currentBranchName = MutableStateFlow("main")
    val currentBranchName: StateFlow<String> = _currentBranchName.asStateFlow()

    init {
        // Auto-select first project if available or create a default demo project on first launch
        viewModelScope.launch {
            projects.collectLatest { list ->
                if (list.isEmpty()) {
                    createProject("My Starter Project")
                } else if (_activeProject.value == null) {
                    selectProject(list.first())
                }
            }
        }
    }

    // --- Action Methods ---

    fun setActivePanel(panel: Int) {
        _activePanel.value = panel
    }

    fun selectProject(project: ProjectEntity) {
        _activeProject.value = project
        _openTabs.value = emptyList()
        _activeTabIndex.value = -1
        _activeFileContent.value = ""
        undoHistory.clear()
        redoHistory.clear()
        refreshFileTree()
        loadGitMetadata(project.id)
    }

    fun createProject(name: String) {
        viewModelScope.launch {
            val project = repository.createProject(name)
            selectProject(project)
        }
    }

    fun deleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.deleteProject(project)
            if (_activeProject.value?.id == project.id) {
                _activeProject.value = null
            }
        }
    }

    fun toggleFavorite(project: ProjectEntity) {
        viewModelScope.launch {
            repository.updateProject(project.copy(isFavorite = !project.isFavorite))
        }
    }

    fun refreshFileTree() {
        val current = _activeProject.value ?: return
        viewModelScope.launch {
            val tree = repository.getFileTree(current)
            _fileTree.value = tree
        }
    }

    // --- Tab / File Actions ---

    fun openFile(relativePath: String) {
        val currentProject = _activeProject.value ?: return
        val currentTabs = _openTabs.value.toMutableList()

        viewModelScope.launch {
            if (!currentTabs.contains(relativePath)) {
                currentTabs.add(relativePath)
                _openTabs.value = currentTabs
            }
            val index = currentTabs.indexOf(relativePath)
            _activeTabIndex.value = index

            val content = repository.readFile(currentProject, relativePath)
            _activeFileContent.value = content

            // Save recent file info
            repository.saveRecentFile(currentProject.id, relativePath)
        }
    }

    fun closeTab(index: Int) {
        val currentTabs = _openTabs.value.toMutableList()
        if (index in currentTabs.indices) {
            val path = currentTabs[index]
            currentTabs.removeAt(index)
            _openTabs.value = currentTabs

            undoHistory.remove(path)
            redoHistory.remove(path)

            if (currentTabs.isEmpty()) {
                _activeTabIndex.value = -1
                _activeFileContent.value = ""
            } else {
                val newIndex = if (index >= currentTabs.size) currentTabs.size - 1 else index
                _activeTabIndex.value = newIndex
                openFile(currentTabs[newIndex])
            }
        }
    }

    fun selectTab(index: Int) {
        if (index in _openTabs.value.indices) {
            _activeTabIndex.value = index
            openFile(_openTabs.value[index])
        }
    }

    fun updateActiveFileContent(newContent: String) {
        val activePath = getActiveFilePath() ?: return
        val oldContent = _activeFileContent.value

        _activeFileContent.value = newContent

        // Add to Undo history stack if there's a significant boundary
        if (newContent != oldContent) {
            val pathUndo = undoHistory.getOrPut(activePath) { mutableListOf() }
            if (pathUndo.isEmpty() || shouldRecordState(oldContent, newContent)) {
                if (pathUndo.size > 50) pathUndo.removeAt(0) // limit size
                pathUndo.add(oldContent)
                redoHistory.remove(activePath) // clear redo on new change
            }

            // Auto save if enabled
            if (isAutoSaveEnabled.value) {
                saveActiveFileSilently()
            }
        }
    }

    private fun shouldRecordState(old: String, new: String): Boolean {
        // Record state on space, newline, or a significant length difference
        if (old.length != new.length && (new.endsWith(" ") || new.endsWith("\n") || Math.abs(old.length - new.length) > 8)) {
            return true
        }
        return false
    }

    fun undo() {
        val activePath = getActiveFilePath() ?: return
        val pathUndo = undoHistory[activePath] ?: return
        if (pathUndo.isNotEmpty()) {
            val previousState = pathUndo.removeAt(pathUndo.size - 1)

            val pathRedo = redoHistory.getOrPut(activePath) { mutableListOf() }
            pathRedo.add(_activeFileContent.value)

            _activeFileContent.value = previousState
            saveActiveFileSilently()
        }
    }

    fun redo() {
        val activePath = getActiveFilePath() ?: return
        val pathRedo = redoHistory[activePath] ?: return
        if (pathRedo.isNotEmpty()) {
            val nextState = pathRedo.removeAt(pathRedo.size - 1)

            val pathUndo = undoHistory.getOrPut(activePath) { mutableListOf() }
            pathUndo.add(_activeFileContent.value)

            _activeFileContent.value = nextState
            saveActiveFileSilently()
        }
    }

    fun getActiveFilePath(): String? {
        val tabs = _openTabs.value
        val index = _activeTabIndex.value
        return if (index in tabs.indices) tabs[index] else null
    }

    fun saveActiveFileSilently() {
        val currentProject = _activeProject.value ?: return
        val activePath = getActiveFilePath() ?: return
        val content = _activeFileContent.value
        viewModelScope.launch {
            repository.writeFile(currentProject, activePath, content)
        }
    }

    fun saveActiveFileExplicitly(onSuccess: () -> Unit) {
        val currentProject = _activeProject.value ?: return
        val activePath = getActiveFilePath() ?: return
        val content = _activeFileContent.value
        viewModelScope.launch {
            repository.writeFile(currentProject, activePath, content)
            withContext(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun createNewFile(name: String) {
        val currentProject = _activeProject.value ?: return
        viewModelScope.launch {
            val success = repository.createNewFile(currentProject, name)
            if (success) {
                refreshFileTree()
                openFile(name)
            }
        }
    }

    fun createNewFolder(name: String) {
        val currentProject = _activeProject.value ?: return
        viewModelScope.launch {
            val success = repository.createNewFolder(currentProject, name)
            if (success) {
                refreshFileTree()
            }
        }
    }

    fun deleteFileOrFolder(relativePath: String) {
        val currentProject = _activeProject.value ?: return
        viewModelScope.launch {
            val success = repository.deleteFileOrFolder(currentProject, relativePath)
            if (success) {
                // If it was open, close its tab
                val openIndex = _openTabs.value.indexOf(relativePath)
                if (openIndex != -1) {
                    closeTab(openIndex)
                }
                refreshFileTree()
            }
        }
    }

    fun renameFileOrFolder(oldPath: String, newPath: String) {
        val currentProject = _activeProject.value ?: return
        viewModelScope.launch {
            val success = repository.renameFileOrFolder(currentProject, oldPath, newPath)
            if (success) {
                val openIndex = _openTabs.value.indexOf(oldPath)
                if (openIndex != -1) {
                    val tabs = _openTabs.value.toMutableList()
                    tabs[openIndex] = newPath
                    _openTabs.value = tabs
                }
                refreshFileTree()
            }
        }
    }

    // --- Zip Archive Actions ---

    fun importZipFile(name: String, zipFile: File) {
        viewModelScope.launch {
            val imported = repository.importProjectFromZip(name, zipFile)
            selectProject(imported)
        }
    }

    fun exportActiveProjectToZip(destination: File, onComplete: (Boolean) -> Unit) {
        val active = _activeProject.value ?: return
        viewModelScope.launch {
            val success = repository.exportProjectToZip(active, destination)
            withContext(Dispatchers.Main) {
                onComplete(success)
            }
        }
    }

    // --- Search Files ---

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<String>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        val active = _activeProject.value ?: return
        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val results = mutableListOf<String>()
            val root = File(active.rootPath)
            if (root.exists()) {
                root.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        val relPath = file.relativeTo(root).path
                        if (relPath.contains(query, ignoreCase = true)) {
                            results.add(relPath)
                        } else {
                            try {
                                val text = file.readText()
                                if (text.contains(query, ignoreCase = true)) {
                                    results.add(relPath)
                                }
                            } catch (e: Exception) {
                                // ignore binary or unreadable files
                            }
                        }
                    }
                }
            }
            _searchResults.value = results
        }
    }

    // --- Find and Replace inside Active File ---

    fun executeReplaceAll() {
        val findStr = findQuery.value
        val replaceStr = replaceQuery.value
        if (findStr.isEmpty()) return

        val currentContent = _activeFileContent.value
        val newContent = currentContent.replace(findStr, replaceStr)
        updateActiveFileContent(newContent)
    }

    // --- Local Terminal Execution ---

    fun submitTerminalCommand() {
        val active = _activeProject.value ?: return
        val cmd = terminalInput.value.trim()
        if (cmd.isEmpty()) return

        val logs = _terminalLogs.value.toMutableList()
        logs.add("$ ${_currentBranchName.value} % $cmd")
        terminalInput.value = ""

        viewModelScope.launch {
            val output = repository.executeTerminalCommand(active, cmd)
            withContext(Dispatchers.Main) {
                if (output == "CLEAR_TERMINAL") {
                    _terminalLogs.value = listOf("")
                } else {
                    logs.addAll(output.split("\n"))
                    logs.add("") // trailing gap
                    _terminalLogs.value = logs
                }
            }
        }
    }

    fun clearTerminal() {
        _terminalLogs.value = listOf("Terminal Cleared.", "")
    }

    // --- Git Actions ---

    private fun loadGitMetadata(projectId: Int) {
        viewModelScope.launch {
            repository.getBranches(projectId).collectLatest { list ->
                _gitBranches.value = list
                val current = list.firstOrNull { it.isCurrent }
                if (current != null) {
                    _currentBranchName.value = current.name
                }
            }
        }
        viewModelScope.launch {
            repository.getCommits(projectId).collectLatest { list ->
                _gitCommits.value = list
            }
        }
    }

    fun commitGitChanges(message: String) {
        val active = _activeProject.value ?: return
        val branch = _currentBranchName.value
        viewModelScope.launch {
            repository.commitChanges(active.id, branch, message)
            loadGitMetadata(active.id)
        }
    }

    fun createGitBranch(name: String) {
        val active = _activeProject.value ?: return
        viewModelScope.launch {
            repository.createBranch(active.id, name)
            loadGitMetadata(active.id)
        }
    }

    fun switchGitBranch(name: String) {
        val active = _activeProject.value ?: return
        viewModelScope.launch {
            repository.switchBranch(active.id, name)
            loadGitMetadata(active.id)
        }
    }

    // --- Gemini AI Actions ---

    fun sendChatMessage() {
        val prompt = chatInput.value.trim()
        if (prompt.isEmpty() || isAiLoading.value) return

        val messages = _chatMessages.value.toMutableList()
        messages.add(ChatMessage(prompt, true))
        _chatMessages.value = messages
        chatInput.value = ""
        isAiLoading.value = true

        val activeFileText = _activeFileContent.value
        val activeFilePath = getActiveFilePath() ?: "No active file open"

        viewModelScope.launch {
            val systemPrompt = """
                You are a highly knowledgeable software development assistant and real-time code auditor.
                Help the user build, debug, and understand their code.
                Active file context: Path is '$activeFilePath'. Content of this file:
                ```
                $activeFileText
                ```
                Be concise, precise, and polite. Provide working, production-ready code blocks if requested.
            """.trimIndent()

            val response = GeminiApiClient.getAiResponse(prompt, systemPrompt)
            withContext(Dispatchers.Main) {
                messages.add(ChatMessage(response, false))
                _chatMessages.value = messages
                isAiLoading.value = false
            }
        }
    }

    fun clearChat() {
        _chatMessages.value = listOf(ChatMessage("Hello! I am your Gemini AI coding companion. Ask me to complete code, optimize, explain, or generate new templates!", false))
    }

    // --- Quick Inline AI Operations ---

    fun performQuickAiAction(actionType: String, onCompleted: (String) -> Unit) {
        // actionType: "complete" | "explain" | "fix" | "optimize"
        val activeFileText = _activeFileContent.value
        val activeFilePath = getActiveFilePath() ?: return
        if (activeFileText.trim().isEmpty() || isAiLoading.value) return

        isAiLoading.value = true
        viewModelScope.launch {
            val prompt = when (actionType) {
                "complete" -> "Provide code autocomplete suggestions for the following code file: '$activeFilePath'. Complete the missing parts or suggest next lines. Return only the completed code block."
                "explain" -> "Provide a highly clear, 2-paragraph high-level overview explaining how the code inside '$activeFilePath' is structured, what its algorithms or visual outputs do, and how it executes."
                "fix" -> "Analyze the following code for compile-time syntax errors, bad variables, logic bugs, or unclosed tag brackets. Fix all issues and return only the corrected complete source code file."
                "optimize" -> "Analyze the following code file for performance bottlenecks, redundant declarations, slow iterations, or bloated styling rules. Optimize it and return only the optimized full source code."
                else -> "Help me with the following code."
            }

            val systemPrompt = "You are an expert compiler, static analyzer, and senior software engineer. Respond professionally."
            val userPrompt = "$prompt\n\nCode Content:\n```\n$activeFileText\n```"

            val response = GeminiApiClient.getAiResponse(userPrompt, systemPrompt)
            withContext(Dispatchers.Main) {
                isAiLoading.value = false
                onCompleted(response)
            }
        }
    }
}
